package com.micatechnologies.minecraft.sum.pocket;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.micatechnologies.minecraft.sum.Sum;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Client-side configuration for the pocket HUD overlay: visibility, on-screen position,
 * and scale. Persisted as a small JSON file under {@code config/sum/pocket-hud.json} —
 * separate from the global {@code FavoritesStore} so each can evolve independently.
 *
 * <p>Anchoring uses screen percentages (0.0–1.0 along each axis) anchored on the slot
 * group's top-left corner, so the saved position survives window resizes the way the
 * EvergreenHUD layout works: small UI scale on a high-res monitor still puts the HUD
 * roughly where you placed it on the previous session.
 */
public final class PocketHudConfig {

    private static final Object LOCK = new Object();
    private static File storageFile;

    private static boolean enabled = true;
    /** Fractional X position of the HUD's top-left corner, 0.0 = left edge, 1.0 = right. */
    private static float anchorX = 0.5F;
    /** Fractional Y position of the HUD's top-left corner, 0.0 = top edge, 1.0 = bottom. */
    private static float anchorY = 1.0F;
    /** Multiplier on the base slot-row dimensions. 1.0 = same size as the hotbar. */
    private static float scale = 1.0F;

    private PocketHudConfig() {}

    /**
     * Set the config-dir root and immediately try to load. Called once from
     * {@code SumClientProxy.preInit}.
     */
    public static void setStorageFile(File configRoot) {
        synchronized (LOCK) {
            File sumDir = new File(configRoot, "sum");
            if (!sumDir.exists() && !sumDir.mkdirs()) {
                Sum.LOGGER.warn("Failed to create pocket-HUD storage directory: {}", sumDir);
            }
            storageFile = new File(sumDir, "pocket-hud.json");
        }
        load();
    }

    public static void load() {
        synchronized (LOCK) {
            if (storageFile == null || !storageFile.isFile()) {
                return;
            }
            try {
                String text = new String(Files.readAllBytes(storageFile.toPath()), StandardCharsets.UTF_8);
                if (text.isEmpty()) {
                    return;
                }
                JsonElement root = new JsonParser().parse(text);
                if (!root.isJsonObject()) {
                    Sum.LOGGER.warn("pocket-hud.json is not a JSON object; ignoring.");
                    return;
                }
                JsonObject obj = root.getAsJsonObject();
                enabled = readBool(obj, "enabled", enabled);
                anchorX = clamp01(readFloat(obj, "anchorX", anchorX));
                anchorY = clamp01(readFloat(obj, "anchorY", anchorY));
                scale = clampScale(readFloat(obj, "scale", scale));
            } catch (IOException e) {
                Sum.LOGGER.warn("Failed to read pocket-hud.json: {}", e.getMessage());
            } catch (RuntimeException e) {
                Sum.LOGGER.warn("pocket-hud.json is corrupt; ignoring: {}", e.getMessage());
            }
        }
    }

    public static void save() {
        synchronized (LOCK) {
            if (storageFile == null) {
                return;
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("enabled", enabled);
            obj.addProperty("anchorX", anchorX);
            obj.addProperty("anchorY", anchorY);
            obj.addProperty("scale", scale);
            String text = new GsonBuilder().setPrettyPrinting().create().toJson(obj);
            try {
                Path target = storageFile.toPath();
                Path tmp = target.resolveSibling(storageFile.getName() + ".tmp");
                Files.write(tmp, text.getBytes(StandardCharsets.UTF_8));
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException atomicFail) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                Sum.LOGGER.warn("Failed to save pocket-hud.json: {}", e.getMessage());
            }
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean v) {
        enabled = v;
    }

    public static float getAnchorX() {
        return anchorX;
    }

    public static float getAnchorY() {
        return anchorY;
    }

    public static float getScale() {
        return scale;
    }

    public static void setAnchor(float x, float y) {
        anchorX = clamp01(x);
        anchorY = clamp01(y);
    }

    public static void setScale(float s) {
        scale = clampScale(s);
    }

    private static float clamp01(float v) {
        if (v < 0F) return 0F;
        if (v > 1F) return 1F;
        return v;
    }

    private static float clampScale(float s) {
        if (s < 0.5F) return 0.5F;
        if (s > 2.0F) return 2.0F;
        return s;
    }

    private static boolean readBool(JsonObject obj, String key, boolean def) {
        return obj.has(key) && obj.get(key).isJsonPrimitive()
            ? obj.get(key).getAsBoolean() : def;
    }

    private static float readFloat(JsonObject obj, String key, float def) {
        return obj.has(key) && obj.get(key).isJsonPrimitive()
            ? obj.get(key).getAsFloat() : def;
    }
}
