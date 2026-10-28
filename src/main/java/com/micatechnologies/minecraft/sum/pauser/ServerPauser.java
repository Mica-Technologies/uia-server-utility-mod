package com.micatechnologies.minecraft.sum.pauser;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.afk.AfkTracker;
import net.minecraft.server.MinecraftServer;

/**
 * Decides whether the server pauser should freeze the server this tick. The decision is made
 * once, at the start of {@code MinecraftServer.tick()} ({@link #beginTick}), and every pauser
 * mixin reads that one answer for the rest of the tick, so no phase can run on a different
 * decision from the others.
 *
 * <p>Two levels:
 * <ul>
 *   <li><b>Deep pause</b> ({@link #isDeepPaused()}): nobody is online. {@code MixinMinecraftServer}
 *       skips the whole world loop, the {@code ServerTickEvent} / {@code WorldTickEvent} posts and
 *       autosaves, leaving only networking and scheduled tasks so the next login still gets in.
 *       This is what stops mods that tick from events rather than from entities or TileEntities
 *       (Immersive Vehicles' vehicles, IC2's energy net, FAWE's queue, ...).</li>
 *   <li><b>Soft pause</b> ({@link #isPaused()} without deep): every online player is AFK and
 *       {@code afk.pauseWorldWhenAllAfk} is on. The worlds freeze ({@code MixinWorldServer},
 *       {@code MixinEntityTracker}) but tick events keep firing: players are still connected, mods'
 *       packet queues need their ticks, and {@link AfkTracker}'s own sweep must run to notice a
 *       player coming back.</li>
 * </ul>
 *
 * <p>Transitions are logged once each way, so the server log shows the pauser engaging.
 *
 * <p>Only called from the server thread.
 */
public final class ServerPauser {

    private static boolean paused = false;
    private static boolean deepPaused = false;
    private static boolean loggedPaused = false;

    private ServerPauser() {
    }

    /**
     * Makes this tick's decision. Called at the very start of {@code MinecraftServer.tick()},
     * before the {@code ServerTickEvent} START post, so the whole tick sees one answer.
     *
     * @return true when this tick is the first of a deep pause, so the caller can save once
     */
    public static boolean beginTick(MinecraftServer server) {
        boolean wasDeep = deepPaused;
        if (server == null || server.getPlayerList() == null) {
            paused = false;
            deepPaused = false;
            return false;
        }

        int players = server.getPlayerList().getCurrentPlayerCount();
        String reason = null;
        boolean deep = false;
        if (SumConfig.isPauserEnabled()) {
            if (players <= 0) {
                reason = "0 players online";
                deep = true;
            } else if (SumConfig.isAfkPauseWorldWhenAllAfk() && AfkTracker.allOnlinePlayersAfk(server)) {
                reason = "all " + players + " online player(s) AFK";
            }
        }
        paused = reason != null;
        deepPaused = deep;

        if (paused != loggedPaused) {
            loggedPaused = paused;
            if (paused) {
                Sum.LOGGER.info("Pauser: world frozen ({}{})", reason, deep ? ", tick events suspended" : "");
            } else {
                Sum.LOGGER.info("Pauser: world resumed ({} player(s) online)", players);
            }
        }
        return deepPaused && !wasDeep;
    }

    /** True when the worlds are frozen this tick, at either level. */
    public static boolean isPaused() {
        return paused;
    }

    /** True when nobody is online and the whole server tick, tick events included, is suspended. */
    public static boolean isDeepPaused() {
        return deepPaused;
    }
}
