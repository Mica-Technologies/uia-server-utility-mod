package com.micatechnologies.minecraft.sum.api.event;

import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * Base for every event SUM posts when a player's money moves.
 *
 * <p>Posted on {@code MinecraftForge.EVENT_BUS}, on the server thread, <b>after</b> the movement
 * has settled. SUM's own features post these too, attributed to {@code "sum"} — an event bus that
 * only saw third-party activity would be useless for anything wanting a complete picture of an
 * economy.
 *
 * <p>These are notifications, not decisions: nothing here is cancellable, and by the time a
 * listener runs the money has already moved. A listener that throws is logged and ignored rather
 * than being allowed to roll back a completed transaction.
 *
 * <h2>Reversals</h2>
 *
 * <p>SUM reverses a spend by crediting the money back, which posts its own event. A shop purchase
 * that fails partway therefore appears as a {@code SPEND} followed by a matching {@code CREDIT},
 * rather than as nothing at all. That is deliberate: both movements really happened to the wallet,
 * and a listener maintaining a running total would be wrong if either were hidden.
 */
public class SumEconomyEvent extends Event {

    private final EntityPlayer player;
    private final String sourceModId;
    private final double amount;
    private final String reason;

    protected SumEconomyEvent(EntityPlayer player, String sourceModId, double amount,
            @Nullable String reason) {
        this.player = player;
        this.sourceModId = sourceModId;
        this.amount = amount;
        this.reason = reason;
    }

    /** The player whose money moved. */
    public EntityPlayer getPlayer() {
        return player;
    }

    /**
     * The mod that caused the movement — its mod id, or {@code "sum"} for SUM's own features
     * (shops, plots, jobs, loyalty, {@code /pay}, the ATM).
     */
    public String getSourceModId() {
        return sourceModId;
    }

    /** True when this was SUM itself rather than an integrating mod. */
    public boolean isFromSum() {
        return "sum".equals(sourceModId);
    }

    /** How much moved, in dollars. Always positive; the subclass says which direction. */
    public double getAmount() {
        return amount;
    }

    /** The reason recorded for the movement, if one was given. */
    @Nullable
    public String getReason() {
        return reason;
    }
}
