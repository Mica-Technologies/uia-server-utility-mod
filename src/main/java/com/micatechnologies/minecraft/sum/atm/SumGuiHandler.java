package com.micatechnologies.minecraft.sum.atm;

import com.micatechnologies.minecraft.sum.bank.ContainerSafeDeposit;
import com.micatechnologies.minecraft.sum.bank.GuiSafeDeposit;
import com.micatechnologies.minecraft.sum.bank.InventorySafeDeposit;
import com.micatechnologies.minecraft.sum.bank.SafeDepositSavedData;
import com.micatechnologies.minecraft.sum.economy.ContainerBillChanger;
import com.micatechnologies.minecraft.sum.economy.GuiBillChanger;
import com.micatechnologies.minecraft.sum.economy.TileEntityBillChanger;
import com.micatechnologies.minecraft.sum.jobs.GuiJobBoard;
import com.micatechnologies.minecraft.sum.jobs.JobBoardSavedData;
import com.micatechnologies.minecraft.sum.mailbox.ContainerMailbox;
import com.micatechnologies.minecraft.sum.mailbox.GuiMailbox;
import com.micatechnologies.minecraft.sum.mailbox.TileEntityMailbox;
import com.micatechnologies.minecraft.sum.shop.ContainerShopBuyer;
import com.micatechnologies.minecraft.sum.shop.ContainerShopOwner;
import com.micatechnologies.minecraft.sum.shop.GuiShopBuyer;
import com.micatechnologies.minecraft.sum.shop.GuiShopOwner;
import com.micatechnologies.minecraft.sum.shop.TileEntityShop;
import com.micatechnologies.minecraft.sum.signpost.ContainerSignpost;
import com.micatechnologies.minecraft.sum.signpost.GuiSignpost;
import com.micatechnologies.minecraft.sum.signpost.TileEntitySignpost;
import com.micatechnologies.minecraft.sum.trash.ContainerTrashCan;
import com.micatechnologies.minecraft.sum.trash.GuiTrashCan;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

/**
 * GUI dispatcher for SUM blocks. Use {@link net.minecraft.entity.player.EntityPlayer#openGui}
 * with one of the {@code GUI_*} constants from a server-side block activation handler. Forge
 * routes the call to {@link #getServerGuiElement} on the integrated/dedicated server and
 * {@link #getClientGuiElement} on the client.
 */
public class SumGuiHandler implements IGuiHandler {

    public static final int GUI_ATM = 0;
    public static final int GUI_SAFE_DEPOSIT = 1;
    public static final int GUI_SHOP_OWNER = 2;
    public static final int GUI_SHOP_BUYER = 3;
    public static final int GUI_BILL_CHANGER = 4;
    public static final int GUI_TRASH_CAN = 5;
    public static final int GUI_MAILBOX = 6;
    public static final int GUI_JOB_BOARD = 7;
    public static final int GUI_SIGNPOST = 8;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == GUI_SAFE_DEPOSIT) {
            BlockPos pos = new BlockPos(x, y, z);
            SafeDepositSavedData data = SafeDepositSavedData.get(world);
            NonNullList<ItemStack> backing = data.getInventory(player.getUniqueID(), pos);
            InventorySafeDeposit inv = new InventorySafeDeposit(
                player.getUniqueID(), pos, data, backing);
            return new ContainerSafeDeposit(inv, player);
        }
        if (id == GUI_SHOP_OWNER) {
            TileEntityShop shop = lookupShop(world, x, y, z);
            return shop == null ? null : new ContainerShopOwner(shop, player);
        }
        if (id == GUI_SHOP_BUYER) {
            TileEntityShop shop = lookupShop(world, x, y, z);
            return shop == null ? null : new ContainerShopBuyer(shop, player);
        }
        if (id == GUI_BILL_CHANGER) {
            TileEntityBillChanger ch = lookupChanger(world, x, y, z);
            return ch == null ? null : new ContainerBillChanger(ch, player);
        }
        if (id == GUI_TRASH_CAN) {
            return new ContainerTrashCan(
                new InventoryBasic("sum.trash.title", false, ContainerTrashCan.TRASH_SLOTS),
                player);
        }
        if (id == GUI_MAILBOX) {
            TileEntityMailbox mb = lookupMailbox(world, x, y, z);
            return mb == null ? null : new ContainerMailbox(mb, player);
        }
        if (id == GUI_SIGNPOST) {
            // Signposts have no inventory; the Container exists only for openGui plumbing.
            // Arm changes flow through PacketSignpostUpdate, which independently re-resolves
            // the TileEntity at the targeted BlockPos and validates reach.
            return new ContainerSignpost();
        }
        // ATM is GuiScreen-only (no inventory slots), so no Container is needed server-side.
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == GUI_ATM) {
            return new GuiSumAtm(player);
        }
        if (id == GUI_SAFE_DEPOSIT) {
            // Client doesn't have access to the SavedData; the Container syncs slot contents
            // from the server's matching ContainerSafeDeposit, so the empty list is fine.
            BlockPos pos = new BlockPos(x, y, z);
            InventorySafeDeposit clientInv = new InventorySafeDeposit(
                player.getUniqueID(), pos, null,
                NonNullList.withSize(SafeDepositSavedData.SLOT_COUNT, ItemStack.EMPTY));
            return new GuiSafeDeposit(new ContainerSafeDeposit(clientInv, player));
        }
        if (id == GUI_SHOP_OWNER) {
            TileEntityShop shop = lookupShop(world, x, y, z);
            return shop == null ? null : new GuiShopOwner(new ContainerShopOwner(shop, player), player);
        }
        if (id == GUI_SHOP_BUYER) {
            TileEntityShop shop = lookupShop(world, x, y, z);
            return shop == null ? null : new GuiShopBuyer(new ContainerShopBuyer(shop, player), player);
        }
        if (id == GUI_BILL_CHANGER) {
            TileEntityBillChanger ch = lookupChanger(world, x, y, z);
            return ch == null ? null : new GuiBillChanger(new ContainerBillChanger(ch, player));
        }
        if (id == GUI_TRASH_CAN) {
            return new GuiTrashCan(new ContainerTrashCan(
                new InventoryBasic("sum.trash.title", false, ContainerTrashCan.TRASH_SLOTS),
                player));
        }
        if (id == GUI_MAILBOX) {
            TileEntityMailbox mb = lookupMailbox(world, x, y, z);
            return mb == null ? null : new GuiMailbox(new ContainerMailbox(mb, player));
        }
        if (id == GUI_JOB_BOARD) {
            return new GuiJobBoard(player,
                JobBoardSavedData.get(world).getActive(System.currentTimeMillis()));
        }
        if (id == GUI_SIGNPOST) {
            BlockPos pos = new BlockPos(x, y, z);
            TileEntity te = world.getTileEntity(pos);
            if (!(te instanceof TileEntitySignpost)) {
                return null;
            }
            return new GuiSignpost(pos, ((TileEntitySignpost) te).getArms());
        }
        return null;
    }

    private static TileEntityMailbox lookupMailbox(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        return te instanceof TileEntityMailbox ? (TileEntityMailbox) te : null;
    }

    private static TileEntityShop lookupShop(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        return te instanceof TileEntityShop ? (TileEntityShop) te : null;
    }

    private static TileEntityBillChanger lookupChanger(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        return te instanceof TileEntityBillChanger ? (TileEntityBillChanger) te : null;
    }
}
