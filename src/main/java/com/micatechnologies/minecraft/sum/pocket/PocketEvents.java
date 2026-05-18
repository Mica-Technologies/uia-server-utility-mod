package com.micatechnologies.minecraft.sum.pocket;

import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;

/**
 * Lifecycle wiring for {@link PocketInventory}:
 * <ul>
 *   <li>Attach a fresh {@link PocketProvider} to every player as they're constructed.</li>
 *   <li>Push a sync packet to the client on login and dimension change so the HUD has
 *       data to render from the first frame.</li>
 *   <li>Carry the pocket contents across death/respawn (and end-portal return) by copying
 *       items from the cloned player's old capability to the new one. Default rule:
 *       pocket survives death. If we ever want a config knob for "drop on death" this is
 *       the place to gate it.</li>
 * </ul>
 *
 * <p>Modeled after {@code SumMoneyEvents} so the lifecycle invariants stay consistent
 * across SUM's per-player capabilities.</p>
 */
public class PocketEvents {

    @SubscribeEvent
    public void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof EntityPlayer) {
            event.addCapability(CapabilityPocket.KEY, new PocketProvider());
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerLoggedInEvent event) {
        syncTo(event.player);
    }

    @SubscribeEvent
    public void onDimensionChange(PlayerChangedDimensionEvent event) {
        syncTo(event.player);
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        PocketInventory oldInv = PocketInventory.get(event.getOriginal());
        PocketInventory newInv = PocketInventory.get(event.getEntityPlayer());
        if (oldInv != null && newInv != null) {
            // ItemStackHandler exposes setStackInSlot which copies the stack — using the
            // serialize → deserialize round trip is just as easy and avoids per-slot loops
            // if the layout grows.
            newInv.deserializeNBT(oldInv.serializeNBT());
        }
        syncTo(event.getEntityPlayer());
    }

    private static void syncTo(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        PocketInventory inv = PocketInventory.get(player);
        if (inv == null) {
            return;
        }
        SumNetwork.CHANNEL.sendTo(new PacketSyncPocket(inv), (EntityPlayerMP) player);
    }

    /**
     * Public sync helper for callers outside the lifecycle events (container slot changes,
     * commands, etc.). Safe to call with a client-side player (no-op).
     */
    public static void sync(EntityPlayer player) {
        syncTo(player);
    }
}
