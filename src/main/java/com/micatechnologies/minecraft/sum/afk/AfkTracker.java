package com.micatechnologies.minecraft.sum.afk;

import com.micatechnologies.minecraft.sum.SumConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Tracks which online players are AFK (no movement, look change, or chat for a configurable
 * window) so other SUM features can react:
 *
 * <ul>
 *   <li>{@code SleepVoteHandler} drops AFK players from the sleep head count, so one idle player
 *       can't block the night skip.</li>
 *   <li>The server pauser ({@code MixinWorldServer}) can optionally freeze the world while every
 *       online player is AFK, not just when the server is empty.</li>
 * </ul>
 *
 * <p>Runs entirely server-side. All state mutation happens on the server thread (tick / chat /
 * logout events and the pauser mixin all run there), so a plain {@link HashMap} is safe.
 */
public final class AfkTracker {

    /** How often the sweep runs. 20 ticks = 1 real second. */
    private static final int CHECK_INTERVAL = 20;

    private int tickCounter = 0;

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static final class State {
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        long lastActiveMs;
        boolean afk;

        State(EntityPlayer player, long now) {
            snapshot(player, now);
        }

        void snapshot(EntityPlayer player, long now) {
            x = player.posX;
            y = player.posY;
            z = player.posZ;
            yaw = player.rotationYaw;
            pitch = player.rotationPitch;
            lastActiveMs = now;
        }

        boolean moved(EntityPlayer player) {
            return player.posX != x || player.posY != y || player.posZ != z
                || player.rotationYaw != yaw || player.rotationPitch != pitch;
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!SumConfig.isAfkEnabled()) {
            return;
        }
        if (++tickCounter < CHECK_INTERVAL) {
            return;
        }
        tickCounter = 0;

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long thresholdMs = SumConfig.getAfkThresholdSeconds() * 1000L;

        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            State state = STATES.get(player.getUniqueID());
            if (state == null) {
                STATES.put(player.getUniqueID(), new State(player, now));
                continue;
            }
            // Sleeping, moving, or looking around all count as active.
            if (player.isPlayerSleeping() || state.moved(player)) {
                if (state.afk) {
                    setAfk(player, state, false);
                }
                state.snapshot(player, now);
                continue;
            }
            if (!state.afk && now - state.lastActiveMs >= thresholdMs) {
                setAfk(player, state, true);
            }
        }
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        markActive(event.getPlayer());
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.player.getUniqueID());
    }

    /** Marks a player active right now, clearing AFK if set. Safe to call from server-thread events. */
    public static void markActive(EntityPlayer player) {
        if (player == null) {
            return;
        }
        State state = STATES.get(player.getUniqueID());
        if (state == null) {
            return;   // first sweep will create the entry; they're active by definition
        }
        if (state.afk && player instanceof EntityPlayerMP) {
            setAfk((EntityPlayerMP) player, state, false);
        }
        state.snapshot(player, System.currentTimeMillis());
    }

    public static boolean isAfk(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        State state = STATES.get(player.getUniqueID());
        return state != null && state.afk;
    }

    /** True when at least one player is online and every online player is currently AFK. */
    public static boolean allOnlinePlayersAfk(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) {
            return false;
        }
        List<EntityPlayerMP> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return false;
        }
        for (EntityPlayerMP player : players) {
            if (!isAfk(player)) {
                return false;
            }
        }
        return true;
    }

    private static void setAfk(EntityPlayerMP player, State state, boolean afk) {
        if (state.afk == afk) {
            return;
        }
        state.afk = afk;
        if (!SumConfig.isAfkAnnounce()) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        TextComponentString message = new TextComponentString(
            TextFormatting.GRAY + player.getName() + (afk ? " is now AFK." : " is no longer AFK."));
        for (EntityPlayerMP recipient : server.getPlayerList().getPlayers()) {
            recipient.sendMessage(message);
        }
    }
}
