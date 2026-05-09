package com.micatechnologies.minecraft.sum.dropper;

import com.micatechnologies.minecraft.sum.SumConfig;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityDropper;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;

public class TileEntityAutoDropper extends TileEntityDropper implements ITickable {

    private int tickCounter;

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }
        int interval = SumConfig.getAutoDropperTickInterval();
        if (interval <= 0) {
            return;
        }
        if (++tickCounter < interval) {
            return;
        }
        tickCounter = 0;
        if (!SumConfig.isAutoDropperEnabled()) {
            return;
        }
        if (world.isBlockPowered(pos)) {
            return;
        }
        dispenseOne();
    }

    private void dispenseOne() {
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockDispenser)) {
            return;
        }
        EnumFacing facing = state.getValue(BlockDispenser.FACING);
        int slot = getDispenseSlot();
        if (slot < 0) {
            return;
        }
        ItemStack stack = decrStackSize(slot, 1);
        if (stack.isEmpty()) {
            return;
        }
        BlockPos frontPos = pos.offset(facing);
        TileEntity frontTe = world.getTileEntity(frontPos);
        ItemStack remaining = stack;
        if (frontTe instanceof IInventory) {
            remaining = TileEntityHopper.putStackInInventoryAllSlots(
                this, (IInventory) frontTe, stack, facing.getOpposite());
        }
        if (remaining != null && !remaining.isEmpty()) {
            spawnNoScatter(remaining, frontPos);
        }
        markDirty();
    }

    private void spawnNoScatter(ItemStack stack, BlockPos frontPos) {
        double x = frontPos.getX() + 0.5;
        double y = frontPos.getY() + 0.5;
        double z = frontPos.getZ() + 0.5;
        EntityItem item = new EntityItem(world, x, y, z, stack);
        item.motionX = 0.0;
        item.motionY = 0.0;
        item.motionZ = 0.0;
        item.setDefaultPickupDelay();
        world.spawnEntity(item);
    }
}
