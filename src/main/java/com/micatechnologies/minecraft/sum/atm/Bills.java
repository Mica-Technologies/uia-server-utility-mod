package com.micatechnologies.minecraft.sum.atm;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.economy.EconomyBridge;
import com.micatechnologies.minecraft.sum.economy.ItemSumBill;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

/**
 * Lookup helper for currency bill items used by the ATM. Bills are physical money items
 * (separate from the balance capability) in eight denominations: $1, $5, $10, $20, $50,
 * $100, $200, $500. Withdraw turns balance into bill items; deposit converts bill items back
 * into balance.
 *
 * <p>Two backends, mirroring the {@link EconomyBridge} facade:
 * <ul>
 *   <li><b>EconomyInc</b> (preferred when loaded): {@code economy:item_oneb}..
 *       {@code economy:item_fivehundreedb}. Registry names verified from EconomyInc 1.6.2 -
 *       the upstream typos {@code item_fiftybe} and {@code item_hundreedb} are intentional.</li>
 *   <li><b>SUM</b> (always available since C2): {@code sum:bill_1}..{@code sum:bill_500}.
 *       SUM bills coexist with EconomyInc bills when both mods are loaded - {@link #denominationOf}
 *       accepts both, so old EconomyInc bills can be deposited even after a migration to SUM.</li>
 * </ul>
 *
 * <p>{@link #billItem} returns whichever backend is preferred for new bills produced by the ATM:
 * EconomyInc when loaded, SUM otherwise.
 */
public final class Bills {

    /** Denominations from highest to lowest. */
    private static final int[] DENOMINATIONS = {500, 200, 100, 50, 20, 10, 5, 1};

    private static final String[] ECONOMY_INC_REGISTRY_NAMES = {
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

    /** Lazily-built map: {@code denomination -> preferred bill Item}. EconomyInc bills if
     *  EconomyInc is loaded, SUM bills otherwise. Built at first use. */
    private static volatile Map<Integer, Item> preferredBillByDenom;
    /** Lazily-built map: {@code billItem -> denomination}. Includes BOTH EconomyInc and SUM
     *  bills, so deposits accept either type seamlessly. */
    private static volatile Map<Item, Integer> denomByAnyBillItem;

    private Bills() {}

    public static int[] allDenominationsHighToLow() {
        return DENOMINATIONS.clone();
    }

    private static void resolveIfNeeded() {
        if (preferredBillByDenom != null) {
            return;
        }
        synchronized (Bills.class) {
            if (preferredBillByDenom != null) {
                return;
            }

            Map<Integer, Item> economyIncByDenom = new LinkedHashMap<>();
            Map<Integer, Item> sumByDenom = new LinkedHashMap<>();
            Map<Item, Integer> anyByItem = new LinkedHashMap<>();

            // EconomyInc bills (only present if EconomyInc is loaded)
            for (int i = 0; i < DENOMINATIONS.length; i++) {
                Item item = Item.REGISTRY.getObject(new ResourceLocation(ECONOMY_INC_REGISTRY_NAMES[i]));
                if (item != null) {
                    economyIncByDenom.put(DENOMINATIONS[i], item);
                    anyByItem.put(item, DENOMINATIONS[i]);
                }
            }

            // SUM bills (always present after Sum.preInit registers them)
            for (int denom : DENOMINATIONS) {
                Item item = Item.REGISTRY.getObject(
                    new ResourceLocation("sum", "bill_" + denom));
                if (item instanceof ItemSumBill) {
                    sumByDenom.put(denom, item);
                    anyByItem.put(item, denom);
                }
            }

            // Prefer EconomyInc when it's the active backend; else SUM. Empty if neither is
            // available (shouldn't happen post-init since SUM bills always register).
            Map<Integer, Item> preferred = EconomyBridge.isEconomyIncBackend() && !economyIncByDenom.isEmpty()
                ? economyIncByDenom
                : sumByDenom;

            preferredBillByDenom = Collections.unmodifiableMap(preferred);
            denomByAnyBillItem = Collections.unmodifiableMap(anyByItem);

            Sum.LOGGER.info("[economy] Bill resolver: {} EconomyInc denominations, {} SUM denominations,"
                + " preferred backend = {}.",
                economyIncByDenom.size(), sumByDenom.size(),
                preferred == economyIncByDenom ? "EconomyInc" : "SUM");
        }
    }

    /** @return the preferred-backend {@link Item} for {@code denomination}, or null if missing.
     *  Used when the ATM withdraws and needs to produce physical bills. */
    @Nullable
    public static Item billItem(int denomination) {
        resolveIfNeeded();
        return preferredBillByDenom.get(denomination);
    }

    /** @return the dollar value of {@code item} if it's a known bill (EconomyInc OR SUM),
     *  else 0. Used when the ATM deposits and needs to count any bills the player has. */
    public static int denominationOf(Item item) {
        resolveIfNeeded();
        Integer v = denomByAnyBillItem.get(item);
        return v == null ? 0 : v;
    }
}
