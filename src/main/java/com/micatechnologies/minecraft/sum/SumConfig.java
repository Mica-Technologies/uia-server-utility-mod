package com.micatechnologies.minecraft.sum;

import com.micatechnologies.minecraft.sum.border.BorderEntry;
import com.micatechnologies.minecraft.sum.loyalty.LoyaltyMilestone;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraftforge.common.config.Configuration;

public class SumConfig {

    private static final String CATEGORY_ROAMER = "roamer";

    private static final String FIELD_KEY_ROAMER_WALKABLE_BLOCKS = "walkableBlocks";
    private static final String FIELD_DESCRIPTION_ROAMER_WALKABLE_BLOCKS =
        "List of block registry names (e.g. minecraft:grass, minecraft:stone) that roamer NPCs are allowed to walk on.";
    private static final String[] FIELD_DEFAULT_ROAMER_WALKABLE_BLOCKS = {
        "minecraft:grass",
        "minecraft:stone",
        "minecraft:cobblestone",
        "minecraft:planks",
        "minecraft:brick_block",
        "minecraft:sand",
        "minecraft:gravel",
        "minecraft:dirt",
        "minecraft:sandstone",
        "minecraft:stonebrick",
        "minecraft:concrete"
    };

    private static final String CATEGORY_FAVORITES = "favorites";

    private static final String FIELD_KEY_FAVORITES_STAR_OVERLAY = "enableStarOverlay";
    private static final String FIELD_DESCRIPTION_FAVORITES_STAR_OVERLAY =
        "Whether to render a small gold star in the upper-right of every creative-inventory slot "
            + "whose item is in your favorites list. Set to false to disable the overlay without "
            + "removing favorites themselves.";
    private static final boolean FIELD_DEFAULT_FAVORITES_STAR_OVERLAY = true;

    private static final String CATEGORY_ROADRUNNER = "roadrunner";

    private static final String FIELD_KEY_ROADRUNNER_SPEED_BLOCKS = "speedBlocks";
    private static final String FIELD_DESCRIPTION_ROADRUNNER_SPEED_BLOCKS =
        "List of block-to-speed-multiplier mappings. Each entry is in the format 'blockRegistryName=multiplier', "
            + "where multiplier is a decimal number representing the speed boost factor (e.g. 1.5 = 50% faster). "
            + "Both vanilla and modded blocks are supported. There is no limit on the number of entries. "
            + "Example: minecraft:concrete=1.8";
    private static final String[] FIELD_DEFAULT_ROADRUNNER_SPEED_BLOCKS = {
        "minecraft:concrete=1.25"
    };

    private static final String CATEGORY_BORDER = "border";

    private static final String FIELD_KEY_BORDER_ENABLED = "enabled";
    private static final String FIELD_DESCRIPTION_BORDER_ENABLED =
        "When true, players crossing the configured per-dimension borders are bounced back inside or "
            + "wrapped to the opposite axis depending on the entry's mode.";
    private static final boolean FIELD_DEFAULT_BORDER_ENABLED = true;

    private static final String FIELD_KEY_BORDER_BORDERS = "borders";
    private static final String FIELD_DESCRIPTION_BORDER_BORDERS =
        "Per-dimension borders. Each entry is '<dimId>=<radius>:<mode>'. radius is a square half-width "
            + "in blocks (radius 10000 = 20000-block-wide square centered on origin). mode is 'bounce' "
            + "(player gets pushed back inside) or 'loop' (Pac-Man-style wrap to opposite side). "
            + "Empty list disables all borders.";
    private static final String[] FIELD_DEFAULT_BORDER_BORDERS = {
        "0=10000:bounce"
    };

    private static final String CATEGORY_AUTO_DROPPER = "autodropper";

    private static final String FIELD_KEY_AUTO_DROPPER_ENABLED = "enabled";
    private static final String FIELD_DESCRIPTION_AUTO_DROPPER_ENABLED =
        "Whether placed Auto Dropper blocks tick automatically. Set false to disable the feature globally "
            + "without removing existing blocks.";
    private static final boolean FIELD_DEFAULT_AUTO_DROPPER_ENABLED = true;

