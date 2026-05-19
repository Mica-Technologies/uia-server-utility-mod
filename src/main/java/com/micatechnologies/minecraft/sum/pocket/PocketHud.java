package com.micatechnologies.minecraft.sum.pocket;

import cc.polyfrost.oneconfig.hud.BasicHud;
import cc.polyfrost.oneconfig.libs.universal.UMatrixStack;
import com.micatechnologies.minecraft.sum.favorites.FavoriteKey;
import com.micatechnologies.minecraft.sum.favorites.FavoritesClientHandler;
import com.micatechnologies.minecraft.sum.favorites.FavoritesStore;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

/**
 * Top-three SUM favorites painted as a compact icon row, with the favorites-tab
 * keybind label rendered inline as a prefix — e.g. {@code Z: [icon][icon][icon]}.
 *
 * <p>Class name is historical — this was originally a pocket-inventory preview
 * HUD before the pocket-into-vanilla-inventory integration was scrapped. Kept as
 * {@code PocketHud} so existing user-saved OneConfig positions continue to load
 * by field name without breaking. The {@code @HUD(name = ...)} entry in
 * {@link SumOneConfig} carries the user-visible "Favorites HUD" label.</p>
 *
 * <p>Reads from {@link FavoritesStore} every frame — the store is process-local
 * and cheap to read, so caching wouldn't pay off here.</p>
 */
public class PocketHud extends BasicHud {

    /** Pixel size of each slot at scale=1.0. Matches the vanilla hotbar slot frame. */
    private static final int SLOT_PX = 22;
    /** Spacing between adjacent slots in HUD pixels at scale=1.0. */
    private static final int SLOT_GAP = 2;
    /** How many favorites to display. Keeps the HUD compact and matches the original
     *  three-slot footprint so positions transferred from the old pocket HUD still
     *  feel right. */
    private static final int FAVORITES_SHOWN = 3;
    /** Conservative pixel reserve for the keybind label when font metrics aren't
     *  available yet (eg. during early HUD construction before {@code mc.fontRenderer}
     *  is ready). 18px = "F12:" worst case; for the typical "Z:" the actual
     *  measurement at draw time will be smaller and slots shift left accordingly. */
    private static final int LABEL_RESERVE_PX = 18;

    private static final int SLOT_BG = 0x80101010;
    private static final int SLOT_BORDER = 0xFF202020;
    private static final int SLOT_BORDER_EMPTY = 0xFF383838;
    private static final int KEY_LABEL_COLOR = 0xFFFFFF80;
    private static final int KEY_LABEL_SHADOW = 0xFF000000;

    public PocketHud() {
        // Default: enabled, anchored near bottom-right on a 1080p screen, background
        // off (the slot frames already provide visual containment). User can toggle
        // background/border in OneConfig.
        super(true, 1700f, 1010f, 1f, false, false, 2, 5, 5,
            new cc.polyfrost.oneconfig.config.core.OneColor(0, 0, 0, 120),
            false, 2,
            new cc.polyfrost.oneconfig.config.core.OneColor(0, 0, 0));
    }

