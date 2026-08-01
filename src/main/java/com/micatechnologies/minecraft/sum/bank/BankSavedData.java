package com.micatechnologies.minecraft.sum.bank;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

/**
 * Local bank balances, one per player, persisted in the world save.
 *
 * <p>This is the fallback backend for {@link BankService}: it is what a server's bank runs on when
 * no remote economy service is configured. Without it, ATMs and the debit card would be dead on
 * every server not using the Open MCEconomic API, which is most of them.
 *
 * <p>Stored per-world rather than per-player-NBT so an administrator can inspect and repair every
 * account in one file, and so a balance survives a player entity being reset.
 */
public class BankSavedData extends WorldSavedData {

    private static final String NAME = "sum_bank";
    private static final String NBT_ACCOUNTS = "accounts";
    private static final String NBT_UUID = "uuid";
    private static final String NBT_BALANCE = "balance";

    private final Map<UUID, Double> balances = new HashMap<>();

    public BankSavedData() {
        super(NAME);
    }

    public BankSavedData(String name) {
        super(name);
    }

    /** Loads or creates the store for a world. Call with the overworld so one bank serves all
     *  dimensions rather than each keeping its own accounts. */
    public static BankSavedData get(World world) {
        MapStorage storage = world.getMapStorage();
        if (storage == null) {
            return new BankSavedData();
        }
        BankSavedData data = (BankSavedData) storage.getOrLoadData(BankSavedData.class, NAME);
        if (data == null) {
            data = new BankSavedData();
            storage.setData(NAME, data);
        }
        return data;
    }

    /** @return the player's bank balance, or 0 if they have never banked. */
    public double getBalance(UUID player) {
        Double value = balances.get(player);
        return value == null ? 0.0 : value;
    }

    /**
     * Adds {@code delta} to a balance, refusing to overdraw.
     *
     * @return false if the result would be negative, in which case nothing changes.
     */
    public boolean adjust(UUID player, double delta) {
        double next = getBalance(player) + delta;
        if (next < 0.0) {
            return false;
        }
        setBalance(player, next);
        return true;
    }

    /** Sets a balance outright. Negative values are clamped to zero. */
    public void setBalance(UUID player, double balance) {
        balances.put(player, Math.max(0.0, balance));
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        balances.clear();
        NBTTagList list = nbt.getTagList(NBT_ACCOUNTS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (!entry.hasUniqueId(NBT_UUID)) {
                continue;
            }
            balances.put(entry.getUniqueId(NBT_UUID), entry.getDouble(NBT_BALANCE));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setUniqueId(NBT_UUID, entry.getKey());
            tag.setDouble(NBT_BALANCE, entry.getValue());
            list.appendTag(tag);
        }
        nbt.setTag(NBT_ACCOUNTS, list);
        return nbt;
    }
}
