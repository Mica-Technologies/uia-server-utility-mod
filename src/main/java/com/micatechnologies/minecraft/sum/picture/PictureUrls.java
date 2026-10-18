package com.micatechnologies.minecraft.sum.picture;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Rules for which URLs a picture frame may load.
 *
 * <p>Two checks, on two sides, against two different harms:
 * <ul>
 *   <li>{@link #validate} runs on the server when a frame is set. It is the operator's control:
 *       only http(s), and only from {@code picture_frame.allowedHosts} when that list is set.</li>
 *   <li>{@link #isPublicAddress} runs on every client before each request, redirects included.
 *       A frame makes every player who looks at it fetch its URL, so without this a player could
 *       hang a frame pointing at {@code http://192.168.1.1/...} and have every viewer's game send
 *       requests into that viewer's own home network. The server cannot make this check for
 *       them: it is the client's network that matters.</li>
 * </ul>
 */
public final class PictureUrls {

    private PictureUrls() {}

    /**
     * Checks a URL a player wants to put on a frame.
     *
     * @param allowedHosts lower-case host names; empty allows any host. An entry also allows its
     *                     subdomains, and a leading {@code *.} is accepted and ignored.
     * @return why the URL is refused, or {@code null} if it is fine. An empty URL is fine: it
     *         clears the frame.
     */
    @Nullable
    public static String validate(String url, Collection<String> allowedHosts) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        if (url.length() > PictureFrameSettings.MAX_URL_LENGTH) {
            return "That link is too long (over " + PictureFrameSettings.MAX_URL_LENGTH + " characters).";
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return "That isn't a valid link.";
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return "Only http:// and https:// links can be shown.";
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return "That link has no host name.";
        }
        if (uri.getRawUserInfo() != null) {
            return "Links with a user name or password in them can't be shown.";
        }
        if (!isHostAllowed(host, allowedHosts)) {
            return "This server only shows images from: " + String.join(", ", allowedHosts) + ".";
        }
        return null;
    }

    static boolean isHostAllowed(String host, Collection<String> allowedHosts) {
        if (allowedHosts.isEmpty()) {
            return true;
        }
        String h = host.toLowerCase(Locale.ROOT);
        if (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);
        }
        for (String entry : allowedHosts) {
            String allowed = entry.startsWith("*.") ? entry.substring(2) : entry;
            if (h.equals(allowed) || h.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * False for any address on the viewer's own machine or private network: loopback, link-local,
     * site-local (10/8, 172.16/12, 192.168/16), carrier-grade NAT (100.64/10), the unspecified
     * address, multicast, and IPv6 unique-local (fc00::/7).
     */
    public static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
            || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] raw = address.getAddress();
        if (address instanceof Inet6Address) {
            if ((raw[0] & 0xFE) == 0xFC) {
                return false;
            }
            // IPv4-mapped (::ffff:a.b.c.d): judge the IPv4 address inside it.
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                if (raw[i] != 0) {
                    mapped = false;
                    break;
                }
            }
            if (mapped && (raw[10] & 0xFF) == 0xFF && (raw[11] & 0xFF) == 0xFF) {
                try {
                    return isPublicAddress(InetAddress.getByAddress(
                        new byte[] {raw[12], raw[13], raw[14], raw[15]}));
                } catch (java.net.UnknownHostException impossible) {
                    return false;
                }
            }
            return true;
        }
        int first = raw[0] & 0xFF;
        int second = raw[1] & 0xFF;
        if (first == 0) {
            return false;
        }
        return !(first == 100 && second >= 64 && second <= 127);
    }
}
