package com.micatechnologies.minecraft.sum.picture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.sum.picture.PictureDecoder.Decoded;
import com.micatechnologies.minecraft.sum.picture.PictureDecoder.PictureException;
import com.micatechnologies.minecraft.sum.picture.PictureFrameSettings.Fit;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pure-logic tests for picture frames: URL rules, settings limits and persistence, image
 * decoding for every supported format, and the disk cache.
 */
class PictureFrameTest {

    // 1x1 WebP samples, as used by browser feature-detection scripts.
    private static final String WEBP_LOSSLESS = "UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==";
    private static final String WEBP_LOSSY = "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA";
    private static final String WEBP_ANIMATED = "UklGRlIAAABXRUJQVlA4WAoAAAASAAAAAAAAAAAAQU5JTQYAAAD/////"
        + "AABBTk1GJgAAAAAAAAAAAAAAAAAAAGQAAABWUDhMDQAAAC8AAAAQBxAREYiI/gcA";

    // --- URL rules ---

    @Test
    void emptyUrlClearsTheFrame() {
        assertNull(PictureUrls.validate("", Collections.emptyList()));
    }

    @Test
    void onlyHttpAndHttpsAreAllowed() {
        assertNull(PictureUrls.validate("https://i.imgur.com/a.png", Collections.emptyList()));
        assertNull(PictureUrls.validate("http://example.com/a.png", Collections.emptyList()));
        assertNotNull(PictureUrls.validate("file:///C:/secret.png", Collections.emptyList()));
        assertNotNull(PictureUrls.validate("ftp://example.com/a.png", Collections.emptyList()));
        assertNotNull(PictureUrls.validate("not a url", Collections.emptyList()));
        assertNotNull(PictureUrls.validate("https:///nohost.png", Collections.emptyList()));
    }

    @Test
    void credentialsInUrlsAreRefused() {
        assertNotNull(PictureUrls.validate("https://user:pw@example.com/a.png", Collections.emptyList()));
    }

    @Test
    void overlongUrlsAreRefused() {
        char[] longPath = new char[PictureFrameSettings.MAX_URL_LENGTH];
        Arrays.fill(longPath, 'a');
        assertNotNull(PictureUrls.validate("https://e.com/" + new String(longPath), Collections.emptyList()));
    }

    @Test
    void allowedHostsIncludeSubdomainsButNotLookalikes() {
        List<String> allowed = Arrays.asList("imgur.com", "*.discordapp.net");
        assertTrue(PictureUrls.isHostAllowed("imgur.com", allowed));
        assertTrue(PictureUrls.isHostAllowed("i.IMGUR.com", allowed));
        assertTrue(PictureUrls.isHostAllowed("media.discordapp.net", allowed));
        assertTrue(PictureUrls.isHostAllowed("discordapp.net", allowed));
        assertFalse(PictureUrls.isHostAllowed("notimgur.com", allowed));
        assertFalse(PictureUrls.isHostAllowed("imgur.com.evil.net", allowed));
        assertNotNull(PictureUrls.validate("https://evil.net/a.png", allowed));
    }

    @Test
    void privateAndLocalAddressesAreNotPublic() throws Exception {
        for (String local : new String[] {"127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1",
            "169.254.1.1", "0.0.0.0", "100.64.0.1", "224.0.0.1", "::1", "fe80::1", "fd00::1",
            "::ffff:192.168.1.1"}) {
            assertFalse(PictureUrls.isPublicAddress(InetAddress.getByName(local)), local);
        }
        for (String pub : new String[] {"8.8.8.8", "1.1.1.1", "100.128.0.1", "2606:4700::1111"}) {
            assertTrue(PictureUrls.isPublicAddress(InetAddress.getByName(pub)), pub);
        }
    }

    // --- Settings ---

    @Test
    void clampHoldsEveryFieldToTheServerLimits() {
        PictureFrameSettings wild = new PictureFrameSettings("https://e.com/a.png", 999F, 0.01F,
            -500F, 500F, 7, Fit.FILL, true, 3F, 99999);
        PictureFrameSettings c = wild.clamp(16, 128);
        assertEquals(16F, c.getWidth());
        assertEquals(PictureFrameSettings.MIN_SIZE, c.getHeight());
        assertEquals(-16F, c.getOffsetX());
        assertEquals(16F, c.getOffsetY());
        assertEquals(3, c.getRotation(), "rotation wraps into 0-3");
        assertEquals(1F, c.getOpacity());
        assertEquals(128, c.getRenderDistance());
    }

