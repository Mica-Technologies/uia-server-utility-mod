package com.micatechnologies.minecraft.sum.bank;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Single-block passcode-locked vault door. Right-clicking by an unowned vault claims it for
 * the player; further unlocks need {@code /sum vault unlock <code>} once a passcode is set.
 * The block toggles between a solid full cube (closed: blocks light, blocks movement) and
 * an empty model (open: passable, transparent). Auto-closes after 5 seconds via
 * {@link TileEntityVaultDoor}.
 */
public class BlockVaultDoor extends BlockContainer {

    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyBool OPEN = PropertyBool.create("open");

    public BlockVaultDoor() {
        super(Material.IRON);
        setRegistryName(SumConstants.MOD_NAMESPACE, "vault_door");
        setTranslationKey(SumConstants.MOD_NAMESPACE + ".vault_door");
        setHardness(5.0F);
        setResistance(30.0F);
        setSoundType(SoundType.METAL);
        setCreativeTab(SumTab.TAB);
        setDefaultState(blockState.getBaseState()
            .withProperty(FACING, EnumFacing.NORTH)
            .withProperty(OPEN, false));

        SumRegistry.registerBlock(this);
        ItemBlock itemBlock = new ItemBlock(this);
        itemBlock.setRegistryName(getRegistryName());
        SumRegistry.registerItem(itemBlock);
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileEntityVaultDoor();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        return getDefaultState()
            .withProperty(FACING, placer.getHorizontalFacing().getOpposite())
            .withProperty(OPEN, false);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityVaultDoor)) {
            return true;
        }
        TileEntityVaultDoor vault = (TileEntityVaultDoor) te;

        if (state.getValue(OPEN)) {
            // Already open - right-clicking again resets the auto-close timer (keep it open longer).
            vault.onOpened();
            return true;
        }

        if (!vault.isClaimed()) {
            vault.claim(player);
            sendMessage(player, TextFormatting.GREEN,
                "Vault claimed. Set a passcode with: /sum vault setcode <code>");
            openDoor(world, pos);
            return true;
        }
        if (vault.isOwner(player)) {
            openDoor(world, pos);
            sendMessage(player, TextFormatting.GREEN, "Vault opened.");
            return true;
        }
        if (!vault.hasPasscode()) {
            // Owned but unprotected; act like a public door.
            openDoor(world, pos);
            return true;
        }
        sendMessage(player, TextFormatting.YELLOW,
            "This vault is locked. Use: /sum vault unlock <code>");
        return true;
    }

    /** Flip the door to OPEN, play the open sound, and start the auto-close timer. */
    public static void openDoor(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockVaultDoor) || state.getValue(OPEN)) {
            return;
        }
        world.setBlockState(pos, state.withProperty(OPEN, true), 3);
        world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 1.0F, 0.6F);
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityVaultDoor) {
            ((TileEntityVaultDoor) te).onOpened();
        }
    }

    public static void closeDoor(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockVaultDoor) || !state.getValue(OPEN)) {
            return;
        }
        world.setBlockState(pos, state.withProperty(OPEN, false), 3);
        world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, 1.0F, 0.6F);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return state.getValue(OPEN) ? Block.NULL_AABB : Block.FULL_BLOCK_AABB;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(OPEN) ? Block.NULL_AABB : Block.FULL_BLOCK_AABB;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return !state.getValue(OPEN);
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return !state.getValue(OPEN);
    }

    /** BlockContainer defaults to {@code INVISIBLE} which would hide the door entirely.
     *  We render via JSON model in both states, so use MODEL. */
    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.byHorizontalIndex(meta & 0x3);
        boolean open = (meta & 0x4) != 0;
        return getDefaultState().withProperty(FACING, facing).withProperty(OPEN, open);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(OPEN) ? 0x4 : 0);
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rot) {
        return state.withProperty(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withRotation(mirror.toRotation(state.getValue(FACING)));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, OPEN);
    }

    private static void sendMessage(EntityPlayer player, TextFormatting color, String text) {
        TextComponentString tcs = new TextComponentString(text);
        tcs.getStyle().setColor(color);
        player.sendMessage(tcs);
    }
}
