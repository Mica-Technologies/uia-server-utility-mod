package com.micatechnologies.minecraft.sum.plots;

import com.micatechnologies.minecraft.sum.Sum;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Cancels block-modify events inside owned plots when the actor isn't allowed.
 *
 * <p>Allowed actors: the plot owner, anyone in {@link SumPlot#getTrustedBuilders}, and ops at
 * level 2+ (auto-granted via {@code sum.plots.bypass} permission node, which permission mods
 * like LP can revoke if a server doesn't want admin bypass).
 *
 * <p>What's protected:
 * <ul>
 *   <li>{@code BlockEvent.BreakEvent} — player breaks. Cancel if owner+/trusted+ check fails.</li>
 *   <li>{@code BlockEvent.PlaceEvent} — player places. Same.</li>
 *   <li>{@code LivingDestroyBlockEvent} — mobs (creepers, withers, endermen) destroying blocks
 *       inside an owned plot. Always cancelled regardless of mob type.</li>
 *   <li>{@code ExplosionEvent.Detonate} — TNT/charged-creeper explosions affecting plot
 *       blocks. We filter the affected-blocks list to drop protected positions, so the
 *       explosion still happens but doesn't shred the plot.</li>
 * </ul>
 *
 * <p>What's NOT protected in v1: right-click interactions (chest/door access), entity
 * damage, item-frame breaks. Those need extra logic and a config toggle that we'll layer in
 * later if needed.
 *
 * <p>Chat-message throttling: {@link #lastNoticeTime} tracks the last warning per player so
 * a stream of cancelled break attempts doesn't spam the chat once per tick. 2-second cooldown.
 */
public class PlotsProtectionHandler {

    private static final long NOTICE_COOLDOWN_MS = 2000L;

    private final Map<UUID, Long> lastNoticeTime = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (event.getWorld().isRemote) return;
        EntityPlayer player = event.getPlayer();
        if (player == null) return;
        if (canBypass(player)) return;
        if (!isAllowed(event.getWorld(), event.getPos(), player)) {
            event.setCanceled(true);
            notify(player, "You can't break blocks in this plot.");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPlace(BlockEvent.PlaceEvent event) {
        if (event.getWorld().isRemote) return;
        EntityPlayer player = event.getPlayer();
        if (player == null) return;
        if (canBypass(player)) return;
        if (!isAllowed(event.getWorld(), event.getPos(), player)) {
            event.setCanceled(true);
            notify(player, "You can't build in this plot.");
        }
    }

    @SubscribeEvent
    public void onMobDestroy(LivingDestroyBlockEvent event) {
        if (event.getEntity().world.isRemote) return;
        if (anyOwnedPlotContains(event.getEntity().world, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getWorld().isRemote) return;
        World world = event.getWorld();
        SumPlotsWorldSavedData data = SumPlotsWorldSavedData.get(world);
        if (data.getAll().isEmpty()) return;
        Iterator<BlockPos> it = event.getAffectedBlocks().iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            for (SumPlot plot : data.getPlotsContaining(pos)) {
                if (plot.getOwnerUuid() != null) {
                    it.remove();
                    break;
                }
            }
        }
    }

    private static boolean isAllowed(World world, BlockPos pos, EntityPlayer player) {
        SumPlotsWorldSavedData data = SumPlotsWorldSavedData.get(world);
        for (SumPlot plot : data.getPlotsContaining(pos)) {
            if (!plot.canBuild(player)) return false;
        }
        return true;
    }

    private static boolean anyOwnedPlotContains(World world, BlockPos pos) {
        SumPlotsWorldSavedData data = SumPlotsWorldSavedData.get(world);
        for (SumPlot plot : data.getPlotsContaining(pos)) {
            if (plot.getOwnerUuid() != null) return true;
        }
        return false;
    }

    private static boolean canBypass(EntityPlayer player) {
        return player.canUseCommand(2, "sum.plots.bypass");
    }

    private void notify(EntityPlayer player, String text) {
        long now = System.currentTimeMillis();
        Long last = lastNoticeTime.get(player.getUniqueID());
        if (last != null && now - last < NOTICE_COOLDOWN_MS) return;
        lastNoticeTime.put(player.getUniqueID(), now);
        // Lazy cleanup so the map doesn't grow unbounded on a server with thousands of joins.
        if (lastNoticeTime.size() > 256) {
            lastNoticeTime.entrySet().removeIf(e -> now - e.getValue() > NOTICE_COOLDOWN_MS * 5);
        }
        TextComponentString tcs = new TextComponentString(TextFormatting.RED + text);
        player.sendMessage(tcs);
        Sum.LOGGER.debug("[plots] denied {} action at owned plot", player.getName());
    }
}
