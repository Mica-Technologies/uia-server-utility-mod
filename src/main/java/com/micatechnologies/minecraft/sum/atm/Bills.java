package com.micatechnologies.minecraft.sum.atm;

import com.micatechnologies.minecraft.sum.Sum;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

/**
 * Lookup helper for the EconomyInc bill items used by the ATM. Bills are physical money items
 * (separate from the IMoney capability balance) in eight denominations: $1, $5, $10, $20, $50,
 * $100, $200, $500. Withdraw turns balance into bill items; deposit converts bill items back
 * into balance.
 *
 * <p>Registry names verified from the EconomyInc 1.6.2 jar via javap. Note the upstream
 * misspellings ({@code item_fiftybe} with a trailing 'e' and {@code item_hundreedb} with a
 * doubled 'e') are intentional - these are the actual strings the mod's items register under.
 */
public final class Bills {

    /** Denominations from highest to lowest, matched 1:1 with {@link #REGISTRY_NAMES}. */
    private static final int[] DENOMINATIONS = {500, 200, 100, 50, 20, 10, 5, 1};
    private static final String[] REGISTRY_NAMES = {
        "economy:item_fivehundreedb",
        "economy:item_twohundreedb",
        "economy:item_hundreedb",
        "economy:item_fiftybe",
        "economy:item_twentyb",
        "economy:item_tenb",
        "economy:item_fiveb",
        "economy:item_oneb",
    };

    /** Denominations offered as withdraw buttons in the ATM GUI. Smaller subset of DENOMINATIONS
     *  to keep the GUI compact; $200 and $500 are reachable by repeated withdraws of $100. */
    public static final int[] WITHDRAW_DENOMINATIONS = {1, 5, 10, 20, 50, 100};

    private static volatile Map<Integer, Item> resolvedDenomToItem;
    private static volatile Map<Item, Integer> resolvedItemToDenom;

    private Bills() {}

    public static int[] allDenominationsHighToLow() {
        return DENOMINATIONS.clone();
    }

    private static void resolveIfNeeded() {
        if (resolvedDenomToItem != null) {
            return;
        }
        synchronized (Bills.class) {
            if (resolvedDenomToItem != null) {
                return;
            }
            Map<Integer, Item> denomToItem = new LinkedHashMap<>();
            Map<Item, Integer> itemToDenom = new LinkedHashMap<>();
            for (int i = 0; i < DENOMINATIONS.length; i++) {
                Item item = Item.REGISTRY.getObject(new ResourceLocation(REGISTRY_NAMES[i]));
                if (item == null) {
                    Sum.LOGGER.warn("[economy] Bill item not found in registry: {}", REGISTRY_NAMES[i]);
                    continue;
                }
                denomToItem.put(DENOMINATIONS[i], item);
                itemToDenom.put(item, DENOMINATIONS[i]);
            }
            resolvedDenomToItem = Collections.unmodifiableMap(denomToItem);
            resolvedItemToDenom = Collections.unmodifiableMap(itemToDenom);
            Sum.LOGGER.info("[economy] Bill resolver bound {} of {} denominations.",
                denomToItem.size(), DENOMINATIONS.length);
        }
    }

    /** @return the EconomyInc {@link Item} for {@code denomination}, or null if missing. */
    @Nullable
    public static Item billItem(int denomination) {
        resolveIfNeeded();
        return resolvedDenomToItem.get(denomination);
    }

    /** @return the dollar value of {@code item} if it's a known bill, else 0. */
    public static int denominationOf(Item item) {
        resolveIfNeeded();
        Integer v = resolvedItemToDenom.get(item);
        return v == null ? 0 : v;
    }
}
