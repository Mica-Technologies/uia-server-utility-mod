package com.micatechnologies.minecraft.sum;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.item.Item;

public class SumRegistry {

    private static final Map<String, Block> BLOCKS = new HashMap<>();
    private static final Map<String, Item> ITEMS = new HashMap<>();

    public static Collection<Block> getBlocks() {
        return BLOCKS.values();
    }

    public static Collection<Item> getItems() {
        return ITEMS.values();
    }

    public static void registerBlock(Block block) {
        String key = block.getRegistryName() != null
            ? block.getRegistryName().toString()
            : block.getTranslationKey();
        if (BLOCKS.containsKey(key)) {
            throw new IllegalArgumentException("Block with registry name " + key + " already registered.");
        }
        BLOCKS.put(key, block);
    }

    public static void registerItem(Item item) {
        String key = item.getRegistryName() != null
            ? item.getRegistryName().toString()
            : item.getTranslationKey();
        if (ITEMS.containsKey(key)) {
            throw new IllegalArgumentException("Item with registry name " + key + " already registered.");
        }
        ITEMS.put(key, item);
    }
}
