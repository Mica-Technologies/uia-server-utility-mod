package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * Armor durability summary — shows the lowest-durability armor piece's percentage so
 * the player knows when something's about to break. Modeled on EvergreenHUD's armour
 * element.
 */
public class ArmourHud extends SingleTextHud {

    public ArmourHud() {
        super("Armor:", true, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return "62%";
        }
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) {
            return "—";
        }
        // Walk all four armor slots; report the lowest non-broken percentage. Empty slots
        // are skipped (not "0%" — that would always anchor to zero).
        int lowest = -1;
        for (EntityEquipmentSlot slot : new EntityEquipmentSlot[] {
                EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET }) {
            ItemStack stack = player.getItemStackFromSlot(slot);
            if (stack.isEmpty() || !stack.isItemStackDamageable()) {
                continue;
            }
            int max = stack.getMaxDamage();
            int remaining = max - stack.getItemDamage();
            int pct = (int) Math.round(100.0 * remaining / max);
            if (lowest < 0 || pct < lowest) {
                lowest = pct;
            }
        }
        return lowest < 0 ? "—" : (lowest + "%");
    }
}
