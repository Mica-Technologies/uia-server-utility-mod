package com.micatechnologies.minecraft.sum.economy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.sum.economy.PersonalItemsSavedData.Kind;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.jupiter.api.Test;

/**
 * Tests the pure per-player binding model of {@link PersonalItemsSavedData}: idempotent
 * {@code recordBinding}, membership queries, and the per-UUID {@code int} bitmask NBT round-trip.
 * The {@code get(World)} accessor is MC-coupled and out of scope. Mirrors the existing plots
 * {@code WorldSavedData} tests (no-arg ctor, no Minecraft bootstrap).
 */
class PersonalItemsSavedDataTest {

    @Test
    void recordBindingIsIdempotent() {
        PersonalItemsSavedData data = new PersonalItemsSavedData();
        UUID u = UUID.randomUUID();
        assertTrue(data.recordBinding(u, Kind.PHONE), "first bind is new");
        assertFalse(data.recordBinding(u, Kind.PHONE), "re-binding same kind is a no-op");
        assertTrue(data.hasBinding(u, Kind.PHONE));
        assertFalse(data.hasBinding(u, Kind.DEBIT_CARD), "unrelated kind not bound");
    }

    @Test
    void hasBindingFalseForUnknownPlayer() {
        PersonalItemsSavedData data = new PersonalItemsSavedData();
        assertFalse(data.hasBinding(UUID.randomUUID(), Kind.PHONE));
    }

    @Test
    void nbtRoundTripPreservesMultipleUuidsAndKinds() {
        PersonalItemsSavedData data = new PersonalItemsSavedData();
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        data.recordBinding(u1, Kind.PHONE);
        data.recordBinding(u1, Kind.DEBIT_CARD);
        data.recordBinding(u2, Kind.DEBIT_CARD);

        NBTTagCompound nbt = data.writeToNBT(new NBTTagCompound());
        PersonalItemsSavedData restored = new PersonalItemsSavedData();
        restored.readFromNBT(nbt);

        assertTrue(restored.hasBinding(u1, Kind.PHONE));
        assertTrue(restored.hasBinding(u1, Kind.DEBIT_CARD));
        assertFalse(restored.hasBinding(u2, Kind.PHONE));
        assertTrue(restored.hasBinding(u2, Kind.DEBIT_CARD));
    }

    @Test
    void emptyRoundTripHasNoBindings() {
        PersonalItemsSavedData data = new PersonalItemsSavedData();
        NBTTagCompound nbt = data.writeToNBT(new NBTTagCompound());
        PersonalItemsSavedData restored = new PersonalItemsSavedData();
        restored.readFromNBT(nbt);
        assertFalse(restored.hasBinding(UUID.randomUUID(), Kind.PHONE));
    }

    @Test
    void readingMissingEntriesKeyDoesNotThrow() {
        PersonalItemsSavedData data = new PersonalItemsSavedData();
        data.readFromNBT(new NBTTagCompound());
        assertFalse(data.hasBinding(UUID.randomUUID(), Kind.PHONE));
    }

    @Test
    void zeroMaskEntryIsDroppedOnRead() {
        UUID u = UUID.randomUUID();
        PersonalItemsSavedData data = new PersonalItemsSavedData();
        data.readFromNBT(entryNbt(u, 0));
        assertFalse(data.hasBinding(u, Kind.PHONE), "empty bitmask → no binding retained");
        assertFalse(data.hasBinding(u, Kind.DEBIT_CARD));
    }

    @Test
    void strayHighBitsAreIgnoredOnRead() {
        // bit0 = PHONE, bit1 = DEBIT_CARD; bit2 has no matching ordinal and must be ignored.
        UUID u = UUID.randomUUID();
        PersonalItemsSavedData data = new PersonalItemsSavedData();
        data.readFromNBT(entryNbt(u, 0b101));
        assertTrue(data.hasBinding(u, Kind.PHONE));
        assertFalse(data.hasBinding(u, Kind.DEBIT_CARD));
    }

    /** Builds the {@code Entries[{UUID, Kinds}]} NBT shape the class reads, for edge-case masks. */
    private static NBTTagCompound entryNbt(UUID uuid, int mask) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setUniqueId("UUID", uuid);
        entry.setInteger("Kinds", mask);
        NBTTagList entries = new NBTTagList();
        entries.appendTag(entry);
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("Entries", entries);
        return root;
    }
}