    private static final String FIELD_KEY_AUTO_DROPPER_INTERVAL = "tickInterval";
    private static final String FIELD_DESCRIPTION_AUTO_DROPPER_INTERVAL =
        "Number of server ticks between auto-dispense attempts. 20 ticks = 1 second. Lower values "
            + "dispense more aggressively at higher CPU cost.";
    private static final int FIELD_DEFAULT_AUTO_DROPPER_INTERVAL = 8;

    private static final String CATEGORY_LOYALTY = "loyalty";

    private static final String FIELD_KEY_LOYALTY_ENABLED = "enabled";
    private static final String FIELD_DESCRIPTION_LOYALTY_ENABLED =
        "When true, players receive configured rewards after crossing playtime milestones. "
            + "Milestones fire once per player; tracking is persisted in the player's NBT data.";
    private static final boolean FIELD_DEFAULT_LOYALTY_ENABLED = true;

    private static final String FIELD_KEY_LOYALTY_MILESTONES = "milestones";
    private static final String FIELD_DESCRIPTION_LOYALTY_MILESTONES =
        "List of playtime milestones in the format '<minutes>=<type>:<value>'. Two reward types: "
            + "'money:<amount>' deposits via the active economy backend (SUM or EconomyInc); "
            + "'command:<command>' runs the command as the server console with {player} replaced "
            + "by the player's name. Example entries: 30=money:10, 60=command:give {player} minecraft:diamond 1.";
    private static final String[] FIELD_DEFAULT_LOYALTY_MILESTONES = {
        "30=money:10",
        "120=money:50"
    };

    private static final String CATEGORY_MOVEMENT = "movement";

    private static final String FIELD_KEY_MOVEMENT_ENABLED = "toleranceEnabled";
    private static final String FIELD_DESCRIPTION_MOVEMENT_ENABLED =
        "When true, vanilla's hardcoded \"moved too quickly\" thresholds in NetHandlerPlayServer "
            + "are multiplied by 'toleranceMultiplier'. With multiplier 10 (default), a player can "
            + "be roughly sqrt(10) times farther from their last good position before being "
            + "rubberband-snapped — enough to absorb most lag-induced false positives. With "
            + "multiplier 1, behavior matches vanilla. Inspired by Moving Quickly (coremod by "
            + "thiakil et al.); SUM patches the same constants via Mixin and gates by this flag.";
    private static final boolean FIELD_DEFAULT_MOVEMENT_ENABLED = true;

    private static final String FIELD_KEY_MOVEMENT_MULTIPLIER = "toleranceMultiplier";
    private static final String FIELD_DESCRIPTION_MOVEMENT_MULTIPLIER =
        "Factor applied to vanilla's 100.0 (player) / 100.0 (vehicle) / 300.0 (elytra) thresholds. "
            + "Reasonable range: 1.0 (vanilla) to 100.0 (effectively disabled). Default 10.0.";
    private static final double FIELD_DEFAULT_MOVEMENT_MULTIPLIER = 10.0;
    private static final double FIELD_MIN_MOVEMENT_MULTIPLIER = 1.0;
    private static final double FIELD_MAX_MOVEMENT_MULTIPLIER = 1000.0;

    private static final String CATEGORY_PAUSER = "pauser";

    private static final String FIELD_KEY_PAUSER_ENABLED = "enabled";
    private static final String FIELD_DESCRIPTION_PAUSER_ENABLED =
        "When true, every WorldServer tick is cancelled while zero players are online — halting time, "
            + "weather, mob spawning, scheduled block updates, TileEntity ticks (so chunk-loaded farms "
            + "freeze), and entity ticks. Resumes immediately when the first player logs in. Inspired "
            + "by Server Pauser (Smileycorp, LGPL-2.1); SUM uses a Mixin on WorldServer.tick(), gated "
            + "by this flag.";
    private static final boolean FIELD_DEFAULT_PAUSER_ENABLED = true;

    private static final String CATEGORY_BEACHES = "beaches";

    private static final String FIELD_KEY_BEACHES_ENABLED = "enabled";
    private static final String FIELD_DESCRIPTION_BEACHES_ENABLED =
        "When true, breaking a configured 'affectedBlock' adjacent to a water source replaces the broken block with "
            + "water and animates flooding the surrounding column at sea level. Inspired by Pretty Beaches "
            + "(BlayTheNinth, MIT).";
    private static final boolean FIELD_DEFAULT_BEACHES_ENABLED = true;

