package com.micatechnologies.minecraft.sum.roadrunner;

import com.micatechnologies.minecraft.sum.SumConfig;
import java.util.UUID;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class RoadRunnerHandler {

    private static final UUID ROAD_RUNNER_MODIFIER_UUID = UUID.fromString("b3f40e2a-7c5d-4e8f-9a1b-6d2c8f4e7a3b");
    private static final String ROAD_RUNNER_MODIFIER_NAME = "SUM RoadRunner Speed Boost";

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        EntityPlayer player = event.player;

        IAttributeInstance speedAttribute = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speedAttribute == null) {
            return;
        }

        BlockPos below = new BlockPos(player.posX, player.getEntityBoundingBox().minY - 0.5, player.posZ);
        IBlockState blockState = player.world.getBlockState(below);
        String registryName = blockState.getBlock().getRegistryName() != null
            ? blockState.getBlock().getRegistryName().toString()
            : "";

        double multiplier = SumConfig.getRoadRunnerSpeedMultiplier(registryName);

        AttributeModifier existingModifier = speedAttribute.getModifier(ROAD_RUNNER_MODIFIER_UUID);

        if (multiplier > 0.0) {
            double boostAmount = multiplier - 1.0;
            if (existingModifier != null) {
                if (Math.abs(existingModifier.getAmount() - boostAmount) > 0.001) {
                    speedAttribute.removeModifier(existingModifier);
                    speedAttribute.applyModifier(new AttributeModifier(
                        ROAD_RUNNER_MODIFIER_UUID, ROAD_RUNNER_MODIFIER_NAME, boostAmount, 2));
                }
            } else {
                speedAttribute.applyModifier(new AttributeModifier(
                    ROAD_RUNNER_MODIFIER_UUID, ROAD_RUNNER_MODIFIER_NAME, boostAmount, 2));
            }
        } else {
            if (existingModifier != null) {
                speedAttribute.removeModifier(existingModifier);
            }
        }
    }
}
