package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * Remaining durability of the main-hand item — usually more relevant than the
 * armor-durability HUD, since a tool breaking mid-action is more disruptive than
 * armor degrading slowly over time.
 */
public class DurabilityHud extends SingleTextHud {

    /** Show "1245 (98%)" instead of just "1245". */
    @Switch(name = "Show percent")
    public boolean showPercent = true;

    public DurabilityHud() {
        super("Tool:", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) return showPercent ? "1245 (98%)" : "1245";
        EntityPlayer p = Minecraft.getMinecraft().player;
        if (p == null) return "—";
        ItemStack stack = p.getHeldItemMainhand();
        if (stack.isEmpty() || !stack.isItemStackDamageable()) return "—";
        int max = stack.getMaxDamage();
        int remaining = max - stack.getItemDamage();
        if (!showPercent) return Integer.toString(remaining);
        int pct = max == 0 ? 0 : Math.round(remaining * 100f / max);
        return remaining + " (" + pct + "%)";
    }
}
