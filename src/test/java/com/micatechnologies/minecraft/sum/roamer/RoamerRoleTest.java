package com.micatechnologies.minecraft.sum.roamer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link RoamerRole}: the case-insensitive {@code fromId} lookup with a GENERIC fallback,
 * and the defensive-clone contract of {@code getDefaultGreetings} (a caller mutating the returned
 * array must not corrupt the shared enum data).
 */
class RoamerRoleTest {

    @Test
    void fromIdResolvesKnownRoles() {
        assertSame(RoamerRole.BANK_TELLER, RoamerRole.fromId("bank_teller"));
        assertSame(RoamerRole.GENERIC, RoamerRole.fromId("generic"));
    }

    @Test
    void fromIdIsCaseInsensitive() {
        assertSame(RoamerRole.BANK_TELLER, RoamerRole.fromId("BANK_TELLER"));
        assertSame(RoamerRole.BANK_TELLER, RoamerRole.fromId("Bank_Teller"));
    }

    @Test
    void fromIdFallsBackToGeneric() {
        assertSame(RoamerRole.GENERIC, RoamerRole.fromId(null));
        assertSame(RoamerRole.GENERIC, RoamerRole.fromId(""));
        assertSame(RoamerRole.GENERIC, RoamerRole.fromId("nonsense"));
    }

    @Test
    void defaultNamesAreAsDeclared() {
        assertNull(RoamerRole.GENERIC.getDefaultName(), "GENERIC leaves the name alone");
        assertEquals("Bank Teller", RoamerRole.BANK_TELLER.getDefaultName());
    }

    @Test
    void greetingsAreReturnedAsADefensiveClone() {
        String[] first = RoamerRole.BANK_TELLER.getDefaultGreetings();
        assertEquals(6, first.length);
        assertEquals(3, RoamerRole.GENERIC.getDefaultGreetings().length);

        String original = first[0];
        first[0] = "HACKED";
        assertNotEquals("HACKED", RoamerRole.BANK_TELLER.getDefaultGreetings()[0],
            "mutating the returned array must not affect the enum");
        assertEquals(original, RoamerRole.BANK_TELLER.getDefaultGreetings()[0]);
    }

    @Test
    void idsRoundTripThroughFromId() {
        for (RoamerRole role : RoamerRole.values()) {
            assertSame(role, RoamerRole.fromId(role.getId()));
        }
    }
}
