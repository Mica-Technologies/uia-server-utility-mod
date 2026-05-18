package com.micatechnologies.minecraft.sum.huds;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

/**
 * Single client-side event subscriber that feeds the small counter-style HUDs (CPS,
 * lifetime clicks, placed blocks, current-session playtime). Registered once from the
 * client proxy; HUDs read its public counters every frame.
 *
 * <p>Counters live in static fields so a one-time registration is enough — HUD modules
 * are constructed by OneConfig and don't get to control their own lifecycle, so they
 * can't subscribe themselves.</p>
 */
public class HudStateTracker {

    /** Lifetime click count (left + right), persisted only in-memory for the session. */
    public static long lifetimeClicks = 0;
    /** Lifetime blocks-placed count. Increments on the use-block player-interact event so
     *  it covers vanilla blocks, modded blocks, and item-based placements (signs, etc.). */
    public static long lifetimePlacedBlocks = 0;
    /** Millis at which the current session started — for the playtime HUD. Set on first
     *  client tick after game launch. */
    public static long sessionStartMillis = 0L;

    /** Recent left-click tick timestamps (millis), used to compute clicks-per-second.
     *  Bounded to the last 1000ms so the size stays small even under fast-click load. */
    private static final Deque<Long> recentClicks = new ArrayDeque<>();

    public static long getClicksInLastSecond() {
        evict(System.currentTimeMillis());
        return recentClicks.size();
    }

    private static void evict(long now) {
        while (!recentClicks.isEmpty() && now - recentClicks.peekFirst() > 1000L) {
            recentClicks.pollFirst();
        }
    }

    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        // Read directly from LWJGL's mouse event state rather than calling
        // KeyBinding#isPressed (which consumes a press from the keybind queue and
        // conflicts with vanilla's own consumption). Only count fresh presses (state
        // == true on the down edge); button 0 = left, button 1 = right.
        if (!Mouse.getEventButtonState()) {
            return;
        }
        int button = Mouse.getEventButton();
        long now = System.currentTimeMillis();
        if (button == 0) {
            recentClicks.addLast(now);
            lifetimeClicks++;
        } else if (button == 1) {
            lifetimeClicks++;
        }
    }

    @SubscribeEvent
    public void onBlockPlace(PlayerInteractEvent.RightClickBlock event) {
        // Fires on both sides; restrict to the local client player so we don't double-
        // count on the integrated server.
        if (event.getEntityPlayer() != Minecraft.getMinecraft().player) {
            return;
        }
        if (event.getWorld() != null && event.getWorld().isRemote) {
            lifetimePlacedBlocks++;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // Reset session-start when the player joins a new world / session.
        if (sessionStartMillis == 0L && Minecraft.getMinecraft().player != null) {
            sessionStartMillis = System.currentTimeMillis();
            // One-shot fetch so PhoneNumberHud (and any future phone-cloud-driven HUDs)
            // have data without the user needing to open the phone GUI first. The server
            // replies with PhoneCloudSync which feeds GuiSumPhone.lastCloud.
            try {
                com.micatechnologies.minecraft.sum.phone.GuiSumPhone.requestCloudSync();
            } catch (Throwable ignored) {
                // Defensive: SUM might be running in a context without networking (eg.
                // a unit test or stripped dev env) — don't crash the tick loop over a
                // best-effort prefetch.
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // Reset session timer between sessions so the playtime HUD shows "this play
        // session" rather than "since the JVM started".
        sessionStartMillis = 0L;
    }
}
