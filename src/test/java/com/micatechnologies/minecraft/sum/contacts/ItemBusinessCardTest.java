package com.micatechnologies.minecraft.sum.contacts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ItemBusinessCard#isPersonalizedTag} — the pure NBT check extracted from
 * {@code isPersonalized(ItemStack)}. A card counts as personalized only once both halves of its
 * owner UUID are present. The full ItemStack path needs the item registry and is out of scope.
 */
class ItemBusinessCardTest {

    @Test
    void nullTagIsNotPersonalized() {
        assertFalse(ItemBusinessCard.isPersonalizedTag(null));
    }

    @Test
    void emptyTagIsNotPersonalized() {
        assertFalse(ItemBusinessCard.isPersonalizedTag(new NBTTagCompound()));
    }

    @Test
    void halfWrittenUuidIsNotPersonalized() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("OwnerUUIDMost", 1L); // only the Most half; Least missing
        assertFalse(ItemBusinessCard.isPersonalizedTag(tag));
    }

    @Test
    void bothUuidHalvesArePersonalized() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setUniqueId("OwnerUUID", UUID.randomUUID()); // writes OwnerUUIDMost + OwnerUUIDLeast
        assertTrue(ItemBusinessCard.isPersonalizedTag(tag));
    }
}
