package com.micatechnologies.minecraft.sum.shop;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import com.micatechnologies.minecraft.sum.atm.SumGuiHandler;
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
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.NonNullList;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Player-owned vending machine. First right-click claims the shop; subsequent right-clicks by
 * the owner open the owner GUI (configure item/amount/cost, refill stock, withdraw funds).
 * Right-clicks by other players open the buyer GUI (see what's for sale, click Buy). The
 * tile entity owns all state; this block is just the placement/interaction shell.
 *
 * <p>Breaking the shop drops its stock, template, and accumulated funds (as bills) — owner
 * loss-protection.
 *
 * <p>The model is a cabinet rather than a full cube: hood, base and two side pillars around a
 * cavity closed by a recessed glass pane, with {@link TESRShop} floating the sale item inside
 * it so passers-by can see the goods without opening anything.
 *
 * <p>{@link BlockServerShop} extends this with the ownerless variant, which is why the
 * registry name is a constructor argument and the interaction handler is the only piece it
 * needs to replace.
 */
public class BlockSumShop extends Block implements ITileEntityProvider {

    public static final PropertyDirection FACING = PropertyDirection.create(
        "facing", EnumFacing.Plane.HORIZONTAL);

    public BlockSumShop() {
        this("shop");
    }

    protected BlockSumShop(String registryName) {
        super(Material.IRON);
        setRegistryName(SumConstants.MOD_NAMESPACE, registryName);
        setTranslationKey(SumConstants.MOD_NAMESPACE + "." + registryName);
        setHardness(2.5F);
        setResistance(15.0F);
        setSoundType(SoundType.METAL);
        setCreativeTab(SumTab.TAB);
        // The cabinet's interior faces sample light at the block's own position. Left at the
        // material default (fully light-blocking) that position is always pitch black, so the
        // goods would sit in an unlit box; a glass-fronted display case letting light through
        // is both the better look and the honest physical description.
        setLightOpacity(0);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));

        SumRegistry.registerBlock(this);
        ItemBlock itemBlock = new ItemBlock(this);
        itemBlock.setRegistryName(getRegistryName());
        SumRegistry.registerItem(itemBlock);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityShop();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityShop)) {
            return false;
        }
        TileEntityShop shop = (TileEntityShop) te;

        if (!shop.isOwned()) {
            // First right-click claims the shop and opens owner GUI for setup.
            shop.claim(player);
            player.sendMessage(new TextComponentString(
                TextFormatting.GREEN + "Shop claimed. Set the item, amount, and price to open for business."));
            player.openGui(Sum.instance, SumGuiHandler.GUI_SHOP_OWNER, world,
                pos.getX(), pos.getY(), pos.getZ());
            return true;
        }

        if (shop.isOwner(player)) {
            player.openGui(Sum.instance, SumGuiHandler.GUI_SHOP_OWNER, world,
                pos.getX(), pos.getY(), pos.getZ());
            return true;
        }

        if (!shop.isConfigured()) {
            player.sendMessage(new TextComponentString(
                TextFormatting.YELLOW + "This shop isn't open for business yet."));
            return true;
        }

        player.openGui(Sum.instance, SumGuiHandler.GUI_SHOP_BUYER, world,
            pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityShop) {
            NonNullList<ItemStack> drops = ((TileEntityShop) te).collectDrops();
            for (ItemStack drop : drops) {
                spawnAsEntity(world, pos, drop);
            }
        }
        super.breakBlock(world, pos, state);
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

    /** Vending machines have a transparent glass front; render type is non-default. */
    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    /**
     * The glass pane is genuinely semi-transparent (alpha in {@code shop_glass.png}), which
     * only survives in the translucent pass — {@code CUTOUT} would round it to fully opaque and
     * put us back to a painted-on window. The opaque cabinet quads sit in the same layer at
     * alpha 255, exactly as vanilla stained glass does.
     *
     * <p>{@code @SideOnly} mirrors the method it overrides, so FML strips it on a dedicated
     * server rather than leaving an override of a method the server jar doesn't have.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.TRANSLUCENT;
    }

    /** The model is a cabinet with a cavity, not a solid cube. Collision stays full-block. */
    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        // Glass front means we don't fully occlude; let neighbors render their faces.
        return false;
    }

    /**
     * Every face except the recessed window is still a flat full-block face, so torches, fences
     * and the like keep attaching to the cabinet the way they did when this was a full cube.
     */
    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos,
                                            EnumFacing face) {
        return face == state.getValue(FACING) ? BlockFaceShape.UNDEFINED : BlockFaceShape.SOLID;
    }

    @Override
    public boolean isSideSolid(IBlockState state, IBlockAccess world, BlockPos pos,
                               EnumFacing side) {
        return side != state.getValue(FACING);
    }
}
