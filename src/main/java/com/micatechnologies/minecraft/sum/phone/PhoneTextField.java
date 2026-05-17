package com.micatechnologies.minecraft.sum.phone;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import org.lwjgl.input.Keyboard;

/**
 * Tiny in-house text field shared by the phone's apps. Vanilla {@code GuiTextField} is
 * single-line only, and the notes app needs multi-line input, so we ship our own.
 *
 * <p>Features:
 * <ul>
 *   <li>Char insert/delete with cursor, arrow keys, Home/End.</li>
 *   <li>Optional newline support ({@link #allowNewlines}). Off for compose/name fields.</li>
 *   <li>Auto word/char-wrapped rendering with a blinking cursor that follows the row split.</li>
 *   <li>Max-length cap (silently swallows further input).</li>
 * </ul>
 */
public class PhoneTextField {

    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int CURSOR_COLOR = 0xFFE0E0E0;

    private final StringBuilder value = new StringBuilder();
    private int cursorIndex;
    private final int maxLength;
    private final boolean allowNewlines;

    public PhoneTextField(int maxLength, boolean allowNewlines) {
        this.maxLength = maxLength;
        this.allowNewlines = allowNewlines;
    }

    public String getValue() {
        return value.toString();
    }

    public void setValue(String s) {
        value.setLength(0);
        if (s != null) {
            String clipped = s.length() > maxLength ? s.substring(0, maxLength) : s;
            value.append(clipped);
        }
        cursorIndex = value.length();
    }

    public int length() {
        return value.length();
    }

    public int maxLength() {
        return maxLength;
    }

    public boolean isEmpty() {
        return value.length() == 0;
    }

    /** Returns true when the key was consumed. */
    public boolean handleKey(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_BACK) {
            if (cursorIndex > 0) {
                value.deleteCharAt(cursorIndex - 1);
                cursorIndex--;
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_DELETE) {
            if (cursorIndex < value.length()) {
                value.deleteCharAt(cursorIndex);
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_LEFT) {
            if (cursorIndex > 0) cursorIndex--;
            return true;
        }
        if (keyCode == Keyboard.KEY_RIGHT) {
            if (cursorIndex < value.length()) cursorIndex++;
            return true;
        }
        if (keyCode == Keyboard.KEY_HOME) {
            cursorIndex = 0;
            return true;
        }
        if (keyCode == Keyboard.KEY_END) {
            cursorIndex = value.length();
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (!allowNewlines) return false; // let the parent treat Enter as a submit
            if (value.length() < maxLength) {
                value.insert(cursorIndex, '\n');
                cursorIndex++;
            }
            return true;
        }
        if (typedChar >= 32 && typedChar != 127) {
            if (value.length() < maxLength) {
                value.insert(cursorIndex, typedChar);
                cursorIndex++;
            }
            return true;
        }
        return false;
    }

    /** Render multi-line with wrapping. Used by the notes editor. */
    public void renderMultiline(FontRenderer fr, int x1, int y1, int x2, int y2,
                                boolean cursorVisible) {
        int maxWidth = x2 - x1;
        int lineH = fr.FONT_HEIGHT + 1;
        int row = 0;

        StringBuilder cur = new StringBuilder();
        int cursorRow = -1, cursorPxX = -1;

        for (int i = 0; i <= value.length(); i++) {
            if (i == cursorIndex && cursorRow < 0) {
                cursorRow = row;
                cursorPxX = x1 + fr.getStringWidth(cur.toString());
            }
            if (i == value.length()) break;

            char c = value.charAt(i);
            if (c == '\n') {
                if ((y1 + row * lineH) < y2 - lineH) {
                    fr.drawString(cur.toString(), x1, y1 + row * lineH, TEXT_COLOR);
                }
                row++;
                cur.setLength(0);
                continue;
            }
            int tentativeW = fr.getStringWidth(cur.toString() + c);
            if (tentativeW > maxWidth) {
                if ((y1 + row * lineH) < y2 - lineH) {
                    fr.drawString(cur.toString(), x1, y1 + row * lineH, TEXT_COLOR);
                }
                row++;
                cur.setLength(0);
                if (cursorIndex == i && cursorRow == row - 1) {
                    cursorRow = row;
                    cursorPxX = x1;
                }
            }
            cur.append(c);
        }
        if (cur.length() > 0 && (y1 + row * lineH) < y2 - lineH) {
            fr.drawString(cur.toString(), x1, y1 + row * lineH, TEXT_COLOR);
        }
        if (cursorRow < 0) {
            cursorRow = row;
            cursorPxX = x1 + fr.getStringWidth(cur.toString());
        }

        if (cursorVisible) {
            int cy = y1 + cursorRow * lineH;
            if (cy >= y1 && cy + lineH - 1 < y2) {
                Gui.drawRect(cursorPxX, cy, cursorPxX + 1, cy + lineH - 1, CURSOR_COLOR);
            }
        }
    }

    /** Render single-line, scrolling the visible window so the cursor stays in view. */
    public void renderSingleLine(FontRenderer fr, int x1, int y1, int x2, int y2,
                                 boolean cursorVisible) {
        int innerW = x2 - x1;
        String full = value.toString();
        // Compute scroll offset so the cursor sits inside the visible window.
        int cursorPxFromStart = fr.getStringWidth(full.substring(0, cursorIndex));
        int scroll = 0;
        if (cursorPxFromStart > innerW - 4) {
            scroll = cursorPxFromStart - (innerW - 4);
        }
        // Build the visible substring by trimming `scroll` pixels from the left.
        int dropChars = 0;
        if (scroll > 0) {
            int w = 0;
            for (int i = 0; i < full.length(); i++) {
                w += fr.getCharWidth(full.charAt(i));
                if (w >= scroll) { dropChars = i + 1; break; }
            }
        }
        String visible = full.substring(dropChars);
        fr.drawString(visible, x1, y1 + (y2 - y1 - fr.FONT_HEIGHT) / 2, TEXT_COLOR);

        if (cursorVisible) {
            int cx = x1 + fr.getStringWidth(full.substring(dropChars, cursorIndex));
            int cy1 = y1 + (y2 - y1 - fr.FONT_HEIGHT) / 2;
            int cy2 = cy1 + fr.FONT_HEIGHT;
            Gui.drawRect(cx, cy1, cx + 1, cy2, CURSOR_COLOR);
        }
    }
}
