package com.micatechnologies.minecraft.sum.beaches;

import com.micatechnologies.minecraft.sum.SumConfig;
import java.util.ArrayList;
import java.util.List;
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
            + " (sea level=" + world.getSeaLevel() + ", spread requires Y="
            + (world.getSeaLevel() - 1) + ") — scheduling water placement");
        // BreakEvent fires before vanilla removes the block. Defer the water placement to
        // the next server tick so the break completes first; otherwise our setBlockState
        // gets clobbered by vanilla turning the block to air right after our handler returns.
        // Switching from HarvestDropsEvent (survival-only) to BreakEvent (creative + survival)
        // means we cover both gamemodes.
        final WorldServer ws = (WorldServer) world;
        ws.addScheduledTask(() -> {
            Block here = ws.getBlockState(pos).getBlock();
            if (here != Blocks.AIR && here != Blocks.FLOWING_WATER && here != Blocks.WATER) {
                debugTo(player, "deferred-task skip: block at " + describePos(pos) + " is now '"
                    + (here.getRegistryName() == null ? "?" : here.getRegistryName().toString())
                    + "' (something replaced the broken block)");
                return;
            }
            ws.setBlockState(pos, Blocks.WATER.getDefaultState(), 11);
            debugTo(player, "placed water source at " + describePos(pos)
                + ", scheduling flood (will spread "
                + (pos.getY() == ws.getSeaLevel() - 1 ? "yes — sea-level match" : "no — wrong Y")
                + ")");
            scheduleFlood(ws, pos, 0);
        });
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
        if (event.getEntityPlayer() == null || event.getEntityPlayer() instanceof FakePlayer) {
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
        if (!hasAdjacentWaterSource(event.getWorld(), targetPos)) {
            return;
        }
        event.getWorld().setBlockState(targetPos, Blocks.WATER.getDefaultState(), 11);
        scheduleFlood(event.getWorld(), targetPos, 0);
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (int i = scheduledFloods.size() - 1; i >= 0; i--) {
            ScheduledFlood entry = scheduledFloods.get(i);
            entry.ticksWaited++;
            if (entry.ticksWaited >= FLOOD_TICKS_BETWEEN_STEPS) {
                populateWater(entry.world, entry.pos, entry.depth);
                scheduledFloods.remove(i);
            }
        }
    }

    private void scheduleFlood(World world, BlockPos pos, int depth) {
        if (SumConfig.isBeachesAnimatedFlooding()) {
            scheduledFloods.add(new ScheduledFlood(world, pos.toImmutable(), depth));
        } else {
            populateWater(world, pos, depth);
        }
    }

    private void populateWater(World world, BlockPos pos, int depth) {
        Block here = world.getBlockState(pos).getBlock();
        if (here != Blocks.AIR && here != Blocks.FLOWING_WATER && here != Blocks.WATER) {
            return;
        }
        world.setBlockState(pos, Blocks.WATER.getDefaultState(), 11);
        if (depth > MAX_FLOOD_DEPTH || pos.getY() != world.getSeaLevel() - 1) {
            return;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
            cursor.setPos(pos).move(facing);
            IBlockState neighbor = world.getBlockState(cursor);
            Block neighborBlock = neighbor.getBlock();
            if (neighborBlock == Blocks.AIR) {
                scheduleFlood(world, cursor.toImmutable(), depth + 1);
                return;
            }
            // Any flowing water (any level) is a transient stream that should be promoted
            // to a source by our recursion. Real WATER source blocks are skipped (already
            // what we want).
            if (neighborBlock == Blocks.FLOWING_WATER) {
                scheduleFlood(world, cursor.toImmutable(), depth + 1);
                return;
            }
        }
    }

    private static boolean hasAdjacentWaterSource(World world, BlockPos pos) {
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
        }
        return false;
    }

    private static final class ScheduledFlood {
        final World world;
        final BlockPos pos;
        final int depth;
        int ticksWaited;

        ScheduledFlood(World world, BlockPos pos, int depth) {
            this.world = world;
            this.pos = pos;
            this.depth = depth;
        }
    }
}
