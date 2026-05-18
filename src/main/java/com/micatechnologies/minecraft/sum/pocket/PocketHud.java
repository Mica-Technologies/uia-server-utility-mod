package com.micatechnologies.minecraft.sum.pocket;

import cc.polyfrost.oneconfig.hud.BasicHud;
import cc.polyfrost.oneconfig.libs.universal.UMatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * OneConfig HUD module that paints the three pocket slots (phone / debit card / bills)
 * as a compact icon row. OneConfig handles persistence, drag-to-reposition, scale, the
 * standard background/border knobs, and HUD enable/disable through its built-in editor —
 * SUM just supplies the per-frame draw + measurements.
 *
 * <p>Read-only display: to swap slot contents, open the pocket GUI via the SUM keybind.
 * Reads from the synced client-side {@link PocketInventory} capability that
 * {@link PocketEvents} keeps in lockstep with the server.
 */
public class PocketHud extends BasicHud {

    /** Pixel size of each slot at scale=1.0. Matches the vanilla hotbar slot frame. */
    private static final int SLOT_PX = 22;
    /** Spacing between adjacent slots in HUD pixels at scale=1.0. */
    private static final int SLOT_GAP = 2;

    private static final int SLOT_BG = 0x80101010;
    private static final int SLOT_BORDER = 0xFF202020;
    private static final int SLOT_BORDER_EMPTY = 0xFF383838;

    public PocketHud() {
        // Default: hidden until enabled, anchored near bottom-right on a 1080p screen,
        // background off (the slot frames already provide visual containment). User can
        // toggle background/border in OneConfig.
        super(true, 1700f, 1010f, 1f, false, false, 2, 5, 5,
            new cc.polyfrost.oneconfig.config.core.OneColor(0, 0, 0, 120),
            false, 2,
            new cc.polyfrost.oneconfig.config.core.OneColor(0, 0, 0));
    }

    @Override
    protected void draw(UMatrixStack matrices, float x, float y, float scale, boolean example) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        PocketInventory pocket = player != null ? PocketInventory.get(player) : null;
        // In OneConfig's editor example-mode, render placeholder frames so the user can see
        // and drag the HUD before the capability has been synced.
        PocketInventory effective = (example || pocket == null) ? new PocketInventory() : pocket;

        int slotPx = Math.round(SLOT_PX * scale);
        int gap = Math.round(SLOT_GAP * scale);
        int xi = Math.round(x);
        int yi = Math.round(y);

        // Backgrounds first, item icons on top.
        for (int i = 0; i < PocketInventory.SLOT_COUNT; i++) {
            int sx = xi + i * (slotPx + gap);
            ItemStack stack = effective.getStackInSlot(i);
            int border = stack.isEmpty() ? SLOT_BORDER_EMPTY : SLOT_BORDER;
            net.minecraft.client.gui.Gui.drawRect(sx, yi, sx + slotPx, yi + slotPx, border);
            net.minecraft.client.gui.Gui.drawRect(
                sx + 1, yi + 1, sx + slotPx - 1, yi + slotPx - 1, SLOT_BG);
        }

        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < PocketInventory.SLOT_COUNT; i++) {
            ItemStack stack = effective.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            int sx = xi + i * (slotPx + gap);
            int iconX = sx + (slotPx - 16) / 2;
            int iconY = yi + (slotPx - 16) / 2;
            mc.getRenderItem().renderItemAndEffectIntoGUI(stack, iconX, iconY);
            mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, stack,
                iconX, iconY, null);
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    @Override
    protected float getWidth(float scale, boolean example) {
        return (SLOT_PX * scale * PocketInventory.SLOT_COUNT)
            + (SLOT_GAP * scale * (PocketInventory.SLOT_COUNT - 1));
    }

    @Override
    protected float getHeight(float scale, boolean example) {
        return SLOT_PX * scale;
    }
}
