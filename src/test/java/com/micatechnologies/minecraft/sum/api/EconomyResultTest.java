package com.micatechnologies.minecraft.sum.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The guarantees {@link EconomyResult} and {@link EscrowResult} make to integrating mods.
 *
 * <p>Two of them matter more than the rest. A failed result must always carry a reason and a
 * message, because a consumer showing {@code null} to a player is the failure mode this type
 * exists to prevent. And {@link Double#NaN} — which SUM's internals use for "unknown balance" —
 * must never escape into the public surface, because {@code NaN >= amount} is silently false in
 * every comparison a consumer would naturally write.
 */
class EconomyResultTest {

    @Test
    @DisplayName("a success carries no failure and no message")
    void successIsClean() {
        EconomyResult result = EconomyResult.ok();
        assertTrue(result.isOk());
        assertNull(result.getFailure());
        assertNull(result.getMessage());
        assertFalse(result.getBalance().isPresent(), "no balance was reported");
        assertFalse(result.isCallerError());
    }

    @Test
    @DisplayName("a success can carry the resulting balance")
    void successCarriesBalance() {
        EconomyResult result = EconomyResult.ok(42.5);
        assertTrue(result.getBalance().isPresent());
        assertEquals(42.5, result.getBalance().getAsDouble(), 0.0);
    }

    @Test
    @DisplayName("a NaN balance is reported as unknown rather than leaking NaN to consumers")
    void nanBalanceBecomesEmpty() {
        EconomyResult result = EconomyResult.ok(Double.NaN);
        assertTrue(result.isOk(), "an unknown balance is not a failure");
        assertFalse(result.getBalance().isPresent(),
                "NaN >= amount is silently false; consumers must never see it");
    }

    @Test
    @DisplayName("a zero balance is still a known balance")
    void zeroBalanceIsPresent() {
        assertTrue(EconomyResult.ok(0.0).getBalance().isPresent(),
                "broke is not the same as unknown");
    }

    @Test
    @DisplayName("a failure always has a reason and a non-null player-facing message")
    void failureAlwaysHasMessage() {
        for (EconomyFailure failure : EconomyFailure.values()) {
            EconomyResult result = EconomyResult.fail(failure);
            assertFalse(result.isOk(), failure.name());
            assertEquals(failure, result.getFailure(), failure.name());
            assertNotNull(result.getMessage(), failure.name() + " must be showable to a player");
            assertFalse(result.getMessage().isEmpty(), failure.name());
            assertFalse(result.getBalance().isPresent(),
                    failure.name() + " moved nothing, so it reports no balance");
        }
    }

    @Test
    @DisplayName("a failure falls back to the reason's default message when given none")
    void failureFallsBackToDefaultMessage() {
        assertEquals(EconomyFailure.INSUFFICIENT_FUNDS.getDefaultMessage(),
                EconomyResult.fail(EconomyFailure.INSUFFICIENT_FUNDS, null).getMessage());
        assertEquals("You need $5 more.",
                EconomyResult.fail(EconomyFailure.INSUFFICIENT_FUNDS, "You need $5 more.")
                        .getMessage());
    }

    @Test
    @DisplayName("a failure without a reason is rejected outright")
    void failureNeedsAReason() {
        assertThrows(IllegalArgumentException.class, () -> EconomyResult.fail(null));
    }

    @Test
    @DisplayName("caller errors are flagged so an integration bug gets logged, not shown to a player")
    void callerErrorsAreFlagged() {
        assertTrue(EconomyResult.fail(EconomyFailure.MISSING_SCOPE).isCallerError());
        assertTrue(EconomyResult.fail(EconomyFailure.WRONG_THREAD).isCallerError());
        assertFalse(EconomyResult.fail(EconomyFailure.INSUFFICIENT_FUNDS).isCallerError(),
                "being broke is the player's situation, not the mod's bug");
        assertFalse(EconomyResult.fail(EconomyFailure.BACKEND_ERROR).isCallerError());
    }

    @Test
    @DisplayName("a successful escrow result always carries its ticket")
    void escrowSuccessCarriesTicket() {
        EscrowTicket ticket = new EscrowTicket(UUID.randomUUID(), UUID.randomUUID(), 25.0,
                "mycasino", "blackjack stake", 1_000L);
        EscrowResult result = EscrowResult.ok(ticket);
        assertTrue(result.isOk());
        assertTrue(result.getTicket().isPresent());
        assertEquals(ticket, result.getTicket().get());
        assertEquals(25.0, result.toResult().getBalance().getAsDouble(), 0.0);
    }

    @Test
    @DisplayName("a successful escrow result without a ticket is a contradiction and is rejected")
    void escrowSuccessNeedsATicket() {
        assertThrows(IllegalArgumentException.class, () -> EscrowResult.ok(null));
    }

    @Test
    @DisplayName("a failed escrow result has no ticket to clean up")
    void escrowFailureHasNoTicket() {
        EscrowResult result = EscrowResult.fail(EconomyFailure.INSUFFICIENT_FUNDS);
        assertFalse(result.isOk());
        assertFalse(result.getTicket().isPresent(), "nothing was debited, so nothing is held");
        assertEquals(EconomyFailure.INSUFFICIENT_FUNDS, result.getFailure());
        assertNotNull(result.getMessage());
        assertFalse(result.toResult().isOk(), "the flattened result agrees");
    }

    @Test
    @DisplayName("tickets are identified by id alone, so a re-read ticket equals the original")
    void ticketIdentity() {
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        EscrowTicket first = new EscrowTicket(id, owner, 25.0, "mycasino", "stake", 1_000L);
        EscrowTicket reloaded = new EscrowTicket(id, owner, 25.0, "mycasino", "stake", 9_999L);
        assertEquals(first, reloaded, "a ticket reloaded from disk is the same ticket");
        assertEquals(first.hashCode(), reloaded.hashCode());
    }
}
