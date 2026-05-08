package com.micatechnologies.minecraft.sum.sleep;

import com.micatechnologies.minecraft.sum.SumConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Lets the night end when a configurable share of players are asleep in the overworld
 * (default 50%, rounded up). Vanilla 1.12 requires *every* online player to be in bed; on a
 * busy server that's almost never met, so people resort to /time set day or just suffer
 * through the night. This handler closes the gap.
 *
 * <p>Runs entirely server-side. Only checks the overworld (dimension 0). Skips the night by
 * advancing the world time to the next morning, clearing rain/thunder, and waking sleeping
 * players (vanilla's morning-arrival path also does this, but doing it explicitly keeps the
 * announcement and the sleep state in sync).
 */
public class SleepVoteHandler {

    /** Tick interval for the periodic check. 20 ticks = 1 real second. */
    private static final int CHECK_INTERVAL = 20;

    /** Tracks the last sleeping count we announced per dimension so we don't spam chat
     *  every check tick when nothing changed. */
    private int lastAnnouncedSleeping = -1;
    private int tickCounter = 0;

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.world.isRemote) return;
        if (event.world.provider.getDimension() != 0) return;
        if (!SumConfig.isSleepVoteEnabled()) return;

        if (++tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        WorldServer world = (WorldServer) event.world;
        int total = world.playerEntities.size();
        if (total == 0) {
            lastAnnouncedSleeping = -1;
            return;
        }

        int sleeping = 0;
        for (EntityPlayer p : world.playerEntities) {
            if (p.isPlayerSleeping()) sleeping++;
        }

        int threshold = SumConfig.getSleepVoteThresholdPercent();
        // Ceiling division: required = ceil(total * threshold / 100)
        int required = (total * threshold + 99) / 100;
        if (required < 1) required = 1;

        if (sleeping > 0 && sleeping >= required) {
            skipNight(world, sleeping, total);
            lastAnnouncedSleeping = -1;
            return;
        }

        // Announce only when the count of sleepers changes (and only when at least one is in bed).
        if (sleeping > 0 && sleeping != lastAnnouncedSleeping) {
            announce(world,
                TextFormatting.AQUA + "" + sleeping + "/" + total
                + " players sleeping (" + required + " needed to skip the night).");
            lastAnnouncedSleeping = sleeping;
        } else if (sleeping == 0 && lastAnnouncedSleeping != -1) {
            // Reset so the next time someone gets in bed, we announce again.
            lastAnnouncedSleeping = -1;
        }
    }

    private void skipNight(WorldServer world, int sleepers, int total) {
        // Advance to the next morning. Day-of-cycle is worldTime % 24000, where 0 = morning.
        long now = world.getWorldTime();
        long timeOfDay = now % 24000;
        long delta = timeOfDay == 0 ? 0 : (24000 - timeOfDay);
        world.setWorldTime(now + delta);

        // Stop any active storm.
        world.getWorldInfo().setRaining(false);
        world.getWorldInfo().setThundering(false);
        world.getWorldInfo().setRainTime(0);
        world.getWorldInfo().setThunderTime(0);

        // Wake everyone who was in a bed. wakeUpPlayer(immediately, updateWorldFlag, setSpawn).
        // setSpawn=true matches vanilla's natural morning-wake behavior — the bed becomes the
        // sleeper's respawn point.
        for (EntityPlayer p : world.playerEntities) {
            if (p.isPlayerSleeping() && p instanceof EntityPlayerMP) {
                ((EntityPlayerMP) p).wakeUpPlayer(false, false, true);
            }
        }

        announce(world, TextFormatting.GOLD + "Night skipped (" + sleepers + "/" + total
            + " sleeping). Good morning!");
    }

    private static void announce(WorldServer world, String text) {
        TextComponentString message = new TextComponentString(text);
        for (EntityPlayer p : world.playerEntities) {
            p.sendMessage(message);
        }
    }
}
