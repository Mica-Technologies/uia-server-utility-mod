package com.micatechnologies.minecraft.sum.pauser;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.afk.AfkTracker;
import net.minecraft.server.MinecraftServer;

/**
 * Decides whether the server pauser should freeze the worlds this tick. One decision covers every
 * world and every frozen phase of a server tick ({@code WorldServer.tick()},
 * {@code WorldServer.updateEntities()} and {@code EntityTracker.tick()}), so a world can never
 * run its entities while its block/time tick is frozen, or the other way round.
 *
 * <p>The answer is cached per server tick: the mixins ask once per world per phase, and the
 * all-AFK check walks the player list. Transitions are logged once each way, so the server log
 * shows the pauser engaging.
 *
 * <p>Only called from the server thread.
 */
public final class ServerPauser {

    private static int cachedTick = Integer.MIN_VALUE;
    private static boolean cachedPaused = false;
    private static boolean loggedPaused = false;

    private ServerPauser() {
    }

    /**
     * Returns true when the worlds should be frozen this tick: the pauser is enabled and either
     * nobody is online, or {@code afk.pauseWorldWhenAllAfk} is on and every online player is AFK.
     */
    public static boolean isPaused(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) {
            return false;
        }
        int tick = server.getTickCounter();
        if (tick == cachedTick) {
            return cachedPaused;
        }
        cachedTick = tick;

        int players = server.getPlayerList().getCurrentPlayerCount();
        String reason = null;
        if (SumConfig.isPauserEnabled()) {
            if (players <= 0) {
                reason = "0 players online";
            } else if (SumConfig.isAfkPauseWorldWhenAllAfk() && AfkTracker.allOnlinePlayersAfk(server)) {
                reason = "all " + players + " online player(s) AFK";
            }
        }
        cachedPaused = reason != null;

        if (cachedPaused != loggedPaused) {
            loggedPaused = cachedPaused;
            if (cachedPaused) {
                Sum.LOGGER.info("Pauser: world frozen ({})", reason);
            } else {
                Sum.LOGGER.info("Pauser: world resumed ({} player(s) online)", players);
            }
        }
        return cachedPaused;
    }
}
