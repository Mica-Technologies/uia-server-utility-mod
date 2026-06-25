package com.micatechnologies.minecraft.sum.phone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests the pure chunk→exchange hash for desk phones
 * ({@link TileEntityDeskPhone#deriveExchangeForChunk}). Covers TESTING_PLAN.md §4.10
 * (area codes / chunk-derived numbers, commit {@code 2fb3682}) — determinism and range.
 */
class DeskPhoneExchangeTest {

    @Test
    void exchangeIsInValidRange() {
        for (int x = -50; x <= 50; x++) {
            for (int z = -50; z <= 50; z++) {
                int ex = TileEntityDeskPhone.deriveExchangeForChunk(x, z);
                assertTrue(ex >= 0 && ex < 1000,
                    "exchange out of [0,1000): " + ex + " at chunk (" + x + "," + z + ")");
            }
        }
    }

    @Test
    void exchangeIsDeterministic() {
        // "Same chunk → same exchange forever" is a documented stability guarantee.
        assertEquals(
            TileEntityDeskPhone.deriveExchangeForChunk(123, -456),
            TileEntityDeskPhone.deriveExchangeForChunk(123, -456));
        assertEquals(
            TileEntityDeskPhone.deriveExchangeForChunk(0, 0),
            TileEntityDeskPhone.deriveExchangeForChunk(0, 0));
    }

    @Test
    void negativeCoordinatesNeverProduceNegativeExchange() {
        // Math.floorMod guards against the negative-seed case.
        assertTrue(TileEntityDeskPhone.deriveExchangeForChunk(-1, -1) >= 0);
        assertTrue(TileEntityDeskPhone.deriveExchangeForChunk(Integer.MIN_VALUE, Integer.MIN_VALUE) >= 0);
        assertTrue(TileEntityDeskPhone.deriveExchangeForChunk(Integer.MAX_VALUE, Integer.MIN_VALUE) >= 0);
    }

    @Test
    void neighboringChunksDoNotAllCollide() {
        // The mixing function should spread adjacent chunks across many exchanges, not pile
        // them onto one. Collect exchanges over a 20x20 block of chunks and require variety.
        Set<Integer> seen = new HashSet<>();
        for (int x = 0; x < 20; x++) {
            for (int z = 0; z < 20; z++) {
                seen.add(TileEntityDeskPhone.deriveExchangeForChunk(x, z));
            }
        }
        // 400 chunks; with good mixing we expect well over 100 distinct exchanges.
        assertTrue(seen.size() > 100, "expected good spread, got only " + seen.size() + " distinct exchanges");
    }
}
