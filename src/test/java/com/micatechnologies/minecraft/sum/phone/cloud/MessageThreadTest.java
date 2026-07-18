package com.micatechnologies.minecraft.sum.phone.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link MessageThread}: FIFO eviction past {@link MessageThread#MAX_MESSAGES},
 * {@code lastMessage}, and the ordered NBT round-trip incl. {@code unreadCount}. Eviction is
 * the bound that keeps NBT from growing without ever rejecting a send.
 */
class MessageThreadTest {

    private static Message msg(String text, long ts) {
        return new Message(UUID.randomUUID(), text, ts);
    }

    @Test
    void appendTracksLastMessage() {
        MessageThread t = new MessageThread(UUID.randomUUID());
        assertNull(t.lastMessage(), "empty thread has no last message");
        Message m = msg("first", 1L);
        t.append(m);
        assertEquals(1, t.messages.size());
        assertSame(m, t.lastMessage());
    }

    @Test
    void staysAtCapWhenFull() {
        MessageThread t = new MessageThread(UUID.randomUUID());
        for (int i = 0; i < MessageThread.MAX_MESSAGES; i++) {
            t.append(msg("m" + i, i));
        }
        assertEquals(MessageThread.MAX_MESSAGES, t.messages.size());
    }

    @Test
    void evictsOldestPastCap() {
        MessageThread t = new MessageThread(UUID.randomUUID());
        for (int i = 0; i < MessageThread.MAX_MESSAGES + 1; i++) {
            t.append(msg("m" + i, i));
        }
        assertEquals(MessageThread.MAX_MESSAGES, t.messages.size());
        // The very first message (m0) was evicted; element 0 is now the second-inserted (m1),
        // and the newest is still the last appended.
        assertEquals("m1", t.messages.get(0).text);
        assertEquals("m" + MessageThread.MAX_MESSAGES, t.lastMessage().text);
    }

    @Test
    void nbtRoundTripPreservesOrderAndUnread() {
        UUID partner = UUID.randomUUID();
        MessageThread t = new MessageThread(partner);
        t.unreadCount = 2;
        t.append(msg("a", 1L));
        t.append(msg("b", 2L));
        t.append(msg("c", 3L));

        MessageThread restored = MessageThread.readNbt(t.writeNbt());
        assertEquals(partner, restored.partnerUuid);
        assertEquals(2, restored.unreadCount);
        assertEquals(3, restored.messages.size());
        assertEquals("a", restored.messages.get(0).text);
        assertEquals("b", restored.messages.get(1).text);
        assertEquals("c", restored.messages.get(2).text);
    }

    @Test
    void emptyThreadRoundTripsToEmpty() {
        MessageThread restored = MessageThread.readNbt(new MessageThread(UUID.randomUUID()).writeNbt());
        assertEquals(0, restored.messages.size());
        assertEquals(0, restored.unreadCount);
    }
}
