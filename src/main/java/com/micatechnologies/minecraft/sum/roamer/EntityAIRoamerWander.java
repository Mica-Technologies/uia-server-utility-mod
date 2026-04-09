package com.micatechnologies.minecraft.sum.roamer;

import com.micatechnologies.minecraft.sum.SumConfig;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * AI task that makes the roamer wander to random nearby positions, but only if
 * the destination is on a walkable block. The roamer pauses for a random duration
 * between walks.
 */
public class EntityAIRoamerWander extends EntityAIBase {

    private final EntityCreature entity;
    private final double speed;
    private final int maxSearchRange;

    private double targetX;
    private double targetY;
    private double targetZ;

    private int idleTimer;
    private int idleDuration;

    private static final int MIN_IDLE_TICKS = 60;   // 3 seconds
    private static final int MAX_IDLE_TICKS = 300;   // 15 seconds

    public EntityAIRoamerWander(EntityCreature entity, double speed, int maxSearchRange) {
        this.entity = entity;
        this.speed = speed;
        this.maxSearchRange = maxSearchRange;
        this.setMutexBits(1); // movement mutex
    }

    @Override
    public boolean shouldExecute() {
        if (idleTimer > 0) {
            idleTimer--;
            return false;
        }

        Vec3d target = RandomPositionGenerator.findRandomTarget(entity, maxSearchRange, 7);
        if (target == null) {
            return false;
        }

        BlockPos groundPos = new BlockPos(target.x, target.y - 1, target.z);
        IBlockState groundState = entity.world.getBlockState(groundPos);
        String registryName = groundState.getBlock().getRegistryName().toString();

        if (!SumConfig.isBlockWalkableByRoamer(registryName)) {
            return false;
        }

        targetX = target.x;
        targetY = target.y;
        targetZ = target.z;
        return true;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return !entity.getNavigator().noPath();
    }

    @Override
    public void startExecuting() {
        entity.getNavigator().tryMoveToXYZ(targetX, targetY, targetZ, speed);
    }

    @Override
    public void resetTask() {
        // After finishing a walk, idle for a random duration
        idleDuration = MIN_IDLE_TICKS + entity.getRNG().nextInt(MAX_IDLE_TICKS - MIN_IDLE_TICKS + 1);
        idleTimer = idleDuration;
    }
}
