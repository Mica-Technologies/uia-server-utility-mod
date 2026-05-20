package com.micatechnologies.minecraft.sum.serverconfig;

import javax.annotation.Nullable;

/**
 * Client-side cache of the most recently received {@link ServerConfigSnapshot}.
 * Populated by {@link PacketSyncServerConfig.Handler} on the client main thread,
 * read by {@link GuiServerConfigViewer} and any future feature that wants to know
 * what server-side knobs are currently in effect.
 *
 * <p>Lifetime: the snapshot is set on login and stays until the next login (or
 * until the client disconnects and reconnects to a different server, in which
 * case the new server's snapshot overwrites the cache). Null before the first
 * packet arrives.</p>
 */
public final class ServerConfigMirror {

    @Nullable
    private static volatile ServerConfigSnapshot latest;

    @Nullable
    private static volatile Long receivedAtMs;

    private ServerConfigMirror() {}

    public static void receive(ServerConfigSnapshot snapshot) {
        latest = snapshot;
        receivedAtMs = System.currentTimeMillis();
    }

    @Nullable
    public static ServerConfigSnapshot latest() {
        return latest;
    }

    @Nullable
    public static Long receivedAtMs() {
        return receivedAtMs;
    }

    public static void clear() {
        latest = null;
        receivedAtMs = null;
    }
}
