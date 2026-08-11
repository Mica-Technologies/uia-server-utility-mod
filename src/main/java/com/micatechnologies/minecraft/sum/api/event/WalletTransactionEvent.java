package com.micatechnologies.minecraft.sum.api.event;

import java.util.OptionalDouble;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Posted after money has left or entered a player's wallet.
 *
 * <p>This is the busy one: every shop purchase, plot sale, job payout, loyalty reward,
 * {@code /pay}, ATM transfer and integrating-mod transaction passes through here.
 */
public class WalletTransactionEvent extends SumEconomyEvent {

    /** Which way the money went. */
    public enum Type {
        /** Money left the wallet. */
        SPEND,
        /** Money entered the wallet. */
        CREDIT
    }

    private final Type type;
    private final double resultingBalance;
    private final boolean balanceKnown;

    public WalletTransactionEvent(EntityPlayer player, String sourceModId, Type type, double amount,
            @Nullable String reason, double resultingBalance) {
        super(player, sourceModId, amount, reason);
        this.type = type;
        this.balanceKnown = !Double.isNaN(resultingBalance);
        this.resultingBalance = balanceKnown ? resultingBalance : 0.0;
    }

    public Type getType() {
        return type;
    }

    public boolean isSpend() {
        return type == Type.SPEND;
    }

    /**
     * The wallet total after the movement, when it could be read.
     *
     * <p>Empty rather than {@link Double#NaN}, which is what SUM uses internally for "unknown" and
     * which compares false against everything.
     */
    public OptionalDouble getResultingBalance() {
        return balanceKnown ? OptionalDouble.of(resultingBalance) : OptionalDouble.empty();
    }
}
