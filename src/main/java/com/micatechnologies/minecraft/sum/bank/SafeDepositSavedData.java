package com.micatechnologies.minecraft.sum.bank;

import com.micatechnologies.minecraft.sum.SumConstants;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/**
 * Per-dimension persistent state for the safe deposit box block. Maps each
 * (player UUID, block position) pair to a 9-slot inventory; different players right-clicking
 * the same block see independent storage. The saved-data file is named
 * {@code sum_safe_deposit} and lives in {@code world/data/} (per-dimension).
 */
public class SafeDepositSavedData extends WorldSavedData {

    public static final String DATA_NAME = SumConstants.MOD_NAMESPACE + "_safe_deposit";
    public static final int SLOT_COUNT = 9;

    private final Map<UUID, Map<BlockPos, NonNullList<ItemStack>>> inventories = new HashMap<>();

    public SafeDepositSavedData() {
        super(DATA_NAME);
    }

    public SafeDepositSavedData(String name) {
        super(name);
    }

    public static SafeDepositSavedData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        SafeDepositSavedData data = (SafeDepositSavedData) storage.getOrLoadData(SafeDepositSavedData.class, DATA_NAME);
        if (data == null) {
            data = new SafeDepositSavedData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    /** Returns the 9-slot list for {@code (owner, pos)}, creating an empty list on first access. */
    public NonNullList<ItemStack> getInventory(UUID owner, BlockPos pos) {
        return inventories
            .computeIfAbsent(owner, k -> new HashMap<>())
            .computeIfAbsent(pos.toImmutable(), k -> NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY));
    }

    /**
     * Sum of currency-bill values across every safe-deposit box owned by {@code owner} in
     * this dimension. Item stacks that aren't bills (per {@link com.micatechnologies.minecraft.sum.atm.Bills})
     * are ignored — boxes also hold arbitrary items, but only bills contribute to the
     * "bank balance" readout surfaced by the player-status snapshot.
     */
    public long totalBillValueFor(UUID owner) {
        Map<BlockPos, NonNullList<ItemStack>> boxes = inventories.get(owner);
        if (boxes == null || boxes.isEmpty()) return 0L;
        long total = 0L;
        for (NonNullList<ItemStack> box : boxes.values()) {
            for (ItemStack stack : box) {
                if (stack.isEmpty()) continue;
                int denom = com.micatechnologies.minecraft.sum.atm.Bills.denominationOf(stack.getItem());
                if (denom > 0) {
                    total += (long) denom * stack.getCount();
                }
            }
        }
        return total;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList playerList = new NBTTagList();
        for (Map.Entry<UUID, Map<BlockPos, NonNullList<ItemStack>>> playerEntry : inventories.entrySet()) {
            // Skip entries whose every box is fully empty - keeps the file from accruing
            // garbage when players take everything out and never come back.
            boolean anyContent = false;
            for (NonNullList<ItemStack> list : playerEntry.getValue().values()) {
                for (ItemStack stack : list) {
                    if (!stack.isEmpty()) { anyContent = true; break; }
                }
                if (anyContent) break;
            }
            if (!anyContent) {
                continue;
            }
            NBTTagCompound playerTag = new NBTTagCompound();
            playerTag.setUniqueId("uuid", playerEntry.getKey());
            NBTTagList boxList = new NBTTagList();
            for (Map.Entry<BlockPos, NonNullList<ItemStack>> boxEntry : playerEntry.getValue().entrySet()) {
                NonNullList<ItemStack> slots = boxEntry.getValue();
                NBTTagList slotList = new NBTTagList();
                for (int i = 0; i < slots.size(); i++) {
                    ItemStack stack = slots.get(i);
                    if (!stack.isEmpty()) {
                        NBTTagCompound slotTag = new NBTTagCompound();
                        slotTag.setByte("slot", (byte) i);
                        stack.writeToNBT(slotTag);
                        slotList.appendTag(slotTag);
                    }
                }
                if (slotList.tagCount() == 0) {
                    continue;
                }
                NBTTagCompound boxTag = new NBTTagCompound();
                boxTag.setLong("pos", boxEntry.getKey().toLong());
                boxTag.setTag("slots", slotList);
                boxList.appendTag(boxTag);
            }
            playerTag.setTag("boxes", boxList);
            playerList.appendTag(playerTag);
        }
        nbt.setTag("players", playerList);
        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        inventories.clear();
        NBTTagList playerList = nbt.getTagList("players", 10);
        for (int i = 0; i < playerList.tagCount(); i++) {
            NBTTagCompound playerTag = playerList.getCompoundTagAt(i);
            UUID uuid = playerTag.getUniqueId("uuid");
            NBTTagList boxList = playerTag.getTagList("boxes", 10);
            Map<BlockPos, NonNullList<ItemStack>> boxes = new HashMap<>();
            for (int j = 0; j < boxList.tagCount(); j++) {
                NBTTagCompound boxTag = boxList.getCompoundTagAt(j);
                BlockPos pos = BlockPos.fromLong(boxTag.getLong("pos"));
                NonNullList<ItemStack> slots = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
                NBTTagList slotList = boxTag.getTagList("slots", 10);
                for (int k = 0; k < slotList.tagCount(); k++) {
                    NBTTagCompound slotTag = slotList.getCompoundTagAt(k);
                    int slot = slotTag.getByte("slot") & 0xFF;
                    if (slot < slots.size()) {
                        slots.set(slot, new ItemStack(slotTag));
                    }
                }
                boxes.put(pos, slots);
            }
            inventories.put(uuid, boxes);
        }
    }
}
