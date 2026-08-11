package com.micatechnologies.minecraft.sum.api.event;

import java.util.OptionalDouble;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Posted after money has entered or left a player's bank account.
 *
 * <p>Only ever posted for a <b>settled</b> movement. On a remote economy backend that is after the
 * round trip has come back committed, so a listener never sees a deposit that the service later
 * refuses. Requests that fail post nothing at all.
 *
 * <p>Note that a bank movement is usually half of a pair — money moved from a wallet into a bank
 * posts both this and a {@link WalletTransactionEvent}, because both balances really changed.
 */
public class BankTransactionEvent extends SumEconomyEvent {

    /** Which way the money went. */
    public enum Type {
        /** Money entered the account. */
        DEPOSIT,
        /** Money left the account. */
        WITHDRAW
    }

    private final Type type;
    private final boolean remoteBackend;
    private final double resultingBalance;
    private final boolean balanceKnown;

    public BankTransactionEvent(EntityPlayer player, String sourceModId, Type type, double amount,
            @Nullable String reason, double resultingBalance, boolean remoteBackend) {
        super(player, sourceModId, amount, reason);
        this.type = type;
        this.remoteBackend = remoteBackend;
        this.balanceKnown = !Double.isNaN(resultingBalance);
        this.resultingBalance = balanceKnown ? resultingBalance : 0.0;
    }

    public Type getType() {
        return type;
    }

    public boolean isDeposit() {
        return type == Type.DEPOSIT;
    }

    /** True when a remote economy service owns this account rather than the world save. */
    public boolean isRemoteBackend() {
        return remoteBackend;
    }

    /**
     * The account balance after the movement, when it was known.
     *
     * <p>Empty is normal on a remote backend that has not yet reported this player's account.
     */
    public OptionalDouble getResultingBalance() {
        return balanceKnown ? OptionalDouble.of(resultingBalance) : OptionalDouble.empty();
    }
}
