package com.micatechnologies.minecraft.sum.omceapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.sum.omceapi.OmceBalance;
import com.micatechnologies.minecraft.sum.omceapi.OmceProtocol;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The cache is what makes an asynchronous economy safe to read from the game thread. Its two
 * invariants — version monotonicity and separated optimistic deltas — are exactly what these
 * tests pin down.
 */
class OmceBalanceCacheTest {

    private static final UUID PLAYER = UUID.fromString("f84c6a79-0a4e-45e0-879b-cd49ebd4c4e2");

    private static OmceBalance balance(long amount, long version) {
        return new OmceBalance("acct_1", PLAYER, amount, amount, 0L, version,
            OmceProtocol.ACCOUNT_ACTIVE);
    }

    @Test
    @DisplayName("an authoritative balance is stored and read back")
    void storesAuthoritative() {
        OmceBalanceCache cache = new OmceBalanceCache();
        assertTrue(cache.applyAuthoritative(balance(10_000L, 5L)));

        OmceBalanceCache.Entry entry = cache.get(PLAYER);
        assertNotNull(entry);
        assertEquals(10_000L, entry.getAuthoritativeBalance());
        assertEquals(10_000L, entry.getEffectiveBalance());
        assertEquals(5L, entry.getVersion());
        assertEquals("acct_1", entry.getAccountId());
    }

    @Test
    @DisplayName("a stale response is discarded rather than rolling the balance backwards")
    void versionGuardRejectsStale() {
        OmceBalanceCache cache = new OmceBalanceCache();
        cache.applyAuthoritative(balance(10_000L, 5L));

        // A slow /getBalances sweep landing after a fast /processTransaction response.
        assertFalse(cache.applyAuthoritative(balance(99_999L, 4L)), "older version must be ignored");
        assertEquals(10_000L, cache.get(PLAYER).getAuthoritativeBalance());

        // Same version is also a duplicate, not an update.
        assertFalse(cache.applyAuthoritative(balance(99_999L, 5L)));
        assertEquals(10_000L, cache.get(PLAYER).getAuthoritativeBalance());

        assertTrue(cache.applyAuthoritative(balance(7_500L, 6L)), "newer version must apply");
        assertEquals(7_500L, cache.get(PLAYER).getAuthoritativeBalance());
    }

    @Test
    @DisplayName("an unversioned service falls back to last-write-wins")
    void unversionedServiceAlwaysApplies() {
        OmceBalanceCache cache = new OmceBalanceCache();
        assertTrue(cache.applyAuthoritative(balance(10_000L, 0L)));
        assertTrue(cache.applyAuthoritative(balance(9_000L, 0L)),
            "version 0 means the service does not version accounts");
        assertEquals(9_000L, cache.get(PLAYER).getAuthoritativeBalance());
    }

    @Test
    @DisplayName("an optimistic spend shows immediately but leaves the authoritative figure alone")
    void pendingDeltaIsSeparate() {
        OmceBalanceCache cache = new OmceBalanceCache();
        cache.applyAuthoritative(balance(10_000L, 1L));

        cache.addPending(PLAYER, -2_500L);
        OmceBalanceCache.Entry entry = cache.get(PLAYER);
        assertEquals(7_500L, entry.getEffectiveBalance(), "the player sees the spend at once");
        assertEquals(10_000L, entry.getAuthoritativeBalance(), "but it isn't confirmed yet");
        assertTrue(entry.hasPending());
    }

    @Test
    @DisplayName("confirming a spend converges on the authoritative figure without double-counting")
    void settleAfterConfirmation() {
        OmceBalanceCache cache = new OmceBalanceCache();
        cache.applyAuthoritative(balance(10_000L, 1L));
        cache.addPending(PLAYER, -2_500L);

        // The service confirms; its post-transaction balance already reflects the spend.
        cache.applyAuthoritative(balance(7_500L, 2L));
        cache.settlePending(PLAYER, -2_500L);

        OmceBalanceCache.Entry entry = cache.get(PLAYER);
        assertEquals(7_500L, entry.getEffectiveBalance(), "not 5,000 — the delta must not apply twice");
        assertFalse(entry.hasPending());
    }

