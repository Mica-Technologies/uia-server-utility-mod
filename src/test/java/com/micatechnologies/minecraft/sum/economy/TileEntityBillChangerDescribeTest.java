package com.micatechnologies.minecraft.sum.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.sum.economy.TileEntityBillChanger.ChangeResult;
import net.minecraft.util.text.TextComponentTranslation;
import org.junit.jupiter.api.Test;

/**
 * Tests the pure {@link TileEntityBillChanger#describe} result→message mapping. The bundle/
 * unbundle logic itself manipulates {@code ItemStack}s (needs the item registry) and is out of
 * unit scope, but the user-facing message table is pure and worth locking — an unmapped future
 * enum constant would fall through to the generic-unknown key, which the exhaustiveness test
 * catches.
 */
class TileEntityBillChangerDescribeTest {

    private static String key(ChangeResult result) {
        return ((TextComponentTranslation) TileEntityBillChanger.describe(result)).getKey();
    }

    @Test
    void eachResultMapsToItsTranslationKey() {
        assertEquals("sum.changer.ok", key(ChangeResult.OK));
        assertEquals("sum.changer.empty_input", key(ChangeResult.EMPTY_INPUT));
        assertEquals("sum.changer.unknown_input", key(ChangeResult.UNKNOWN_INPUT));
        assertEquals("sum.changer.not_enough_bills", key(ChangeResult.NOT_ENOUGH_BILLS));
        assertEquals("sum.changer.no_packet_for_denom", key(ChangeResult.NO_PACKET_FOR_DENOM));
        assertEquals("sum.changer.no_bill_for_denom", key(ChangeResult.NO_BILL_FOR_DENOM));
        assertEquals("sum.changer.output_blocked", key(ChangeResult.OUTPUT_BLOCKED));
    }

    @Test
    void notEnoughBillsCarriesTheBillsPerPacketCount() {
        TextComponentTranslation msg =
            (TextComponentTranslation) TileEntityBillChanger.describe(ChangeResult.NOT_ENOUGH_BILLS);
        assertEquals(64, msg.getFormatArgs()[0], "message should format in the 64-bills-per-packet count");
    }

    @Test
    void everyResultHasADedicatedKey() {
        // Guards against a new ChangeResult constant slipping through to commands.generic.unknown.
        for (ChangeResult result : ChangeResult.values()) {
            assertTrue(key(result).startsWith("sum.changer."),
                result + " fell through to the generic-unknown default");
        }
    }
}
