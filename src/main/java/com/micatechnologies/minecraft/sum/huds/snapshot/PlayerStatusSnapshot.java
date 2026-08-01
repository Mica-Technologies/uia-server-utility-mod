package com.micatechnologies.minecraft.sum.huds.snapshot;

/**
 * One periodic snapshot of "stuff the SUM HUDs need from the server". Built per
 * player on the server tick by {@link PlayerStatusTracker#buildSnapshot}, shipped
 * as a single {@link PacketSyncPlayerStatus} S→C packet, and cached client-side
 * in {@link PlayerStatusTracker#latest} so the HUDs can read from it without any
 * per-frame network traffic.
 *
 * <p>Designed for "tiny periodic snapshot of slow-changing data" — not for
 * sub-second-latency things like coords or velocity, which the HUDs read from
 * the local client state directly.</p>
 *
 * <p>Fields are public + final so the serializer in {@link PacketSyncPlayerStatus}
 * can read them without ceremony. Add new fields here and remember to update both
 * sides of the packet's {@code fromBytes} / {@code toBytes}.</p>
 */
public final class PlayerStatusSnapshot {

    /** Sum of bill values across every safe-deposit box owned by the player in the
     *  player's current dimension. Zero if the player owns no boxes or none contain
     *  currency bills. This is stored cash, not an account balance. */
    public final long vaultBillValue;

    /** The player's bank account balance. Sent from the server because the account may live on a
     *  remote economy service the client has no access to. NaN when no account is known yet. */
    public final double bankBalance;

    /** Display name of the SUM plot the player is currently standing in, or "" if
     *  the player is outside any plot. When overlapping plots exist, the first one
     *  returned by {@code getPlotsContaining} wins. */
    public final String plotName;

    /** Owner name of {@link #plotName}. Empty when there's no plot or it's unowned. */
    public final String plotOwner;

    /** Count of currently-active job listings server-wide (not filtered by location). */
    public final int jobsAvailable;

    /** Lifetime loyalty ticks accrued by the player. 0 if loyalty is disabled or
     *  the player has no recorded loyalty NBT. */
    public final int loyaltyTicks;

    /** Tick threshold of the next loyalty milestone the player hasn't hit yet, or
     *  -1 when there's no next milestone (config has none, or all are achieved). */
    public final int nextMilestoneTicks;

    public PlayerStatusSnapshot(long vaultBillValue, double bankBalance, String plotName,
                                String plotOwner, int jobsAvailable, int loyaltyTicks,
                                int nextMilestoneTicks) {
        this.vaultBillValue = vaultBillValue;
        this.bankBalance = bankBalance;
        this.plotName = plotName == null ? "" : plotName;
        this.plotOwner = plotOwner == null ? "" : plotOwner;
        this.jobsAvailable = jobsAvailable;
        this.loyaltyTicks = loyaltyTicks;
        this.nextMilestoneTicks = nextMilestoneTicks;
    }

    /** Zero-valued snapshot used as the "no data yet" sentinel — HUDs render "—"
     *  for any field that's clearly default (empty plot name, etc.). */
    public static PlayerStatusSnapshot empty() {
        return new PlayerStatusSnapshot(0L, Double.NaN, "", "", 0, 0, -1);
    }
}
