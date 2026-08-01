package com.micatechnologies.minecraft.sum.huds.snapshot;

import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import com.micatechnologies.minecraft.sum.bank.SafeDepositSavedData;
import com.micatechnologies.minecraft.sum.jobs.JobBoardSavedData;
import com.micatechnologies.minecraft.sum.loyalty.LoyaltyMilestone;
import com.micatechnologies.minecraft.sum.plots.SumPlot;
import com.micatechnologies.minecraft.sum.plots.SumPlotsWorldSavedData;
import java.util.Collection;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Builds per-player {@link PlayerStatusSnapshot}s on the server tick and ships them
 * to each player via {@link PacketSyncPlayerStatus}. Also doubles as the client-side
 * cache: HUDs read {@link #latest} on every render.
 *
 * <p>One instance is registered on the Forge event bus in {@code Sum.preInit};
 * the {@code @SubscribeEvent}-tagged methods are only ever invoked on the server
 * (they check {@link TickEvent.Phase} and instanceof). The static
 * {@link #latest} field is touched only on the client (via the packet handler).
 * Single class to keep the moving parts close together.</p>
 *
 * <p>Push cadence is {@link #PUSH_INTERVAL_TICKS} ticks (40 = 2 s). Higher
 * frequencies would make plot-boundary transitions feel snappier but the snapshot
 * data is otherwise slow-changing, so 2 s is a comfortable trade.</p>
 */
public class PlayerStatusTracker {

    /** Pushed-snapshot cadence, in server ticks. 40 = 2 seconds at 20 TPS. */
    private static final int PUSH_INTERVAL_TICKS = 40;

    /** Client-side cache of the most recently received snapshot. Null until the
     *  first packet arrives — the HUDs render "—" for missing data in that window. */
    public static PlayerStatusSnapshot latest;

    /** Server-side tick counter, shared across all online players (one snapshot
     *  batch per cadence tick, all players in the same batch). */
    private int tickCounter = 0;

    /** Server: push to a single player. Called on login so HUDs have data on the
     *  very first frame after spawn, instead of waiting up to 2 s for the next
     *  scheduled batch. */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            pushTo((EntityPlayerMP) event.player);
        }
    }

    /** Server: scheduled push to every online player on the cadence tick. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++tickCounter < PUSH_INTERVAL_TICKS) return;
        tickCounter = 0;

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            pushTo(player);
        }
    }

    /** Client: handler entry point fired by {@link PacketSyncPlayerStatus.Handler}
     *  on the client main thread. */
    public static void receive(PlayerStatusSnapshot snapshot) {
        latest = snapshot;
    }

    private static void pushTo(EntityPlayerMP player) {
        try {
            PlayerStatusSnapshot snap = buildSnapshot(player);
            SumNetwork.CHANNEL.sendTo(new PacketSyncPlayerStatus(snap), player);
        } catch (Throwable ignored) {
            // Snapshot construction touches several subsystems; if any one throws
            // (eg. a SUM saved-data class isn't initialised because the world is
            // mid-shutdown), swallow it rather than crash the server tick loop.
            // The next cadence tick will retry.
        }
    }

    /**
     * Collect data from each SUM subsystem the snapshot exposes. New fields go here.
     * All reads are read-only and side-effect-free, so this is safe to call from any
     * server-thread context.
     */
    private static PlayerStatusSnapshot buildSnapshot(EntityPlayerMP player) {
        World world = player.world;

        long vaultBillValue = SafeDepositSavedData.get(world)
            .totalBillValueFor(player.getUniqueID());
        // The bank account may live on a remote service, so only the server can read it.
        double bankBalance = com.micatechnologies.minecraft.sum.bank.BankService.getBalance(player);
        String bankNotice =
            com.micatechnologies.minecraft.sum.bank.BankService.getUnavailableNotice(player);

        String plotName = "";
        String plotOwner = "";
        SumPlotsWorldSavedData plots = SumPlotsWorldSavedData.get(world);
        List<SumPlot> here = plots.getPlotsContaining(player.getPosition());
        if (!here.isEmpty()) {
            SumPlot plot = here.get(0);
            plotName = plot.getDisplayName();
            plotOwner = plot.getOwnerName();
        }

        int jobsAvailable = JobBoardSavedData.get(world)
            .getOpenCount(System.currentTimeMillis());

        // Loyalty NBT lives under EntityPlayer.PERSISTED_NBT_TAG.sum_loyalty.ticks —
        // see LoyaltyHandler. Read defensively in case loyalty has never been
        // initialised on this player yet (no NBT root).
        int loyaltyTicks = 0;
        NBTTagCompound persisted = player.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (persisted.hasKey("sum_loyalty")) {
            loyaltyTicks = persisted.getCompoundTag("sum_loyalty").getInteger("ticks");
        }

        int nextMilestoneTicks = -1;
        Collection<LoyaltyMilestone> milestones = SumConfig.getLoyaltyMilestones();
        for (LoyaltyMilestone m : milestones) {
            int t = m.getTicks();
            if (t > loyaltyTicks && (nextMilestoneTicks < 0 || t < nextMilestoneTicks)) {
                nextMilestoneTicks = t;
            }
        }

        return new PlayerStatusSnapshot(vaultBillValue, bankBalance, bankNotice, plotName, plotOwner,
            jobsAvailable, loyaltyTicks, nextMilestoneTicks);
    }
}
