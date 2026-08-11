package com.micatechnologies.minecraft.sum.api;

import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.entity.player.EntityPlayer;

/**
 * An authorized mod's access to SUM's economy, obtained from {@link SumEconomy#acquire(String)}.
 *
 * <p>There are three pools of money behind this interface, and confusing them is the easiest way
 * to write a broken integration:
 *
 * <ul>
 *   <li><b>Wallet</b> — what a player carries and what every in-game purchase spends. Always local
 *       to the world save, so it settles synchronously and cannot fail after the fact. This is
 *       almost certainly the one you want.</li>
 *   <li><b>Bank</b> — savings, normally reachable only at an ATM. May be owned by a remote service,
 *       so every operation is asynchronous and can be refused after a round trip.</li>
 *   <li><b>Escrow</b> — wallet money the server holds on your behalf between a commitment and its
 *       resolution. See {@link EscrowTicket}.</li>
 * </ul>
 *
 * <h2>Threading</h2>
 *
 * <p><b>Every mutating method must be called on the server thread.</b> The wallet mutates player
 * inventory and capabilities with no locking, so an off-thread call is a data race. Calls from the
 * wrong thread are refused with {@link EconomyFailure#WRONG_THREAD} rather than allowed to corrupt
 * state. Calls from a client are refused with {@link EconomyFailure#CLIENT_SIDE}.
 *
 * <p>Bank callbacks are invoked <b>on the server thread</b>, exactly once, whether the backend is
 * local or remote — so it is safe to touch world state inside one.
 *
 * <h2>Scopes</h2>
 *
 * <p>Each method below names the {@link EconomyScope} it requires. A call outside your granted
 * scopes fails with {@link EconomyFailure#MISSING_SCOPE} and moves no money. Check
 * {@link #hasScope} at startup if you want to disable a feature cleanly rather than fail per-use.
 *
 * <h2>Failure</h2>
 *
 * <p>A failed {@link EconomyResult} always means <b>nothing moved</b>. No compensating action is
 * needed, and no partial state was left behind.
 *
 * <p>Implementations are supplied by SUM. Do not implement this interface.
 */
public interface EconomyHandle {

    /** The mod id this handle was issued to. Every transaction is attributed to it. */
    String getModId();

    /** The granted scopes, already closed under {@link EconomyScope#expand}. Unmodifiable. */
    Set<EconomyScope> getScopes();

    /** Convenience for {@code getScopes().contains(scope)}. */
    boolean hasScope(EconomyScope scope);

    /** What the economy can currently do. Re-read rather than caching — see {@link EconomyStatus}. */
    EconomyStatus getStatus();

    // ---------------------------------------------------------------------------------------
    // Wallet — synchronous, server thread only.
    // ---------------------------------------------------------------------------------------

    /**
     * The player's total spendable money: their invisible balance plus the face value of every
     * bill they are carrying.
     *
     * <p>Requires {@link EconomyScope#WALLET_READ}.
     *
     * @return empty when the scope is missing, no backend is attached, or the balance is unknown.
     */
    OptionalDouble getWalletBalance(EntityPlayer player);

    /**
     * True when the wallet can cover {@code amount} right now.
     *
     * <p>This is a hint, not a reservation — nothing stops the player spending in between. Prefer
     * calling {@link #walletSpend} and handling {@link EconomyFailure#INSUFFICIENT_FUNDS}, or
     * {@link #escrowOpen} when you need the money actually held.
     *
     * <p>Requires {@link EconomyScope#WALLET_READ}.
     */
    boolean canAfford(EntityPlayer player, double amount);

    /**
     * Takes money from the player's wallet, breaking carried bills if the invisible balance alone
     * cannot cover it.
     *
     * <p>All-or-nothing: on failure the player's inventory and balance are untouched.
     *
     * <p>Requires {@link EconomyScope#WALLET_WRITE}.
     *
     * @param reason short free text recorded in logs and, on a remote backend, the ledger. Prefixed
     *     with your mod id automatically.
     */
    EconomyResult walletSpend(EntityPlayer player, double amount, String reason);

    /**
     * Gives money to the player's wallet, as an invisible balance rather than as bills.
     *
     * <p>This <b>creates</b> money — it is not paired with a debit anywhere. Use it for winnings,
     * rewards and refunds, and be deliberate about it: a server may cap it with
     * {@code economy_integration.maxWalletTransaction}.
     *
     * <p>Requires {@link EconomyScope#WALLET_WRITE}.
     */
    EconomyResult walletCredit(EntityPlayer player, double amount, String reason);

