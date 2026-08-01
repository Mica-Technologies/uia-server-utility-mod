package com.micatechnologies.minecraft.sum.shop;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.atm.Bills;
import com.micatechnologies.minecraft.sum.economy.EconomyBridge;
import com.micatechnologies.minecraft.sum.omceapi.OmceMoney;
import com.micatechnologies.minecraft.sum.omceapi.OmceParty;
import com.micatechnologies.minecraft.sum.omceapi.OmceProtocol;
import com.micatechnologies.minecraft.sum.omceapi.OmceTransactionRequest;
import com.micatechnologies.minecraft.sum.omceapi.service.OmceEconomyService;
import java.util.UUID;
import java.util.function.Consumer;
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
 * <p><b>Buy flow.</b> A non-owner buyer triggers {@link #attemptPurchase}, which validates
 * configured/has-stock/buyer-has-funds, charges the buyer, then decrements stock, increments
 * accumulated funds, and gifts a fresh stack copied from the template (size = saleAmount).
 *
 * <p>The result arrives via callback rather than a return value because payment timing depends on
 * the economy backend. A local backend settles inline and the callback fires immediately. A remote
 * one cannot: HTTP must not run on the server thread, so the charge is dispatched and the goods
 * are handed over only once the service confirms it. Delivering optimistically would let a refusal
 * arrive after the item was already in the buyer's inventory, where there is nothing reliable to
 * take back — they may have dropped, stashed, or consumed it — and the till would have been
 * credited from money that was never taken.
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

    // --- Economy ledger identity ---

    /**
     * This shop as a transaction counterparty.
     *
     * <p>The shop is a <i>virtual</i> party: a remote service records it on the ledger entry but
     * holds no balance for it, because the till ({@link #fundsAccumulated}) lives here in the tile
     * entity's NBT. Purchases debit the buyer against this party and the later payout credits the
     * owner from it, so money is conserved across the pair.
     *
     * <p>The id encodes world and position so it stays stable across restarts and is meaningful to
     * an operator reading the ledger.
     */
    private OmceParty shopParty() {
        String worldName = (world == null || world.provider == null)
            ? "world"
            : world.provider.getDimensionType().getName();
        String id = OmceParty.positionId("shop", worldName, pos.getX(), pos.getY(), pos.getZ());
        return OmceParty.shop(id, ownerName.isEmpty() ? null : ownerName + "'s shop", ownerUuid);
    }

    /** Short audit-log description of what was bought. */
    private String purchaseReason() {
        ItemStack template = getSaleTemplate();
        if (template.isEmpty()) {
            return "Shop purchase";
        }
        return "Bought " + saleAmount + "x " + template.getItem().getRegistryName();
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
        final double amount = fundsAccumulated;
        // The till is emptied below on the strength of an optimistic credit. If a remote economy
        // later refuses it the money would simply be gone, so put it back.
        if (!EconomyBridge.adjustBalance(player, amount,
            OmceProtocol.TX_SHOP_PAYOUT, shopParty(), "Withdrew shop takings",
            () -> restoreFunds(player, amount))) {
            return 0.0;
        }
        this.fundsAccumulated = 0.0;
        markDirtyAndSync();
        return amount;
    }

    /**
     * Returns takings to the till after a refused payout.
     *
     * <p>Adds rather than assigns, so sales that completed while the payout was in flight are not
     * discarded.
     */
    private void restoreFunds(EntityPlayer owner, double amount) {
        if (isInvalid()) {
            Sum.LOGGER.error("[shop] Payout of {} to {} was refused, but the shop no longer exists. "
                + "The money was not returned — reconcile manually.", amount, owner.getName());
            return;
        }
        this.fundsAccumulated += amount;
        markDirtyAndSync();
        owner.sendMessage(new TextComponentString(TextFormatting.RED
            + "The withdrawal was declined — the funds are back in the shop."));
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
    public void attemptPurchase(EntityPlayerMP buyer, Consumer<BuyResult> callback) {
        BuyResult precondition = checkPreconditions(buyer);
        if (precondition != null) {
            callback.accept(precondition);
            return;
        }

        if (saleCost <= 0.0) {
            // A free shop has nothing to settle. Skip the economy entirely rather than sending a
            // zero-amount transaction, which the protocol lets a service reject outright.
            callback.accept(grantPurchase(buyer));
            return;
        }

        OmceEconomyService remote = EconomyBridge.getRemoteService();
        if (remote != null) {
            // A delivered item cannot be taken back — the buyer may drop, stash, or consume it
            // long before we could react. So under a remote economy nothing is granted until the
            // service confirms the charge, even though that costs a round trip.
            purchaseViaRemote(remote, buyer, callback);
            return;
        }

        // Local backends settle synchronously and cannot fail after returning true, so the
        // original inline flow is still correct for them.
        if (!EconomyBridge.adjustBalance(buyer, -saleCost,
            OmceProtocol.TX_SHOP_PURCHASE, shopParty(), purchaseReason())) {
            callback.accept(BuyResult.INSUFFICIENT_FUNDS);
            return;
        }
        BuyResult result = grantPurchase(buyer);
        if (result != BuyResult.OK) {
            EconomyBridge.adjustBalance(buyer, saleCost,
                OmceProtocol.TX_SHOP_PURCHASE, shopParty(),
                "Refund: shop ran out of stock mid-purchase");
        }
        callback.accept(result);
    }

    /** @return a failing {@link BuyResult}, or null when the purchase may proceed. */
    @Nullable
    private BuyResult checkPreconditions(EntityPlayerMP buyer) {
        BuyResult shopState = checkShopState(buyer);
        if (shopState != null) {
            return shopState;
        }
        double balance = EconomyBridge.getBalance(buyer);
        if (Double.isNaN(balance) || balance < saleCost) {
            return BuyResult.INSUFFICIENT_FUNDS;
        }
        return null;
    }

    /**
     * The shop-side half of {@link #checkPreconditions}, without the affordability check.
     *
     * <p>Separate because the post-charge revalidation must not re-test the balance: the buyer has
     * just been debited, so someone who spent exactly what they had now reads as broke and would
     * have their completed purchase refunded out from under them.
     */
    @Nullable
    private BuyResult checkShopState(EntityPlayerMP buyer) {
        if (!isConfigured()) return BuyResult.UNCONFIGURED;
        if (isOwner(buyer)) return BuyResult.OWNER_CANT_BUY;
        if (!EconomyBridge.isAvailable()) return BuyResult.ECONOMY_UNAVAILABLE;
        if (!hasEnoughStock()) return BuyResult.OUT_OF_STOCK;
        return null;
    }

    /**
     * Charges the buyer through the remote economy and grants the goods only once the service
     * reports the transaction committed.
     *
     * <p>Preconditions are re-checked inside the callback: it runs on the server thread a few
     * hundred milliseconds later, by which time another buyer may have taken the last of the
     * stock or the owner may have reconfigured the shop. If that happens the charge is voided
     * rather than the goods being conjured.
     */
    private void purchaseViaRemote(OmceEconomyService remote, EntityPlayerMP buyer,
        Consumer<BuyResult> callback) {
        long amount = OmceMoney.priceToMinorUnits(saleCost, remote.getMinorUnitDigits());
        OmceTransactionRequest request = OmceTransactionRequest.builder(
                OmceProtocol.TX_SHOP_PURCHASE, amount,
                OmceParty.player(buyer.getUniqueID(), buyer.getName(), null),
                shopParty())
            .initiator(buyer.getUniqueID(), buyer.getName(), OmceTransactionRequest.ROLE_PLAYER)
            .reason(purchaseReason())
            .meta("shopOwner", ownerName)
            .build();

        UUID buyerUuid = buyer.getUniqueID();
        remote.processTransaction(request, result -> {
            if (result.isFailure() || !result.get().isCommitted()) {
                callback.accept(BuyResult.INSUFFICIENT_FUNDS);
                return;
            }
            // Re-resolve the buyer: this runs a round trip later, and the reference we captured is
            // stale if they relogged. Delivering into a discarded inventory would take their money
            // and give them nothing.
            EntityPlayerMP current = resolvePlayer(buyerUuid);
            if (current == null) {
                remote.refund(result.get().getTransaction(), buyer,
                    "Refund: buyer left before delivery");
                callback.accept(BuyResult.ECONOMY_UNAVAILABLE);
                return;
            }
            if (isInvalid() || checkShopState(current) != null) {
                // The shop changed under us while the charge was in flight. Give the money back;
                // no goods have left, so this is a clean reversal.
                remote.refund(result.get().getTransaction(), current,
                    "Refund: shop state changed before delivery");
                callback.accept(BuyResult.OUT_OF_STOCK);
                return;
            }
            BuyResult granted = grantPurchase(current);
            if (granted != BuyResult.OK) {
                remote.refund(result.get().getTransaction(), current,
                    "Refund: shop ran out of stock mid-purchase");
            }
            callback.accept(granted);
        });
    }

    /** @return the online player with this UUID, or null if they are no longer connected. */
    @Nullable
    private EntityPlayerMP resolvePlayer(UUID uuid) {
        if (world == null || world.getMinecraftServer() == null
            || world.getMinecraftServer().getPlayerList() == null) {
            return null;
        }
        return world.getMinecraftServer().getPlayerList().getPlayerByUUID(uuid);
    }

    /**
     * Decrements stock, credits the till, and hands over the goods. Called only once payment is
     * settled, so a non-OK return here means the caller must refund.
     */
    private BuyResult grantPurchase(EntityPlayerMP buyer) {
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
     * Greedy largest-first decomposition of a funds amount into whole bills. Delegates to
     * {@link com.micatechnologies.minecraft.sum.atm.Bills#breakIntoBills}, which is the single
     * home for this arithmetic now that the ATM needs it too; retained here so the existing
     * tests and {@link #collectDrops} keep their local entry point.
     */
    static java.util.List<int[]> breakIntoBills(double funds) {
        return com.micatechnologies.minecraft.sum.atm.Bills.breakIntoBills(funds);
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
