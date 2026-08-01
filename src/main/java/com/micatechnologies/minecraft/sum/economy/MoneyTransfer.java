package com.micatechnologies.minecraft.sum.economy;

import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.omceapi.OmceMoney;
import com.micatechnologies.minecraft.sum.omceapi.service.OmceEconomyService;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Player-to-player money transfer, shared by the {@code /pay} command and the phone "Pay" app so
 * the validation, rounding, fee, and settlement logic live in one place.
 *
 * <p>Both participants must be online. The sender is charged the full {@code amount}; the
 * recipient receives {@code amount} minus the configured {@link SumConfig#getPayFeePercent() fee},
 * which vanishes as a money sink.
 *
 * <p><b>Why the result is delivered by callback.</b> Against a remote economy the transfer is sent
 * as a <i>single</i> transaction naming both players, which the service applies in full or not at
 * all. That answer is not available when this method returns, so callers receive it later.
 *
 * <p>The obvious alternative — debit the sender, then credit the recipient — is unsound here.
 * Both calls would return optimistically, so a sender's debit that the service later refused would
 * leave the recipient credited out of nothing. Local backends have no such gap and still take the
 * two-step path in {@link #transferLocally}.
 */
public final class MoneyTransfer {

    private MoneyTransfer() {}

    /** Outcome of a transfer attempt. On failure, {@link #error} carries a user-facing reason. */
    public static final class Result {
        public final boolean ok;
        public final String error;
        public final double amount;    // total charged from the sender
        public final double credited;  // amount actually received by the recipient (amount - fee)
        public final double fee;

        private Result(boolean ok, String error, double amount, double credited, double fee) {
            this.ok = ok;
            this.error = error;
            this.amount = amount;
            this.credited = credited;
            this.fee = fee;
        }

        static Result fail(String error) {
            return new Result(false, error, 0.0, 0.0, 0.0);
        }

        static Result success(double amount, double credited, double fee) {
            return new Result(true, null, amount, credited, fee);
        }
    }

    /**
     * Validates and performs the transfer, delivering the outcome to {@code callback} on the
     * server thread.
     *
     * <p>Callback-based rather than returning a {@link Result} because a remote economy settles
     * asynchronously and the answer is not known when this method returns. A local backend invokes
     * the callback inline, so its callers behave exactly as before.
     */
    public static void transfer(EntityPlayer from, EntityPlayer to, double rawAmount,
        Consumer<Result> callback) {
        if (!SumConfig.isPayEnabled()) {
            callback.accept(Result.fail("Player-to-player payments are disabled on this server."));
            return;
        }
        if (from == null || to == null) {
            callback.accept(Result.fail("Invalid payment participants."));
            return;
        }
        if (from == to || from.getUniqueID().equals(to.getUniqueID())) {
            callback.accept(Result.fail("You can't pay yourself."));
            return;
        }
        if (!EconomyBridge.isAvailable()) {
            callback.accept(Result.fail("No economy backend is loaded."));
            return;
        }

        // Amount validation, rounding, and fee math are pure — factored into computeAmounts so
        // they can be unit-tested without an economy backend or live players.
        Result computed = computeAmounts(rawAmount, SumConfig.getPayFeePercent());
        if (!computed.ok) {
            callback.accept(computed);
            return;
        }
        double amount = computed.amount;
        double credited = computed.credited;
        double fee = computed.fee;

        double balance = EconomyBridge.getBalance(from);
        if (Double.isNaN(balance)) {
            callback.accept(Result.fail("You don't have a balance handler attached."));
            return;
        }
        if (balance < amount) {
            callback.accept(Result.fail("Insufficient funds. Need $" + money(amount)
                + ", have $" + money(balance) + "."));
            return;
        }

        OmceEconomyService remote = EconomyBridge.getRemoteService();
        if (remote != null) {
            transferViaRemote(remote, from, to, amount, credited, fee, callback);
            return;
        }
        callback.accept(transferLocally(from, to, amount, credited, fee));
    }

    /**
     * Settles the transfer as one atomic transaction against the remote economy.
     *
     * <p>Debiting the sender and crediting the recipient as two independent optimistic calls would
     * be unsound: both return before the service has answered, so a refused debit would leave the
     * recipient credited from nothing. A single transaction with two settled parties is applied by
     * the service in full or not at all.
     */
    private static void transferViaRemote(OmceEconomyService remote, EntityPlayer from,
        EntityPlayer to, double amount, double credited, double fee, Consumer<Result> callback) {
        int digits = remote.getMinorUnitDigits();
        long[] units;
        try {
            units = toSettlementUnits(amount, fee, digits);
        } catch (IllegalArgumentException e) {
            callback.accept(Result.fail("That amount can't be represented by the economy."));
            return;
        }
        final long amountMinor = units[0];
        final long settledFee = units[1];
        remote.transferBetweenPlayers(from, to, amountMinor, settledFee, result -> {
            if (result.isFailure()) {
                callback.accept(Result.fail(result.getError()
                    .playerMessage(digits, remote.getCurrencySymbol())));
                return;
            }
            if (!result.get().isCommitted()) {
                callback.accept(Result.fail("The payment was not completed."));
                return;
            }
            // Report the figures the service actually settled rather than our pre-rounding ones.
            double settledAmount = OmceMoney.toDollars(amountMinor, digits);
            callback.accept(Result.success(settledAmount,
                OmceMoney.toDollars(amountMinor - settledFee, digits),
                OmceMoney.toDollars(settledFee, digits)));
        });
    }

    /**
     * The original two-step path, still correct for local backends: they settle synchronously and
     * cannot fail after returning true, so the refund branch here is genuinely reachable only when
     * the credit itself is rejected up front.
     */
    private static Result transferLocally(EntityPlayer from, EntityPlayer to, double amount,
        double credited, double fee) {
        if (!EconomyBridge.adjustBalance(from, -amount)) {
            return Result.fail("Payment failed — your balance could not be charged.");
        }
        if (credited > 0.0 && !EconomyBridge.adjustBalance(to, credited)) {
            EconomyBridge.adjustBalance(from, amount);
            return Result.fail("Payment failed — the recipient could not be credited. You were refunded.");
        }
        return Result.success(amount, credited, fee);
    }

    /**
     * Converts a transfer's dollar amount and fee into the protocol's integer minor units.
     *
     * <p>The two round in opposite directions on purpose. The sender's charge rounds <i>up</i>, so
     * a coarse currency never settles for less than they agreed to pay. The fee rounds half-up and
     * is then clamped to the charge, so the recipient's share ({@code amount - fee}) can never go
     * negative and the pair always sums back to exactly what the sender was charged.
     *
     * @return {@code [amountMinor, feeMinor]}, with {@code 0 <= feeMinor <= amountMinor}.
     * @throws IllegalArgumentException if either value is unrepresentable at this scale.
     */
    static long[] toSettlementUnits(double amount, double fee, int digits) {
        long amountMinor = OmceMoney.priceToMinorUnits(amount, digits);
        long feeMinor = fee <= 0.0 ? 0L : OmceMoney.toMinorUnits(fee, digits);
        if (feeMinor > amountMinor) {
            feeMinor = amountMinor;
        }
        return new long[] { amountMinor, feeMinor };
    }

    /**
     * Pure amount/fee arithmetic for a transfer, factored out of {@link #transfer} so the
     * validation-rounding-fee-clamp chain can be unit-tested without an economy backend or live
     * players. Rejects non-finite and non-positive amounts, rounds the amount to whole cents,
     * then computes the rounded fee (never exceeding the amount) and the credited remainder.
     *
     * @return a successful {@link Result} carrying {@code amount}/{@code credited}/{@code fee},
     *         or a failure {@link Result} with the same user-facing message {@code transfer} uses.
     */
    static Result computeAmounts(double rawAmount, double feePercent) {
        if (Double.isNaN(rawAmount) || Double.isInfinite(rawAmount)) {
            return Result.fail("Amount is not a valid number.");
        }
        double amount = roundToCents(rawAmount);
        if (amount <= 0.0) {
            return Result.fail("Amount must be greater than $0.00.");
        }
        double fee = feePercent > 0.0 ? roundToCents(amount * feePercent / 100.0) : 0.0;
        if (fee > amount) {
            fee = amount;
        }
        double credited = amount - fee;
        return Result.success(amount, credited, fee);
    }

    /** Rounds a currency value to whole cents (half-up). Package-private for unit testing. */
    static double roundToCents(double v) {
        return Math.floor(v * 100.0 + 0.5) / 100.0;
    }

    private static String money(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
