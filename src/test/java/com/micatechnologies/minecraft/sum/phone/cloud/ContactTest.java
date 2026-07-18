package com.micatechnologies.minecraft.sum.phone.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link Contact}: the null-coalescing constructor/serializer and the NBT round-trip.
 * A null display name or number must serialize as {@code ""} so the phone UI never renders
 * "null".
 */
class ContactTest {

    @Test
    void constructorCoalescesNullNumber() {
        Contact c = new Contact(UUID.randomUUID(), "Bob", null);
        assertEquals("", c.cachedNumber);
    }

    @Test
    void writeNbtCoalescesNulls() {
        Contact c = new Contact();
        c.targetUuid = UUID.randomUUID();
        c.displayName = null;
        c.cachedNumber = null;
        Contact restored = Contact.readNbt(c.writeNbt());
        assertEquals("", restored.displayName);
        assertEquals("", restored.cachedNumber);
    }

    @Test
    void nbtRoundTripPreservesFields() {
        UUID target = UUID.randomUUID();
        Contact c = new Contact(target, "Alice", "456-242-0007");
        Contact restored = Contact.readNbt(c.writeNbt());
        assertEquals(target, restored.targetUuid);
        assertEquals("Alice", restored.displayName);
        assertEquals("456-242-0007", restored.cachedNumber);
    }
}
