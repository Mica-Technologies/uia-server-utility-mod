package com.micatechnologies.minecraft.sum.api.event;

import com.micatechnologies.minecraft.sum.api.EscrowTicket;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Posted when money is held in escrow, or stops being held.
 *
 * <p>Escrow always pairs with a wallet movement — opening a hold debits a wallet, settling one
 * credits it — so a listener tracking balances can ignore these entirely and still be correct.
 * They exist for anything that wants to reason about the <i>holds</i> themselves: how much of an
 * economy is currently committed to unresolved wagers, or which mod left money sitting.
 *
 * <p>{@link #getPlayer()} is whoever the money moved to or from: the owner when a hold is opened,
 * refunded or forfeited, the recipient when one is released. {@link EscrowTicket#getOwner()} always
 * names who originally put the money up.
 *
 * <p>One exception: a {@link Type#FORFEITED} hold needs nobody online to be destroyed, so no event
 * is posted when its owner has logged out. A listener maintaining a running total of committed
 * money should reconcile against
 * {@link com.micatechnologies.minecraft.sum.api.EconomyHandle#listOpenEscrows()} rather than
 * assuming it sees the close of every hold it saw open.
 */
public class EscrowEvent extends SumEconomyEvent {

    /** What happened to the hold. */
    public enum Type {
        /** A hold was created and a wallet debited. */
        OPENED,
        /** A hold was settled to a recipient. */
        RELEASED,
        /** A hold was returned to whoever put the money up. */
        REFUNDED,
        /**
         * A hold was closed and the money destroyed — a lost wager the house kept. This is the one
         * escrow outcome that removes value from the economy, so anything tracking a money supply
         * must treat it differently from a release.
         */
        FORFEITED,
        /**
         * A hold was refunded automatically because its owning mod was removed or de-authorized.
         * Worth surfacing separately: it means money was returned that nobody asked to return.
         */
        ORPHAN_REFUNDED
    }

    private final Type type;
    private final EscrowTicket ticket;

    public EscrowEvent(EntityPlayer player, String sourceModId, Type type, EscrowTicket ticket,
            @Nullable String reason) {
        super(player, sourceModId, ticket.getAmount(), reason);
        this.type = type;
        this.ticket = ticket;
    }

    public Type getType() {
        return type;
    }

    /**
     * The ticket involved. For {@link Type#OPENED} it is now open; for every other type it has
     * just been closed and will not be found again.
     */
    public EscrowTicket getTicket() {
        return ticket;
    }
}
