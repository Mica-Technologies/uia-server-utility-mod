package com.micatechnologies.minecraft.sum.favorites;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link FavoriteKey}: the {@code name#meta} parse/format round-trip and value-equality.
 * {@code of(ItemStack)}/{@code resolveStack()} need the item registry and are out of scope, but
 * the parsing and equality contract (used to persist favorites to JSON) is pure.
 */
class FavoriteKeyTest {

    private static FavoriteKey key(String name, int meta) {
        return new FavoriteKey(new ResourceLocation(name), meta);
    }

    @Test
    void parsesBareName() {
        FavoriteKey k = FavoriteKey.parse("minecraft:stone");
        assertEquals(new ResourceLocation("minecraft:stone"), k.getRegistryName());
        assertEquals(0, k.getMeta());
    }

    @Test
    void parsesNameWithMeta() {
        FavoriteKey k = FavoriteKey.parse("minecraft:wool#14");
        assertEquals(new ResourceLocation("minecraft:wool"), k.getRegistryName());
        assertEquals(14, k.getMeta());
    }

    @Test
    void parseDefaultsDomainToMinecraft() {
        // ResourceLocation("stone") → minecraft:stone.
        assertEquals(new ResourceLocation("minecraft:stone"), FavoriteKey.parse("stone").getRegistryName());
    }

    @Test
    void parseRejectsNullEmptyAndBadMeta() {
        assertNull(FavoriteKey.parse(null));
        assertNull(FavoriteKey.parse(""));
        assertNull(FavoriteKey.parse("minecraft:wool#abc"), "non-numeric meta → null");
    }

    @Test
    void toStringIsNameHashMeta() {
        assertEquals("minecraft:wool#14", key("minecraft:wool", 14).toString());
    }

    @Test
    void parseAndToStringRoundTrip() {
        FavoriteKey k = key("minecraft:wool", 14);
        assertEquals(k, FavoriteKey.parse(k.toString()));
    }

    @Test
    void equalityDependsOnNameAndMeta() {
        assertEquals(key("minecraft:wool", 14), key("minecraft:wool", 14));
        assertEquals(key("minecraft:wool", 14).hashCode(), key("minecraft:wool", 14).hashCode());
        assertNotEquals(key("minecraft:wool", 14), key("minecraft:wool", 0));
        assertNotEquals(key("minecraft:wool", 14), key("minecraft:stone", 14));
    }

    @Test
    void notEqualToNullOrOtherTypes() {
        FavoriteKey k = key("minecraft:wool", 0);
        assertNotEquals(k, null);
        assertNotEquals(k, "minecraft:wool#0");
    }

    @Test
    void constructorRejectsNullName() {
        assertThrows(IllegalArgumentException.class, () -> new FavoriteKey(null, 0));
    }
}
