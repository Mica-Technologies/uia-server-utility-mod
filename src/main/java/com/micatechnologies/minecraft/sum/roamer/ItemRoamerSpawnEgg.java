package com.micatechnologies.minecraft.sum.roamer;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

public class ItemRoamerSpawnEgg extends Item {

    public ItemRoamerSpawnEgg() {
        this.setRegistryName(SumConstants.MOD_NAMESPACE, "roamer_spawn_egg");
        this.setTranslationKey(SumConstants.MOD_NAMESPACE + ".roamer_spawn_egg");
        this.setCreativeTab(SumTab.TAB);
        this.maxStackSize = 64;
        SumRegistry.registerItem(this);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos,
                                      EnumHand hand, EnumFacing facing, float hitX,
                                      float hitY, float hitZ) {
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }

        // Check if the clicked block is a walkable block for roamers
        IBlockState clickedState = world.getBlockState(pos);
        String registryName = clickedState.getBlock().getRegistryName().toString();
        if (!SumConfig.isBlockWalkableByRoamer(registryName)) {
            player.sendMessage(new TextComponentString(
                TextFormatting.RED + "Roamers can only be placed on walkable blocks! "
                + TextFormatting.GRAY + "(" + registryName + " is not in the walkable blocks list)"));
            return EnumActionResult.FAIL;
        }

        BlockPos spawnPos = pos.offset(facing);
        EntityRoamer roamer = new EntityRoamer(world);
        roamer.setLocationAndAngles(
            spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
            world.rand.nextFloat() * 360.0F, 0.0F);
        if (world.spawnEntity(roamer)) {
            Sum.LOGGER.info("Roamer spawned at [{}, {}, {}]",
                spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
            ItemStack stack = player.getHeldItem(hand);
            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1);
            }
            return EnumActionResult.SUCCESS;
        } else {
            player.sendMessage(new TextComponentString(
                TextFormatting.RED + "Failed to spawn Roamer at this location."));
            return EnumActionResult.FAIL;
        }
    }
}
