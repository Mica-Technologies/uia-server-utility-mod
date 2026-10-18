package com.micatechnologies.minecraft.sum.picture;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.w3c.dom.Node;

/**
 * Turns downloaded bytes into frames ready for upload: PNG, JPEG, BMP, GIF (animated) and WebP
 * (lossy, lossless, animated).
 *
 * <p>The format is decided by the file's first bytes, never by the URL or Content-Type, which are
 * routinely wrong. When the bytes are a web page or an SVG the error says so, because "that link
 * is a page, not an image" is the most common mistake a player makes.
 *
 * <p>Animated images are composited here, frame by frame, with each format's blend and disposal
 * rules, so every output frame is a complete picture. Output is held to {@link #MAX_EDGE} pixels
 * a side ({@link #MAX_ANIMATED_EDGE} when animated), {@link #MAX_FRAMES} frames and
 * {@link #MAX_TOTAL_PIXELS} pixels across all frames, scaling down rather than failing, so one
 * huge GIF cannot take the client's memory or GPU with it.
 *
 * <p>Pure AWT and ImageIO, no Minecraft classes: it runs on a download thread and in unit tests.
 */
public final class PictureDecoder {

    static final int MAX_EDGE = 2048;
    static final int MAX_ANIMATED_EDGE = 1024;
    static final int MAX_FRAMES = 300;
    static final long MAX_TOTAL_PIXELS = 48L * 1024 * 1024;
    /** Browsers show GIF delays of 0 or 10 ms as 100 ms; doing the same matches what people expect. */
    static final int DEFAULT_DELAY_MS = 100;

    private PictureDecoder() {}

    /** A decoded image: one frame for a still, several with per-frame delays when animated. */
    public static final class Decoded {
        public final int width;
        public final int height;
        public final List<BufferedImage> frames;
        public final int[] delaysMs;

        Decoded(List<BufferedImage> frames, int[] delaysMs) {
            this.frames = frames;
            this.delaysMs = delaysMs;
            this.width = frames.get(0).getWidth();
            this.height = frames.get(0).getHeight();
        }

        public boolean isAnimated() {
            return frames.size() > 1;
        }
    }

    /** A decode failure with a message fit to show a player. */
    public static final class PictureException extends Exception {
        public PictureException(String message) {
            super(message);
        }
    }

    public static Decoded decode(byte[] data) throws PictureException {
        Format format = sniff(data);
        try {
            switch (format) {
                case GIF:  return limit(decodeGif(data));
                case WEBP: return limit(decodeWebp(data));
                case PNG:
                case JPEG:
                case BMP:  return limit(decodeStill(data));
                case SVG:  throw new PictureException("SVG images aren't supported yet. Use PNG, JPEG, GIF or WebP.");
                case HTML: throw new PictureException("That link is a web page, not an image. Use the image's own address (right-click the image, Copy image address).");
                default:   throw new PictureException("That file isn't an image this frame can show (PNG, JPEG, GIF, WebP or BMP).");
            }
        } catch (IOException | RuntimeException e) {
            throw new PictureException("The image couldn't be read (" + e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ").");
        }
    }

    // --- Format detection ---

    enum Format { PNG, JPEG, GIF, WEBP, BMP, SVG, HTML, UNKNOWN }

    static Format sniff(byte[] d) {
        if (startsWith(d, 0, 0x89, 'P', 'N', 'G')) return Format.PNG;
        if (startsWith(d, 0, 0xFF, 0xD8, 0xFF)) return Format.JPEG;
        if (startsWith(d, 0, 'G', 'I', 'F', '8')) return Format.GIF;
        if (startsWith(d, 0, 'R', 'I', 'F', 'F') && startsWith(d, 8, 'W', 'E', 'B', 'P')) return Format.WEBP;
        if (startsWith(d, 0, 'B', 'M')) return Format.BMP;
        String head = new String(d, 0, Math.min(d.length, 512), StandardCharsets.ISO_8859_1)
            .trim().toLowerCase(Locale.ROOT);
        if (head.contains("<svg")) return Format.SVG;
        if (head.startsWith("<!doctype html") || head.startsWith("<html") || head.contains("<head")) {
            return Format.HTML;
        }
        return Format.UNKNOWN;
    }

