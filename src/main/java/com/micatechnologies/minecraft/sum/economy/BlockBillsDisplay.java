package com.micatechnologies.minecraft.sum.economy;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import com.micatechnologies.minecraft.sum.atm.Bills;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Decorative bills-display block. Right-click with a bill or packet to insert; right-click
 * empty-handed to take the held bills back. The {@link com.micatechnologies.minecraft.sum.economy.TESRBillsDisplay}
 * renders the contained stack hovering above the block, with multiple visual layers as the
 * count increases — so a $1 bill looks small and a fat stack of $500s looks impressive.
 */
public class BlockBillsDisplay extends Block implements ITileEntityProvider {

    /** Short, tray-like bounding box (4/16 tall) so the floating bill render isn't hidden
     *  inside a full cube. */
    private static final AxisAlignedBB TRAY_AABB = new AxisAlignedBB(
        0.0, 0.0, 0.0, 1.0, 4.0 / 16.0, 1.0);

    public BlockBillsDisplay() {
        super(Material.WOOD);
        setRegistryName(SumConstants.MOD_NAMESPACE, "bills_display");
        setTranslationKey(SumConstants.MOD_NAMESPACE + ".bills_display");
        setHardness(1.0F);
        setResistance(5.0F);
        setSoundType(SoundType.WOOD);
        setCreativeTab(SumTab.TAB);

        SumRegistry.registerBlock(this);
        ItemBlock itemBlock = new ItemBlock(this);
        itemBlock.setRegistryName(getRegistryName());
        SumRegistry.registerItem(itemBlock);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityBillsDisplay();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityBillsDisplay)) return false;
        TileEntityBillsDisplay disp = (TileEntityBillsDisplay) te;
        ItemStack held = player.getHeldItem(hand);

        if (held.isEmpty()) {
            ItemStack out = disp.takeAll();
            if (!out.isEmpty()) {
                if (!player.inventory.addItemStackToInventory(out)) {
                    player.dropItem(out, false);
                }
            }
            return true;
        }

        if (Bills.denominationOf(held.getItem()) > 0 || held.getItem() instanceof ItemSumPacket) {
            ItemStack remainder = disp.insert(held);
            player.setHeldItem(hand, remainder);
        }
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityBillsDisplay) {
            ItemStack held = ((TileEntityBillsDisplay) te).takeAll();
            if (!held.isEmpty()) {
                spawnAsEntity(world, pos, held);
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return TRAY_AABB;
    }

    @Override
    public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public boolean isOpaqueCube(IBlockState state) { return false; }
}
