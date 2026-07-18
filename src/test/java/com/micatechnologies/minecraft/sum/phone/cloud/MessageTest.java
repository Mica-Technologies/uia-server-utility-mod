package com.micatechnologies.minecraft.sum.phone.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link Message}: the {@code clip} length cap (which the constructor routes text
 * through) and the {@code writeNbt}/{@code readNbt} round-trip. The clip rule is the
 * "truncate, don't break" guarantee — a 300-char paste must never overflow NBT.
 */
class MessageTest {

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    @Test
    void clipHandlesNullAndEmpty() {
        assertEquals("", Message.clip(null));
        assertEquals("", Message.clip(""));
    }

    @Test
    void clipLeavesShortTextUnchanged() {
        assertEquals("hi", Message.clip("hi"));
    }

    @Test
    void clipIsInclusiveAtTheBoundary() {
        String exactly = repeat('a', Message.MAX_TEXT_LENGTH);
        assertEquals(Message.MAX_TEXT_LENGTH, Message.clip(exactly).length());
        String over = repeat('a', Message.MAX_TEXT_LENGTH + 1);
        assertEquals(Message.MAX_TEXT_LENGTH, Message.clip(over).length());
    }

    @Test
    void constructorClipsText() {
        Message m = new Message(UUID.randomUUID(), repeat('x', 300), 5L);
        assertEquals(Message.MAX_TEXT_LENGTH, m.text.length());
    }

    @Test
    void nbtRoundTripPreservesFields() {
        UUID sender = UUID.randomUUID();
        Message m = new Message(sender, "hello there", 123456789L);
        Message restored = Message.readNbt(m.writeNbt());
        assertEquals(sender, restored.senderUuid);
        assertEquals("hello there", restored.text);
        assertEquals(123456789L, restored.timestamp);
    }

    @Test
    void nullTextSerializesAsEmpty() {
        Message m = new Message();
        m.senderUuid = UUID.randomUUID();
        m.text = null;
        m.timestamp = 0L;
        assertEquals("", Message.readNbt(m.writeNbt()).text);
    }
}