    private static boolean startsWith(byte[] d, int offset, int... expected) {
        if (d.length < offset + expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((d[offset + i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }

    // --- Stills ---

    private static Decoded decodeStill(byte[] data) throws IOException, PictureException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
        if (img == null) {
            throw new PictureException("The image couldn't be read.");
        }
        return single(img);
    }

    private static Decoded single(BufferedImage img) {
        List<BufferedImage> frames = new ArrayList<>(1);
        frames.add(toArgb(img));
        return new Decoded(frames, new int[] {0});
    }

    // --- GIF ---

    private static Decoded decodeGif(byte[] data) throws IOException, PictureException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            throw new PictureException("This Java has no GIF reader.");
        }
        ImageReader reader = readers.next();
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(in, false, false);
            int canvasW = -1;
            int canvasH = -1;
            IIOMetadata stream = reader.getStreamMetadata();
            if (stream != null) {
                Node screen = child(stream.getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
                canvasW = intAttr(screen, "logicalScreenWidth", -1);
                canvasH = intAttr(screen, "logicalScreenHeight", -1);
            }
            List<BufferedImage> frames = new ArrayList<>();
            List<Integer> delays = new ArrayList<>();
            BufferedImage canvas = null;
            for (int i = 0; i < MAX_FRAMES; i++) {
                BufferedImage frame;
                IIOMetadata meta;
                try {
                    frame = reader.read(i);
                    meta = reader.getImageMetadata(i);
                } catch (IndexOutOfBoundsException end) {
                    break;
                } catch (IOException | RuntimeException broken) {
                    // The JDK GIF reader throws on some truncated or slightly malformed files
                    // that browsers show anyway. Keep the frames already read, if any.
                    if (frames.isEmpty()) throw broken;
                    break;
                }
                Node root = meta.getAsTree("javax_imageio_gif_image_1.0");
                Node desc = child(root, "ImageDescriptor");
                Node gce = child(root, "GraphicControlExtension");
                int left = intAttr(desc, "imageLeftPosition", 0);
                int top = intAttr(desc, "imageTopPosition", 0);
                int delayCs = intAttr(gce, "delayTime", 0);
                String disposal = gce == null ? "none" : attr(gce, "disposalMethod", "none");

                if (canvas == null) {
                    int w = canvasW > 0 ? canvasW : left + frame.getWidth();
                    int h = canvasH > 0 ? canvasH : top + frame.getHeight();
                    canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                }
                BufferedImage before = "restoreToPrevious".equals(disposal) ? copy(canvas) : null;
                Graphics2D g = canvas.createGraphics();
                g.drawImage(frame, left, top, null);
                g.dispose();
                frames.add(copy(canvas));
                delays.add(delayCs <= 1 ? DEFAULT_DELAY_MS : delayCs * 10);

                if ("restoreToBackgroundColor".equals(disposal)) {
                    clear(canvas, left, top, frame.getWidth(), frame.getHeight());
                } else if (before != null) {
                    canvas = before;
                }
            }
            if (frames.isEmpty()) {
                throw new PictureException("The GIF has no frames.");
            }
            return animated(frames, delays);
        } finally {
            reader.dispose();
        }
    }

    // --- WebP ---

    private static Decoded decodeWebp(byte[] data) throws IOException, PictureException {
        ImageReader reader = new WebPImageReaderSpi().createReaderInstance(null);
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(in, false, false);
            WebPAnimation animation = WebPAnimation.parse(data);
            if (animation == null || animation.frames.size() <= 1) {
                return single(reader.read(0));
            }
            BufferedImage canvas = new BufferedImage(animation.canvasWidth, animation.canvasHeight,
                BufferedImage.TYPE_INT_ARGB);
            List<BufferedImage> frames = new ArrayList<>();
            List<Integer> delays = new ArrayList<>();
            int count = Math.min(animation.frames.size(), MAX_FRAMES);
            for (int i = 0; i < count; i++) {
                WebPAnimation.Frame f = animation.frames.get(i);
                BufferedImage img = reader.read(i);
                if (img.getWidth() == canvas.getWidth() && img.getHeight() == canvas.getHeight()
                    && (f.width != canvas.getWidth() || f.height != canvas.getHeight())) {
                    // The reader handed back an already-composited canvas; use it as is.
                    canvas = toArgb(img);
                } else {
                    Graphics2D g = canvas.createGraphics();
                    if (!f.blend) {
                        g.setComposite(AlphaComposite.Src);
                    }
                    g.drawImage(img, f.x, f.y, null);
                    g.dispose();
                }
                frames.add(copy(canvas));
                delays.add(f.durationMs <= 10 ? DEFAULT_DELAY_MS : f.durationMs);
                if (f.dispose) {
                    clear(canvas, f.x, f.y, f.width, f.height);
                }
            }
            return animated(frames, delays);
        } finally {
            reader.dispose();
        }
    }

