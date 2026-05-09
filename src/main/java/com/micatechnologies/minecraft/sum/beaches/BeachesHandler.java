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

    private final List<ScheduledFlood> scheduledFloods = new ArrayList<>();

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (!SumConfig.isBeachesEnabled()) {
            return;
        }
        EntityPlayer player = event.getPlayer();
        if (player == null || player instanceof FakePlayer) {
            return;
        }
        Block broken = event.getState().getBlock();
        if (!SumConfig.isBeachesAffectedBlock(broken)) {
            return;
        }
        World world = event.getWorld();
        if (!(world instanceof WorldServer)) {
            return;
        }
        BlockPos pos = event.getPos().toImmutable();
        if (!hasAdjacentWaterSource(world, pos)) {
            return;
        }
        // BreakEvent fires before vanilla removes the block. Defer the water placement to
        // the next server tick so the break completes first; otherwise our setBlockState
        // gets clobbered by vanilla turning the block to air right after our handler returns.
        // Switching from HarvestDropsEvent (survival-only) to BreakEvent (creative + survival)
        // means we cover both gamemodes.
        final WorldServer ws = (WorldServer) world;
        ws.addScheduledTask(() -> {
            Block here = ws.getBlockState(pos).getBlock();
            if (here != Blocks.AIR && here != Blocks.FLOWING_WATER && here != Blocks.WATER) {
                return;
            }
            ws.setBlockState(pos, Blocks.FLOWING_WATER.getDefaultState(), 11);
            scheduleFlood(ws, pos, 0);
        });
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
        event.getWorld().setBlockState(targetPos, Blocks.FLOWING_WATER.getDefaultState(), 11);
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
        world.setBlockState(pos, Blocks.FLOWING_WATER.getDefaultState(), 11);
        if (depth > MAX_FLOOD_DEPTH || pos.getY() != world.getSeaLevel() - 1) {
            return;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
            cursor.setPos(pos).move(facing);
            IBlockState neighbor = world.getBlockState(cursor);
            Block neighborBlock = neighbor.getBlock();
            if (neighborBlock == Blocks.AIR) {
                scheduleFlood(world, cursor, depth + 1);
                return;
            }
            if (neighborBlock == Blocks.FLOWING_WATER) {
                int level = neighbor.getValue(BlockLiquid.LEVEL);
                if (level > 0 && level < 11) {
                    scheduleFlood(world, cursor, depth + 1);
                    return;
                }
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
