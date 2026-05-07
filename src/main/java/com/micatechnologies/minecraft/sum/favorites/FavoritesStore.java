package com.micatechnologies.minecraft.sum.favorites;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class FavoritesStore {

    private static final int CURRENT_VERSION = 1;
    private static final int SOFT_WARN_THRESHOLD = 200;

    private static final Object LOCK = new Object();
    private static final ArrayList<FavoriteKey> ITEMS = new ArrayList<>();
    private static final HashSet<FavoriteKey> ITEM_SET = new HashSet<>();
    private static volatile int version;
    private static File storageFile;
    private static boolean loaded;
    private static boolean warnedOverSoftCap;

    private FavoritesStore() {}

    public static void setStorageFile(File configRoot) {
        synchronized (LOCK) {
            File sumDir = new File(configRoot, "sum");
            if (!sumDir.exists() && !sumDir.mkdirs()) {
                Sum.LOGGER.warn("Failed to create favorites storage directory: {}", sumDir);
            }
            storageFile = new File(sumDir, "favorites.json");
        }
    }

    public static File getStorageFile() {
        synchronized (LOCK) {
            return storageFile;
        }
    }

    public static void load() {
        synchronized (LOCK) {
            ITEMS.clear();
            ITEM_SET.clear();
            warnedOverSoftCap = false;
            version++;
            loaded = true;
            if (storageFile == null || !storageFile.isFile()) {
                return;
            }
            String text;
            try {
                text = new String(Files.readAllBytes(storageFile.toPath()), StandardCharsets.UTF_8);
            } catch (IOException e) {
                Sum.LOGGER.warn("Failed to read favorites file: {}", e.getMessage());
                return;
            }
            if (text.isEmpty()) {
                return;
            }
            try {
                JsonElement root = new JsonParser().parse(text);
                if (!root.isJsonObject()) {
                    Sum.LOGGER.warn("Favorites file is not a JSON object; backing up and starting fresh.");
                    backupCorrupt(text);
                    return;
                }
                JsonObject obj = root.getAsJsonObject();
                if (!obj.has("items") || !obj.get("items").isJsonArray()) {
                    return;
                }
                LinkedHashSet<FavoriteKey> seen = new LinkedHashSet<>();
                for (JsonElement el : obj.getAsJsonArray("items")) {
                    if (!el.isJsonPrimitive()) {
                        continue;
                    }
                    FavoriteKey key = FavoriteKey.parse(el.getAsString());
                    if (key == null) {
                        Sum.LOGGER.warn("Skipping malformed favorites entry: {}", el);
                        continue;
                    }
                    seen.add(key);
                }
                ITEMS.addAll(seen);
                ITEM_SET.addAll(seen);
                if (ITEMS.size() > SOFT_WARN_THRESHOLD) {
                    Sum.LOGGER.warn("Favorites list has {} entries, over the soft cap of {}.",
                        ITEMS.size(), SOFT_WARN_THRESHOLD);
                    warnedOverSoftCap = true;
                }
            } catch (RuntimeException e) {
                Sum.LOGGER.warn("Favorites file is corrupt: {}", e.getMessage());
                backupCorrupt(text);
            }
        }
    }

    public static void save() {
        synchronized (LOCK) {
            if (storageFile == null) {
                return;
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("version", CURRENT_VERSION);
            JsonArray arr = new JsonArray();
            for (FavoriteKey k : ITEMS) {
                arr.add(k.toString());
            }
            obj.add("items", arr);
            String text = new GsonBuilder().setPrettyPrinting().create().toJson(obj);
            try {
                Path target = storageFile.toPath();
                Path tmp = target.resolveSibling(storageFile.getName() + ".tmp");
                Files.write(tmp, text.getBytes(StandardCharsets.UTF_8));
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException atomicFail) {
                    // Some Windows filesystems reject ATOMIC_MOVE; fall back to non-atomic replace.
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                Sum.LOGGER.warn("Failed to save favorites: {}", e.getMessage());
            }
        }
    }

    public static boolean add(FavoriteKey key) {
        synchronized (LOCK) {
            if (key == null || ITEM_SET.contains(key)) {
                return false;
            }
            ITEMS.add(key);
            ITEM_SET.add(key);
            version++;
            if (!warnedOverSoftCap && ITEMS.size() > SOFT_WARN_THRESHOLD) {
                Sum.LOGGER.warn("Favorites list is now over the {} soft cap (currently {}).",
                    SOFT_WARN_THRESHOLD, ITEMS.size());
                warnedOverSoftCap = true;
            }
            return true;
        }
    }

    public static boolean remove(FavoriteKey key) {
        synchronized (LOCK) {
            if (key == null || !ITEM_SET.contains(key)) {
                return false;
            }
            ITEMS.remove(key);
            ITEM_SET.remove(key);
            version++;
            if (warnedOverSoftCap && ITEMS.size() <= SOFT_WARN_THRESHOLD) {
                warnedOverSoftCap = false;
            }
            return true;
        }
    }

    /** Toggles the key. Returns true if the key is present after the call. */
    public static boolean toggle(FavoriteKey key) {
        synchronized (LOCK) {
            if (key == null) {
                return false;
            }
            if (ITEM_SET.contains(key)) {
                remove(key);
                return false;
            }
            add(key);
            return true;
        }
    }

    public static int indexOf(FavoriteKey key) {
        synchronized (LOCK) {
            return key == null ? -1 : ITEMS.indexOf(key);
        }
    }

    public static boolean swap(int a, int b) {
        synchronized (LOCK) {
            int n = ITEMS.size();
            if (a < 0 || b < 0 || a >= n || b >= n || a == b) {
                return false;
            }
            FavoriteKey tmp = ITEMS.get(a);
            ITEMS.set(a, ITEMS.get(b));
            ITEMS.set(b, tmp);
            version++;
            return true;
        }
    }

    public static boolean contains(FavoriteKey key) {
        synchronized (LOCK) {
            return key != null && ITEM_SET.contains(key);
        }
    }

    public static List<FavoriteKey> snapshot() {
        synchronized (LOCK) {
            return new ArrayList<>(ITEMS);
        }
    }

    /** Snapshot of the set view; suitable for repeated contains() checks. */
    public static Set<FavoriteKey> snapshotSet() {
        synchronized (LOCK) {
            return new HashSet<>(ITEM_SET);
        }
    }

    /** Monotonic counter incremented on every mutation. Use to invalidate cached views. */
    public static int getVersion() {
        return version;
    }

    public static int size() {
        synchronized (LOCK) {
            return ITEMS.size();
        }
    }

    public static boolean isLoaded() {
        synchronized (LOCK) {
            return loaded;
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            ITEMS.clear();
            ITEM_SET.clear();
            warnedOverSoftCap = false;
            version++;
        }
    }

    private static void backupCorrupt(String text) {
        if (storageFile == null) {
            return;
        }
        File bak = new File(storageFile.getParentFile(),
            storageFile.getName() + ".bak." + System.currentTimeMillis());
        try {
            Files.write(bak.toPath(), text.getBytes(StandardCharsets.UTF_8));
            Sum.LOGGER.warn("Backed up corrupt favorites file to {}", bak);
        } catch (IOException e) {
            Sum.LOGGER.warn("Could not back up corrupt favorites file: {}", e.getMessage());
        }
    }
}
