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
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.NonNullList;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * Player-owned vending machine. First right-click claims the shop; subsequent right-clicks by
 * the owner open the owner GUI (configure item/amount/cost, refill stock, withdraw funds).
 * Right-clicks by other players open the buyer GUI (see what's for sale, click Buy). The
 * tile entity owns all state; this block is just the placement/interaction shell.
 *
 * <p>Breaking the shop drops its stock, template, and accumulated funds (as bills) — owner
 * loss-protection.
 */
public class BlockSumShop extends Block implements ITileEntityProvider {

    public static final PropertyDirection FACING = PropertyDirection.create(
        "facing", EnumFacing.Plane.HORIZONTAL);

    public BlockSumShop() {
        super(Material.IRON);
        setRegistryName(SumConstants.MOD_NAMESPACE, "shop");
        setTranslationKey(SumConstants.MOD_NAMESPACE + ".shop");
        setHardness(2.5F);
        setResistance(15.0F);
        setSoundType(SoundType.METAL);
        setCreativeTab(SumTab.TAB);
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

    @Override
    public boolean isFullCube(IBlockState state) {
        return true;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        // Glass front means we don't fully occlude; let neighbors render their faces.
        return false;
    }
}
