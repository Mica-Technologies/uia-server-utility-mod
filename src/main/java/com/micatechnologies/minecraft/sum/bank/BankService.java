package com.micatechnologies.minecraft.sum.bank;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.api.event.BankTransactionEvent;
import com.micatechnologies.minecraft.sum.economy.EconomyAttribution;
import com.micatechnologies.minecraft.sum.economy.EconomyEventPoster;
import com.micatechnologies.minecraft.sum.omceapi.OmceMoney;
import com.micatechnologies.minecraft.sum.omceapi.OmceParty;
import com.micatechnologies.minecraft.sum.omceapi.OmceProtocol;
import com.micatechnologies.minecraft.sum.omceapi.OmceTransactionRequest;
import com.micatechnologies.minecraft.sum.omceapi.service.OmceEconomyService;
import com.micatechnologies.minecraft.sum.economy.EconomyBridge;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;

/**
 * The player's <b>bank account</b> — savings held away from their person, reached through an ATM
 * or a debit card.
 *
 * <p>Unlike the {@link com.micatechnologies.minecraft.sum.economy.WalletService wallet}, the bank
 * has two possible owners:
 *
 * <ul>
 *   <li><b>A remote economy service</b>, when the Open MCEconomic API is configured. The account
 *       then lives outside Minecraft entirely and may be shared with other systems, so operations
 *       are asynchronous and can be refused after the fact.</li>
 *   <li><b>The world save</b> ({@link BankSavedData}) otherwise, which settles immediately.</li>
 * </ul>
 *
 * <p>Callers do not choose. Every method takes a callback and it is invoked on the server thread
 * either way — inline for the local backend, after a round trip for the remote one. That keeps
 * ATM code identical across both and means a server with no economy service still has working
 * banking.
 *
 * <p>Money only enters or leaves the bank through this class, and only from an ATM or debit card.
 * In-game purchases spend the wallet.
 */
public final class BankService {

    private BankService() {}

    /** Outcome of a bank operation. On failure {@link #error} is player-facing text. */
    public static final class Result {

        public final boolean ok;
        public final String error;
        public final double balance;

        /**
         * Machine-readable cause of a failure — an {@link OmceProtocol} {@code ERR_*} code — or
         * null when the cause has no useful classification.
         *
         * <p>{@link #error} is written for a player to read, and callers that need to *act* on a
         * failure had otherwise to match on its wording. That breaks the moment the wording is
         * improved, and it breaks silently, so the distinction between "you're broke" and "the
         * bank is down" is carried explicitly here instead.
         */
        @Nullable
        public final String failureCode;

        private Result(boolean ok, String error, double balance, @Nullable String failureCode) {
            this.ok = ok;
            this.error = error;
            this.balance = balance;
            this.failureCode = failureCode;
        }

        static Result success(double balance) {
            return new Result(true, null, balance, null);
        }

        static Result fail(String error) {
            return new Result(false, error, 0.0, null);
        }

        static Result fail(String error, @Nullable String failureCode) {
            return new Result(false, error, 0.0, failureCode);
        }
    }

    /** True when the account is backed by a remote economy service rather than the world save. */
    public static boolean isRemote() {
        return EconomyBridge.isRemoteBackend();
    }

    /**
     * Decimal places the bank can actually store.
     *
     * <p>A remote service declares its own scale, and it may be coarser than the wallet's.
     * The local backend stores a double and handles cents.
     */
    public static int getMinorUnitDigits() {
        OmceEconomyService remote = EconomyBridge.getRemoteService();
        return remote != null ? remote.getMinorUnitDigits() : 2;
    }

    /**
     * Rounds an amount <b>down</b> to something the bank can hold exactly.
     *
     * <p>Every transfer has to move the same figure on both sides, and the wallet is the
     * finer-grained one. Depositing $354.50 into a whole-unit bank must move $354 and leave the
     * 50c in the wallet: rounding the bank side up credits money the wallet never gave up, and
     * rounding the wallet side up charges for money the bank never received. Down is the only
     * direction that conserves value, so callers quantise before either side moves.
     */
    public static double quantise(double amount) {
        int digits = getMinorUnitDigits();
        double factor = 1.0;
        for (int i = 0; i < digits; i++) {
            factor *= 10.0;
        }
        // The epsilon absorbs float noise on a value that is really already representable,
        // so 137.0 does not floor to 136.
        return Math.floor(amount * factor + 1.0e-9) / factor;
    }

