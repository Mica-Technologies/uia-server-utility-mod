package com.micatechnologies.minecraft.sum.serverconfig;

import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import com.micatechnologies.minecraft.sum.border.BorderEntry;
import com.micatechnologies.minecraft.sum.loyalty.LoyaltyMilestone;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * Server-side @SubscribeEvent that ships the runtime {@code sum.cfg} state to each
 * player on login. The client caches the snapshot in {@link ServerConfigMirror} and
 * surfaces it through the OneConfig viewer ({@link GuiServerConfigViewer}) so admins
 * can sanity-check the server's settings without opening the file on disk.
 *
 * <p>One-shot per login. Reloading the server config via {@code /sum reloadconfig}
 * does not push refreshed snapshots to already-online players — the doc string on the
 * viewer GUI calls this out so admins know to re-log if they want fresh values. A
 * push-on-reload could be added later if it becomes useful.</p>
 */
public class ServerConfigBridge {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        SumNetwork.CHANNEL.sendTo(new PacketSyncServerConfig(buildSnapshot()), player);
    }

    static ServerConfigSnapshot buildSnapshot() {
        ServerConfigSnapshot s = new ServerConfigSnapshot();

        s.sleepVoteEnabled = SumConfig.isSleepVoteEnabled();
        s.sleepVoteThreshold = SumConfig.getSleepVoteThresholdPercent();

        s.beachesEnabled = SumConfig.isBeachesEnabled();
        s.beachesAnimated = SumConfig.isBeachesAnimatedFlooding();
        s.beachesInfiniteBucket = SumConfig.isBeachesInfiniteBucketWater();
        s.beachesRealistic = SumConfig.isBeachesRealisticErosion();
        s.beachesAffectedBlocks = new ArrayList<>(SumConfig.getBeachesAffectedBlockNames());

        s.pauserEnabled = SumConfig.isPauserEnabled();

        s.movementToleranceEnabled = SumConfig.isMovementToleranceEnabled();
        s.movementToleranceMultiplier = SumConfig.getMovementToleranceMultiplier();

        s.loyaltyEnabled = SumConfig.isLoyaltyEnabled();
        s.loyaltyMilestones = formatMilestones(SumConfig.getLoyaltyMilestones());
        s.loyaltySessionMilestones = formatMilestones(SumConfig.getLoyaltySessionMilestones());

        s.autoDropperEnabled = SumConfig.isAutoDropperEnabled();
        s.autoDropperTickInterval = SumConfig.getAutoDropperTickInterval();

        s.borderEnabled = SumConfig.isBorderEnabled();
        s.borderEntries = formatBorders(SumConfig.getAllBorderEntries());

        s.roamerWalkableBlocks = new ArrayList<>(SumConfig.getRoamerWalkableBlocks());
        s.roadrunnerSpeedBlocks = formatSpeedBlocks(SumConfig.getRoadRunnerSpeedBlocks());

        return s;
    }

    private static List<String> formatMilestones(java.util.Collection<LoyaltyMilestone> ms) {
        List<String> out = new ArrayList<>(ms.size());
        for (LoyaltyMilestone m : ms) {
            String typeStr = m.getType() == LoyaltyMilestone.Type.MONEY ? "money" : "command";
            out.add(m.getMinutes() + "=" + typeStr + ":" + m.getValue());
        }
        return out;
    }

    private static List<String> formatBorders(Map<Integer, BorderEntry> entries) {
        List<String> out = new ArrayList<>(entries.size());
        for (BorderEntry b : entries.values()) {
            String mode = b.getMode() == BorderEntry.Mode.LOOP ? "loop" : "bounce";
            // Round to int radius for the canonical string form (matches the config
            // file syntax); raw radius is a double for fractional borders, which is
            // unusual but legal.
            String radiusStr;
            if (b.getRadius() == Math.floor(b.getRadius())) {
                radiusStr = Long.toString((long) b.getRadius());
            } else {
                radiusStr = String.format(Locale.ROOT, "%.2f", b.getRadius());
            }
            out.add(b.getDimId() + "=" + radiusStr + ":" + mode);
        }
        return out;
    }

    private static List<String> formatSpeedBlocks(Map<String, Double> map) {
        List<String> out = new ArrayList<>(map.size());
        for (Map.Entry<String, Double> e : map.entrySet()) {
            out.add(e.getKey() + "=" + String.format(Locale.ROOT, "%.3g", e.getValue()));
        }
        return out;
    }
}