    private static final String FIELD_KEY_BEACHES_AFFECTED_BLOCKS = "affectedBlocks";
    private static final String FIELD_DESCRIPTION_BEACHES_AFFECTED_BLOCKS =
        "List of block registry names whose breakage near water triggers the flooding behavior. "
            + "Use \"*\" to affect every block.";
    private static final String[] FIELD_DEFAULT_BEACHES_AFFECTED_BLOCKS = {
        "minecraft:sand"
    };

    private static final String FIELD_KEY_BEACHES_ANIMATED = "animatedFlooding";
    private static final String FIELD_DESCRIPTION_BEACHES_ANIMATED =
        "When true, flooding spreads one step every " + 10 + " ticks (animated). When false, the entire column "
            + "fills instantly on break.";
    private static final boolean FIELD_DEFAULT_BEACHES_ANIMATED = true;

    private static final String FIELD_KEY_BEACHES_INFINITE_BUCKET = "infiniteBucketWater";
    private static final String FIELD_DESCRIPTION_BEACHES_INFINITE_BUCKET =
        "When true, filling a bucket at a flowing-water tile adjacent to a source restores the source instead "
            + "of leaving an empty pocket. Off by default to preserve vanilla bucket behavior.";
    private static final boolean FIELD_DEFAULT_BEACHES_INFINITE_BUCKET = false;

    private static final String FIELD_KEY_BEACHES_REALISTIC = "realisticErosion";
    private static final String FIELD_DESCRIPTION_BEACHES_REALISTIC =
        "When true, the flooding behavior also fires for a curated set of \"erodible\" blocks: dirt, "
            + "grass, gravel, clay, mycelium, soul sand, snow blocks, and snow layers. Lets water "
            + "carve through riverbanks and other soft terrain, not just sand. The 'affectedBlocks' "
            + "list is still respected — this just adds the realistic-erosion set on top.";
    private static final boolean FIELD_DEFAULT_BEACHES_REALISTIC = false;

    /** Hardcoded set of blocks that get the beaches treatment when {@code realisticErosion}
     *  is enabled. Curated for materials water plausibly erodes — soft soil, gravel, snow.
     *  Stone and harder materials are deliberately excluded since flooding any hole next to
     *  water in stone caves would be unwanted. */
    private static final Set<String> REALISTIC_EROSION_BLOCKS = new HashSet<>(Arrays.asList(
        "minecraft:sand",
        "minecraft:dirt",
        "minecraft:grass",
        "minecraft:gravel",
        "minecraft:clay",
        "minecraft:mycelium",
        "minecraft:soul_sand",
        "minecraft:snow",
        "minecraft:snow_layer"
    ));

    private static final String CATEGORY_SLEEP_VOTE = "sleep_vote";

    private static final String FIELD_KEY_SLEEP_VOTE_ENABLED = "enabled";
    private static final String FIELD_DESCRIPTION_SLEEP_VOTE_ENABLED =
        "When true, the night ends if at least 'thresholdPercent' of online players are asleep "
            + "in the overworld. Set to false to fall back to vanilla's all-must-sleep behavior.";
    private static final boolean FIELD_DEFAULT_SLEEP_VOTE_ENABLED = true;

    private static final String FIELD_KEY_SLEEP_VOTE_THRESHOLD = "thresholdPercent";
    private static final String FIELD_DESCRIPTION_SLEEP_VOTE_THRESHOLD =
        "Percentage of online players (1-100) who must be in bed for the night to be skipped. "
            + "Default 50. The required count is rounded up so 50% on a server of 3 needs 2.";
    private static final int FIELD_DEFAULT_SLEEP_VOTE_THRESHOLD = 50;

    private static String[] roamerWalkableBlocks;
    private static Set<String> roamerWalkableBlockSet;
    // Resolved lazily on first use. Block instances aren't available at preInit, since the block
    // registry is populated between preInit and init. The pathfinder hot path only runs after
    // entities exist in the world (post-init), so lazy resolution is safe.
    private static volatile Set<Block> roamerWalkableBlockResolved;

    private static Map<String, Double> roadRunnerSpeedBlocks;

