package com.micatechnologies.minecraft.sum.picture;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.picture.PictureDecoder.PictureException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collections;
import javax.annotation.Nullable;

/**
 * Fetches a picture over HTTP(S), refusing anything that resolves to the viewer's own machine or
 * network (see {@link PictureUrls#isPublicAddress}), on every hop of a redirect chain.
 *
 * <p>Redirects are followed by hand, at most {@link #MAX_REDIRECTS}, because
 * {@code HttpURLConnection} would follow them without letting each hop be checked, and does not
 * follow an http-to-https hop at all. Bodies over {@link #MAX_BYTES} are abandoned mid-stream.
 *
 * <p>The address check resolves the host and then connects, which leaves a DNS-rebinding window
 * between the two. Closing it would mean connecting to the checked address directly and
 * re-implementing TLS host verification; for a picture frame the remaining risk does not justify
 * that, but it is the known gap.
 */
public final class PictureDownloader {

    static final int MAX_BYTES = 24 * 1024 * 1024;
    static final int MAX_REDIRECTS = 5;
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final String USER_AGENT =
        "SUM-PictureFrame/" + SumConstants.MOD_VERSION + " (Minecraft 1.12.2 mod)";

    private PictureDownloader() {}

    /** A response: 200 with a body, or 304 when the conditional request found the copy current. */
    public static final class Response {
        public final boolean notModified;
        @Nullable public final byte[] body;
        @Nullable public final String etag;
        @Nullable public final String lastModified;

        Response(boolean notModified, @Nullable byte[] body, @Nullable String etag, @Nullable String lastModified) {
            this.notModified = notModified;
            this.body = body;
            this.etag = etag;
            this.lastModified = lastModified;
        }
    }

    /**
     * @param etag         sent as {@code If-None-Match} when re-checking a cached copy
     * @param lastModified sent as {@code If-Modified-Since} when re-checking a cached copy
     */
    public static Response fetch(String url, @Nullable String etag, @Nullable String lastModified)
        throws PictureException {
        String current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            URL target = checkedUrl(current);
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) target.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setRequestProperty("Accept", "image/webp,image/png,image/jpeg,image/gif,image/*;q=0.8");
                if (etag != null) conn.setRequestProperty("If-None-Match", etag);
                if (lastModified != null) conn.setRequestProperty("If-Modified-Since", lastModified);
                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    return new Response(true, null, conn.getHeaderField("ETag"),
                        conn.getHeaderField("Last-Modified"));
                }
                if (code >= 300 && code < 400) {
                    String location = conn.getHeaderField("Location");
                    if (location == null) {
                        throw new PictureException("The image host sent a redirect with no destination.");
                    }
                    current = target.toURI().resolve(location).toString();
                    continue;
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    throw new PictureException("The image host answered HTTP " + code
                        + (code == 403 ? " (it may not allow embedding)" : code == 404 ? " (not found)" : "") + ".");
                }
                long declared = conn.getContentLengthLong();
                if (declared > MAX_BYTES) {
                    throw new PictureException(tooBig());
                }
                byte[] body = readCapped(conn.getInputStream());
                return new Response(false, body, conn.getHeaderField("ETag"), conn.getHeaderField("Last-Modified"));
            } catch (IOException | URISyntaxException e) {
                throw new PictureException("Couldn't download the image (" + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ").");
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        throw new PictureException("The image link redirects too many times.");
    }

    private static URL checkedUrl(String url) throws PictureException {
        String problem = PictureUrls.validate(url, Collections.emptyList());
        if (problem != null) {
            throw new PictureException(problem);
        }
        try {
            URI uri = new URI(url);
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            for (InetAddress address : addresses) {
                if (!PictureUrls.isPublicAddress(address)) {
                    throw new PictureException("Pictures can't be loaded from a local or private network address.");
                }
            }
            return uri.toURL();
        } catch (UnknownHostException e) {
            throw new PictureException("The image host " + e.getMessage() + " couldn't be found.");
        } catch (URISyntaxException | IOException | IllegalArgumentException e) {
            throw new PictureException("That isn't a valid link.");
        }
    }

    private static byte[] readCapped(InputStream in) throws IOException, PictureException {
        try (InputStream body = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = body.read(buf)) != -1) {
                if (out.size() + n > MAX_BYTES) {
                    throw new PictureException(tooBig());
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static String tooBig() {
        return "The image is over " + (MAX_BYTES / (1024 * 1024)) + " MB.";
    }
}
