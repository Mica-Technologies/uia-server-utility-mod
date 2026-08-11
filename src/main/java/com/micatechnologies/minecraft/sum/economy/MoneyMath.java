package com.micatechnologies.minecraft.sum.economy;

/**
 * The one place currency rounding is defined.
 *
 * <p>The wallet holds a {@code double}, and doubles cannot represent most cent values exactly.
 * Left alone, that turns into balances like {@code 19.999999999999996} and purchases a player can
 * "not quite" afford by a millionth of a cent. Every amount entering the wallet is therefore
 * rounded to whole cents on the way in.
 *
 * <p>This lives on its own because two independent copies of a rounding rule is one copy too many:
 * if {@code /pay} rounds half-up and an integrating mod rounds half-even, the same $10.005 becomes
 * a different amount depending on which code path a player happened to use, and the difference
 * accumulates silently.
 */
public final class MoneyMath {

    private MoneyMath() {}

    /**
     * Rounds a currency amount to whole cents, half-up.
     *
     * <p>Half-up rather than {@link Math#round}'s banker-friendly variants because it is the rule
     * players already expect from a till: $0.005 costs a cent.
     *
     * <p>Non-finite input is returned unchanged rather than silently becoming a number — callers
     * validate amounts before spending, and quietly turning {@code NaN} into {@code 0.0} here
     * would disguise a caller's bug as a free transaction.
     */
    public static double roundToCents(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            return amount;
        }
        return Math.floor(amount * 100.0 + 0.5) / 100.0;
    }
}
