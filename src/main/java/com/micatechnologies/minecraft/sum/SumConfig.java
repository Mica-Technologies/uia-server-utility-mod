package com.micatechnologies.minecraft.sum;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

    private static String[] roamerWalkableBlocks;
    private static Set<String> roamerWalkableBlockSet;

    private static Map<String, Double> roadRunnerSpeedBlocks;

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

        if (config.hasChanged()) {
            config.save();
        }
    }

    public static Set<String> getRoamerWalkableBlocks() {
        return roamerWalkableBlockSet;
    }

    public static boolean isBlockWalkableByRoamer(String registryName) {
        return roamerWalkableBlockSet != null && roamerWalkableBlockSet.contains(registryName);
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
}