    /**
     * The player's bank balance.
     *
     * <p>Reads never block: the remote backend serves this from the balance cache its background
     * poller keeps fresh, and the local backend reads the world save directly.
     *
     * @return the balance in dollars, or {@link Double#NaN} if the account is not yet known —
     *     which for the remote backend means the service has not answered for this player yet.
     */
    public static double getBalance(EntityPlayer player) {
        if (player == null) {
            return Double.NaN;
        }
        OmceEconomyService remote = EconomyBridge.getRemoteService();
        if (remote != null) {
            return remote.getCachedBalanceDollars(player.getUniqueID());
        }
        if (player.world == null) {
            return Double.NaN;
        }
        return BankSavedData.get(player.world).getBalance(player.getUniqueID());
    }

    /**
     * Why the bank is unusable for this player, or null when it is fine.
     *
     * <p>Only a remote service can explain itself -- the local backend creates an account on
     * demand, so it never has a reason to give.
     */
    @Nullable
    public static String getUnavailableNotice(EntityPlayer player) {
        if (player == null) {
            return null;
        }
        OmceEconomyService remote = EconomyBridge.getRemoteService();
        return remote == null ? null : remote.getAccountNotice(player.getUniqueID());
    }

    /** True when the bank can be transacted with right now. */
    public static boolean isAvailable(EntityPlayer player) {
        return !Double.isNaN(getBalance(player));
    }

    /**
     * Moves money into the account.
     *
     * @param counterparty where the money came from, for the remote ledger — the player's wallet
     *     or physical cash. Ignored by the local backend, which keeps no ledger.
     */
    public static void deposit(EntityPlayer player, double amount, OmceParty counterparty,
        String reason, Consumer<Result> callback) {
        apply(player, amount, counterparty, OmceProtocol.TX_ATM_DEPOSIT, reason, null, callback);
    }

    /**
     * Moves money out of the account.
     *
     * <p>The callback fires only once the withdrawal is settled, so a caller may hand over bills
     * or credit a wallet on success without risking a later refusal.
     */
    public static void withdraw(EntityPlayer player, double amount, OmceParty counterparty,
        String reason, Consumer<Result> callback) {
        apply(player, -amount, counterparty, OmceProtocol.TX_ATM_WITHDRAW, reason, null, callback);
    }

    /**
     * Deposit on behalf of something that is not the ATM, naming its own transaction type and
     * ledger metadata.
     *
     * <p>Added for the public economy API, so a move an integrating mod made is distinguishable in
     * the ledger from a player standing at an ATM. Recording both as {@code atm_deposit} would
     * make an integration's activity impossible to audit or reconcile separately.
     *
     * @param transactionType an {@link OmceProtocol} {@code TX_*} value. Ignored by the local
     *     backend, which keeps no ledger.
     * @param metadata extra ledger annotations, or null. Silently dropped past the protocol's
     *     16-entry limit — metadata is diagnostic and never worth failing a real transaction over.
     */
    public static void deposit(EntityPlayer player, double amount, OmceParty counterparty,
        String reason, String transactionType, @Nullable Map<String, String> metadata,
        Consumer<Result> callback) {
        apply(player, amount, counterparty, transactionType, reason, metadata, callback);
    }

    /** Withdrawal counterpart of {@link #deposit(EntityPlayer, double, OmceParty, String, String,
     *  Map, Consumer)}. */
    public static void withdraw(EntityPlayer player, double amount, OmceParty counterparty,
        String reason, String transactionType, @Nullable Map<String, String> metadata,
        Consumer<Result> callback) {
        apply(player, -amount, counterparty, transactionType, reason, metadata, callback);
    }

    /**
     * @param delta positive to credit the account, negative to debit it.
     */
    private static void apply(EntityPlayer player, double delta, OmceParty counterparty,
        String transactionType, String reason, @Nullable Map<String, String> metadata,
        Consumer<Result> callback) {
        if (player == null) {
            callback.accept(Result.fail("Invalid account holder."));
            return;
        }
        if (delta == 0.0) {
            callback.accept(Result.success(getBalance(player)));
            return;
        }

        OmceEconomyService remote = EconomyBridge.getRemoteService();
        if (remote != null) {
            applyRemote(remote, player, delta, counterparty, transactionType, reason, metadata,
                callback);
            return;
        }
        callback.accept(applyLocal(player, delta, reason));
    }

    /** World-save backend: settles immediately, so the callback runs inline. */
    private static Result applyLocal(EntityPlayer player, double delta, @Nullable String reason) {
        if (player.world == null) {
            return Result.fail("The bank is unavailable right now.",
                OmceProtocol.ERR_SERVICE_UNAVAILABLE);
        }
        BankSavedData data = BankSavedData.get(player.world);
        if (!data.adjust(player.getUniqueID(), delta)) {
            // adjust() refuses only when the account cannot cover the debit.
            return Result.fail("Insufficient funds in your account.",
                OmceProtocol.ERR_INSUFFICIENT_FUNDS);
        }
        double balance = data.getBalance(player.getUniqueID());
        postSettled(player, delta, reason, balance, false);
        return Result.success(balance);
    }

