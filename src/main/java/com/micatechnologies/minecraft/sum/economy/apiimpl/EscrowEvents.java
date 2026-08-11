package com.micatechnologies.minecraft.sum.economy.apiimpl;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Drives the orphaned-escrow sweep.
 *
 * <p>The sweep is <i>armed</i> in {@link EconomyApiRegistry#install()}, which runs on
 * {@code FMLServerStartingEvent} — after the economy backend is chosen and before any player can
 * log in. This class only supplies the two things that make it run afterwards: the passage of time,
 * and a player arriving who is owed a refund.
 *
 * <p>Registered unconditionally; every handler is a cheap no-op when nothing is held, which is the
 * normal case on a server with no economy integrations.
 */
public class EscrowEvents {

    /**
     * Ticks the sweep.
     *
     * <p>END phase and server side only: the sweep touches world save data and credits wallets,
     * neither of which a client has any business doing.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side == Side.SERVER) {
            EscrowService.onServerTick();
        }
    }

    /**
     * Re-arms the sweep when someone logs in.
     *
     * <p>An orphaned hold belonging to an offline player cannot be refunded — there is no wallet
     * to credit — so it waits, and their arrival is the event that makes it possible.
     */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        EscrowService.onPlayerLogin();
    }
}
