package com.micatechnologies.minecraft.sum.picture;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import javax.annotation.Nullable;

/**
 * Downloaded image bytes on disk, so a picture seen once loads without the network after a
 * restart, and a server full of frames is not re-downloaded by every player on every login.
 *
 * <p>One {@code <sha256 of url>.img} per URL, with a {@code .meta} beside it holding the URL,
 * when it was fetched, and the response's {@code ETag} / {@code Last-Modified} so a stale entry
 * can be re-checked with a cheap conditional request instead of a full download. The original
 * bytes are kept rather than decoded pixels: they are usually a tenth of the size.
 *
 * <p>Size-capped, least-recently-used first: reading an entry bumps its file time, and
 * {@link #prune} deletes the oldest until the total fits. Thread-safe; the download workers all
 * share one instance.
 */
public final class PictureDiskCache {

    /** One cached download. */
    public static final class Entry {
        public final byte[] bytes;
        public final long fetchedAtMillis;
        @Nullable public final String etag;
        @Nullable public final String lastModified;

        Entry(byte[] bytes, long fetchedAtMillis, @Nullable String etag, @Nullable String lastModified) {
            this.bytes = bytes;
            this.fetchedAtMillis = fetchedAtMillis;
            this.etag = etag;
            this.lastModified = lastModified;
        }

        public boolean isFresh(long nowMillis, long maxAgeMillis) {
            return nowMillis - fetchedAtMillis < maxAgeMillis;
        }
    }

    private final File dir;

    public PictureDiskCache(File dir) {
        this.dir = dir;
    }

    @Nullable
    public synchronized Entry read(String url) {
        File img = imageFile(url);
        File meta = metaFile(url);
        if (!img.isFile() || !meta.isFile()) {
            return null;
        }
        try {
            Properties p = loadMeta(meta);
            if (!url.equals(p.getProperty("url"))) {
                return null;   // a hash collision, or a hand-edited file: not ours
            }
            byte[] bytes = Files.readAllBytes(img.toPath());
            //noinspection ResultOfMethodCallIgnored
            img.setLastModified(System.currentTimeMillis());
            return new Entry(bytes, parseLong(p.getProperty("fetchedAt")), p.getProperty("etag"),
                p.getProperty("lastModified"));
        } catch (IOException e) {
            return null;
        }
    }

    public synchronized void write(String url, byte[] bytes, @Nullable String etag,
                                   @Nullable String lastModified) throws IOException {
        ensureDir();
        File tmp = new File(dir, hash(url) + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(bytes);
        }
        Files.move(tmp.toPath(), imageFile(url).toPath(), StandardCopyOption.REPLACE_EXISTING);
        writeMeta(url, System.currentTimeMillis(), etag, lastModified);
    }

    /** Records that the server confirmed the cached copy is current (an HTTP 304). */
    public synchronized void markFresh(String url, @Nullable String etag, @Nullable String lastModified) {
        Entry old = read(url);
        if (old == null) return;
        try {
            writeMeta(url, System.currentTimeMillis(), etag != null ? etag : old.etag,
                lastModified != null ? lastModified : old.lastModified);
        } catch (IOException ignored) {
            // Worst case it is re-checked again next time.
        }
    }

    public synchronized void delete(String url) {
        //noinspection ResultOfMethodCallIgnored
        imageFile(url).delete();
        //noinspection ResultOfMethodCallIgnored
        metaFile(url).delete();
    }

    /** Deletes least recently used entries until the cache holds at most {@code maxBytes}. */
    public synchronized void prune(long maxBytes) {
        File[] images = dir.listFiles((d, name) -> name.endsWith(".img"));
        if (images == null) return;
        long total = 0;
        for (File f : images) total += f.length();
        if (total <= maxBytes) return;
        List<File> oldestFirst = new ArrayList<>(Arrays.asList(images));
        oldestFirst.sort(Comparator.comparingLong(File::lastModified));
        for (File f : oldestFirst) {
            if (total <= maxBytes) break;
            total -= f.length();
            String base = f.getName().substring(0, f.getName().length() - 4);
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            //noinspection ResultOfMethodCallIgnored
            new File(dir, base + ".meta").delete();
        }
    }

    public synchronized void clear() {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String name = f.getName();
            if (name.endsWith(".img") || name.endsWith(".meta") || name.endsWith(".tmp")) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    // --- Files ---

    private void writeMeta(String url, long fetchedAt, @Nullable String etag,
                           @Nullable String lastModified) throws IOException {
        Properties p = new Properties();
        p.setProperty("url", url);
        p.setProperty("fetchedAt", Long.toString(fetchedAt));
        if (etag != null) p.setProperty("etag", etag);
        if (lastModified != null) p.setProperty("lastModified", lastModified);
        File tmp = new File(dir, hash(url) + ".meta.tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            p.store(out, null);
        }
        Files.move(tmp.toPath(), metaFile(url).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static Properties loadMeta(File meta) throws IOException {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(meta)) {
            p.load(in);
        }
        return p;
    }

    private void ensureDir() throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("can't create " + dir);
        }
    }

    private File imageFile(String url) { return new File(dir, hash(url) + ".img"); }
    private File metaFile(String url) { return new File(dir, hash(url) + ".meta"); }

    static String hash(String url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }

    private static long parseLong(@Nullable String s) {
        try {
            return s == null ? 0L : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
