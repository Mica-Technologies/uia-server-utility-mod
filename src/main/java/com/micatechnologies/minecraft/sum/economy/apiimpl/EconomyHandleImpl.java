package com.micatechnologies.minecraft.sum.economy.apiimpl;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.api.EconomyFailure;
import com.micatechnologies.minecraft.sum.api.EconomyHandle;
import com.micatechnologies.minecraft.sum.api.EconomyResult;
import com.micatechnologies.minecraft.sum.api.EconomyScope;
import com.micatechnologies.minecraft.sum.api.EconomyStatus;
import com.micatechnologies.minecraft.sum.api.EscrowResult;
import com.micatechnologies.minecraft.sum.api.EscrowTicket;
import com.micatechnologies.minecraft.sum.bank.BankService;
import com.micatechnologies.minecraft.sum.economy.EconomyAttribution;
import com.micatechnologies.minecraft.sum.economy.MoneyMath;
import com.micatechnologies.minecraft.sum.economy.WalletService;
import com.micatechnologies.minecraft.sum.omceapi.OmceParty;
import com.micatechnologies.minecraft.sum.omceapi.OmceProtocol;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

/**
 * One integrating mod's view of SUM's economy.
 *
 * <p><b>Scopes are read live, not frozen at acquire time.</b> A handle holds nothing but a mod id;
 * every call re-reads that mod's grants from config. Caching them would mean an operator who
 * revokes a mod's access sees no effect until the next restart, which defeats the point of having
 * a revocable allowlist — the mod would keep spending player money for the rest of the session.
 * The cost is a map lookup per call, against operations that touch inventories and the network.
 *
 * <p>Every mutating call passes through {@link #guard} first. That single choke point is where
 * scope, side, thread and amount are checked, and it is where a future local transaction ledger
 * would hook in.
 */
public final class EconomyHandleImpl implements EconomyHandle {

    /** Ledger key naming the mod behind a transaction, so an integration's activity is auditable. */
    static final String META_SOURCE_MOD = "sum.source_mod";

    /** Matches the protocol's reason limit, so the log line and the ledger entry agree. */
    static final int MAX_REASON_LENGTH = 256;

    private final String modId;

    EconomyHandleImpl(String modId) {
        this.modId = modId;
    }

    @Override
    public String getModId() {
        return modId;
    }

    @Override
    public Set<EconomyScope> getScopes() {
        return EconomyAuthorizer.scopesFor(modId);
    }

    @Override
    public boolean hasScope(EconomyScope scope) {
        return scope != null && getScopes().contains(scope);
    }

    @Override
    public EconomyStatus getStatus() {
        return EconomyApiRegistry.currentStatus();
    }

    // -------------------------------------------------------------------------------------------
    // Guard
    // -------------------------------------------------------------------------------------------