    @Override
    protected void draw(UMatrixStack matrices, float x, float y, float scale, boolean example) {
        Minecraft mc = Minecraft.getMinecraft();

        // Snapshot once per frame; the store can mutate from key events.
        List<FavoriteKey> favorites = FavoritesStore.snapshot();

        int slotPx = Math.round(SLOT_PX * scale);
        int gap = Math.round(SLOT_GAP * scale);
        int xi = Math.round(x);
        int yi = Math.round(y);

        // Inline keybind prefix: "Z: " becomes the left-most element of the row,
        // vertically centred against the slot frames. Slot frames + icons then
        // start to the right of the label, offset by labelOffset.
        String labelStr = labelStringForKeyBind();
        int labelPxUnscaled = mc.fontRenderer != null
            ? mc.fontRenderer.getStringWidth(labelStr)
            : LABEL_RESERVE_PX;
        int labelOffset = labelStr.isEmpty()
            ? 0
            : Math.round(labelPxUnscaled * scale) + gap;

        // Draw the prefix first, scaled to match the HUD scale so it shrinks/grows
        // with the slots instead of fixed at 8px tall (which dwarfs everything at
        // Realistic Game HUD scale 0.4).
        if (!labelStr.isEmpty() && mc.fontRenderer != null) {
            FontRenderer fr = mc.fontRenderer;
            float labelY = yi + (slotPx - fr.FONT_HEIGHT * scale) / 2f;
            GlStateManager.pushMatrix();
            GlStateManager.translate(xi, labelY, 0f);
            GlStateManager.scale(scale, scale, 1f);
            fr.drawString(labelStr, 1, 1, KEY_LABEL_SHADOW);
            fr.drawString(labelStr, 0, 0, KEY_LABEL_COLOR);
            GlStateManager.popMatrix();
        }

        int rowStartX = xi + labelOffset;

        // Slot frames after the label.
        for (int i = 0; i < FAVORITES_SHOWN; i++) {
            int sx = rowStartX + i * (slotPx + gap);
            boolean present = i < favorites.size();
            int border = present ? SLOT_BORDER : SLOT_BORDER_EMPTY;
            net.minecraft.client.gui.Gui.drawRect(sx, yi, sx + slotPx, yi + slotPx, border);
            net.minecraft.client.gui.Gui.drawRect(
                sx + 1, yi + 1, sx + slotPx - 1, yi + slotPx - 1, SLOT_BG);
        }

        // Item icons. Minecraft's renderItemAndEffectIntoGUI always paints at 16x16
        // regardless of HUD scale, so at small scales (eg. Realistic Game HUD at
        // 0.4x → slotPx ≈ 9) icons would overflow the slot frame and spill onto
        // neighbouring HUDs. Wrapping with GlStateManager.scale() shrinks the
        // 16x16 output to fit the slot, with a 2px padding on each side at the
        // current scale.
        float iconBox = Math.max(4f, slotPx - 4f);
        float iconScale = iconBox / 16f;
        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < FAVORITES_SHOWN && i < favorites.size(); i++) {
            ItemStack stack = favorites.get(i).resolveStack();
            if (stack.isEmpty()) {
                continue;
            }
            int sx = rowStartX + i * (slotPx + gap);
            float iconX = sx + (slotPx - iconBox) / 2f;
            float iconY = yi + (slotPx - iconBox) / 2f;
            GlStateManager.pushMatrix();
            GlStateManager.translate(iconX, iconY, 0f);
            GlStateManager.scale(iconScale, iconScale, 1f);
            mc.getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
            mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, stack, 0, 0, null);
            GlStateManager.popMatrix();
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    @Override
    protected float getWidth(float scale, boolean example) {
        float slotsWidth = (SLOT_PX * scale * FAVORITES_SHOWN)
            + (SLOT_GAP * scale * (FAVORITES_SHOWN - 1));
        String labelStr = labelStringForKeyBind();
        if (labelStr.isEmpty()) {
            return slotsWidth;
        }
        Minecraft mc = Minecraft.getMinecraft();
        int labelPx = (mc != null && mc.fontRenderer != null)
            ? mc.fontRenderer.getStringWidth(labelStr)
            : LABEL_RESERVE_PX;
        return labelPx * scale + SLOT_GAP * scale + slotsWidth;
    }

    @Override
    protected float getHeight(float scale, boolean example) {
        return SLOT_PX * scale;
    }

    /** "{key}: " when the favorites JUMP keybind is bound, empty string otherwise. */
    private static String labelStringForKeyBind() {
        int code = FavoritesClientHandler.JUMP.getKeyCode();
        if (code == Keyboard.KEY_NONE) {
            return "";
        }
        try {
            String name = Keyboard.getKeyName(code);
            return (name == null || name.isEmpty()) ? "" : name + ": ";
        } catch (Throwable t) {
            return "";
        }
    }
}
