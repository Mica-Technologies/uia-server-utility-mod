package com.micatechnologies.minecraft.sum.phone.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link PhoneCloudData}: {@code getOrCreateThread} identity, {@code totalUnread} summing,
 * and the NBT round-trip including the read-side notes caps ({@link PhoneCloudData#MAX_NOTES}
 * count, {@link PhoneCloudData#MAX_NOTE_LENGTH} per-note length).
 */
class PhoneCloudDataTest {

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    @Test
    void getOrCreateThreadIsStablePerPartner() {
        PhoneCloudData d = new PhoneCloudData(UUID.randomUUID(), "456-000-0001");
        UUID partner = UUID.randomUUID();
        MessageThread first = d.getOrCreateThread(partner);
        assertEquals(partner, first.partnerUuid);
        assertSame(first, d.getOrCreateThread(partner), "same partner → same thread instance");
        assertNotSame(first, d.getOrCreateThread(UUID.randomUUID()), "different partner → new thread");
    }

    @Test
    void totalUnreadSumsAcrossThreads() {
        PhoneCloudData d = new PhoneCloudData(UUID.randomUUID(), "456-000-0002");
        assertEquals(0, d.totalUnread());
        d.getOrCreateThread(UUID.randomUUID()).unreadCount = 2;
        d.getOrCreateThread(UUID.randomUUID()).unreadCount = 5;
        d.getOrCreateThread(UUID.randomUUID()).unreadCount = 0;
        assertEquals(7, d.totalUnread());
    }

    @Test
    void nbtRoundTripPreservesContactsThreadsAndNotes() {
        UUID owner = UUID.randomUUID();
        PhoneCloudData d = new PhoneCloudData(owner, "456-242-0007");
        d.contacts.add(new Contact(UUID.randomUUID(), "Bob", "987-100-0001"));
        UUID partner = UUID.randomUUID();
        d.getOrCreateThread(partner).append(new Message(owner, "yo", 1L));
        d.getOrCreateThread(partner).unreadCount = 1;
        d.notes.add("buy milk");

        PhoneCloudData restored = PhoneCloudData.readNbt(d.writeNbt());
        assertEquals(owner, restored.ownerUuid);
        assertEquals("456-242-0007", restored.phoneNumber);
        assertEquals(1, restored.contacts.size());
        assertEquals("Bob", restored.contacts.get(0).displayName);
        assertEquals(1, restored.threads.size());
        assertEquals(1, restored.threads.get(partner).unreadCount);
        assertEquals("buy milk", restored.notes.get(0));
    }

    @Test
    void nullPhoneNumberSerializesAsEmpty() {
        PhoneCloudData d = new PhoneCloudData(UUID.randomUUID(), null);
        assertEquals("", PhoneCloudData.readNbt(d.writeNbt()).phoneNumber);
    }

    @Test
    void readCapsNotesToMax() {
        PhoneCloudData d = new PhoneCloudData(UUID.randomUUID(), "456-000-0003");
        for (int i = 0; i < PhoneCloudData.MAX_NOTES + 3; i++) {
            d.notes.add("note " + i);
        }
        // writeNbt persists them all; readNbt is where the cap applies.
        assertEquals(PhoneCloudData.MAX_NOTES, PhoneCloudData.readNbt(d.writeNbt()).notes.size());
    }

    @Test
    void readTruncatesOverlongNote() {
        PhoneCloudData d = new PhoneCloudData(UUID.randomUUID(), "456-000-0004");
        d.notes.add(repeat('z', PhoneCloudData.MAX_NOTE_LENGTH + 50));
        assertEquals(PhoneCloudData.MAX_NOTE_LENGTH,
            PhoneCloudData.readNbt(d.writeNbt()).notes.get(0).length());
    }
}
