package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import com.micatechnologies.minecraft.sum.pocket.PocketInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * Text version of the icon-based pocket HUD — useful for layouts that prefer
 * plain text (e.g. the Realistic Game HUD style at scale 0.4 where 16px item
 * icons look out of place). Lists the display name of each occupied pocket slot,
 * comma-separated, or "(empty)" if all three slots are empty.
 *
 * <p>Reads from the same client-synced {@link PocketInventory} capability as the
 * graphical PocketHud, so the two stay in lockstep without extra wiring.</p>
 */
public class PocketTextHud extends SingleTextHud {

    public PocketTextHud() {
        super("Pocket:", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) return "Phone, Debit Card, Bills";
        EntityPlayer p = Minecraft.getMinecraft().player;
        if (p == null) return "—";
        PocketInventory pocket = PocketInventory.get(p);
        if (pocket == null) return "—";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PocketInventory.SLOT_COUNT; i++) {
            ItemStack stack = pocket.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(stack.getDisplayName());
        }
        return sb.length() == 0 ? "(empty)" : sb.toString();
    }
}