    // --- Limits ---

    /**
     * Scales the result down until it fits every limit. Frames past {@link #MAX_FRAMES} were
     * already dropped while decoding.
     */
    static Decoded limit(Decoded d) {
        int maxEdge = d.isAnimated() ? MAX_ANIMATED_EDGE : MAX_EDGE;
        double scale = Math.min(1.0, (double) maxEdge / Math.max(d.width, d.height));
        long totalPixels = (long) d.width * d.height * d.frames.size();
        if (totalPixels * scale * scale > MAX_TOTAL_PIXELS) {
            scale = Math.sqrt((double) MAX_TOTAL_PIXELS / totalPixels);
        }
        if (scale >= 1.0) {
            return d;
        }
        int w = Math.max(1, (int) Math.round(d.width * scale));
        int h = Math.max(1, (int) Math.round(d.height * scale));
        List<BufferedImage> scaled = new ArrayList<>(d.frames.size());
        for (BufferedImage frame : d.frames) {
            scaled.add(resize(frame, w, h));
        }
        return new Decoded(scaled, d.delaysMs);
    }

    /** Halves repeatedly, then a final bilinear step: much less aliasing than one big jump. */
    static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage current = src;
        int cw = src.getWidth();
        int ch = src.getHeight();
        while (cw / 2 >= w && ch / 2 >= h) {
            cw /= 2;
            ch /= 2;
            current = draw(current, cw, ch);
        }
        return cw == w && ch == h ? current : draw(current, w, h);
    }

    private static BufferedImage draw(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    // --- Helpers ---

    private static Decoded animated(List<BufferedImage> frames, List<Integer> delays) {
        if (frames.size() == 1) {
            return new Decoded(frames, new int[] {0});
        }
        int[] d = new int[delays.size()];
        for (int i = 0; i < d.length; i++) d[i] = delays.get(i);
        return new Decoded(frames, d);
    }

    static BufferedImage toArgb(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_ARGB) return img;
        return copy(img);
    }

    private static BufferedImage copy(BufferedImage img) {
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return out;
    }

    private static void clear(BufferedImage canvas, int x, int y, int w, int h) {
        Graphics2D g = canvas.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(x, y, w, h);
        g.dispose();
    }

    private static Node child(Node parent, String name) {
        if (parent == null) return null;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (name.equals(n.getNodeName())) return n;
        }
        return null;
    }

    private static String attr(Node node, String name, String fallback) {
        if (node == null || node.getAttributes() == null) return fallback;
        Node a = node.getAttributes().getNamedItem(name);
        return a == null ? fallback : a.getNodeValue();
    }

    private static int intAttr(Node node, String name, int fallback) {
        try {
            return Integer.parseInt(attr(node, name, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
