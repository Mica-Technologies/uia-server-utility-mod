package com.micatechnologies.minecraft.sum.economy;

import java.util.EnumSet;
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
 * Per-player roster of bound personal items (phone, debit card). Used by
 * {@link ItemAccountAccess} to auto-rebind a fresh stack to the player who already owns one
 * elsewhere — so a player who loses their phone (replaced it in the hotbar, dropped on death
 * if rules apply, etc.) can spawn a new one via creative / {@code /give} / their favorites
 * tab and have it immediately resolve back to their account instead of stranding their
 * pairing on the lost stack.
 *
 * <p>One {@link WorldSavedData} instance lives on the overworld's {@link MapStorage}, so the
 * data is server-wide and persists across restarts.
 */
public class PersonalItemsSavedData extends WorldSavedData {

    private static final String NAME = "sum_personal_items";
    private static final String KEY_ENTRIES = "Entries";
    private static final String KEY_UUID = "UUID";
    private static final String KEY_KINDS = "Kinds";

    public enum Kind {
        PHONE,
        DEBIT_CARD
    }

    private final Map<UUID, EnumSet<Kind>> bindings = new HashMap<>();

    public PersonalItemsSavedData() {
        super(NAME);
    }

    public PersonalItemsSavedData(String name) {
        super(name);
    }

    public static PersonalItemsSavedData get(World world) {
        MapStorage storage = world.getMapStorage();
        if (storage == null) {
            // Defensive fallback — should never happen during normal gameplay; a transient
            // instance lets callers proceed without NPE but the data won't persist.
            return new PersonalItemsSavedData();
        }
        PersonalItemsSavedData data =
            (PersonalItemsSavedData) storage.getOrLoadData(PersonalItemsSavedData.class, NAME);
        if (data == null) {
            data = new PersonalItemsSavedData();
            storage.setData(NAME, data);
        }
        return data;
    }

    /**
     * Records that {@code playerUuid} has bound a personal item of {@code kind}. Idempotent.
     * Returns true if this was a new binding (the player had no prior entry for this kind).
     */
    public boolean recordBinding(UUID playerUuid, Kind kind) {
        EnumSet<Kind> kinds = bindings.computeIfAbsent(playerUuid, k -> EnumSet.noneOf(Kind.class));
        boolean added = kinds.add(kind);
        if (added) {
            markDirty();
        }
        return added;
    }

    /** True if the given player has previously bound an item of the given kind. */
    public boolean hasBinding(UUID playerUuid, Kind kind) {
        EnumSet<Kind> kinds = bindings.get(playerUuid);
        return kinds != null && kinds.contains(kind);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        bindings.clear();
        if (!nbt.hasKey(KEY_ENTRIES)) {
            return;
        }
        NBTTagList entries = nbt.getTagList(KEY_ENTRIES, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            if (!entry.hasUniqueId(KEY_UUID)) {
                continue;
            }
            UUID uuid = entry.getUniqueId(KEY_UUID);
            EnumSet<Kind> kinds = EnumSet.noneOf(Kind.class);
            // Stored as a bitmask of ordinals — small enum, no need for a string list.
            int mask = entry.getInteger(KEY_KINDS);
            for (Kind k : Kind.values()) {
                if ((mask & (1 << k.ordinal())) != 0) {
                    kinds.add(k);
                }
            }
            if (!kinds.isEmpty()) {
                bindings.put(uuid, kinds);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList entries = new NBTTagList();
        for (Map.Entry<UUID, EnumSet<Kind>> e : bindings.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setUniqueId(KEY_UUID, e.getKey());
            int mask = 0;
            for (Kind k : e.getValue()) {
                mask |= (1 << k.ordinal());
            }
            entry.setInteger(KEY_KINDS, mask);
            entries.appendTag(entry);
        }
        nbt.setTag(KEY_ENTRIES, entries);
        return nbt;
    }
}
