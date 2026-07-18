package com.micatechnologies.minecraft.sum.phone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests the pure input state machine of {@link PhoneTextField}: {@code setValue} clamping and
 * the {@code handleKey} cursor/edit logic. The LWJGL {@code Keyboard.KEY_*} values are inlined
 * compile-time constants, so this needs no game runtime. The render methods (which need a
 * {@code FontRenderer}) are out of scope.
 */
class PhoneTextFieldTest {

    // LWJGL2 key codes (constant expressions, safe to hard-code without loading Keyboard).
    private static final int KEY_BACK = 14;
    private static final int KEY_RETURN = 28;
    private static final int KEY_LEFT = 203;
    private static final int KEY_DELETE = 211;
    private static final int KEY_NONE = 0;
    private static final char NO_CHAR = '\0';

    @Test
    void setValueClampsToMaxLength() {
        PhoneTextField f = new PhoneTextField(4, false);
        f.setValue("abcdefg");
        assertEquals("abcd", f.getValue());
        assertEquals(4, f.length());
    }

    @Test
    void setValueNullClearsField() {
        PhoneTextField f = new PhoneTextField(10, false);
        f.setValue("something");
        f.setValue(null);
        assertTrue(f.isEmpty());
        assertEquals(0, f.length());
    }

    @Test
    void setValuePlacesCursorAtEnd() {
        PhoneTextField f = new PhoneTextField(10, false);
        f.setValue("abc");
        // Cursor at end → backspace removes the last char.
        f.handleKey(NO_CHAR, KEY_BACK);
        assertEquals("ab", f.getValue());
    }

    @Test
    void typingInsertsPrintableChars() {
        PhoneTextField f = new PhoneTextField(10, false);
        assertTrue(f.handleKey('a', KEY_NONE));
        assertTrue(f.handleKey('b', KEY_NONE));
        assertEquals("ab", f.getValue());
    }

    @Test
    void backspaceAtStartIsANoOpButConsumed() {
        PhoneTextField f = new PhoneTextField(10, false);
        assertTrue(f.handleKey(NO_CHAR, KEY_BACK), "backspace is always consumed");
        assertEquals("", f.getValue());
    }

    @Test
    void enterIsRejectedWhenNewlinesDisallowed() {
        PhoneTextField f = new PhoneTextField(10, false);
        f.setValue("hi");
        // false → the parent GUI treats Enter as a submit; no newline inserted.
        assertFalse(f.handleKey('\r', KEY_RETURN));
        assertEquals("hi", f.getValue());
    }

    @Test
    void enterInsertsNewlineWhenAllowed() {
        PhoneTextField f = new PhoneTextField(10, true);
        f.setValue("hi");
        assertTrue(f.handleKey('\r', KEY_RETURN));
        assertEquals("hi\n", f.getValue());
    }

    @Test
    void typingIsSwallowedAtMaxLength() {
        PhoneTextField f = new PhoneTextField(2, false);
        f.setValue("ab");
        assertTrue(f.handleKey('c', KEY_NONE), "still consumed, just not inserted");
        assertEquals("ab", f.getValue());
    }

    @Test
    void cursorMovementInsertsMidString() {
        PhoneTextField f = new PhoneTextField(10, false);
        f.setValue("ab");
        f.handleKey(NO_CHAR, KEY_LEFT); // cursor between a and b
        f.handleKey('X', KEY_NONE);
        assertEquals("aXb", f.getValue());
    }

    @Test
    void deleteAtEndIsANoOp() {
        PhoneTextField f = new PhoneTextField(10, false);
        f.setValue("ab"); // cursor at end
        assertTrue(f.handleKey(NO_CHAR, KEY_DELETE));
        assertEquals("ab", f.getValue());
    }

    @Test
    void controlCharsAreNotConsumed() {
        PhoneTextField f = new PhoneTextField(10, false);
        // A sub-space char with no matching special key falls through to "not handled".
        assertFalse(f.handleKey((char) 7, KEY_NONE));
        assertEquals("", f.getValue());
    }
}
