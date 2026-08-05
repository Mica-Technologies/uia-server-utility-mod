package com.micatechnologies.minecraft.sum.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Guards the one behavioural difference in {@link TileEntityServerShop} that is reachable
 * without a world or a player: a server shop has no till, so a completed sale's takings are
 * destroyed instead of banked. The role checks around it ({@code canManage}/{@code canPurchase})
 * need a real {@code EntityPlayer} and are covered by the in-game checklist instead.
 */
class TileEntityServerShopTillTest {

    @Test
    void playerShopBanksTakings() {
        TileEntityShop shop = new TileEntityShop();
        shop.depositFunds(12.50);
        shop.depositFunds(2.50);
        assertEquals(15.0, shop.getFundsAccumulated());
        assertFalse(shop.isServerShop());
    }

    @Test
    void serverShopDestroysTakings() {
        TileEntityServerShop shop = new TileEntityServerShop();
        shop.depositFunds(999.0);
        assertEquals(0.0, shop.getFundsAccumulated(),
            "a server shop has no owner to credit; the payment must leave circulation");
        assertTrue(shop.isServerShop());
    }

    @Test
    void serverShopHasNothingToWithdraw() {
        // Null player is safe here: the server-shop override returns before touching it, which
        // is itself the thing worth pinning — no code path can drain a till that doesn't exist.
        assertEquals(0.0, new TileEntityServerShop().withdrawFunds(null));
    }
}