    // ---------------------------------------------------------------------------------------
    // Bank — asynchronous, callback on the server thread.
    // ---------------------------------------------------------------------------------------

    /**
     * The player's bank balance. Never blocks: a remote backend serves this from a cache its
     * background poller keeps fresh.
     *
     * <p>Requires {@link EconomyScope#BANK_READ}.
     *
     * @return empty when the scope is missing or the backend has not yet reported this player's
     *     account — which is a normal state shortly after login on a remote backend.
     */
    OptionalDouble getBankBalance(EntityPlayer player);

    /**
     * Rounds an amount <b>down</b> to something the bank can hold exactly.
     *
     * <p>A remote bank may be coarser than the wallet, even whole units. Moving $354.50 into a
     * whole-unit bank must move $354 and leave the 50c in the wallet: rounding the bank side up
     * credits money the wallet never gave up. Call this before moving money on either side, and
     * move the same figure on both.
     *
     * <p>Requires no scope.
     */
    double quantiseForBank(double amount);

    /**
     * Puts money into the player's bank account. Does <b>not</b> take it from their wallet — that
     * is a separate {@link #walletSpend}, and you are responsible for reversing one if the other
     * fails.
     *
     * <p>Requires {@link EconomyScope#BANK_WRITE}. The callback runs on the server thread, exactly
     * once, including on immediate rejection.
     */
    void bankDeposit(EntityPlayer player, double amount, String reason,
            Consumer<EconomyResult> callback);

    /**
     * Takes money out of the player's bank account, without putting it anywhere. Pair it with a
     * {@link #walletCredit} inside the callback, and deposit it back if that credit fails.
     *
     * <p>Requires {@link EconomyScope#BANK_WRITE}. The callback runs on the server thread, exactly
     * once, including on immediate rejection.
     */
    void bankWithdraw(EntityPlayer player, double amount, String reason,
            Consumer<EconomyResult> callback);

    // ---------------------------------------------------------------------------------------
    // Escrow — synchronous, server thread only.
    // ---------------------------------------------------------------------------------------

    /**
     * Debits the player's wallet and holds the money against a ticket.
     *
     * <p>Requires {@link EconomyScope#ESCROW}.
     *
     * @return a ticket on success; on failure nothing was debited.
     */
    EscrowResult escrowOpen(EntityPlayer player, double amount, String reason);

    /**
     * Closes a ticket and credits the held amount to {@code recipient} — who may be the original
     * owner, another player, or whoever won.
     *
     * <p>If the credit cannot be applied the ticket <b>stays open</b> and this fails, so money is
     * never lost to a half-completed release. The recipient must be online
     * ({@link EconomyFailure#RECIPIENT_OFFLINE}).
     *
     * <p>Requires {@link EconomyScope#ESCROW}, and the ticket must belong to your mod.
     */
    EconomyResult escrowRelease(EscrowTicket ticket, EntityPlayer recipient, String reason);

    /**
     * Closes a ticket and returns the held amount to the player who opened it.
     *
     * <p>Requires {@link EconomyScope#ESCROW}, and the ticket must belong to your mod.
     */
    EconomyResult escrowRefund(EscrowTicket ticket, String reason);

    /**
     * Closes a ticket and <b>destroys</b> the money it held — a lost wager the house keeps.
     *
     * <p>This is the only operation here that removes value from the economy, and it is deliberately
     * a separate named method rather than "release to nobody": a null recipient reaching
     * {@link #escrowRelease} is far more likely to be a bug than an intention, and silently burning
     * a player's stake on one would be the worst possible way to handle it.
     *
     * <p>Destruction matches how SUM's own server shop treats its takings. If your house should
     * accumulate the money instead, release the ticket to a house player and keep the books
     * yourself.
     *
     * <p>Unlike a release or a refund, this needs <b>nobody online</b> — a round can be settled
     * after the player logs out. The trade-off is that no {@code EscrowEvent} is posted in that
     * case, because every economy event names a player.
     *
     * <p>Requires {@link EconomyScope#ESCROW}, and the ticket must belong to your mod.
     */
    EconomyResult escrowForfeit(EscrowTicket ticket, String reason);

    /**
     * Every ticket your mod currently holds open, oldest first.
     *
     * <p>Call this on server start: a crash between opening a ticket and resolving it leaves the
     * money held, and this is how you find it and refund it. Tickets belonging to other mods are
     * never returned.
     *
     * <p>Requires {@link EconomyScope#ESCROW}.
     */
    List<EscrowTicket> listOpenEscrows();
}
