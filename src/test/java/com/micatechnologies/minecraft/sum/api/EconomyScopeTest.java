package com.micatechnologies.minecraft.sum.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Scope parsing and the implication rules behind {@link EconomyScope#expand}.
 *
 * <p>These decide what an integrating mod is allowed to do with real player money, and they are
 * evaluated against operator-written config text. A rule that under-expands produces baffling
 * permission failures on a correctly-configured server; one that over-expands hands a mod a
 * capability nobody granted it. Both are worth pinning down here, where it costs nothing.
 */
class EconomyScopeTest {

    @Test
    @DisplayName("tokens round-trip, and are matched case-insensitively with surrounding space")
    void tokenRoundTrip() {
        for (EconomyScope scope : EconomyScope.values()) {
            assertEquals(scope, EconomyScope.fromToken(scope.getToken()), scope.name());
            assertEquals(scope, EconomyScope.fromToken("  " + scope.getToken().toUpperCase() + " "),
                    "operators type config by hand; be forgiving about case and spacing");
        }
    }

    @Test
    @DisplayName("an unknown or null token resolves to null rather than throwing")
    void unknownToken() {
        assertNull(EconomyScope.fromToken("wallet_writ"), "a typo must not take the allowlist down");
        assertNull(EconomyScope.fromToken(""));
        assertNull(EconomyScope.fromToken(null));
        assertNull(EconomyScope.fromToken(EconomyScope.WILDCARD_TOKEN),
                "the wildcard is expanded by the config parser, not resolved as a scope");
    }

    @Test
    @DisplayName("writing a wallet implies reading it")
    void walletWriteImpliesRead() {
        Set<EconomyScope> expanded = EconomyScope.expand(EnumSet.of(EconomyScope.WALLET_WRITE));
        assertTrue(expanded.contains(EconomyScope.WALLET_READ));
        assertFalse(expanded.contains(EconomyScope.BANK_READ), "wallet access must not reach the bank");
    }

    @Test
    @DisplayName("writing a bank implies reading it, and nothing else")
    void bankWriteImpliesRead() {
        Set<EconomyScope> expanded = EconomyScope.expand(EnumSet.of(EconomyScope.BANK_WRITE));
        assertEquals(EnumSet.of(EconomyScope.BANK_WRITE, EconomyScope.BANK_READ), expanded);
    }

    @Test
    @DisplayName("escrow implies the whole wallet chain, since opening and refunding move a wallet")
    void escrowImpliesWalletChain() {
        Set<EconomyScope> expanded = EconomyScope.expand(EnumSet.of(EconomyScope.ESCROW));
        assertEquals(EnumSet.of(EconomyScope.ESCROW, EconomyScope.WALLET_WRITE,
                EconomyScope.WALLET_READ), expanded,
                "the chain is transitive: ESCROW -> WALLET_WRITE -> WALLET_READ");
    }

    @Test
    @DisplayName("expanding is idempotent")
    void expandIsIdempotent() {
        Set<EconomyScope> once = EconomyScope.expand(EnumSet.of(EconomyScope.ESCROW));
        assertEquals(once, EconomyScope.expand(once));
    }

    @Test
    @DisplayName("empty and null input expand to nothing, which is the deny-by-default case")
    void emptyExpandsToNothing() {
        assertTrue(EconomyScope.expand(Collections.emptySet()).isEmpty());
        assertTrue(EconomyScope.expand(null).isEmpty(),
                "an unlisted mod must get no scopes, not a crash");
    }

    @Test
    @DisplayName("a null entry among granted scopes is ignored rather than propagated")
    void nullEntryIgnored() {
        Set<EconomyScope> expanded = EconomyScope.expand(Arrays.asList(EconomyScope.BANK_READ, null));
        assertEquals(EnumSet.of(EconomyScope.BANK_READ), expanded);
    }

    @Test
    @DisplayName("the wildcard grants every scope, and all() is already closed")
    void wildcardGrantsEverything() {
        assertEquals(EnumSet.allOf(EconomyScope.class), EconomyScope.all());
        assertEquals(EconomyScope.all(), EconomyScope.expand(EconomyScope.all()));
    }

    @Test
    @DisplayName("expanded sets are unmodifiable, so a granted scope set cannot be widened in place")
    void expandedSetIsUnmodifiable() {
        Set<EconomyScope> expanded = EconomyScope.expand(EnumSet.of(EconomyScope.WALLET_READ));
        assertThrows(UnsupportedOperationException.class,
                () -> expanded.add(EconomyScope.BANK_WRITE));
        assertThrows(UnsupportedOperationException.class,
                () -> EconomyScope.all().add(EconomyScope.BANK_WRITE));
    }
}
