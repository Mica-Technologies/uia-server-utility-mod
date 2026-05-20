package com.micatechnologies.minecraft.sum.huds;

import java.text.SimpleDateFormat;
import java.util.Date;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * Substitutes {@code %placeholder%} tokens inside an arbitrary string with live client-side
 * values. Used by {@link CustomTextHud} so users can author free-form HUD strings like
 * {@code "%player% @ %x%, %z%"} without having to compose multiple HUDs.
 *
 * <p>Unknown placeholders are left intact so the user gets visible feedback that they
 * typoed a name. Lookups requiring an unavailable resource (no player, no server) emit
 * {@code "—"} for that token specifically — the rest of the string still renders.</p>
 *
 * <p>Supported tokens (case-insensitive):</p>
 * <ul>
 *   <li>{@code %player%} — display name of the local player</li>
 *   <li>{@code %x%}, {@code %y%}, {@code %z%} — floored integer coords</li>
 *   <li>{@code %dim%} — dimension type registry name (e.g. "overworld", "the_nether")</li>
 *   <li>{@code %biome%} — biome at the player's feet</li>
 *   <li>{@code %server%} — server IP, "Singleplayer", or "LAN"</li>
 *   <li>{@code %time%} — in-game time 24h ({@code HH:MM})</li>
 *   <li>{@code %realtime%} — wall-clock time 24h ({@code HH:MM})</li>
 *   <li>{@code %date%} — wall-clock date ({@code YYYY-MM-DD})</li>
 *   <li>{@code %fps%} — current Minecraft FPS</li>
 *   <li>{@code %direction%} — cardinal direction (N/E/S/W)</li>
 * </ul>
 */
public final class TextPlaceholders {

    private static final SimpleDateFormat REAL_TIME = new SimpleDateFormat("HH:mm");
    private static final SimpleDateFormat REAL_DATE = new SimpleDateFormat("yyyy-MM-dd");

    private TextPlaceholders() {}

    public static String apply(String input) {
        if (input == null || input.isEmpty() || input.indexOf('%') < 0) {
            return input == null ? "" : input;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        World world = mc.world;

        StringBuilder out = new StringBuilder(input.length() + 16);
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c != '%') {
                out.append(c);
                i++;
                continue;
            }
            int end = input.indexOf('%', i + 1);
            if (end < 0) {
                // Trailing unmatched '%' — emit literally and stop scanning.
                out.append(input, i, input.length());
                break;
            }
            String token = input.substring(i + 1, end).toLowerCase();
            String value = resolve(token, mc, player, world);
            if (value == null) {
                // Unknown token — leave the %name% intact so the user notices a typo.
                out.append(input, i, end + 1);
            } else {
                out.append(value);
            }
            i = end + 1;
        }
        return out.toString();
    }

    private static String resolve(String token, Minecraft mc, EntityPlayer player, World world) {
        switch (token) {
            case "player":
                return player != null ? player.getDisplayNameString() : "—";
            case "x":
                return player != null ? Integer.toString(MathHelper.floor(player.posX)) : "—";
            case "y":
                return player != null ? Integer.toString(MathHelper.floor(player.posY)) : "—";
            case "z":
                return player != null ? Integer.toString(MathHelper.floor(player.posZ)) : "—";
            case "dim":
                if (world == null || world.provider == null) return "—";
                return world.provider.getDimensionType().getName();
            case "biome":
                if (world == null || player == null) return "—";
                Biome biome = world.getBiome(player.getPosition());
                return biome != null ? biome.getBiomeName() : "—";
            case "server":
                if (mc.isSingleplayer()) return "Singleplayer";
                ServerData data = mc.getCurrentServerData();
                if (data == null) return "—";
                if (data.isOnLAN()) return "LAN";
                return data.serverIP != null ? data.serverIP : "—";
            case "time":
                if (world == null) return "—";
                long tod = world.getWorldTime() % 24000L;
                long minutesIntoDay = ((tod + 6000L) % 24000L) * 60L / 1000L;
                long hh = minutesIntoDay / 60L;
                long mm = minutesIntoDay % 60L;
                return String.format("%02d:%02d", hh, mm);
            case "realtime":
                synchronized (REAL_TIME) {
                    return REAL_TIME.format(new Date());
                }
            case "date":
                synchronized (REAL_DATE) {
                    return REAL_DATE.format(new Date());
                }
            case "fps":
                return Integer.toString(Minecraft.getDebugFPS());
            case "direction":
                if (player == null) return "—";
                int facing = MathHelper.floor((player.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
                switch (facing) {
                    case 0: return "S";
                    case 1: return "W";
                    case 2: return "N";
                    case 3: return "E";
                    default: return "—";
                }
            default:
                return null;
        }
    }

    /** For the example/preview rendering inside the OneConfig editor — uses stable made-up
     *  values so the layout doesn't twitch when previewing. */
    public static String applyExample(String input) {
        if (input == null || input.isEmpty() || input.indexOf('%') < 0) {
            return input == null ? "" : input;
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c != '%') {
                out.append(c);
                i++;
                continue;
            }
            int end = input.indexOf('%', i + 1);
            if (end < 0) { out.append(input, i, input.length()); break; }
            String token = input.substring(i + 1, end).toLowerCase();
            String value = exampleValue(token);
            if (value == null) {
                out.append(input, i, end + 1);
            } else {
                out.append(value);
            }
            i = end + 1;
        }
        return out.toString();
    }

    private static String exampleValue(String token) {
        switch (token) {
            case "player":    return "Steve";
            case "x":         return "100";
            case "y":         return "64";
            case "z":         return "-250";
            case "dim":       return "overworld";
            case "biome":     return "Plains";
            case "server":    return "play.example.com";
            case "time":      return "12:00";
            case "realtime":  return "14:30";
            case "date":      return "2026-05-20";
            case "fps":       return "120";
            case "direction": return "N";
            default:          return null;
        }
    }
}