    @Test
    void clampReplacesNanAndInfinity() {
        PictureFrameSettings bad = new PictureFrameSettings("", Float.NaN, Float.POSITIVE_INFINITY,
            Float.NaN, Float.NEGATIVE_INFINITY, 0, Fit.FIT, false, Float.NaN, 64);
        PictureFrameSettings c = bad.clamp(16, 128);
        assertEquals(1F, c.getWidth());
        assertEquals(1F, c.getHeight());
        assertEquals(0F, c.getOffsetX());
        assertEquals(0F, c.getOffsetY());
        assertEquals(1F, c.getOpacity());
    }

    @Test
    void settingsRoundTripThroughNbtAndBytes() {
        PictureFrameSettings s = new PictureFrameSettings("https://e.com/x.webp", 3.5F, 2F, -1F, 0.5F,
            1, Fit.STRETCH, true, 0.75F, 96);
        assertEquals(s, PictureFrameSettings.readFromNBT(s.writeToNBT(new NBTTagCompound())));
        ByteBuf buf = Unpooled.buffer();
        s.writeTo(buf);
        assertEquals(s, PictureFrameSettings.readFrom(buf));
    }

    @Test
    void missingNbtMeansAnEmptyFrame() {
        assertEquals(PictureFrameSettings.EMPTY, PictureFrameSettings.readFromNBT(new NBTTagCompound()));
    }

    // --- Decoding ---

