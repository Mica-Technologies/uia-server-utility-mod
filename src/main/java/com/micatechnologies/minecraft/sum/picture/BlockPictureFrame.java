package com.micatechnologies.minecraft.sum.picture;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Online picture frame: a wall-mounted plate that shows an image from a URL.
 *
 * <p>The block has no model of its own. {@link TESRPictureFrame} draws the picture, which can be
 * up to {@code picture_frame.maxSizeBlocks} wide and tall and offset from the block, so the block
 * is only the anchor and the thing you click. Its box is a 1/16-thick plate against the wall.
 *
 * <p>Right-click opens the editor for the placer and for operators. The server decides that and
 * sends {@link PacketOpenPictureFrame}; the client never opens the editor on its own say-so.
 */
public class BlockPictureFrame extends Block implements ITileEntityProvider {

    public static final PropertyDirection FACING = PropertyDirection.create(
        "facing", EnumFacing.Plane.HORIZONTAL);

    /** Plate thickness, in blocks. The renderer draws the picture just in front of it. */
    public static final double THICKNESS = 1.0 / 16.0;

    private static final AxisAlignedBB BB_NORTH = new AxisAlignedBB(0.0, 0.0, 1.0 - THICKNESS, 1.0, 1.0, 1.0);
    private static final AxisAlignedBB BB_SOUTH = new AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 1.0, THICKNESS);
    private static final AxisAlignedBB BB_WEST = new AxisAlignedBB(1.0 - THICKNESS, 0.0, 0.0, 1.0, 1.0, 1.0);
    private static final AxisAlignedBB BB_EAST = new AxisAlignedBB(0.0, 0.0, 0.0, THICKNESS, 1.0, 1.0);

    public BlockPictureFrame() {
        super(Material.WOOD);
        setRegistryName(SumConstants.MOD_NAMESPACE, "picture_frame");
        setTranslationKey(SumConstants.MOD_NAMESPACE + ".picture_frame");
        setHardness(0.5F);
        setResistance(2.0F);
        setSoundType(SoundType.WOOD);
        setCreativeTab(SumTab.TAB);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));

        SumRegistry.registerBlock(this);
        ItemBlock itemBlock = new ItemBlock(this);
        itemBlock.setRegistryName(getRegistryName());
        SumRegistry.registerItem(itemBlock);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityPictureFrame();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        EnumFacing out = facing.getAxis().isHorizontal()
            ? facing : placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING, out);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                EntityLivingBase placer, ItemStack stack) {
        if (world.isRemote || !(placer instanceof EntityPlayer)) return;
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityPictureFrame) {
            ((TileEntityPictureFrame) te).setOwner((EntityPlayer) placer);
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) return true;
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityPictureFrame) || !(player instanceof EntityPlayerMP)) return true;
        TileEntityPictureFrame frame = (TileEntityPictureFrame) te;
        if (!frame.canEdit(player)) {
            TextComponentString msg = new TextComponentString("Only "
                + (frame.getOwnerName().isEmpty() ? "an operator" : frame.getOwnerName() + " or an operator")
                + " can change this picture.");
            msg.getStyle().setColor(TextFormatting.GRAY);
            player.sendStatusMessage(msg, true);
            return true;
        }
        SumNetwork.CHANNEL.sendTo(new PacketOpenPictureFrame(pos, frame.getSettings()),
            (EntityPlayerMP) player);
        return true;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        // No block model: the TESR draws everything.
        return EnumBlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        switch (state.getValue(FACING)) {
            case SOUTH: return BB_SOUTH;
            case WEST:  return BB_WEST;
            case EAST:  return BB_EAST;
            case NORTH:
            default:    return BB_NORTH;
        }
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public boolean isOpaqueCube(IBlockState state) { return false; }

    @Override
    public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        return side.getAxis().isHorizontal() && super.canPlaceBlockOnSide(world, pos, side);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
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
        return new BlockStateContainer(this, FACING);
    }
}
