package com.micatechnologies.minecraft.sum.phone.cloud;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@link PhoneCloudAction} tagged-union wire format. The private fields have no
 * getters by design (the class is a one-shot packet), so round-trips are verified by
 * encode→decode→re-encode byte stability, and the notes clamps are verified by comparing the
 * encoding of an over-limit action against the encoding of its already-trimmed equivalent.
 */
class PhoneCloudActionTest {

    private static byte[] encode(PhoneCloudAction a) {
        ByteBuf buf = Unpooled.buffer();
        a.toBytes(buf);
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }

    /** Encodes, decodes into a fresh action, and re-encodes — a symmetric codec is a fixpoint. */
    private static byte[] reencode(byte[] wire) {
        PhoneCloudAction decoded = new PhoneCloudAction();
        decoded.fromBytes(Unpooled.wrappedBuffer(wire));
        return encode(decoded);
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    @Test
    void sendMessageRoundTrips() {
        byte[] wire = encode(PhoneCloudAction.sendMessage(UUID.randomUUID(), "hello"));
        assertArrayEquals(wire, reencode(wire));
    }

    @Test
    void addContactRoundTrips() {
        byte[] wire = encode(PhoneCloudAction.addContact("Steve"));
        assertArrayEquals(wire, reencode(wire));
    }

    @Test
    void sendMoneyRoundTrips() {
        byte[] wire = encode(PhoneCloudAction.sendMoney(UUID.randomUUID(), 12.5));
        assertArrayEquals(wire, reencode(wire));
    }

    @Test
    void markReadAndRemoveContactRoundTrip() {
        byte[] mr = encode(PhoneCloudAction.markRead(UUID.randomUUID()));
        assertArrayEquals(mr, reencode(mr));
        byte[] rc = encode(PhoneCloudAction.removeContact(UUID.randomUUID()));
        assertArrayEquals(rc, reencode(rc));
    }

    @Test
    void nullMessageTextEncodesAsEmpty() {
        UUID target = UUID.randomUUID();
        assertArrayEquals(
            encode(PhoneCloudAction.sendMessage(target, "")),
            encode(PhoneCloudAction.sendMessage(target, null)));
    }

    @Test
    void nullContactNameEncodesAsEmpty() {
        assertArrayEquals(
            encode(PhoneCloudAction.addContact("")),
            encode(PhoneCloudAction.addContact(null)));
    }

    @Test
    void notesCountIsClampedToMax() {
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < PhoneCloudData.MAX_NOTES + 8; i++) {
            tooMany.add("n" + i);
        }
        // Byte 0 is the action; byte 1 is the note count for UPDATE_NOTES.
        byte[] wire = encode(PhoneCloudAction.updateNotes(tooMany));
        assertEquals(PhoneCloudData.MAX_NOTES, wire[1] & 0xFF);
    }

    @Test
    void overlongNoteIsTruncatedToMaxLength() {
        List<String> big = new ArrayList<>();
        big.add(repeat('q', PhoneCloudData.MAX_NOTE_LENGTH + 40));
        List<String> trimmed = new ArrayList<>();
        trimmed.add(repeat('q', PhoneCloudData.MAX_NOTE_LENGTH));
        assertArrayEquals(
            encode(PhoneCloudAction.updateNotes(trimmed)),
            encode(PhoneCloudAction.updateNotes(big)));
    }
}
