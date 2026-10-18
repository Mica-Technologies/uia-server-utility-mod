package com.micatechnologies.minecraft.sum.picture;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Frame layout of an animated WebP, read straight from its RIFF chunks.
 *
 * <p>TwelveMonkeys decodes each frame's pixels but keeps the timing and placement to itself, so
 * {@link PictureDecoder} reads them here: the canvas size from {@code VP8X} and, per
 * {@code ANMF} chunk, where the frame goes, how long it shows, and its blend and dispose flags.
 * Layout per the WebP container spec (RIFF, little-endian, chunks padded to even length).
 */
final class WebPAnimation {

    static final class Frame {
        final int x;
        final int y;
        final int width;
        final int height;
        final int durationMs;
        /** Alpha-blend onto the canvas; false means overwrite the frame's rectangle. */
        final boolean blend;
        /** Clear the frame's rectangle to transparent before the next frame. */
        final boolean dispose;

        Frame(int x, int y, int width, int height, int durationMs, boolean blend, boolean dispose) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.durationMs = durationMs;
            this.blend = blend;
            this.dispose = dispose;
        }
    }

    final int canvasWidth;
    final int canvasHeight;
    final List<Frame> frames;

    private WebPAnimation(int canvasWidth, int canvasHeight, List<Frame> frames) {
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        this.frames = frames;
    }

    /** The animation, or {@code null} for a still WebP or one this cannot make sense of. */
    @Nullable
    static WebPAnimation parse(byte[] d) {
        if (d.length < 12) return null;
        int canvasW = -1;
        int canvasH = -1;
        List<Frame> frames = new ArrayList<>();
        int pos = 12;
        while (pos + 8 <= d.length) {
            String fourCc = new String(d, pos, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long size = u32(d, pos + 4);
            int payload = pos + 8;
            if (size < 0 || payload + size > d.length) break;
            if (fourCc.equals("VP8X") && size >= 10) {
                canvasW = u24(d, payload + 4) + 1;
                canvasH = u24(d, payload + 7) + 1;
            } else if (fourCc.equals("ANMF") && size >= 16) {
                int flags = d[payload + 15] & 0xFF;
                frames.add(new Frame(
                    u24(d, payload) * 2, u24(d, payload + 3) * 2,
                    u24(d, payload + 6) + 1, u24(d, payload + 9) + 1,
                    u24(d, payload + 12),
                    (flags & 0x02) == 0, (flags & 0x01) != 0));
            }
            pos = payload + (int) size + (int) (size & 1);
        }
        if (canvasW <= 0 || canvasH <= 0 || frames.isEmpty()) return null;
        return new WebPAnimation(canvasW, canvasH, frames);
    }

    private static int u24(byte[] d, int i) {
        return (d[i] & 0xFF) | (d[i + 1] & 0xFF) << 8 | (d[i + 2] & 0xFF) << 16;
    }

    private static long u32(byte[] d, int i) {
        return ((long) u24(d, i)) | ((long) (d[i + 3] & 0xFF)) << 24;
    }
}
