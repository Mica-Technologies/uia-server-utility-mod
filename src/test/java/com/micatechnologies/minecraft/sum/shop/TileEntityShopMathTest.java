package com.micatechnologies.minecraft.sum.shop;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.sum.shop.TileEntityShop.BuyResult;
import java.util.List;
import net.minecraft.util.text.TextComponentString;
import org.junit.jupiter.api.Test;

/**
 * Tests the pure pieces of {@link TileEntityShop}: the {@code describe} result-message table, the
 * sale-amount/cost clamp bounds, and the greedy {@code breakIntoBills} decomposition. The
 * purchase flow itself touches {@code EconomyBridge}/{@code ItemStack}/world and is out of scope.
 */
class TileEntityShopMathTest {

    private static String text(BuyResult r, double cost, double balance) {
        return ((TextComponentString) TileEntityShop.describe(r, cost, balance))
            .getUnformattedComponentText();
    }

    // --- describe ---

    @Test
    void describeReportsCostsAndReasons() {
        assertTrue(text(BuyResult.OK, 5.0, 0).contains("-$5.00"));
        assertTrue(text(BuyResult.OUT_OF_STOCK, 0, 0).contains("Sold out"));
        assertTrue(text(BuyResult.OWNER_CANT_BUY, 0, 0).contains("own shop"));
        String insufficient = text(BuyResult.INSUFFICIENT_FUNDS, 10.0, 3.0);
        assertTrue(insufficient.contains("$10.00") && insufficient.contains("$3.00"));
    }

    @Test
    void everyBuyResultHasADedicatedMessage() {
        // All mapped results return a TextComponentString; an unmapped future constant would
        // fall through to the generic-unknown TextComponentTranslation and fail this instanceof.
        for (BuyResult r : BuyResult.values()) {
            assertTrue(TileEntityShop.describe(r, 1.0, 1.0) instanceof TextComponentString,
                r + " has no dedicated message");
        }
    }

    // --- clamp bounds ---

    @Test
    void saleAmountClampsToOneThroughSixtyFour() {
        assertEquals(1, TileEntityShop.clamp(0, TileEntityShop.MIN_SALE_AMOUNT, TileEntityShop.MAX_SALE_AMOUNT));
        assertEquals(64, TileEntityShop.clamp(65, TileEntityShop.MIN_SALE_AMOUNT, TileEntityShop.MAX_SALE_AMOUNT));
        assertEquals(32, TileEntityShop.clamp(32, TileEntityShop.MIN_SALE_AMOUNT, TileEntityShop.MAX_SALE_AMOUNT));
    }

    @Test
    void saleCostClampsToZeroThroughMillion() {
        assertEquals(0.0, TileEntityShop.clampD(-1, TileEntityShop.MIN_SALE_COST, TileEntityShop.MAX_SALE_COST));
        assertEquals(1_000_000.0, TileEntityShop.clampD(2_000_000, TileEntityShop.MIN_SALE_COST, TileEntityShop.MAX_SALE_COST));
        assertEquals(500.0, TileEntityShop.clampD(500, TileEntityShop.MIN_SALE_COST, TileEntityShop.MAX_SALE_COST));
    }

    // --- breakIntoBills ---

    private static int billValue(List<int[]> pairs) {
        int sum = 0;
        for (int[] p : pairs) {
            sum += p[0] * p[1];
        }
        return sum;
    }

    @Test
    void breakIntoBillsIsEmptyForZero() {
        assertTrue(TileEntityShop.breakIntoBills(0).isEmpty());
    }

    @Test
    void breakIntoBillsIsGreedyLargestFirst() {
        List<int[]> pairs = TileEntityShop.breakIntoBills(700);
        assertEquals(2, pairs.size());
        assertArrayEquals(new int[]{500, 1}, pairs.get(0));
        assertArrayEquals(new int[]{200, 1}, pairs.get(1));
    }

    @Test
    void breakIntoBillsConservesValue() {
        assertEquals(786, billValue(TileEntityShop.breakIntoBills(786)));
    }

    @Test
    void breakIntoBillsSplitsStacksAtSixtyFour() {
        // $50,000 = 100 × $500 bills → one 64-stack + one 36-stack.
        List<int[]> pairs = TileEntityShop.breakIntoBills(50_000);
        assertEquals(2, pairs.size());
        assertArrayEquals(new int[]{500, 64}, pairs.get(0));
        assertArrayEquals(new int[]{500, 36}, pairs.get(1));
    }

    @Test
    void breakIntoBillsFloorsFractionalCents() {
        assertEquals(100, billValue(TileEntityShop.breakIntoBills(100.5)));
    }
}
