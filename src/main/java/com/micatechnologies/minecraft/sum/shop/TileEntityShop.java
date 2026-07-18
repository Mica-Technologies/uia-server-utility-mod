package com.micatechnologies.minecraft.sum.shop;

import com.micatechnologies.minecraft.sum.atm.Bills;
import com.micatechnologies.minecraft.sum.economy.EconomyBridge;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

/**
 * Server-side state for {@link BlockSumShop}: owner, sale-item template, sale amount, sale
 * price, stock inventory, accumulated funds, and the admin "infinite stock" flag.
 *
 * <p><b>Slot layout.</b> The TE owns 10 inventory slots:
 * <ul>
 *   <li>Slot {@link #TEMPLATE_SLOT} — the sale-item template. Its current contents define
 *       what the shop sells; only items matching this template (item + meta + NBT) count as
 *       stock. Empty template means the shop is unconfigured.</li>
 *   <li>Slots {@link #STOCK_SLOT_FIRST}..{@link #STOCK_SLOT_LAST} — the 3x3 stock grid the
 *       owner refills.</li>
 * </ul>
 *
 * <p><b>Buy flow.</b> A non-owner buyer triggers {@link #attemptPurchase}. The TE validates
 * configured/has-stock/buyer-has-funds, then decrements stock + buyer balance, increments
 * accumulated funds, and gifts a fresh stack copied from the template (size = saleAmount).
 *
 * <p><b>Infinite-stock flag.</b> Admin-only toggle. When set, stock checks/decrements are
 * skipped and every buy succeeds against an unlimited well of items.
 */
public class TileEntityShop extends TileEntity implements IInventory {

    public static final int TEMPLATE_SLOT = 0;
    public static final int STOCK_SLOT_FIRST = 1;
    public static final int STOCK_SLOT_LAST = 9;
    public static final int INVENTORY_SIZE = 10;

    public static final int MIN_SALE_AMOUNT = 1;
    public static final int MAX_SALE_AMOUNT = 64;
    public static final double MIN_SALE_COST = 0.0;
    public static final double MAX_SALE_COST = 1_000_000.0;

