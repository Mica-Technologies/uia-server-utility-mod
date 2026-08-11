package com.micatechnologies.minecraft.sum.economy.apiimpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.sum.api.EscrowTicket;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which held stakes get refunded when their owning mod disappears.
 *
 * <p>The rule has to be right in both directions. Too eager, and an operator who pulls a mod out
 * for a single restart comes back to find every in-flight wager refunded out from under it. Too
 * reluctant, and a removed mod's players never see their stakes again — the money left their
 * wallets, and nothing else in the game knows those tickets exist.
 */
class EscrowOrphanSweepTest {

    private static final long NOW = 1_700_000_000_000L;

    private static EscrowTicket ticket(String modId, long openedAt) {
        return new EscrowTicket(UUID.randomUUID(), UUID.randomUUID(), 10.0, modId, "stake",
            openedAt);
    }

    private static java.util.Set<String> active(String... modIds) {
        return new HashSet<>(Arrays.asList(modIds));
    }

    @Test
    @DisplayName("a hold whose mod is gone is orphaned once the grace period has passed")
    void missingModIsOrphaned() {
        List<EscrowTicket> orphans = EscrowService.selectOrphans(
            Collections.singletonList(ticket("gonemod", NOW - 5_000L)), active(), NOW, NOW - 1L);
        assertEquals(1, orphans.size());
    }

    @Test
    @DisplayName("nothing is refunded while the grace period is still running")
    void gracePeriodBlocksTheSweep() {
        // The case this protects: a mod pulled out for one restart. Refunding everyone's wagers
        // the instant the server comes up would be worse than waiting for the operator to notice.
        List<EscrowTicket> orphans = EscrowService.selectOrphans(
            Collections.singletonList(ticket("gonemod", NOW - 5_000L)), active(), NOW, NOW + 1L);
        assertTrue(orphans.isEmpty());
    }

    @Test
    @DisplayName("the sweep fires exactly at the moment it comes due")
    void sweepFiresOnTheBoundary() {
        List<EscrowTicket> orphans = EscrowService.selectOrphans(
            Collections.singletonList(ticket("gonemod", NOW - 5_000L)), active(), NOW, NOW);
        assertEquals(1, orphans.size(), "now >= due, so it is due");
    }

    @Test
    @DisplayName("a hold whose mod is still loaded and authorized is left alone")
    void activeModIsNotOrphaned() {
        List<EscrowTicket> orphans = EscrowService.selectOrphans(
            Collections.singletonList(ticket("mycasino", NOW - 5_000L)), active("mycasino"), NOW,
            NOW - 1L);
        assertTrue(orphans.isEmpty(), "an in-flight wager must not be refunded under a live mod");
    }

    @Test
    @DisplayName("only the missing mod's holds are swept, not everyone's")
    void sweepIsSelective() {
        List<EscrowTicket> orphans = EscrowService.selectOrphans(
            Arrays.asList(ticket("mycasino", NOW - 1_000L), ticket("gonemod", NOW - 2_000L),
                ticket("mycasino", NOW - 3_000L)),
            active("mycasino"), NOW, NOW - 1L);
        assertEquals(1, orphans.size());
        assertEquals("gonemod", orphans.get(0).getOwningModId());
    }

    @Test
    @DisplayName("orphans come out oldest first, so a partial sweep makes predictable progress")
    void orphansAreOldestFirst() {
        List<EscrowTicket> orphans = EscrowService.selectOrphans(
            Arrays.asList(ticket("gonemod", NOW - 1_000L), ticket("gonemod", NOW - 9_000L),
                ticket("gonemod", NOW - 5_000L)),
            active(), NOW, NOW - 1L);
        assertEquals(NOW - 9_000L, orphans.get(0).getOpenedAtMillis());
        assertEquals(NOW - 5_000L, orphans.get(1).getOpenedAtMillis());
        assertEquals(NOW - 1_000L, orphans.get(2).getOpenedAtMillis());
    }

    @Test
    @DisplayName("an empty or null set of holds sweeps nothing")
    void nothingHeldSweepsNothing() {
        assertTrue(EscrowService.selectOrphans(Collections.emptyList(), active(), NOW, NOW - 1L)
            .isEmpty());
        assertTrue(EscrowService.selectOrphans(null, active(), NOW, NOW - 1L).isEmpty());
    }

    @Test
    @DisplayName("a null ticket among the holds is skipped rather than crashing the sweep")
    void nullTicketIsSkipped() {
        // A sweep that throws would leave every other player's refund unprocessed.
        List<EscrowTicket> orphans = EscrowService.selectOrphans(
            Arrays.asList(ticket("gonemod", NOW - 1_000L), null), active(), NOW, NOW - 1L);
        assertEquals(1, orphans.size());
    }
}