    @Test
    @DisplayName("rejecting a spend restores the balance exactly")
    void settleAfterRejection() {
        OmceBalanceCache cache = new OmceBalanceCache();
        cache.applyAuthoritative(balance(10_000L, 1L));
        cache.addPending(PLAYER, -2_500L);

        cache.settlePending(PLAYER, -2_500L);

        assertEquals(10_000L, cache.get(PLAYER).getEffectiveBalance());
        assertFalse(cache.get(PLAYER).hasPending());
    }

    @Test
    @DisplayName("concurrent spends settle independently")
    void overlappingPendingDeltas() {
        OmceBalanceCache cache = new OmceBalanceCache();
        cache.applyAuthoritative(balance(10_000L, 1L));

        cache.addPending(PLAYER, -2_000L);
        cache.addPending(PLAYER, -3_000L);
        assertEquals(5_000L, cache.get(PLAYER).getEffectiveBalance());

        // The second one resolves first — settling must subtract only its own amount, not reset.
        cache.settlePending(PLAYER, -3_000L);
        assertEquals(8_000L, cache.get(PLAYER).getEffectiveBalance());
        assertTrue(cache.get(PLAYER).hasPending(), "the first spend is still in flight");

        cache.settlePending(PLAYER, -2_000L);
        assertEquals(10_000L, cache.get(PLAYER).getEffectiveBalance());
        assertFalse(cache.get(PLAYER).hasPending());
    }

    @Test
    @DisplayName("an authoritative update arriving mid-flight preserves the pending delta")
    void authoritativeUpdateKeepsPending() {
        OmceBalanceCache cache = new OmceBalanceCache();
        cache.applyAuthoritative(balance(10_000L, 1L));
        cache.addPending(PLAYER, -2_500L);

        // An unrelated credit lands from the change feed while our spend is still in flight.
        cache.applyAuthoritative(balance(12_000L, 2L));

        OmceBalanceCache.Entry entry = cache.get(PLAYER);
        assertEquals(12_000L, entry.getAuthoritativeBalance());
        assertEquals(9_500L, entry.getEffectiveBalance(), "the in-flight spend must survive");
        assertEquals(-2_500L, entry.getPendingDelta());
    }

    @Test
    @DisplayName("account ids can be recorded before any balance is known")
    void accountIdBeforeBalance() {
        OmceBalanceCache cache = new OmceBalanceCache();
        cache.putAccountId(PLAYER, "acct_42", OmceProtocol.ACCOUNT_ACTIVE);

        assertEquals("acct_42", cache.get(PLAYER).getAccountId());

        cache.applyAuthoritative(balance(500L, 1L));
        assertEquals("acct_1", cache.get(PLAYER).getAccountId(), "a balance response may correct it");
        assertEquals(500L, cache.get(PLAYER).getAuthoritativeBalance());
    }

    @Test
    @DisplayName("staleness is driven by the injected clock")
    void staleDetection() {
        long[] now = { 1_000L };
        OmceBalanceCache cache = new OmceBalanceCache(() -> now[0]);
        cache.applyAuthoritative(balance(10_000L, 1L));

        assertTrue(cache.findStale(Collections.singletonList(PLAYER), 5_000L).isEmpty());

        now[0] = 6_500L;
        List<UUID> stale = cache.findStale(Collections.singletonList(PLAYER), 5_000L);
        assertEquals(1, stale.size());
        assertEquals(PLAYER, stale.get(0));
    }

    @Test
    @DisplayName("an uncached player counts as stale so a sweep picks them up")
    void unknownPlayerIsStale() {
        OmceBalanceCache cache = new OmceBalanceCache();
        UUID other = UUID.randomUUID();
        assertEquals(Arrays.asList(other), cache.findStale(Collections.singletonList(other), 1_000L));
    }
}
