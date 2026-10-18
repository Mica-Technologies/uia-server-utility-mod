package com.micatechnologies.minecraft.sum.picture;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.picture.PictureDecoder.Decoded;
import com.micatechnologies.minecraft.sum.picture.PictureDecoder.PictureException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * Every picture this client is showing or has recently shown, as GPU textures, keyed by URL.
 *
 * <p>Two cache levels. In memory, a URL is downloaded, decoded and uploaded once however many
 * frames show it, and its textures are kept while anything draws it. On disk,
 * {@link PictureDiskCache} keeps the downloaded bytes across restarts, re-checking them with the
 * host after the configured number of days.
 *
 * <p>Threads: downloads and decoding run on two background workers; everything that touches
 * OpenGL or this map runs on the client thread. Finished decodes come back through a queue and
 * are uploaded in {@link #onClientTick} under {@link #UPLOAD_PIXEL_BUDGET} pixels a tick, so a
 * wall of new pictures appears over a few ticks instead of freezing the game for one.
 *
 * <p>Eviction: textures not drawn for {@link #IDLE_EVICT_MS} are freed, and if the estimated
 * texture memory passes {@link #GPU_BUDGET_BYTES}, the least recently drawn pictures go first.
 * A picture that failed is retried after {@link #RETRY_FAILED_MS}.
 */
@SideOnly(Side.CLIENT)
public final class PictureCache {

    public static final PictureCache INSTANCE = new PictureCache();

    static final long IDLE_EVICT_MS = 60_000L;
    static final long RETRY_FAILED_MS = 5 * 60_000L;
    static final long GPU_BUDGET_BYTES = 768L * 1024 * 1024;
    static final long UPLOAD_PIXEL_BUDGET = 4L * 1024 * 1024;

    public enum Status { LOADING, READY, FAILED }

    /** One URL's state, read by the renderer. */
    public static final class Picture {
        private final String url;
        private Status status = Status.LOADING;
        private String error = "";
        private int width;
        private int height;
        private int[] textures = new int[0];
        private int[] delaysMs = new int[0];
        private int totalMs;
        private long estimatedBytes;
        private long lastUsed;
        private long failedAt;

        Picture(String url) { this.url = url; }

        public Status getStatus() { return status; }
        public String getError() { return error; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }

        /** The texture to draw now, stepping through an animation by wall-clock time. */
        public int textureAt(long nowMillis) {
            if (textures.length == 1 || totalMs <= 0) return textures[0];
            long t = nowMillis % totalMs;
            for (int i = 0; i < delaysMs.length; i++) {
                t -= delaysMs[i];
                if (t < 0) return textures[i];
            }
            return textures[textures.length - 1];
        }
    }

    /** A worker's result, waiting for the client thread. */
    private static final class Finished {
        final Picture picture;
        final Decoded decoded;
        final String error;
        int nextFrame;
        int[] uploaded;

        Finished(Picture picture, Decoded decoded, String error) {
            this.picture = picture;
            this.decoded = decoded;
            this.error = error;
        }
    }

    private final Map<String, Picture> pictures = new HashMap<>();
    private final ConcurrentLinkedQueue<Finished> finished = new ConcurrentLinkedQueue<>();
    private Finished uploading;
    private final ExecutorService workers;
    private volatile PictureDiskCache disk;

    private PictureCache() {
        AtomicInteger n = new AtomicInteger();
        workers = new ThreadPoolExecutor(2, 2, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "SUM picture loader " + n.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY + 1);
            return t;
        });
        ((ThreadPoolExecutor) workers).allowCoreThreadTimeOut(true);
    }

    private PictureDiskCache disk() {
        if (disk == null) {
            disk = new PictureDiskCache(new File(Minecraft.getMinecraft().gameDir, "sum/picture-cache"));
        }
        return disk;
    }

    /** The picture for a URL, starting its load the first time it is asked for. Client thread. */
    public Picture get(String url) {
        long now = Minecraft.getSystemTime();
        Picture p = pictures.get(url);
        if (p == null) {
            p = new Picture(url);
            pictures.put(url, p);
            submit(p);
        } else if (p.status == Status.FAILED && now - p.failedAt > RETRY_FAILED_MS) {
            p.status = Status.LOADING;
            submit(p);
        }
        p.lastUsed = now;
        return p;
    }

    /** Frees every texture and empties the disk cache. Client thread. */
    public void clearAll() {
        for (Picture p : pictures.values()) {
            deleteTextures(p);
        }
        pictures.clear();
        if (uploading != null && uploading.uploaded != null) {
            for (int i = 0; i < uploading.nextFrame; i++) TextureUtil.deleteTexture(uploading.uploaded[i]);
        }
        uploading = null;
        finished.clear();
        PictureDiskCache d = disk();
        workers.execute(d::clear);
    }

    private void submit(Picture p) {
        String url = p.url;
        long maxAge = PictureClientSettings.diskCacheMaxAgeMillis();
        long maxBytes = PictureClientSettings.diskCacheBytes();
        PictureDiskCache d = disk();
        workers.execute(() -> {
            try {
                finished.add(new Finished(p, PictureDecoder.decode(load(d, url, maxAge, maxBytes)), null));
            } catch (PictureException e) {
                finished.add(new Finished(p, null, e.getMessage()));
            } catch (Throwable t) {
                Sum.LOGGER.warn("[picture-frame] loading {} failed", url, t);
                finished.add(new Finished(p, null, "Couldn't load the image (" + t.getClass().getSimpleName() + ")."));
            }
        });
    }

    /** Bytes for a URL: the disk copy if fresh, re-checked if stale, downloaded if missing. Worker thread. */
    private static byte[] load(PictureDiskCache disk, String url, long maxAge, long maxBytes) throws PictureException {
        PictureDiskCache.Entry cached = disk.read(url);
        if (cached != null && cached.isFresh(System.currentTimeMillis(), maxAge)) {
            return cached.bytes;
        }
        PictureDownloader.Response response;
        try {
            response = PictureDownloader.fetch(url,
                cached == null ? null : cached.etag, cached == null ? null : cached.lastModified);
        } catch (PictureException offline) {
            if (cached != null) {
                return cached.bytes;   // stale beats nothing when the host is down
            }
            throw offline;
        }
        if (response.notModified && cached != null) {
            disk.markFresh(url, response.etag, response.lastModified);
            return cached.bytes;
        }
        byte[] body = response.body;
        if (body == null) {
            throw new PictureException("The image host sent nothing.");
        }
        // Only keep what decodes, so a page or a broken file is not served from disk for a week.
        if (PictureDecoder.sniff(body) != PictureDecoder.Format.HTML
            && PictureDecoder.sniff(body) != PictureDecoder.Format.UNKNOWN) {
            try {
                disk.write(url, body, response.etag, response.lastModified);
                disk.prune(maxBytes);
            } catch (java.io.IOException e) {
                Sum.LOGGER.debug("[picture-frame] couldn't cache {} on disk: {}", url, e.toString());
            }
        }
        return body;
    }

    // --- Client thread housekeeping ---

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = Minecraft.getSystemTime();
        upload(now);
        evict(now);
    }

    private void upload(long now) {
        long budget = UPLOAD_PIXEL_BUDGET;
        while (budget > 0) {
            if (uploading == null) {
                uploading = finished.poll();
                if (uploading == null) return;
                if (pictures.get(uploading.picture.url) != uploading.picture) {
                    uploading = null;   // cleared or evicted while loading
                    continue;
                }
                if (uploading.decoded == null) {
                    fail(uploading.picture, uploading.error, now);
                    uploading = null;
                    continue;
                }
                uploading.uploaded = new int[uploading.decoded.frames.size()];
            }
            Finished f = uploading;
            BufferedImage frame = f.decoded.frames.get(f.nextFrame);
            f.uploaded[f.nextFrame] = uploadTexture(frame);
            f.nextFrame++;
            budget -= (long) frame.getWidth() * frame.getHeight();
            if (f.nextFrame == f.uploaded.length) {
                ready(f, now);
                uploading = null;
            }
        }
    }

    private static void fail(Picture p, String error, long now) {
        p.status = Status.FAILED;
        p.error = error == null ? "Couldn't load the image." : error;
        p.failedAt = now;
    }

    private static void ready(Finished f, long now) {
        Picture p = f.picture;
        deleteTextures(p);
        p.textures = f.uploaded;
        p.delaysMs = f.decoded.delaysMs;
        int total = 0;
        for (int d : p.delaysMs) total += d;
        p.totalMs = total;
        p.width = f.decoded.width;
        p.height = f.decoded.height;
        // RGBA, plus a third again for the mipmap chain.
        p.estimatedBytes = (long) p.width * p.height * 4L * p.textures.length * 4L / 3L;
        p.status = Status.READY;
        p.error = "";
        p.lastUsed = now;
    }

    private void evict(long now) {
        long total = 0;
        Iterator<Picture> it = pictures.values().iterator();
        while (it.hasNext()) {
            Picture p = it.next();
            if (now - p.lastUsed > IDLE_EVICT_MS) {
                deleteTextures(p);
                it.remove();
            } else {
                total += p.estimatedBytes;
            }
        }
        if (total <= GPU_BUDGET_BYTES) return;
        List<Picture> oldestFirst = new ArrayList<>(pictures.values());
        oldestFirst.sort((a, b) -> Long.compare(a.lastUsed, b.lastUsed));
        for (Picture p : oldestFirst) {
            if (total <= GPU_BUDGET_BYTES) break;
            // Something drawn this second is on screen; dropping it would just reload it.
            if (now - p.lastUsed < 1_000L) break;
            total -= p.estimatedBytes;
            deleteTextures(p);
            pictures.remove(p.url);
        }
    }

    private static void deleteTextures(Picture p) {
        for (int id : p.textures) TextureUtil.deleteTexture(id);
        p.textures = new int[0];
        p.estimatedBytes = 0;
    }

    /** Uploads one frame with a full mipmap chain where the driver can build one. */
    private static int uploadTexture(BufferedImage image) {
        int id = TextureUtil.glGenTextures();
        TextureUtil.uploadTextureImageAllocate(id, image, true, true);
        GlStateManager.bindTexture(id);
        ContextCapabilities caps = GLContext.getCapabilities();
        if (caps.OpenGL30 || caps.GL_ARB_framebuffer_object) {
            int levels = 32 - Integer.numberOfLeadingZeros(Math.max(image.getWidth(), image.getHeight())) - 1;
            // allocateTexture pins the texture to level 0; open the range up again first.
            GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, levels);
            GlStateManager.glTexParameterf(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, levels);
            if (caps.OpenGL30) {
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            } else {
                ARBFramebufferObject.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            }
            GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        }
        return id;
    }
}
