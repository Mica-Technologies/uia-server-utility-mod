package com.micatechnologies.minecraft.sum.favorites;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the in-memory state machine of the static {@link FavoritesStore}: add/remove/contains/
 * toggle/swap, ordered snapshots, and the monotonic {@code version} counter (which cached views
 * poll for invalidation). File I/O ({@code load}/{@code save}) needs a storage file and is out of
 * scope; the store starts each test cleared.
 */
class FavoritesStoreTest {

    private static FavoriteKey key(String name) {
        return new FavoriteKey(new ResourceLocation(name), 0);
    }

    @BeforeEach
    void reset() {
        FavoritesStore.clear();
    }

    @Test
    void addAndContains() {
        FavoriteKey k = key("minecraft:stone");
        assertTrue(FavoritesStore.add(k));
        assertEquals(1, FavoritesStore.size());
        assertTrue(FavoritesStore.contains(k));
    }

    @Test
    void duplicateAddIsRejected() {
        FavoriteKey k = key("minecraft:stone");
        assertTrue(FavoritesStore.add(k));
        assertFalse(FavoritesStore.add(k));
        assertEquals(1, FavoritesStore.size());
    }

    @Test
    void nullAddAndRemoveAreRejected() {
        assertFalse(FavoritesStore.add(null));
        assertFalse(FavoritesStore.remove(null));
        assertFalse(FavoritesStore.remove(key("minecraft:absent")));
    }

    @Test
    void removeClearsMembership() {
        FavoriteKey k = key("minecraft:stone");
        FavoritesStore.add(k);
        assertTrue(FavoritesStore.remove(k));
        assertEquals(0, FavoritesStore.size());
        assertFalse(FavoritesStore.contains(k));
    }

    @Test
    void toggleAddsThenRemoves() {
        FavoriteKey k = key("minecraft:stone");
        assertTrue(FavoritesStore.toggle(k), "absent → added → present");
        assertTrue(FavoritesStore.contains(k));
        assertFalse(FavoritesStore.toggle(k), "present → removed → absent");
        assertFalse(FavoritesStore.contains(k));
        assertFalse(FavoritesStore.toggle(null));
    }

    @Test
    void indexOfReflectsInsertionOrder() {
        FavoriteKey a = key("minecraft:stone");
        FavoriteKey b = key("minecraft:dirt");
        FavoritesStore.add(a);
        FavoritesStore.add(b);
        assertEquals(0, FavoritesStore.indexOf(a));
        assertEquals(1, FavoritesStore.indexOf(b));
        assertEquals(-1, FavoritesStore.indexOf(null));
        assertEquals(-1, FavoritesStore.indexOf(key("minecraft:absent")));
    }

    @Test
    void swapReordersAndValidatesBounds() {
        FavoriteKey a = key("minecraft:stone");
        FavoriteKey b = key("minecraft:dirt");
        FavoritesStore.add(a);
        FavoritesStore.add(b);

        assertTrue(FavoritesStore.swap(0, 1));
        assertEquals(1, FavoritesStore.indexOf(a));
        assertEquals(0, FavoritesStore.indexOf(b));

        assertFalse(FavoritesStore.swap(0, 0), "a == b is a no-op");
        assertFalse(FavoritesStore.swap(-1, 0));
        assertFalse(FavoritesStore.swap(0, 5));
    }

    @Test
    void snapshotsAreIndependentCopies() {
        FavoriteKey k = key("minecraft:stone");
        FavoritesStore.add(k);
        List<FavoriteKey> snap = FavoritesStore.snapshot();
        snap.clear();
        assertEquals(1, FavoritesStore.size(), "mutating a snapshot must not affect the store");
        assertTrue(FavoritesStore.snapshotSet().contains(k));
    }

    @Test
    void versionBumpsOnMutationButNotOnNoOp() {
        int v0 = FavoritesStore.getVersion();
        FavoriteKey k = key("minecraft:stone");
        FavoritesStore.add(k);
        int v1 = FavoritesStore.getVersion();
        assertTrue(v1 > v0, "add bumps version");

        FavoritesStore.add(k); // duplicate → no-op
        assertEquals(v1, FavoritesStore.getVersion(), "rejected add must not bump version");

        FavoritesStore.remove(k);
        assertTrue(FavoritesStore.getVersion() > v1, "remove bumps version");
    }
}