    /**
     * Announces a settled movement to listeners.
     *
     * <p>Only ever called once a balance has actually changed, so a listener never sees a deposit
     * the backend went on to refuse.
     */
    private static void postSettled(EntityPlayer player, double delta, @Nullable String reason,
        double balance, boolean remote) {
        EconomyEventPoster.bankMoved(player,
            delta > 0.0 ? BankTransactionEvent.Type.DEPOSIT : BankTransactionEvent.Type.WITHDRAW,
            Math.abs(delta), balance, remote, reason);
    }

    /**
     * Remote backend: settles over the network, so the callback runs a round trip later.
     *
     * <p>Uses the confirming path rather than the optimistic one. Every caller of this class is
     * about to hand the player something irreversible — bills, or wallet credit — so nothing may
     * be granted until the service reports the transaction committed.
     */
    private static void applyRemote(OmceEconomyService remote, EntityPlayer player, double delta,
        OmceParty counterparty, String transactionType, String reason,
        @Nullable Map<String, String> metadata, Consumer<Result> callback) {
        int digits = remote.getMinorUnitDigits();
        long amountMinor;
        double magnitude = Math.abs(delta);
        try {
            // No rounding here on purpose. Callers quantise first so both sides of a transfer
            // move the same figure; silently rounding at this boundary is exactly how half a
            // Buck gets created or destroyed. An amount the service cannot represent reaching
            // this point is a caller bug, and failing loudly beats papering over it.
            if (!OmceMoney.isExactlyRepresentable(magnitude, digits)) {
                Sum.LOGGER.error("[bank] Refusing {}: not representable at {} decimal place(s). "
                    + "The caller should have quantised it first.", magnitude, digits);
                callback.accept(Result.fail("That amount can't be represented by the economy.",
                    OmceProtocol.ERR_AMOUNT_INVALID));
                return;
            }
            amountMinor = OmceMoney.toMinorUnits(magnitude, digits);
        } catch (IllegalArgumentException e) {
            callback.accept(Result.fail("That amount can't be represented by the economy.",
                OmceProtocol.ERR_AMOUNT_INVALID));
            return;
        }

        OmceParty self = OmceParty.player(player.getUniqueID(), player.getName(), null);
        OmceTransactionRequest.Builder builder = OmceTransactionRequest
            .builder(transactionType, amountMinor,
                delta < 0.0 ? self : counterparty,
                delta < 0.0 ? counterparty : self)
            .initiator(player.getUniqueID(), player.getName(), OmceTransactionRequest.ROLE_PLAYER)
            .reason(reason);
        if (metadata != null) {
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                builder.meta(entry.getKey(), entry.getValue());
            }
        }
        OmceTransactionRequest request = builder.build();

        // Captured now, restored around the post below. The callback runs a round trip later, by
        // which time the caller's attribution scope has long since closed — without this, every
        // bank movement an integrating mod made on a remote backend would be reported to
        // listeners as SUM's own.
        final String sourceModId = EconomyAttribution.currentModId();
        final String sourceReason = EconomyAttribution.currentReason();

        remote.processTransaction(request, result -> {
            if (result.isFailure()) {
                callback.accept(Result.fail(
                    result.getError().playerMessage(digits, remote.getCurrencySymbol()),
                    result.getError().getCode()));
                return;
            }
            if (!result.get().isCommitted()) {
                callback.accept(Result.fail("The bank did not complete that transaction."));
                return;
            }
            double balance = getBalance(player);
            try (EconomyAttribution.Scope ignored =
                     EconomyAttribution.enter(sourceModId, sourceReason)) {
                postSettled(player, delta, reason, balance, true);
            }
            callback.accept(Result.success(balance));
        });
    }

    /**
     * Sets a balance outright, for administrative correction.
     *
     * <p>Only supported on the local backend. A remote service owns its own account and may have
     * its own rules about absolute adjustments, so this refuses rather than guessing.
     */
    public static boolean adminSetBalance(EntityPlayer player, double balance) {
        if (player == null || player.world == null) {
            return false;
        }
        if (isRemote()) {
            Sum.LOGGER.warn("[bank] Refusing to set {}'s bank balance directly: the account is "
                + "owned by a remote economy service.", player.getName());
            return false;
        }
        BankSavedData.get(player.world).setBalance(player.getUniqueID(), balance);
        return true;
    }

    /** Adjusts a balance administratively. Local backend only, for the reason above. */
    public static boolean adminAdjust(EntityPlayer player, double delta, @Nullable String reason) {
        if (player == null || player.world == null) {
            return false;
        }
        if (isRemote()) {
            Sum.LOGGER.warn("[bank] Refusing to adjust {}'s bank balance directly: the account is "
                + "owned by a remote economy service.", player.getName());
            return false;
        }
        return BankSavedData.get(player.world).adjust(player.getUniqueID(), delta);
    }
}
