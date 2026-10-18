package com.micatechnologies.minecraft.sum.jail;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Jail Alarm Relay: gives a full redstone signal while a jailbreak alarm sounds for a cell it is
 * linked to (link it with the Jail Control Tool), and none otherwise. See {@link JailAlarms}.
 *
 * <p>It re-checks the alarm once a second while powered and switches itself off when the alarm
 * has run out or been silenced, so a relay never sticks on after a restart or a missed silence.
 */
public class BlockJailAlarmRelay extends Block {

    public static final PropertyBool POWERED = PropertyBool.create("powered");

    private static final int RECHECK_TICKS = 20;

    public BlockJailAlarmRelay() {
        super(Material.IRON);
        setRegistryName(SumConstants.MOD_NAMESPACE, "jail_alarm_relay");
        setTranslationKey(SumConstants.MOD_NAMESPACE + ".jail_alarm_relay");
        setHardness(1.5F);
        setResistance(10.0F);
        setSoundType(SoundType.METAL);
        setCreativeTab(SumTab.TAB);
        setDefaultState(blockState.getBaseState().withProperty(POWERED, false));

        SumRegistry.registerBlock(this);
        ItemBlock itemBlock = new ItemBlock(this);
        itemBlock.setRegistryName(getRegistryName());
        SumRegistry.registerItem(itemBlock);
    }

    static void power(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockJailAlarmRelay)) return;
        if (!state.getValue(POWERED)) {
            world.setBlockState(pos, state.withProperty(POWERED, true), 3);
        }
        world.scheduleUpdate(pos, state.getBlock(), RECHECK_TICKS);
    }

    static void unpower(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof BlockJailAlarmRelay && state.getValue(POWERED)) {
            world.setBlockState(pos, state.withProperty(POWERED, false), 3);
        }
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
        if (world.isRemote || !state.getValue(POWERED)) return;
        if (JailAlarms.isSoundingFor(world, pos)) {
            world.scheduleUpdate(pos, this, RECHECK_TICKS);
        } else {
            unpower(world, pos);
        }
    }

    @Override
    public boolean canProvidePower(IBlockState state) {
        return true;
    }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return getWeakPower(state, world, pos, side);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(POWERED, (meta & 1) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(POWERED) ? 1 : 0;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, POWERED);
    }
}
