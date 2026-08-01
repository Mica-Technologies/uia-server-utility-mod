package com.micatechnologies.minecraft.sum.economy;

import com.micatechnologies.minecraft.sum.SumConfig;
import java.util.Locale;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Player-to-player money transfer, shared by the {@code /pay} command and the phone "Pay" app so
 * the validation, rounding, fee, and rollback logic live in one place.
 *
 * <p>Moves money between {@link WalletService wallets}, not bank accounts — paying someone hands
 * them cash, and both sides can spend it immediately. Both participants must be online.
 *
 * <p>The sender is charged the full {@code amount}; the recipient receives {@code amount} minus
 * the configured {@link SumConfig#getPayFeePercent() fee}, which vanishes as a money sink.
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

        public static Result success(double amount, double credited, double fee) {
            return new Result(true, null, amount, credited, fee);
        }
    }

    /**
     * Validates and performs the transfer.
     *
     * <p>Synchronous: both wallets are local, so the sender's charge cannot be refused after the
     * fact. The sender is charged first; if crediting the recipient fails they are refunded, so no
     * money is created or destroyed.
     */
    public static Result transfer(EntityPlayer from, EntityPlayer to, double rawAmount) {
        if (!SumConfig.isPayEnabled()) {
            return Result.fail("Player-to-player payments are disabled on this server.");
        }
        if (from == null || to == null) {
            return Result.fail("Invalid payment participants.");
        }
        if (from == to || from.getUniqueID().equals(to.getUniqueID())) {
            return Result.fail("You can't pay yourself.");
        }
        if (!WalletService.isAvailable()) {
            return Result.fail("No economy backend is loaded.");
        }

        // Amount validation, rounding, and fee math are pure — factored into computeAmounts so
        // they can be unit-tested without an economy backend or live players.
        Result computed = computeAmounts(rawAmount, SumConfig.getPayFeePercent());
        if (!computed.ok) {
            return computed;
        }
        double amount = computed.amount;
        double credited = computed.credited;
        double fee = computed.fee;

        double balance = WalletService.getTotal(from);
        if (Double.isNaN(balance)) {
            return Result.fail("You don't have a wallet.");
        }
        if (balance < amount) {
            return Result.fail("Insufficient funds. Need $" + money(amount)
                + ", have $" + money(balance) + ".");
        }

        // Charge the sender first; the wallet refuses to overdraw, so a false here means abort.
        if (!WalletService.spend(from, amount)) {
            return Result.fail("Payment failed — your wallet could not be charged.");
        }
        // Credit the recipient; refund the sender on failure so the books stay balanced.
        if (credited > 0.0 && !WalletService.credit(to, credited)) {
            WalletService.credit(from, amount);
            return Result.fail("Payment failed — the recipient could not be credited. You were refunded.");
        }
        return Result.success(amount, credited, fee);
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
