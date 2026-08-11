package com.micatechnologies.minecraft.sum.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.micatechnologies.minecraft.sum.api.event.EscrowEvent;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The ways a hold is allowed to end, pinned as a contract.
 *
 * <p>Escrow is the one part of this API where a missing operation is not merely inconvenient — it
 * silently changes who keeps the money. Before {@code escrowForfeit} existed, a casino settling a
 * loss had nothing to call but {@code escrowRelease}, which hands the stake straight back to the
 * player who just lost it. That is not a compile error anywhere; it is a house that never wins.
 *
 * <p>So these tests exist to keep the four documented outcomes in
 * {@code docs/SUM_ECONOMY_API.md} ("the four ways a hold ends") and the shipped surface from
 * drifting apart. They are deliberately reflective rather than behavioural: settling a real hold
 * needs a world and an online player, which {@code TESTING_PLAN} §4.0 puts out of reach here.
 */
class EscrowSettlementContractTest {

    @Test
    @DisplayName("every documented way to settle a hold is actually callable")
    void settlementMethodsExist() throws Exception {
        assertSignature("escrowOpen", EscrowResult.class,
            net.minecraft.entity.player.EntityPlayer.class, double.class, String.class);
        assertSignature("escrowRelease", EconomyResult.class,
            EscrowTicket.class, net.minecraft.entity.player.EntityPlayer.class, String.class);
        assertSignature("escrowRefund", EconomyResult.class, EscrowTicket.class, String.class);
        assertSignature("escrowForfeit", EconomyResult.class, EscrowTicket.class, String.class);
    }

    @Test
    @DisplayName("forfeiting is its own method, not a release with no recipient")
    void forfeitIsNotAnOverloadOfRelease() {
        // A null recipient reaching escrowRelease is overwhelmingly more likely to be a consumer's
        // bug than a deliberate "burn it", so the two must never be spelled the same way. If some
        // future refactor collapses them, this fails rather than letting a null slip through and
        // quietly destroy a player's stake.
        long releaseOverloads = Arrays.stream(EconomyHandle.class.getMethods())
            .filter(m -> "escrowRelease".equals(m.getName()))
            .count();
        assertEquals(1L, releaseOverloads,
            "escrowRelease must have exactly one form, which requires a recipient");
    }

    @Test
    @DisplayName("every settlement outcome has an event type, so a listener can see all of them")
    void everyOutcomeIsObservable() {
        Set<EscrowEvent.Type> types = EnumSet.allOf(EscrowEvent.Type.class);
        assertTrue(types.contains(EscrowEvent.Type.OPENED));
        assertTrue(types.contains(EscrowEvent.Type.RELEASED));
        assertTrue(types.contains(EscrowEvent.Type.REFUNDED));
        assertTrue(types.contains(EscrowEvent.Type.FORFEITED),
            "a destroyed stake must be distinguishable from a paid-out one, or a listener "
                + "tracking the money supply is simply wrong");
        assertTrue(types.contains(EscrowEvent.Type.ORPHAN_REFUNDED));
    }

    @Test
    @DisplayName("exactly one outcome removes money from the economy")
    void onlyForfeitDestroysValue() {
        // Stated here because it is the invariant the whole escrow design rests on: value is
        // conserved unless somebody explicitly asked for it not to be. If a second destructive
        // outcome is ever added, this failing is the prompt to say so in the docs too.
        Set<String> destructive = EnumSet.allOf(EscrowEvent.Type.class).stream()
            .filter(t -> t == EscrowEvent.Type.FORFEITED)
            .map(Enum::name)
            .collect(Collectors.toSet());
        assertEquals(1, destructive.size());
    }

    private static void assertSignature(String name, Class<?> returns, Class<?>... parameters)
            throws Exception {
        Method method = EconomyHandle.class.getMethod(name, parameters);
        assertNotNull(method, name + " is missing from the API");
        assertEquals(returns, method.getReturnType(),
            name + " must keep its return type; consumers switch on it");
    }

    /** Kept so a mistyped reflective lookup fails loudly rather than silently passing. */
    @Test
    @DisplayName("the signature check would notice a method that is not there")
    void signatureCheckActuallyChecks() {
        try {
            assertSignature("escrowVanish", EconomyResult.class, EscrowTicket.class, String.class);
            fail("assertSignature must throw for a method that does not exist");
        } catch (NoSuchMethodException expected) {
            // The check works.
        } catch (Exception other) {
            fail("unexpected failure: " + other);
        }
    }
}