    private static boolean favoritesStarOverlay;
    private static boolean sleepVoteEnabled;
    private static int sleepVoteThresholdPercent;

    private static boolean beachesEnabled;
    private static boolean beachesAnimatedFlooding;
    private static boolean beachesInfiniteBucketWater;
    private static boolean beachesRealisticErosion;
    private static Set<String> beachesAffectedBlockNames;
    private static boolean beachesAffectsAllBlocks;

    private static boolean pauserEnabled;

    private static boolean movementToleranceEnabled;
    private static double movementToleranceMultiplier;

    private static boolean loyaltyEnabled;
    private static List<LoyaltyMilestone> loyaltyMilestones = Collections.emptyList();

    private static boolean autoDropperEnabled;
    private static int autoDropperTickInterval;

    private static boolean borderEnabled;
    private static Map<Integer, BorderEntry> borderEntries = Collections.emptyMap();

    private static Configuration config;

    static void init(File configFile) {
        if (config == null) {
            config = new Configuration(configFile);
            loadConfig();
        }
    }

    private static void loadConfig() {
        roamerWalkableBlocks = config.getStringList(
            FIELD_KEY_ROAMER_WALKABLE_BLOCKS, CATEGORY_ROAMER,
            FIELD_DEFAULT_ROAMER_WALKABLE_BLOCKS, FIELD_DESCRIPTION_ROAMER_WALKABLE_BLOCKS);
        roamerWalkableBlockSet = new HashSet<>(Arrays.asList(roamerWalkableBlocks));
        roamerWalkableBlockResolved = null;

        String[] speedBlockEntries = config.getStringList(
            FIELD_KEY_ROADRUNNER_SPEED_BLOCKS, CATEGORY_ROADRUNNER,
            FIELD_DEFAULT_ROADRUNNER_SPEED_BLOCKS, FIELD_DESCRIPTION_ROADRUNNER_SPEED_BLOCKS);
        roadRunnerSpeedBlocks = new HashMap<>();
        for (String entry : speedBlockEntries) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2) {
                try {
                    double multiplier = Double.parseDouble(parts[1].trim());
                    roadRunnerSpeedBlocks.put(parts[0].trim(), multiplier);
                } catch (NumberFormatException e) {
                    Sum.LOGGER.warn("Invalid RoadRunner speed entry '{}': multiplier is not a number", entry);
                }
            } else {
                Sum.LOGGER.warn("Invalid RoadRunner speed entry '{}': expected format 'block=multiplier'", entry);
            }
        }

        favoritesStarOverlay = config.getBoolean(
            FIELD_KEY_FAVORITES_STAR_OVERLAY, CATEGORY_FAVORITES,
            FIELD_DEFAULT_FAVORITES_STAR_OVERLAY, FIELD_DESCRIPTION_FAVORITES_STAR_OVERLAY);

        sleepVoteEnabled = config.getBoolean(
            FIELD_KEY_SLEEP_VOTE_ENABLED, CATEGORY_SLEEP_VOTE,
            FIELD_DEFAULT_SLEEP_VOTE_ENABLED, FIELD_DESCRIPTION_SLEEP_VOTE_ENABLED);
        sleepVoteThresholdPercent = config.getInt(
            FIELD_KEY_SLEEP_VOTE_THRESHOLD, CATEGORY_SLEEP_VOTE,
            FIELD_DEFAULT_SLEEP_VOTE_THRESHOLD, 1, 100,
            FIELD_DESCRIPTION_SLEEP_VOTE_THRESHOLD);

        beachesEnabled = config.getBoolean(
            FIELD_KEY_BEACHES_ENABLED, CATEGORY_BEACHES,
            FIELD_DEFAULT_BEACHES_ENABLED, FIELD_DESCRIPTION_BEACHES_ENABLED);
        beachesAnimatedFlooding = config.getBoolean(
            FIELD_KEY_BEACHES_ANIMATED, CATEGORY_BEACHES,
            FIELD_DEFAULT_BEACHES_ANIMATED, FIELD_DESCRIPTION_BEACHES_ANIMATED);
        beachesInfiniteBucketWater = config.getBoolean(
            FIELD_KEY_BEACHES_INFINITE_BUCKET, CATEGORY_BEACHES,
            FIELD_DEFAULT_BEACHES_INFINITE_BUCKET, FIELD_DESCRIPTION_BEACHES_INFINITE_BUCKET);
        String[] beachesAffectedEntries = config.getStringList(
            FIELD_KEY_BEACHES_AFFECTED_BLOCKS, CATEGORY_BEACHES,
            FIELD_DEFAULT_BEACHES_AFFECTED_BLOCKS, FIELD_DESCRIPTION_BEACHES_AFFECTED_BLOCKS);
        beachesAffectedBlockNames = new HashSet<>(Arrays.asList(beachesAffectedEntries));
        beachesAffectsAllBlocks = beachesAffectedBlockNames.contains("*");
        beachesRealisticErosion = config.getBoolean(
            FIELD_KEY_BEACHES_REALISTIC, CATEGORY_BEACHES,
            FIELD_DEFAULT_BEACHES_REALISTIC, FIELD_DESCRIPTION_BEACHES_REALISTIC);

        pauserEnabled = config.getBoolean(
            FIELD_KEY_PAUSER_ENABLED, CATEGORY_PAUSER,
            FIELD_DEFAULT_PAUSER_ENABLED, FIELD_DESCRIPTION_PAUSER_ENABLED);

        movementToleranceEnabled = config.getBoolean(
            FIELD_KEY_MOVEMENT_ENABLED, CATEGORY_MOVEMENT,
            FIELD_DEFAULT_MOVEMENT_ENABLED, FIELD_DESCRIPTION_MOVEMENT_ENABLED);
        movementToleranceMultiplier = config.get(CATEGORY_MOVEMENT, FIELD_KEY_MOVEMENT_MULTIPLIER,
            FIELD_DEFAULT_MOVEMENT_MULTIPLIER, FIELD_DESCRIPTION_MOVEMENT_MULTIPLIER,
            FIELD_MIN_MOVEMENT_MULTIPLIER, FIELD_MAX_MOVEMENT_MULTIPLIER).getDouble();

        loyaltyEnabled = config.getBoolean(
            FIELD_KEY_LOYALTY_ENABLED, CATEGORY_LOYALTY,
            FIELD_DEFAULT_LOYALTY_ENABLED, FIELD_DESCRIPTION_LOYALTY_ENABLED);
        String[] milestoneEntries = config.getStringList(
            FIELD_KEY_LOYALTY_MILESTONES, CATEGORY_LOYALTY,
            FIELD_DEFAULT_LOYALTY_MILESTONES, FIELD_DESCRIPTION_LOYALTY_MILESTONES);
        loyaltyMilestones = parseLoyaltyMilestones(milestoneEntries);

        autoDropperEnabled = config.getBoolean(
            FIELD_KEY_AUTO_DROPPER_ENABLED, CATEGORY_AUTO_DROPPER,
            FIELD_DEFAULT_AUTO_DROPPER_ENABLED, FIELD_DESCRIPTION_AUTO_DROPPER_ENABLED);
        autoDropperTickInterval = config.getInt(
            FIELD_KEY_AUTO_DROPPER_INTERVAL, CATEGORY_AUTO_DROPPER,
            FIELD_DEFAULT_AUTO_DROPPER_INTERVAL, 1, 1200,
            FIELD_DESCRIPTION_AUTO_DROPPER_INTERVAL);

        borderEnabled = config.getBoolean(
            FIELD_KEY_BORDER_ENABLED, CATEGORY_BORDER,
            FIELD_DEFAULT_BORDER_ENABLED, FIELD_DESCRIPTION_BORDER_ENABLED);
        String[] borderEntryStrings = config.getStringList(
            FIELD_KEY_BORDER_BORDERS, CATEGORY_BORDER,
            FIELD_DEFAULT_BORDER_BORDERS, FIELD_DESCRIPTION_BORDER_BORDERS);
        borderEntries = parseBorderEntries(borderEntryStrings);

        if (config.hasChanged()) {
            config.save();
        }
    }

    public static boolean isSleepVoteEnabled() {
        return sleepVoteEnabled;
    }

    public static boolean isBeachesEnabled() {
        return beachesEnabled;
    }

    public static boolean isBeachesAnimatedFlooding() {
        return beachesAnimatedFlooding;
    }

    public static boolean isBeachesInfiniteBucketWater() {
        return beachesInfiniteBucketWater;
    }

    public static boolean isBorderEnabled() {
        return borderEnabled;
    }

    public static BorderEntry getBorderForDim(int dimId) {
        return borderEntries.get(dimId);
    }

    private static Map<Integer, BorderEntry> parseBorderEntries(String[] entries) {
        Map<Integer, BorderEntry> result = new HashMap<>();
        for (String raw : entries) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                Sum.LOGGER.warn("[border] invalid entry '{}': expected '<dimId>=<radius>:<mode>'", entry);
                continue;
            }
            int dimId;
            try {
                dimId = Integer.parseInt(entry.substring(0, eq).trim());
            } catch (NumberFormatException e) {
                Sum.LOGGER.warn("[border] invalid entry '{}': dimId is not an integer", entry);
                continue;
            }
            String body = entry.substring(eq + 1).trim();
            int colon = body.indexOf(':');
            if (colon <= 0) {
                Sum.LOGGER.warn("[border] invalid entry '{}': expected '<radius>:<mode>'", entry);
                continue;
            }
            double radius;
            try {
                radius = Double.parseDouble(body.substring(0, colon).trim());
            } catch (NumberFormatException e) {
                Sum.LOGGER.warn("[border] invalid entry '{}': radius is not a number", entry);
                continue;
            }
            if (radius <= 0) {
                Sum.LOGGER.warn("[border] invalid entry '{}': radius must be positive", entry);
                continue;
            }
            String modeRaw = body.substring(colon + 1).trim().toUpperCase();
            BorderEntry.Mode mode;
            try {
                mode = BorderEntry.Mode.valueOf(modeRaw);
            } catch (IllegalArgumentException e) {
                Sum.LOGGER.warn("[border] invalid entry '{}': unknown mode '{}' (expected bounce or loop)",
                    entry, modeRaw);
                continue;
            }
            if (result.containsKey(dimId)) {
                Sum.LOGGER.warn("[border] duplicate border for dim {}; keeping the first entry", dimId);
                continue;
            }
            result.put(dimId, new BorderEntry(dimId, radius, mode));
        }
        return Collections.unmodifiableMap(result);
    }

    public static boolean isAutoDropperEnabled() {
        return autoDropperEnabled;
    }

    public static int getAutoDropperTickInterval() {
        return autoDropperTickInterval;
    }

    public static boolean isLoyaltyEnabled() {
        return loyaltyEnabled;
    }

    public static Collection<LoyaltyMilestone> getLoyaltyMilestones() {
        return loyaltyMilestones;
    }

    private static List<LoyaltyMilestone> parseLoyaltyMilestones(String[] entries) {
        List<LoyaltyMilestone> result = new ArrayList<>();
        Set<Integer> seenMinutes = new HashSet<>();
        for (String raw : entries) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                Sum.LOGGER.warn("[loyalty] invalid milestone '{}': expected '<minutes>=<type>:<value>'", entry);
                continue;
            }
            int minutes;
            try {
                minutes = Integer.parseInt(entry.substring(0, eq).trim());
            } catch (NumberFormatException e) {
                Sum.LOGGER.warn("[loyalty] invalid milestone '{}': minutes is not an integer", entry);
                continue;
            }
            if (minutes <= 0) {
                Sum.LOGGER.warn("[loyalty] invalid milestone '{}': minutes must be positive", entry);
                continue;
            }
            String body = entry.substring(eq + 1).trim();
            int colon = body.indexOf(':');
            if (colon <= 0) {
                Sum.LOGGER.warn("[loyalty] invalid milestone '{}': expected '<type>:<value>'", entry);
                continue;
            }
            String typeRaw = body.substring(0, colon).trim().toUpperCase();
            String value = body.substring(colon + 1).trim();
            LoyaltyMilestone.Type type;
            try {
                type = LoyaltyMilestone.Type.valueOf(typeRaw);
            } catch (IllegalArgumentException e) {
                Sum.LOGGER.warn("[loyalty] invalid milestone '{}': unknown type '{}' (expected money or command)",
                    entry, typeRaw);
                continue;
            }
            if (!seenMinutes.add(minutes)) {
                Sum.LOGGER.warn("[loyalty] duplicate milestone for {}min; keeping the first entry", minutes);
                continue;
            }
            result.add(new LoyaltyMilestone(minutes, type, value));
        }
        return Collections.unmodifiableList(result);
    }

    public static boolean isPauserEnabled() {
        return pauserEnabled;
    }

    public static boolean isMovementToleranceEnabled() {
        return movementToleranceEnabled;
    }

    public static double getMovementToleranceMultiplier() {
        return movementToleranceMultiplier;
    }

    public static boolean isBeachesAffectedBlock(Block block) {
        if (block == null || beachesAffectedBlockNames == null) {
            return false;
        }
        if (beachesAffectsAllBlocks) {
            return true;
        }
        net.minecraft.util.ResourceLocation registryName = block.getRegistryName();
        if (registryName == null) {
            return false;
        }
        String name = registryName.toString();
        if (beachesAffectedBlockNames.contains(name)) {
            return true;
        }
        return beachesRealisticErosion && REALISTIC_EROSION_BLOCKS.contains(name);
    }

    public static int getSleepVoteThresholdPercent() {
        return sleepVoteThresholdPercent;
    }

    public static boolean isFavoritesStarOverlayEnabled() {
        return favoritesStarOverlay;
    }

    public static Set<String> getRoamerWalkableBlocks() {
        return roamerWalkableBlockSet;
    }

    public static boolean isBlockWalkableByRoamer(String registryName) {
        return roamerWalkableBlockSet != null && roamerWalkableBlockSet.contains(registryName);
    }

    /**
     * Hot-path overload used by the roamer pathfinder and AI. Resolves the configured registry
     * names into {@link Block} instances on first call (after registries are populated) and
     * matches by reference identity, avoiding {@code getRegistryName().toString()} allocations
     * on every node expansion.
     */
    public static boolean isBlockWalkableByRoamer(Block block) {
        Set<Block> resolved = roamerWalkableBlockResolved;
        if (resolved == null) {
            resolved = resolveWalkableBlocks();
            roamerWalkableBlockResolved = resolved;
        }
        return resolved.contains(block);
    }

    private static synchronized Set<Block> resolveWalkableBlocks() {
        if (roamerWalkableBlockResolved != null) {
            return roamerWalkableBlockResolved;
        }
        Set<Block> set = new HashSet<>();
        if (roamerWalkableBlockSet != null) {
            for (String name : roamerWalkableBlockSet) {
                Block b = Block.getBlockFromName(name);
                if (b != null) {
                    set.add(b);
                }
            }
        }
        return set;
    }

    public static Map<String, Double> getRoadRunnerSpeedBlocks() {
        return roadRunnerSpeedBlocks;
    }

    public static double getRoadRunnerSpeedMultiplier(String registryName) {
        if (roadRunnerSpeedBlocks == null) {
            return 0.0;
        }
        return roadRunnerSpeedBlocks.getOrDefault(registryName, 0.0);
    }

    public static void reloadConfig() {
        if (config != null) {
            config.load();
            loadConfig();
        }
    }

    public static boolean addRoamerWalkableBlock(String registryName) {
        if (roamerWalkableBlockSet.contains(registryName)) {
            return false;
        }
        roamerWalkableBlockSet.add(registryName);
        roamerWalkableBlockResolved = null;
        saveRoamerWalkableBlocks();
        return true;
    }

    public static boolean removeRoamerWalkableBlock(String registryName) {
        if (!roamerWalkableBlockSet.contains(registryName)) {
            return false;
        }
        roamerWalkableBlockSet.remove(registryName);
        roamerWalkableBlockResolved = null;
        saveRoamerWalkableBlocks();
        return true;
    }

    private static void saveRoamerWalkableBlocks() {
        roamerWalkableBlocks = roamerWalkableBlockSet.toArray(new String[0]);
        config.get(CATEGORY_ROAMER, FIELD_KEY_ROAMER_WALKABLE_BLOCKS,
            FIELD_DEFAULT_ROAMER_WALKABLE_BLOCKS, FIELD_DESCRIPTION_ROAMER_WALKABLE_BLOCKS)
            .set(roamerWalkableBlocks);
        config.save();
    }
}
