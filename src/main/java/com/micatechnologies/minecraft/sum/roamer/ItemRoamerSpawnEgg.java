package com.micatechnologies.minecraft.sum.roamer;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumTab;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ItemRoamerSpawnEgg extends Item {

    public ItemRoamerSpawnEgg() {
        this.setRegistryName(SumConstants.MOD_NAMESPACE, "roamer_spawn_egg");
        this.setTranslationKey(SumConstants.MOD_NAMESPACE + ".roamer_spawn_egg");
        this.setCreativeTab(SumTab.TAB);
        this.maxStackSize = 64;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos,
                                      EnumHand hand, EnumFacing facing, float hitX,
                                      float hitY, float hitZ) {
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }

        BlockPos spawnPos = pos.offset(facing);
        EntityRoamer roamer = new EntityRoamer(world);
        roamer.setPosition(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        world.spawnEntity(roamer);

        ItemStack stack = player.getHeldItem(hand);
        if (!player.capabilities.isCreativeMode) {
            stack.shrink(1);
        }

        return EnumActionResult.SUCCESS;
    }
}
