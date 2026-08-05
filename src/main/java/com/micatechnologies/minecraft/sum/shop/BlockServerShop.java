package com.micatechnologies.minecraft.sum.shop;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.atm.SumGuiHandler;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * Ownerless vending machine. Same cabinet, same buyer flow and same GUIs as
 * {@link BlockSumShop}; the difference is entirely in who does what:
 *
 * <ul>
 *   <li><b>Sneak + right-click with an empty hand</b> opens the setup GUI, for operators only.
 *       (Sneaking with something in hand uses the held item instead — that is vanilla's
 *       interaction rule, not a choice this block makes.)</li>
 *   <li><b>Plain right-click</b> opens the buyer GUI for <em>everyone</em>, operators included.
 *       Being able to stock the shop does not make its goods free.</li>
 * </ul>
 *
 * <p>Only an operator can break it, and it shrugs off explosions, so a shop the staff set up
 * can't be walked off with by the first player to bring a pickaxe. See
 * {@link TileEntityServerShop} for where the money goes (nowhere — it is a sink).
 */
public class BlockServerShop extends BlockSumShop {

    public BlockServerShop() {
        super("server_shop");
        // Obsidian-class blast resistance: an admin fixture shouldn't be removable by creeper.
        setResistance(2000.0F);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityServerShop();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityServerShop)) {
            return false;
        }
        TileEntityServerShop shop = (TileEntityServerShop) te;

        if (player.isSneaking()) {
            if (!shop.canManage(player)) {
                player.sendMessage(new TextComponentString(TextFormatting.RED
                    + "Only server operators can set up a server shop."));
                return true;
            }
            player.openGui(Sum.instance, SumGuiHandler.GUI_SHOP_OWNER, world,
                pos.getX(), pos.getY(), pos.getZ());
            return true;
        }

        if (!shop.isConfigured()) {
            player.sendMessage(new TextComponentString(shop.canManage(player)
                ? TextFormatting.YELLOW + "This server shop isn't set up yet. Sneak and "
                    + "right-click it with an empty hand to configure it."
                : TextFormatting.YELLOW + "This shop isn't open for business yet."));
            return true;
        }

        player.openGui(Sum.instance, SumGuiHandler.GUI_SHOP_BUYER, world,
            pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    /**
     * Server shops are operator fixtures, not loot. Refusing removal here is the authoritative
     * check (it also covers creative-mode instant breaking); {@link
     * #getPlayerRelativeBlockHardness} keeps the client from animating a break that will be
     * rejected.
     */
    @Override
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos,
                                   EntityPlayer player, boolean willHarvest) {
        if (!canManage(world, pos, player)) {
            if (!world.isRemote) {
                player.sendMessage(new TextComponentString(TextFormatting.RED
                    + "Only server operators can break a server shop."));
            }
            return false;
        }
        return super.removedByPlayer(state, world, pos, player, willHarvest);
    }

    @Override
    public float getPlayerRelativeBlockHardness(IBlockState state, EntityPlayer player,
                                                World world, BlockPos pos) {
        return canManage(world, pos, player)
            ? super.getPlayerRelativeBlockHardness(state, player, world, pos)
            : 0.0F;
    }

    private static boolean canManage(World world, BlockPos pos, EntityPlayer player) {
        TileEntity te = world.getTileEntity(pos);
        // A missing TE means the block is mid-removal or corrupt; don't strand it as unbreakable.
        return !(te instanceof TileEntityShop) || ((TileEntityShop) te).canManage(player);
    }
}
