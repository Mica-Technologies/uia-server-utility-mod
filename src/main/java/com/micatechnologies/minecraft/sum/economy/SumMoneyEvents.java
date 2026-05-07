package com.micatechnologies.minecraft.sum.economy;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;

/**
 * Lifecycle hooks for the {@link ISumMoney} capability:
 * <ul>
 *   <li>Attach the {@link SumMoneyProvider} to every player on construction.</li>
 *   <li>Push the current balance to the client on login and after dimension change.</li>
 *   <li>Carry the balance across death/respawn (not lost on death).</li>
 * </ul>
 */
public class SumMoneyEvents {

    @SubscribeEvent
    public void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof EntityPlayer) {
            event.addCapability(CapabilitySumMoney.KEY, new SumMoneyProvider());
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        ISumMoney money = event.player.getCapability(CapabilitySumMoney.CAPABILITY, null);
        if (money != null) {
            money.sync(event.player);
        }
    }

    @SubscribeEvent
    public void onDimensionChange(PlayerChangedDimensionEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        ISumMoney money = event.player.getCapability(CapabilitySumMoney.CAPABILITY, null);
        if (money != null) {
            money.sync(event.player);
        }
    }

    @SubscribeEvent
    public void onClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        // Preserve balance across death and end-portal-return. Without this, the post-respawn
        // EntityPlayer gets a fresh DefaultSumMoney and the player's money disappears.
        ISumMoney oldMoney = event.getOriginal().getCapability(CapabilitySumMoney.CAPABILITY, null);
        ISumMoney newMoney = event.getEntityPlayer().getCapability(CapabilitySumMoney.CAPABILITY, null);
        if (oldMoney != null && newMoney != null) {
            newMoney.setBalance(oldMoney.getBalance());
        }
        if (event.getEntityPlayer() instanceof EntityPlayerMP && newMoney != null) {
            newMoney.sync(event.getEntityPlayer());
        }
    }
}
