package com.micatechnologies.minecraft.sum.beaches;

import com.micatechnologies.minecraft.sum.SumConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class BeachesHandler {

    private static final int FLOOD_TICKS_BETWEEN_STEPS = 10;
    private static final int MAX_FLOOD_DEPTH = 6;

    /** When true, the handler chat-logs its per-break decisions to the breaking player.
     *  Toggled via {@code /sum beaches debug}. Transient — resets to false on server restart. */
    public static volatile boolean debug = false;

    private final List<ScheduledFlood> scheduledFloods = new ArrayList<>();

    /** Positions where we've scheduled a water placement but haven't applied it yet.
     *  Treated as "adjacent water source" by {@link #hasAdjacentWaterSource} so that
     *  rapid creative-mode breaks of multiple blocks in a row (where each subsequent
     *  break is adjacent only to the just-broken air block) all chain correctly. Both
     *  reads and writes happen on the server thread, so a plain HashSet is fine. */
    private final Set<BlockPos> pendingWaterPlacements = new HashSet<>();

    private static void debugTo(EntityPlayer player, String msg) {
        if (debug && player != null) {
            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                net.minecraft.util.text.TextFormatting.GRAY + "[beaches] " + msg));
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        EntityPlayer player = event.getPlayer();
        if (event.isCanceled()) {
            debugTo(player, "skip: BreakEvent already canceled");
            return;
        }
        if (!SumConfig.isBeachesEnabled()) {
            debugTo(player, "skip: feature disabled in config (beaches.enabled=false)");
            return;
        }
        if (player == null || player instanceof FakePlayer) {
            debugTo(player, "skip: no real player (null or FakePlayer)");
            return;
        }
        Block broken = event.getState().getBlock();
        if (!SumConfig.isBeachesAffectedBlock(broken)) {
            String name = broken.getRegistryName() == null ? "?" : broken.getRegistryName().toString();
            debugTo(player, "skip: '" + name + "' not in affectedBlocks list");
            return;
        }
        World world = event.getWorld();
        if (!(world instanceof WorldServer)) {
            debugTo(player, "skip: world is not a WorldServer (client-side?)");
            return;
        }
        BlockPos pos = event.getPos().toImmutable();
        if (!hasAdjacentWaterSource(world, pos)) {
            debugTo(player, "skip: no adjacent water source at " + describePos(pos)
                + " — neighbors: " + describeNeighbors(world, pos));
            return;
        }
        debugTo(player, "match at " + describePos(pos)
            + " (sea level=" + world.getSeaLevel() + ", spread Y="
            + (world.getSeaLevel() - 1) + ") — queued for next tick");
        // Mark this position as pending so subsequent breaks in the SAME tick (e.g. a
        // creative-mode line dig) see it as "adjacent water" even though we haven't
        // placed the source yet.
        pendingWaterPlacements.add(pos);
        // Queue for processing in WorldTickEvent.END — that's the only reliable hook for
        // "fire after vanilla finishes the current tick." WorldServer.addScheduledTask
        // runs the runnable INLINE when called from the main thread (which we are, since
        // BreakEvent fires on the main thread), so the task would execute before vanilla
        // even removes the broken block. WorldTickEvent.END fires once per world tick,
        // strictly after all packet/world processing for that tick has completed.
        scheduledFloods.add(new ScheduledFlood((WorldServer) world, pos, 0, player));
    }

    private static String describePos(BlockPos pos) {
        return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    private static String describeNeighbors(World world, BlockPos pos) {
        StringBuilder sb = new StringBuilder();
        for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
            BlockPos p = pos.offset(facing);
            IBlockState st = world.getBlockState(p);
            String name = st.getBlock().getRegistryName() == null
                ? "?" : st.getBlock().getRegistryName().toString();
            String extra = "";
            if (st.getBlock() == Blocks.FLOWING_WATER) {
                extra = "(level=" + st.getValue(BlockLiquid.LEVEL) + ")";
            }
            if (sb.length() > 0) sb.append(", ");
            sb.append(facing.getName()).append("=").append(name).append(extra);
        }
        return sb.toString();
    }

    @SubscribeEvent
    public void onBucketFilled(FillBucketEvent event) {
        if (!SumConfig.isBeachesEnabled() || !SumConfig.isBeachesInfiniteBucketWater()) {
            return;
        }
        if (event.getEmptyBucket().getItem() != Items.BUCKET) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        if (player == null || player instanceof FakePlayer) {
            return;
        }
        RayTraceResult target = event.getTarget();
        if (target == null) {
            return;
        }
        BlockPos targetPos = target.getBlockPos();
        if (targetPos == null) {
            return;
        }
        World world = event.getWorld();
        if (!(world instanceof WorldServer)) {
            return;
        }
        if (!hasAdjacentWaterSource(world, targetPos)) {
            return;
        }
        BlockPos immutable = targetPos.toImmutable();
        pendingWaterPlacements.add(immutable);
        scheduledFloods.add(new ScheduledFlood((WorldServer) world, immutable, 0, player));
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) {
            return;
        }
        for (int i = scheduledFloods.size() - 1; i >= 0; i--) {
            ScheduledFlood entry = scheduledFloods.get(i);
            if (entry.world != event.world) {
                continue;
            }
            int delay = entry.delayTicks();
            if (entry.ticksWaited < delay) {
                entry.ticksWaited++;
                continue;
            }
            // Ready to attempt placement.
            boolean placed = tryPlaceAndRecurse(entry);
            if (placed || --entry.retriesLeft <= 0) {
                pendingWaterPlacements.remove(entry.pos);
                scheduledFloods.remove(i);
            }
            // else: leave in queue, retry next tick.
        }
    }

    private boolean tryPlaceAndRecurse(ScheduledFlood entry) {
        WorldServer world = entry.world;
        BlockPos pos = entry.pos;
        Block here = world.getBlockState(pos).getBlock();
        if (here != Blocks.AIR && here != Blocks.FLOWING_WATER && here != Blocks.WATER) {
            debugTo(entry.debugPlayer, "tick-defer retry: block at " + describePos(pos) + " is '"
                + (here.getRegistryName() == null ? "?" : here.getRegistryName().toString())
                + "' (waiting for break to complete)");
            return false;
        }
        world.setBlockState(pos, Blocks.WATER.getDefaultState(), 11);
        debugTo(entry.debugPlayer, "placed water source at " + describePos(pos)
            + " (depth=" + entry.depth + ")");
        if (entry.depth >= MAX_FLOOD_DEPTH || pos.getY() != world.getSeaLevel() - 1) {
            return true;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
            cursor.setPos(pos).move(facing);
            IBlockState neighbor = world.getBlockState(cursor);
            Block neighborBlock = neighbor.getBlock();
            if (neighborBlock == Blocks.AIR || neighborBlock == Blocks.FLOWING_WATER) {
                BlockPos next = cursor.toImmutable();
                pendingWaterPlacements.add(next);
                scheduledFloods.add(new ScheduledFlood(world, next, entry.depth + 1, entry.debugPlayer));
                return true;
            }
        }
        return true;
    }

    private boolean hasAdjacentWaterSource(World world, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
            cursor.setPos(pos).move(facing);
            IBlockState state = world.getBlockState(cursor);
            Block block = state.getBlock();
            if (block == Blocks.WATER) {
                return true;
            }
            if (block == Blocks.FLOWING_WATER && state.getValue(BlockLiquid.LEVEL) == 0) {
                return true;
            }
            if (pendingWaterPlacements.contains(cursor.toImmutable())) {
                return true;
            }
        }
        return false;
    }

    private static final class ScheduledFlood {
        final WorldServer world;
        final BlockPos pos;
        final int depth;
        final EntityPlayer debugPlayer;
        int ticksWaited;
        /** Number of attempts allowed after the initial-delay window — in case the block
         *  break hasn't completed yet by WorldTickEvent.END (depending on Forge/MC packet
         *  processing order). 5 retries is generous and avoids a brittle hard-coded
         *  one-shot. */
        int retriesLeft = 5;

        ScheduledFlood(WorldServer world, BlockPos pos, int depth, EntityPlayer debugPlayer) {
            this.world = world;
            this.pos = pos;
            this.depth = depth;
            this.debugPlayer = debugPlayer;
        }

        /** Initial placement (depth 0) fires on the next world tick end (delay 1).
         *  Recursion (depth > 0) uses the animation interval if enabled, else next tick. */
        int delayTicks() {
            if (depth == 0) {
                return 1;
            }
            return SumConfig.isBeachesAnimatedFlooding() ? FLOOD_TICKS_BETWEEN_STEPS : 1;
        }
    }
}
