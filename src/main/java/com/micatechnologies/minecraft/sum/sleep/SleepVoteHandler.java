package com.micatechnologies.minecraft.sum.sleep;

import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.afk.AfkTracker;
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
 * advancing the world time to the next morning and waking sleeping players. Weather is left
 * alone — the server controls rain/thunder via its own systems and the sleep vote should not
 * override that.
 *
 * <p>Progress feedback: while at least one player is in bed, every overworld player gets a
 * live action-bar line ("3/5 sleeping (need 3 to skip)"), re-pushed each check second so it
 * stays visible and fades naturally a couple seconds after the last sleeper gets up. The
 * denominator is the AFK-adjusted total, matching the vote itself. Setting
 * {@code sleep_vote.actionBarProgress=false} falls back to the older chat line that fires
 * only when the sleeper count changes.
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
        boolean excludeAfk = SumConfig.isAfkEnabled() && SumConfig.isAfkExcludeFromSleepVote();

        // AFK players are dropped from the denominator so one idle player can't block the skip.
        // Sleepers are never AFK (getting into bed counts as activity), so they stay counted.
        int total = 0;
        int sleeping = 0;
        for (EntityPlayer p : world.playerEntities) {
            if (excludeAfk && AfkTracker.isAfk(p)) continue;
            total++;
            if (p.isPlayerSleeping()) sleeping++;
        }
        if (total == 0) {
            lastAnnouncedSleeping = -1;
            return;
        }

        int required = requiredSleepers(total, SumConfig.getSleepVoteThresholdPercent());

        if (sleeping > 0 && sleeping >= required) {
            skipNight(world, sleeping, total);
            lastAnnouncedSleeping = -1;
            return;
        }

        if (SumConfig.isSleepVoteActionBarProgress()) {
            // Re-push every check second while anyone is in bed; the action bar fades on its
            // own shortly after the last sleeper gets up, so no explicit clear is needed.
            if (sleeping > 0) {
                TextComponentString message = new TextComponentString(
                    TextFormatting.AQUA + progressLine(sleeping, total, required));
                for (EntityPlayer p : world.playerEntities) {
                    p.sendStatusMessage(message, true);
                }
            }
            lastAnnouncedSleeping = -1;
            return;
        }

        // Chat fallback: announce only when the count of sleepers changes (and only when at
        // least one is in bed).
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

    /** Sleepers needed to skip: ceil(total * thresholdPercent / 100), never below 1. */
    public static int requiredSleepers(int total, int thresholdPercent) {
        int required = (total * thresholdPercent + 99) / 100;
        return Math.max(required, 1);
    }

    /** The action-bar progress line, e.g. {@code "3/5 sleeping (need 3 to skip)"}. */
    public static String progressLine(int sleeping, int total, int required) {
        return sleeping + "/" + total + " sleeping (need " + required + " to skip)";
    }

    /**
     * Ticks to advance from {@code worldTime} to the next morning (day-of-cycle 0), or 0 if it's
     * already exactly morning. Pure so the night-skip delta is testable without a world.
     */
    static long ticksUntilMorning(long worldTime) {
        long timeOfDay = worldTime % 24000;
        return timeOfDay == 0 ? 0 : (24000 - timeOfDay);
    }

    private void skipNight(WorldServer world, int sleepers, int total) {
        // Advance to the next morning. Day-of-cycle is worldTime % 24000, where 0 = morning.
        long now = world.getWorldTime();
        world.setWorldTime(now + ticksUntilMorning(now));

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
