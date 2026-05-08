package com.micatechnologies.minecraft.sum.economy;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.SumConstants;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootEntryItem;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.RandomValueRange;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraft.world.storage.loot.functions.SetCount;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Adds a low-weight pool of SUM bills to vanilla dungeon-chest loot tables. So players who
 * raid a stronghold library, abandoned mineshaft, jungle temple, etc. occasionally find
 * starter cash they can deposit at an ATM.
 *
 * <p>Pool design: one roll, weighted heavily toward "nothing" so most chests still drop
 * mostly vanilla loot. When a bill does drop, smaller denominations are far more common than
 * $100s, and $200/$500 are excluded from world-gen entirely (they have to be earned).
 */
public class BillsLootInjector {

    /** Vanilla chest loot tables we inject into. End city + woodland mansion are the
     *  highest-yield locations and get the same pool — players who clear those have already
     *  earned the cash. */
    private static final Set<ResourceLocation> TARGET_TABLES = new HashSet<>();
    static {
        TARGET_TABLES.add(new ResourceLocation("minecraft", "chests/abandoned_mineshaft"));
        TARGET_TABLES.add(new ResourceLocation("minecraft", "chests/desert_pyramid"));
        TARGET_TABLES.add(new ResourceLocation("minecraft", "chests/jungle_temple"));
        TARGET_TABLES.add(new ResourceLocation("minecraft", "chests/simple_dungeon"));
        TARGET_TABLES.add(new ResourceLocation("minecraft", "chests/stronghold_corridor"));
        TARGET_TABLES.add(new ResourceLocation("minecraft", "chests/stronghold_crossing"));
        TARGET_TABLES.add(new ResourceLocation("minecraft", "chests/stronghold_library"));
        TARGET_TABLES.add(new ResourceLocation("minecraft", "chests/igloo_chest"));
        TARGET_TABLES.add(new ResourceLocation("minecraft", "chests/nether_bridge"));
        TARGET_TABLES.add(new ResourceLocation("minecraft", "chests/end_city_treasure"));
        TARGET_TABLES.add(new ResourceLocation("minecraft", "chests/woodland_mansion"));
        TARGET_TABLES.add(new ResourceLocation("minecraft", "chests/spawn_bonus_chest"));
        TARGET_TABLES.add(new ResourceLocation("minecraft", "chests/village_blacksmith"));
    }

    /** Per-denomination entry: {denomination, pool weight, min count, max count}. Smaller
     *  denominations are more common; large bills are rare. $200 and $500 don't appear in
     *  world-gen — they're earned via gameplay. */
    private static final int[][] ENTRIES = {
        // {denom, weight, min, max}
        {1,   12, 1, 6},
        {5,    8, 1, 4},
        {10,   5, 1, 3},
        {20,   3, 1, 2},
        {50,   2, 1, 2},
        {100,  1, 1, 1},
    };

    /** Empty-entry weight — when rolled, the pool produces nothing for that chest. The
     *  total bills weight is sum(ENTRIES[*][1]) = 31; empty=80 means ~71% of rolls produce
     *  no bill. */
    private static final int EMPTY_WEIGHT = 80;

    @SubscribeEvent
    public void onLootTableLoad(LootTableLoadEvent event) {
        if (!TARGET_TABLES.contains(event.getName())) {
            return;
        }

        LootEntry[] entries = buildEntries();
        LootCondition[] noConditions = new LootCondition[0];
        LootPool pool = new LootPool(
            entries,
            noConditions,
            new RandomValueRange(1.0F),
            new RandomValueRange(0.0F),
            SumConstants.MOD_NAMESPACE + "_bills");
        event.getTable().addPool(pool);
        Sum.LOGGER.debug("[economy] Injected SUM bill pool into {}", event.getName());
    }

    private static LootEntry[] buildEntries() {
        LootEntry[] entries = new LootEntry[ENTRIES.length + 1];
        for (int i = 0; i < ENTRIES.length; i++) {
            int denom = ENTRIES[i][0];
            int weight = ENTRIES[i][1];
            int min = ENTRIES[i][2];
            int max = ENTRIES[i][3];
            ResourceLocation billId = new ResourceLocation(SumConstants.MOD_NAMESPACE, "bill_" + denom);
            net.minecraft.item.Item bill = net.minecraft.item.Item.REGISTRY.getObject(billId);
            // SetCount picks an int in [min, max] inclusive when applied to the rolled stack.
            LootFunction[] functions = (min == 1 && max == 1)
                ? new LootFunction[0]
                : new LootFunction[]{
                    new SetCount(new LootCondition[0], new RandomValueRange(min, max))
                };
            entries[i] = new LootEntryItem(
                bill,
                weight,
                /* quality = */ 0,
                functions,
                new LootCondition[0],
                SumConstants.MOD_NAMESPACE + "_bill_" + denom);
        }
        // Empty entry — rolled when the player gets nothing.
        entries[ENTRIES.length] = new LootEntryItem(
            net.minecraft.init.Items.AIR,
            EMPTY_WEIGHT,
            0,
            new LootFunction[0],
            new LootCondition[0],
            SumConstants.MOD_NAMESPACE + "_bill_empty");
        return entries;
    }
}
