package com.micatechnologies.minecraft.sum.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Who gets blamed for a money movement.
 *
 * <p>Attribution is what makes the economy event bus and the audit log worth having: given a
 * balance that changed unexpectedly, it names the mod that changed it. A scope that leaks would
 * mislabel every later transaction on the server as belonging to whichever integration ran last,
 * which is worse than having no attribution at all — it would point an investigation at an
 * innocent mod.
 */
class EconomyAttributionTest {

    @Test
    @DisplayName("SUM's own features are the default")
    void defaultsToSum() {
        assertEquals(EconomyAttribution.SUM, EconomyAttribution.currentModId());
        assertNull(EconomyAttribution.currentReason());
    }

    @Test
    @DisplayName("a scope names its mod, and closing restores what came before")
    void scopeAttributesAndRestores() {
        try (EconomyAttribution.Scope ignored =
                 EconomyAttribution.enter("mycasino", "blackjack stake")) {
            assertEquals("mycasino", EconomyAttribution.currentModId());
            assertEquals("blackjack stake", EconomyAttribution.currentReason());
        }
        assertEquals(EconomyAttribution.SUM, EconomyAttribution.currentModId());
        assertNull(EconomyAttribution.currentReason());
    }

    @Test
    @DisplayName("nested scopes restore the outer one, not the default")
    void nestingRestoresTheOuterScope() {
        // Escrow opening a hold spends a wallet, so a scope really does nest inside another.
        // Resetting to "sum" on the inner close would mislabel the rest of the outer operation.
        try (EconomyAttribution.Scope outer = EconomyAttribution.enter("mycasino", "outer")) {
            try (EconomyAttribution.Scope inner = EconomyAttribution.enter("othermod", "inner")) {
                assertEquals("othermod", EconomyAttribution.currentModId());
            }
            assertEquals("mycasino", EconomyAttribution.currentModId());
            assertEquals("outer", EconomyAttribution.currentReason());
        }
        assertEquals(EconomyAttribution.SUM, EconomyAttribution.currentModId());
    }

    @Test
    @DisplayName("a scope is restored even when the body throws")
    void scopeSurvivesAnException() {
        // A leaked scope would silently misattribute every transaction for the rest of the
        // server's life, so this is the case that matters most.
        assertThrows(IllegalStateException.class, () -> {
            try (EconomyAttribution.Scope ignored = EconomyAttribution.enter("mycasino", "boom")) {
                throw new IllegalStateException("boom");
            }
        });
        assertEquals(EconomyAttribution.SUM, EconomyAttribution.currentModId());
        assertNull(EconomyAttribution.currentReason());
    }

    @Test
    @DisplayName("a null mod id falls back to SUM rather than to null")
    void nullModIdFallsBack() {
        try (EconomyAttribution.Scope ignored = EconomyAttribution.enter(null, null)) {
            assertEquals(EconomyAttribution.SUM, EconomyAttribution.currentModId(),
                "an event must always name someone");
        }
    }
}