    @Test
    void formatIsSniffedFromContentNotName() {
        assertEquals(PictureDecoder.Format.HTML,
            PictureDecoder.sniff("<!DOCTYPE html><html>".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(PictureDecoder.Format.SVG,
            PictureDecoder.sniff("<?xml version=\"1.0\"?><svg xmlns=".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(PictureDecoder.Format.WEBP, PictureDecoder.sniff(Base64.getDecoder().decode(WEBP_LOSSY)));
        assertEquals(PictureDecoder.Format.UNKNOWN, PictureDecoder.sniff(new byte[] {1, 2, 3}));
    }

    @Test
    void webPagesAndSvgGetAHelpfulError() {
        PictureException page = assertThrows(PictureException.class,
            () -> PictureDecoder.decode("<html><head></head></html>".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(page.getMessage().contains("web page"));
        PictureException svg = assertThrows(PictureException.class,
            () -> PictureDecoder.decode("<svg></svg>".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(svg.getMessage().contains("SVG"));
    }

    @Test
    void decodesPngAndJpeg() throws Exception {
        BufferedImage img = solid(40, 20, Color.RED);
        for (String format : new String[] {"png", "jpg", "bmp"}) {
            Decoded d = PictureDecoder.decode(encode(img, format));
            assertEquals(40, d.width, format);
            assertEquals(20, d.height, format);
            assertFalse(d.isAnimated(), format);
        }
    }

    @Test
    void decodesEveryKindOfWebp() throws Exception {
        for (String sample : new String[] {WEBP_LOSSLESS, WEBP_LOSSY, WEBP_ANIMATED}) {
            Decoded d = PictureDecoder.decode(Base64.getDecoder().decode(sample));
            assertEquals(1, d.width);
            assertEquals(1, d.height);
        }
    }

    @Test
    void readsWebpAnimationChunks() {
        WebPAnimation anim = WebPAnimation.parse(Base64.getDecoder().decode(WEBP_ANIMATED));
        assertNotNull(anim);
        assertEquals(1, anim.canvasWidth);
        assertEquals(1, anim.canvasHeight);
        assertEquals(1, anim.frames.size());
        assertEquals(100, anim.frames.get(0).durationMs);
        assertNull(WebPAnimation.parse(Base64.getDecoder().decode(WEBP_LOSSY)), "a still has no animation");
    }

    @Test
    void animatedGifFramesAreCompositedWithTheirDelays() throws Exception {
        // Frame 1 fills the canvas red; frame 2 is a small blue patch drawn over it. With
        // "doNotDispose" the second output frame must still show red around the patch.
        BufferedImage full = solid(8, 8, Color.RED);
        BufferedImage patch = solid(2, 2, Color.BLUE);
        byte[] gif = gif(new BufferedImage[] {full, patch}, new int[] {0, 3}, new int[] {0, 3},
            new int[] {5, 0}, "doNotDispose");
        Decoded d = PictureDecoder.decode(gif);
        assertTrue(d.isAnimated());
        assertEquals(8, d.width);
        assertArrayEquals(new int[] {50, PictureDecoder.DEFAULT_DELAY_MS}, d.delaysMs,
            "5 cs is 50 ms; 0 cs falls back to the browser default");
        BufferedImage second = d.frames.get(1);
        assertEquals(Color.BLUE.getRGB(), second.getRGB(3, 3));
        assertEquals(Color.RED.getRGB(), second.getRGB(0, 0));
    }

    @Test
    void oversizedImagesAreScaledDownKeepingTheirShape() {
        BufferedImage huge = new BufferedImage(PictureDecoder.MAX_EDGE * 2, PictureDecoder.MAX_EDGE, BufferedImage.TYPE_INT_ARGB);
        Decoded d = PictureDecoder.limit(new Decoded(Collections.singletonList(huge), new int[] {0}));
        assertEquals(PictureDecoder.MAX_EDGE, d.width);
        assertEquals(PictureDecoder.MAX_EDGE / 2, d.height);
    }

    // --- Disk cache ---

    @Test
    void diskCacheStoresRevalidatesAndPrunes(@TempDir File dir) throws IOException {
        PictureDiskCache cache = new PictureDiskCache(dir);
        assertNull(cache.read("https://e.com/a.png"));

        cache.write("https://e.com/a.png", new byte[100], "\"v1\"", "Mon, 01 Jan 2026 00:00:00 GMT");
        PictureDiskCache.Entry a = cache.read("https://e.com/a.png");
        assertNotNull(a);
        assertEquals(100, a.bytes.length);
        assertEquals("\"v1\"", a.etag);
        assertTrue(a.isFresh(System.currentTimeMillis(), 60_000L));
        assertFalse(a.isFresh(a.fetchedAtMillis + 60_000L, 60_000L));

        cache.markFresh("https://e.com/a.png", null, null);
        assertEquals("\"v1\"", cache.read("https://e.com/a.png").etag, "a 304 keeps the old validators");

        cache.write("https://e.com/b.png", new byte[100], null, null);
        // Make a the least recently used, then cap the cache at one entry's worth.
        assertTrue(new File(dir, PictureDiskCache.hash("https://e.com/a.png") + ".img").setLastModified(1000L));
        cache.prune(150);
        assertNull(cache.read("https://e.com/a.png"), "oldest entry pruned first");
        assertNotNull(cache.read("https://e.com/b.png"));

        cache.clear();
        assertNull(cache.read("https://e.com/b.png"));
        assertEquals(0, Files.list(dir.toPath()).count());
    }

    // --- Helpers ---

    private static BufferedImage solid(int w, int h, Color color) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static byte[] encode(BufferedImage img, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(img, format, out), format);
        return out.toByteArray();
    }

    /** Writes a GIF with per-frame positions, delays (centiseconds) and one disposal method. */
    private static byte[] gif(BufferedImage[] frames, int[] lefts, int[] tops, int[] delaysCs,
                              String disposal) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < frames.length; i++) {
                ImageWriteParam param = writer.getDefaultWriteParam();
                IIOMetadata meta = writer.getDefaultImageMetadata(
                    ImageTypeSpecifier.createFromRenderedImage(frames[i]), param);
                String fmt = meta.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(fmt);
                IIOMetadataNode gce = (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0);
                gce.setAttribute("disposalMethod", disposal);
                gce.setAttribute("delayTime", String.valueOf(delaysCs[i]));
                IIOMetadataNode desc = (IIOMetadataNode) root.getElementsByTagName("ImageDescriptor").item(0);
                desc.setAttribute("imageLeftPosition", String.valueOf(lefts[i]));
                desc.setAttribute("imageTopPosition", String.valueOf(tops[i]));
                meta.setFromTree(fmt, root);
                writer.writeToSequence(new IIOImage(frames[i], null, meta), param);
            }
            writer.endWriteSequence();
        }
        writer.dispose();
        return bytes.toByteArray();
    }
}
