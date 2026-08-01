package com.micatechnologies.minecraft.sum.omceapi.service;

import com.micatechnologies.minecraft.sum.economy.EconomyBridge;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Seeds and drops the remote economy cache as players come and go.
 *
 * <p>Registered unconditionally in {@code Sum.preInit}; every handler is a no-op unless a remote
 * backend is actually connected, so there is nothing to unregister when the API is disabled.
 */
public class OmceEconomyEvents {

    /**
     * Resolves the player's account and loads their balance on login, so the first HUD frame and
     * the first shop GUI they open already have a real number rather than a blank.
     *
     * <p>Dispatched to the client's background executor immediately; nothing here waits.
     */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        OmceEconomyService service = EconomyBridge.getRemoteService();
        if (service != null) {
            service.onPlayerLogin((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        OmceEconomyService service = EconomyBridge.getRemoteService();
        if (service != null) {
            service.onPlayerLogout((EntityPlayerMP) event.player);
        }
    }
}