    private final NonNullList<ItemStack> slots =
        NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);

    @Nullable
    private UUID ownerUuid;
    private String ownerName = "";
    private int saleAmount = 1;
    private double saleCost = 1.0;
    private double fundsAccumulated = 0.0;
    private boolean infiniteStock = false;

    // --- Owner ---

    public boolean isOwned() {
        return ownerUuid != null;
    }

    @Nullable
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public boolean isOwner(EntityPlayer player) {
        return ownerUuid != null && ownerUuid.equals(player.getUniqueID());
    }

    /** Claims an unowned shop for {@code player}. Idempotent on already-owned shops. */
    public boolean claim(EntityPlayer player) {
        if (ownerUuid != null) return false;
        this.ownerUuid = player.getUniqueID();
        this.ownerName = player.getName();
        markDirtyAndSync();
        return true;
    }

    public void disown() {
        this.ownerUuid = null;
        this.ownerName = "";
        this.fundsAccumulated = 0.0;
        markDirtyAndSync();
    }

    // --- Sale config ---

    public int getSaleAmount() { return saleAmount; }
    public double getSaleCost() { return saleCost; }
    public double getFundsAccumulated() { return fundsAccumulated; }
    public boolean isInfiniteStock() { return infiniteStock; }

    public void setSaleAmount(int amount) {
        this.saleAmount = clamp(amount, MIN_SALE_AMOUNT, MAX_SALE_AMOUNT);
        markDirtyAndSync();
    }

    public void setSaleCost(double cost) {
        this.saleCost = clampD(cost, MIN_SALE_COST, MAX_SALE_COST);
        markDirtyAndSync();
    }

    /** Admin-only toggle. The block layer enforces the permission check. */
    public void setInfiniteStock(boolean infinite) {
        this.infiniteStock = infinite;
        markDirtyAndSync();
    }

    /** True when there's a sale item set and the price is non-negative. Used by the buyer
     *  GUI gate; an unconfigured shop just shows a "not open for business" message. */
    public boolean isConfigured() {
        return !getStackInSlot(TEMPLATE_SLOT).isEmpty() && saleCost >= MIN_SALE_COST;
    }

    public ItemStack getSaleTemplate() {
        return getStackInSlot(TEMPLATE_SLOT);
    }

    // --- Funds ---

    /**
     * Withdraws all accumulated funds to the owner's balance. Returns the amount withdrawn,
     * or 0 if there was nothing to withdraw (or no balance backend).
     */
    public double withdrawFunds(EntityPlayer player) {
        if (!isOwner(player) || fundsAccumulated <= 0.0) {
            return 0.0;
        }
        if (!EconomyBridge.adjustBalance(player, fundsAccumulated)) {
            return 0.0;
        }
        double amount = fundsAccumulated;
        this.fundsAccumulated = 0.0;
        markDirtyAndSync();
        return amount;
    }

    // --- Stock helpers ---

    /** Total count of items in stock slots that match the template by item + meta + NBT.
     *  Returns {@link Integer#MAX_VALUE} when {@link #isInfiniteStock infinite stock} is on. */
    public int getStockCount() {
        if (infiniteStock) return Integer.MAX_VALUE;
        ItemStack template = getSaleTemplate();
        if (template.isEmpty()) return 0;
        int total = 0;
        for (int i = STOCK_SLOT_FIRST; i <= STOCK_SLOT_LAST; i++) {
            ItemStack stack = getStackInSlot(i);
            if (matchesTemplate(template, stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public boolean hasEnoughStock() {
        return getStockCount() >= saleAmount;
    }

    private static boolean matchesTemplate(ItemStack template, ItemStack candidate) {
        if (template.isEmpty() || candidate.isEmpty()) return false;
        return ItemStack.areItemsEqual(template, candidate)
            && ItemStack.areItemStackTagsEqual(template, candidate);
    }

    // --- Purchase ---

    public enum BuyResult {
        OK,
        UNCONFIGURED,
        OWNER_CANT_BUY,
        OUT_OF_STOCK,
        INSUFFICIENT_FUNDS,
        ECONOMY_UNAVAILABLE,
        INVENTORY_FULL,
    }

    /**
     * Attempts to buy {@link #saleAmount} of the template from this shop on behalf of
     * {@code buyer}. Server-side; safe to call from a packet handler. Mutates state on success
     * and on partial failure modes that already debited the buyer (the implementation refunds
     * before returning a failure code, except for INVENTORY_FULL, which drops the items at the
     * buyer's feet).
     */
    public BuyResult attemptPurchase(EntityPlayerMP buyer) {
        if (!isConfigured()) return BuyResult.UNCONFIGURED;
        if (isOwner(buyer)) return BuyResult.OWNER_CANT_BUY;
        if (!EconomyBridge.isAvailable()) return BuyResult.ECONOMY_UNAVAILABLE;
        if (!hasEnoughStock()) return BuyResult.OUT_OF_STOCK;

        double balance = EconomyBridge.getBalance(buyer);
        if (Double.isNaN(balance) || balance < saleCost) {
            return BuyResult.INSUFFICIENT_FUNDS;
        }

        if (!EconomyBridge.adjustBalance(buyer, -saleCost)) {
            return BuyResult.INSUFFICIENT_FUNDS;
        }

        // From this point we've taken the buyer's money — any failure must refund.
        ItemStack template = getSaleTemplate();
        ItemStack toGive = template.copy();
        toGive.setCount(saleAmount);

        if (!infiniteStock) {
            int needed = saleAmount;
            for (int i = STOCK_SLOT_FIRST; i <= STOCK_SLOT_LAST && needed > 0; i++) {
                ItemStack stack = getStackInSlot(i);
                if (matchesTemplate(template, stack)) {
                    int take = Math.min(stack.getCount(), needed);
                    stack.shrink(take);
                    if (stack.isEmpty()) {
                        slots.set(i, ItemStack.EMPTY);
                    }
                    needed -= take;
                }
            }
            if (needed > 0) {
                // Should not happen given hasEnoughStock above, but defend in depth.
                EconomyBridge.adjustBalance(buyer, saleCost);
                return BuyResult.OUT_OF_STOCK;
            }
        }

        fundsAccumulated += saleCost;

        if (!buyer.inventory.addItemStackToInventory(toGive)) {
            // Inventory full; drop the leftover at the player's feet rather than swallowing it.
            buyer.dropItem(toGive, false);
        }
        buyer.inventoryContainer.detectAndSendChanges();
        markDirtyAndSync();
        return BuyResult.OK;
    }

    /** Best-effort owner-readable description of the result for chat. */
    public static ITextComponent describe(BuyResult result, double saleCost, double balance) {
        switch (result) {
            case OK:
                return new TextComponentString(TextFormatting.GREEN
                    + "Purchase complete (-$"
                    + String.format(java.util.Locale.ROOT, "%.2f", saleCost) + ").");
            case UNCONFIGURED:
                return new TextComponentString(TextFormatting.RED
                    + "This shop isn't open for business yet.");
            case OWNER_CANT_BUY:
                return new TextComponentString(TextFormatting.RED
                    + "You can't buy from your own shop.");
            case OUT_OF_STOCK:
                return new TextComponentString(TextFormatting.RED + "Sold out.");
            case INSUFFICIENT_FUNDS:
                return new TextComponentString(TextFormatting.RED
                    + "Insufficient funds. You need $"
                    + String.format(java.util.Locale.ROOT, "%.2f", saleCost)
                    + ", balance is $"
                    + String.format(java.util.Locale.ROOT, "%.2f", balance) + ".");
            case ECONOMY_UNAVAILABLE:
                return new TextComponentString(TextFormatting.RED
                    + "No economy backend is loaded.");
            case INVENTORY_FULL:
                return new TextComponentString(TextFormatting.YELLOW
                    + "Your inventory is full; items dropped at your feet.");
            default:
                return new TextComponentTranslation("commands.generic.unknown");
        }
    }

    // --- IInventory ---

    @Override
    public int getSizeInventory() { return INVENTORY_SIZE; }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : slots) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return index < 0 || index >= slots.size() ? ItemStack.EMPTY : slots.get(index);
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        ItemStack stack = ItemStackHelper.getAndSplit(slots, index, count);
        if (!stack.isEmpty()) markDirtyAndSync();
        return stack;
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        ItemStack stack = ItemStackHelper.getAndRemove(slots, index);
        if (!stack.isEmpty()) markDirtyAndSync();
        return stack;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        slots.set(index, stack);
        if (!stack.isEmpty() && stack.getCount() > getInventoryStackLimit()) {
            stack.setCount(getInventoryStackLimit());
        }
        markDirtyAndSync();
    }

    @Override
    public int getInventoryStackLimit() { return 64; }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return world.getTileEntity(pos) == this
            && player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void openInventory(EntityPlayer player) {}

    @Override
    public void closeInventory(EntityPlayer player) {}

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) { return true; }

    @Override
    public int getField(int id) { return 0; }

    @Override
    public void setField(int id, int value) {}

    @Override
    public int getFieldCount() { return 0; }

    @Override
    public void clear() {
        slots.clear();
        markDirtyAndSync();
    }

    @Override
    public String getName() { return "sum.shop.title"; }

    @Override
    public boolean hasCustomName() { return false; }

    @Override
    public ITextComponent getDisplayName() {
        return new TextComponentTranslation(getName());
    }

    // --- NBT + sync ---

    private void markDirtyAndSync() {
        markDirty();
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (ownerUuid != null) nbt.setUniqueId("owner", ownerUuid);
        nbt.setString("ownerName", ownerName);
        nbt.setInteger("saleAmount", saleAmount);
        nbt.setDouble("saleCost", saleCost);
        nbt.setDouble("fundsAccumulated", fundsAccumulated);
        nbt.setBoolean("infiniteStock", infiniteStock);
        ItemStackHelper.saveAllItems(nbt, slots);
        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.ownerUuid = nbt.hasUniqueId("owner") ? nbt.getUniqueId("owner") : null;
        this.ownerName = nbt.getString("ownerName");
        this.saleAmount = clamp(nbt.getInteger("saleAmount"), MIN_SALE_AMOUNT, MAX_SALE_AMOUNT);
        this.saleCost = nbt.hasKey("saleCost")
            ? clampD(nbt.getDouble("saleCost"), MIN_SALE_COST, MAX_SALE_COST)
            : 1.0;
        this.fundsAccumulated = nbt.getDouble("fundsAccumulated");
        this.infiniteStock = nbt.getBoolean("infiniteStock");
        slots.clear();
        for (int i = 0; i < slots.size(); i++) slots.set(i, ItemStack.EMPTY);
        ItemStackHelper.loadAllItems(nbt, slots);
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    // --- Drop on break ---

    /** Stock + template + accumulated-funds bills, dumped to a list for the block to spawn
     *  as drops when the shop is broken. Funds become bills via {@link Bills#billItem}. */
    public NonNullList<ItemStack> collectDrops() {
        NonNullList<ItemStack> drops = NonNullList.create();
        for (ItemStack stack : slots) {
            if (!stack.isEmpty()) drops.add(stack.copy());
        }
        // Dispense remaining funds as bills, largest denominations first. All eight SUM
        // denominations always register, so billItem is non-null in practice; the guard is
        // defensive for the (never-observed) case a denomination's item is missing.
        for (int[] pair : breakIntoBills(fundsAccumulated)) {
            Item bill = Bills.billItem(pair[0]);
            if (bill != null) drops.add(new ItemStack(bill, pair[1]));
        }
        return drops;
    }

    /**
     * Greedy largest-first decomposition of a funds amount into whole bills, each stack capped at
     * 64. Pure ({@code [denom, count]} pairs) so the bill-making arithmetic is unit-testable
     * without the item registry; {@link #collectDrops} maps the pairs to bill items. Fractional
     * cents below $1 are dropped (floored).
     */
    static java.util.List<int[]> breakIntoBills(double funds) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        double remaining = funds;
        for (int denom : new int[]{500, 200, 100, 50, 20, 10, 5, 1}) {
            int count = (int) Math.floor(remaining / denom);
            while (count > 0) {
                int give = Math.min(count, 64);
                out.add(new int[]{denom, give});
                count -= give;
                remaining -= (double) give * denom;
            }
        }
        return out;
    }

    // --- utilities ---

    // Package-private (not private) so the pure clamp bounds are unit-testable.
    static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static double clampD(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