    /**
     * Checks everything that must hold before money moves.
     *
     * <p>Ordered so the most specific, most actionable failure wins: a mod that is both missing a
     * scope and calling off-thread should hear about the scope, because that is the one an
     * operator can fix from config.
     *
     * @param limit the configured per-call cap to apply, or 0 for none.
     * @return null when the call may proceed, otherwise the failure to return to the caller.
     */
    @Nullable
    EconomyResult guard(EconomyScope required, @Nullable EntityPlayer player, double amount,
            double limit) {
        if (!hasScope(required)) {
            return EconomyResult.fail(EconomyFailure.MISSING_SCOPE,
                "'" + modId + "' is not granted " + required.getToken()
                    + " in economy_integration.allowedMods.");
        }
        if (player == null) {
            return EconomyResult.fail(EconomyFailure.INVALID_AMOUNT, "No player was given.");
        }
        if (player.world != null && player.world.isRemote) {
            return EconomyResult.fail(EconomyFailure.CLIENT_SIDE);
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return EconomyResult.fail(EconomyFailure.ECONOMY_UNAVAILABLE,
                "The server is not running.");
        }
        if (!server.isCallingFromMinecraftThread()) {
            // Refused rather than attempted: the wallet mutates player inventory and capabilities
            // with no locking, so proceeding here is a data race that corrupts real balances.
            return EconomyResult.fail(EconomyFailure.WRONG_THREAD,
                "'" + modId + "' called the economy off the server thread.");
        }
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            return EconomyResult.fail(EconomyFailure.INVALID_AMOUNT,
                "The amount is not a finite number.");
        }
        if (amount < 0.0) {
            return EconomyResult.fail(EconomyFailure.INVALID_AMOUNT,
                "The amount is negative. Use the opposite operation instead.");
        }
        if (limit > 0.0 && amount > limit) {
            return EconomyResult.fail(EconomyFailure.AMOUNT_TOO_LARGE);
        }
        return null;
    }

    /** The wallet cap an operator configured for integrating mods, or 0 for none. */
    static double walletLimit() {
        return SumConfig.getEconomyIntegrationMaxWalletTransaction();
    }

    /** The bank cap an operator configured for integrating mods, or 0 for none. */
    static double bankLimit() {
        return SumConfig.getEconomyIntegrationMaxBankTransaction();
    }

    // -------------------------------------------------------------------------------------------
    // Wallet
    // -------------------------------------------------------------------------------------------

    @Override
    public OptionalDouble getWalletBalance(EntityPlayer player) {
        if (!hasScope(EconomyScope.WALLET_READ) || player == null) {
            return OptionalDouble.empty();
        }
        return finite(WalletService.getTotal(player));
    }

    @Override
    public boolean canAfford(EntityPlayer player, double amount) {
        if (!hasScope(EconomyScope.WALLET_READ) || player == null
                || Double.isNaN(amount) || Double.isInfinite(amount)) {
            return false;
        }
        return WalletService.canAfford(player, MoneyMath.roundToCents(amount));
    }

    @Override
    public EconomyResult walletSpend(EntityPlayer player, double amount, String reason) {
        EconomyResult refusal = guard(EconomyScope.WALLET_WRITE, player, amount, walletLimit());
        if (refusal != null) {
            return log("walletSpend", player, amount, reason, refusal);
        }
        if (!WalletService.isAvailable()) {
            return log("walletSpend", player, amount, reason,
                EconomyResult.fail(EconomyFailure.ECONOMY_UNAVAILABLE));
        }
        double rounded = MoneyMath.roundToCents(amount);
        if (rounded == 0.0) {
            return log("walletSpend", player, amount, reason,
                EconomyResult.ok(WalletService.getTotal(player)));
        }
        boolean spent;
        try (EconomyAttribution.Scope ignored = EconomyAttribution.enter(modId, reason)) {
            spent = WalletService.spend(player, rounded);
        }
        if (!spent) {
            return log("walletSpend", player, rounded, reason,
                EconomyResult.fail(EconomyFailure.INSUFFICIENT_FUNDS));
        }
        return log("walletSpend", player, rounded, reason,
            EconomyResult.ok(WalletService.getTotal(player)));
    }

    @Override
    public EconomyResult walletCredit(EntityPlayer player, double amount, String reason) {
        EconomyResult refusal = guard(EconomyScope.WALLET_WRITE, player, amount, walletLimit());
        if (refusal != null) {
            return log("walletCredit", player, amount, reason, refusal);
        }
        if (!WalletService.isAvailable()) {
            return log("walletCredit", player, amount, reason,
                EconomyResult.fail(EconomyFailure.ECONOMY_UNAVAILABLE));
        }
        double rounded = MoneyMath.roundToCents(amount);
        boolean credited = true;
        if (rounded > 0.0) {
            try (EconomyAttribution.Scope ignored = EconomyAttribution.enter(modId, reason)) {
                credited = WalletService.credit(player, rounded);
            }
        }
        if (!credited) {
            return log("walletCredit", player, rounded, reason,
                EconomyResult.fail(EconomyFailure.BACKEND_REFUSED,
                    "The wallet could not be credited."));
        }
        return log("walletCredit", player, rounded, reason,
            EconomyResult.ok(WalletService.getTotal(player)));
    }

    // -------------------------------------------------------------------------------------------
    // Bank
    // -------------------------------------------------------------------------------------------

    @Override
    public OptionalDouble getBankBalance(EntityPlayer player) {
        if (!hasScope(EconomyScope.BANK_READ) || player == null) {
            return OptionalDouble.empty();
        }
        return finite(BankService.getBalance(player));
    }

    @Override
    public double quantiseForBank(double amount) {
        return BankService.quantise(amount);
    }

    @Override
    public void bankDeposit(EntityPlayer player, double amount, String reason,
            Consumer<EconomyResult> callback) {
        bankMove(player, amount, reason, callback, true);
    }

    @Override
    public void bankWithdraw(EntityPlayer player, double amount, String reason,
            Consumer<EconomyResult> callback) {
        bankMove(player, amount, reason, callback, false);
    }

    /**
     * Shared body of {@link #bankDeposit} and {@link #bankWithdraw}.
     *
     * <p>The callback is invoked on every path, including immediate rejection. A consumer told
     * "your callback always fires" that never hears back has no way to recover — it waits forever
     * on a wager that will never resolve.
     */
    private void bankMove(EntityPlayer player, double amount, String reason,
            Consumer<EconomyResult> callback, boolean deposit) {
        String operation = deposit ? "bankDeposit" : "bankWithdraw";
        EconomyResult refusal = guard(EconomyScope.BANK_WRITE, player, amount, bankLimit());
        if (refusal != null) {
            deliver(callback, log(operation, player, amount, reason, refusal));
            return;
        }
        if (!BankService.isAvailable(player)) {
            String notice = BankService.getUnavailableNotice(player);
            deliver(callback, log(operation, player, amount, reason,
                EconomyResult.fail(EconomyFailure.ECONOMY_UNAVAILABLE, notice)));
            return;
        }

        // Quantise before anything moves: a remote bank may be coarser than the wallet, and
        // BankService refuses outright rather than silently rounding at its own boundary.
        double quantised = BankService.quantise(amount);
        if (quantised <= 0.0) {
            EconomyResult outcome = amount == 0.0
                ? EconomyResult.ok(BankService.getBalance(player))
                : EconomyResult.fail(EconomyFailure.NOT_REPRESENTABLE,
                    "That amount is smaller than the smallest unit this bank can hold.");
            deliver(callback, log(operation, player, amount, reason, outcome));
            return;
        }

        Map<String, String> metadata = new HashMap<>();
        metadata.put(META_SOURCE_MOD, modId);
        String attributed = attributeReason(modId, reason);
        OmceParty counterparty = OmceParty.system("mod:" + modId);

        Consumer<BankService.Result> onSettled = result -> deliver(callback,
            log(operation, player, quantised, reason, translate(result)));

        try (EconomyAttribution.Scope ignored = EconomyAttribution.enter(modId, reason)) {
            if (deposit) {
                BankService.deposit(player, quantised, counterparty, attributed,
                    OmceProtocol.TX_MOD_DEPOSIT, metadata, onSettled);
            } else {
                BankService.withdraw(player, quantised, counterparty, attributed,
                    OmceProtocol.TX_MOD_WITHDRAW, metadata, onSettled);
            }
        }
    }

    /**
     * Turns a {@link BankService.Result} into the public API's result type.
     *
     * <p>Classification comes from {@code failureCode}, not from the message text — the message is
     * written for a player and gets reworded, and matching on its wording would break silently the
     * next time somebody improves it.
     */
    static EconomyResult translate(BankService.Result result) {
        if (result.ok) {
            return EconomyResult.ok(result.balance);
        }
        return EconomyResult.fail(classify(result.failureCode), result.error);
    }

    /** Maps a protocol error code onto the public failure vocabulary. */
    static EconomyFailure classify(@Nullable String failureCode) {
        if (failureCode == null) {
            return EconomyFailure.BACKEND_REFUSED;
        }
        switch (failureCode) {
            case OmceProtocol.ERR_INSUFFICIENT_FUNDS:
                return EconomyFailure.INSUFFICIENT_FUNDS;
            case OmceProtocol.ERR_AMOUNT_INVALID:
                return EconomyFailure.NOT_REPRESENTABLE;
            case OmceProtocol.ERR_SERVICE_UNAVAILABLE:
            case OmceProtocol.ERR_RATE_LIMITED:
            case OmceProtocol.ERR_TRANSPORT:
            case OmceProtocol.ERR_BAD_RESPONSE:
            case OmceProtocol.ERR_NOT_CONNECTED:
                return EconomyFailure.BACKEND_ERROR;
            default:
                // Everything else — frozen accounts, limits, unknown accounts — is the backend
                // deliberately saying no, which is a different thing from being unable to ask.
                return EconomyFailure.BACKEND_REFUSED;
        }
    }

    // -------------------------------------------------------------------------------------------
    // Escrow
    // -------------------------------------------------------------------------------------------

    @Override
    public EscrowResult escrowOpen(EntityPlayer player, double amount, String reason) {
        EconomyResult refusal = guard(EconomyScope.ESCROW, player, amount, walletLimit());
        if (refusal != null) {
            log("escrowOpen", player, amount, reason, refusal);
            return EscrowResult.fail(refusal.getFailure(), refusal.getMessage());
        }
        try (EconomyAttribution.Scope ignored = EconomyAttribution.enter(modId, reason)) {
            return EscrowService.open(modId, player, amount, reason);
        }
    }

    @Override
    public EconomyResult escrowRelease(EscrowTicket ticket, EntityPlayer recipient, String reason) {
        // Checked before the guard, which would otherwise report a null player as an invalid
        // amount — true in a sense, and useless to whoever has to debug it.
        if (recipient == null) {
            return EconomyResult.fail(EconomyFailure.RECIPIENT_OFFLINE,
                "No recipient was given to release the held money to.");
        }
        // The amount is not the caller's to state here — it comes from the stored ticket — so the
        // guard checks zero and the operator's cap does not apply to paying out a held stake.
        EconomyResult refusal = guard(EconomyScope.ESCROW, recipient, 0.0, 0.0);
        if (refusal != null) {
            return log("escrowRelease", recipient, null, reason, refusal);
        }
        try (EconomyAttribution.Scope ignored = EconomyAttribution.enter(modId, reason)) {
            return log("escrowRelease", recipient, null, reason,
                EscrowService.release(modId, ticket, recipient, reason));
        }
    }

    @Override
    public EconomyResult escrowRefund(EscrowTicket ticket, String reason) {
        // No recipient argument to validate: a refund goes to whoever the stored ticket says put
        // the money up, so the usual player checks happen inside the service.
        if (!hasScope(EconomyScope.ESCROW)) {
            return EconomyResult.fail(EconomyFailure.MISSING_SCOPE,
                "'" + modId + "' is not granted escrow in economy_integration.allowedMods.");
        }
        if (!onServerThread()) {
            return EconomyResult.fail(EconomyFailure.WRONG_THREAD,
                "'" + modId + "' called the economy off the server thread.");
        }
        try (EconomyAttribution.Scope ignored = EconomyAttribution.enter(modId, reason)) {
            return log("escrowRefund", null, null, reason,
                EscrowService.refund(modId, ticket, reason));
        }
    }

    @Override
    public EconomyResult escrowForfeit(EscrowTicket ticket, String reason) {
        // Same shape as a refund: no recipient to validate, and no player needed at all, because
        // destroying the money is not a payment to anybody.
        if (!hasScope(EconomyScope.ESCROW)) {
            return EconomyResult.fail(EconomyFailure.MISSING_SCOPE,
                "'" + modId + "' is not granted escrow in economy_integration.allowedMods.");
        }
        if (!onServerThread()) {
            return EconomyResult.fail(EconomyFailure.WRONG_THREAD,
                "'" + modId + "' called the economy off the server thread.");
        }
        try (EconomyAttribution.Scope ignored = EconomyAttribution.enter(modId, reason)) {
            return log("escrowForfeit", null, null, reason,
                EscrowService.forfeit(modId, ticket, reason));
        }
    }

    @Override
    public List<EscrowTicket> listOpenEscrows() {
        if (!hasScope(EconomyScope.ESCROW) || !onServerThread()) {
            return Collections.emptyList();
        }
        return EscrowService.listOpen(modId);
    }

    /** True when the caller is on the server thread and a server exists to be on. */
    private static boolean onServerThread() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        return server != null && server.isCallingFromMinecraftThread();
    }

    // -------------------------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------------------------

    /** {@link Double#NaN} means "unknown" inside SUM; the public API says so with an empty. */
    private static OptionalDouble finite(double value) {
        return Double.isNaN(value) ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    /**
     * Tags a reason with the mod that caused it, for the log and the remote ledger.
     *
     * <p>Attribution is the concrete thing the allowlist buys an operator: given a balance that
     * moved unexpectedly, the ledger entry names which integration moved it. Truncated to the
     * protocol's limit here rather than leaving it to the request builder, so the line in the
     * server log and the line in the ledger read the same.
     */
    static String attributeReason(String modId, @Nullable String reason) {
        String prefix = "[" + modId + "] ";
        String body = (reason == null || reason.trim().isEmpty()) ? "(no reason given)"
            : reason.trim();
        String combined = prefix + body;
        return combined.length() <= MAX_REASON_LENGTH ? combined
            : combined.substring(0, MAX_REASON_LENGTH);
    }

    /**
     * Records an operation when the operator has audit logging on, and returns the result
     * unchanged so call sites can {@code return log(...)} in one line.
     *
     * <p>Failures caused by the calling mod are logged at WARN even with audit logging off: a
     * missing scope or an off-thread call is a bug an operator needs to see, not routine traffic
     * they opted out of.
     */
    private EconomyResult log(String operation, @Nullable EntityPlayer player,
            @Nullable Double amount, @Nullable String reason, EconomyResult result) {
        boolean callerError = result.isCallerError();
        if (!callerError && !SumConfig.isEconomyIntegrationLoggingEnabled()) {
            return result;
        }
        String what = describe(operation, player, amount);
        if (callerError) {
            Sum.LOGGER.warn("[economy-api] {} {}: {} ({})", modId, what, result.getFailure(),
                result.getMessage());
        } else if (result.isOk()) {
            Sum.LOGGER.info("[economy-api] {} {} -> ok. Reason: {}", modId, what,
                attributeReason(modId, reason));
        } else {
            Sum.LOGGER.info("[economy-api] {} {} -> {}: {}", modId, what, result.getFailure(),
                result.getMessage());
        }
        return result;
    }

    /**
     * Renders the subject of a log line, leaving out what the call site genuinely does not know.
     *
     * <p>The escrow settlements identify their money by ticket, not by amount, and a refund or
     * forfeit does not even need a player. Printing the placeholders anyway produced lines reading
     * {@code escrowForfeit 0.0 for unknown -> ok} next to the accurate one {@link EscrowService}
     * writes — which invites an operator to read a settled hold as a $0 movement, in the log whose
     * whole purpose is reconstructing where money went.
     */
    private static String describe(String operation, @Nullable EntityPlayer player,
            @Nullable Double amount) {
        StringBuilder text = new StringBuilder(operation);
        if (amount != null) {
            text.append(' ').append(amount);
        }
        if (player != null) {
            text.append(" for ").append(player.getName());
        }
        return text.toString();
    }

    /** Hands a result to a bank callback, containing any exception the consumer throws. */
    private void deliver(@Nullable Consumer<EconomyResult> callback, EconomyResult result) {
        if (callback == null) {
            return;
        }
        try {
            callback.accept(result);
        } catch (Throwable t) {
            Sum.LOGGER.error("[economy-api] '{}' threw from its economy callback", modId, t);
        }
    }
}
