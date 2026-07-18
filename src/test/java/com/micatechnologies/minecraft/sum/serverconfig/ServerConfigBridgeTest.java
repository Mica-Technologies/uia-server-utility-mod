package com.micatechnologies.minecraft.sum.serverconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.sum.border.BorderEntry;
import com.micatechnologies.minecraft.sum.loyalty.LoyaltyMilestone;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests the pure config→string-list formatters in {@link ServerConfigBridge} (the inverse of the
 * {@code SumConfig} parsers already under test). {@code buildSnapshot} reads live {@code SumConfig}
 * statics and is out of scope; the three format helpers are pure.
 */
class ServerConfigBridgeTest {

    @Test
    void formatsMilestonesByType() {
        List<LoyaltyMilestone> ms = Arrays.asList(
            new LoyaltyMilestone(60, LoyaltyMilestone.Type.MONEY, "100"),
            new LoyaltyMilestone(120, LoyaltyMilestone.Type.COMMAND, "say hi"));
        assertEquals(Arrays.asList("60=money:100", "120=command:say hi"),
            ServerConfigBridge.formatMilestones(ms));
    }

    @Test
    void formatsBordersWithIntegerAndFractionalRadius() {
        Map<Integer, BorderEntry> entries = new LinkedHashMap<>();
        entries.put(0, new BorderEntry(0, 5000.0, BorderEntry.Mode.LOOP));
        entries.put(1, new BorderEntry(1, 100.5, BorderEntry.Mode.BOUNCE));
        assertEquals(Arrays.asList("0=5000:loop", "1=100.50:bounce"),
            ServerConfigBridge.formatBorders(entries));
    }

    @Test
    void formatsSpeedBlocksWithThreeSigFigs() {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("minecraft:stone", 1.5);
        map.put("minecraft:ice", 0.5);
        map.put("minecraft:rail", 1000.0);
        assertEquals(
            Arrays.asList("minecraft:stone=1.50", "minecraft:ice=0.500", "minecraft:rail=1.00e+03"),
            ServerConfigBridge.formatSpeedBlocks(map));
    }

    @Test
    void emptyInputsProduceEmptyLists() {
        assertTrue(ServerConfigBridge.formatMilestones(Collections.emptyList()).isEmpty());
        assertTrue(ServerConfigBridge.formatBorders(Collections.emptyMap()).isEmpty());
        assertTrue(ServerConfigBridge.formatSpeedBlocks(Collections.emptyMap()).isEmpty());
    }
}
