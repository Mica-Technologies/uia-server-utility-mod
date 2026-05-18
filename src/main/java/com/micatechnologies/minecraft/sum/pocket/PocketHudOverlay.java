package com.micatechnologies.minecraft.sum.pocket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * HUD overlay that paints the player's pocket slots above (or wherever
 * {@link PocketHudConfig} anchors) the hotbar. Read-only display: the overlay surfaces what
 * the pocket contains so the player can see their phone / debit card / bills count without
 * opening the pocket GUI. To modify slot contents, open the GUI ({@link GuiPocket}) via
 * the SUM keybind.
 *
 * <p>Driven by the synced client-side capability mutated by {@link PacketSyncPocket}, so the
 * HUD always reflects authoritative server state.
 *
 * <p>The HUD position/visibility is configurable via {@link PocketHudConfig} and editable
 * live in {@link GuiPocketHudEditor}. The drag-to-reposition UX is inspired by
 * Polyfrost's EvergreenHUD — see README for attribution.
 */
public class PocketHudOverlay extends Gui {

    /** Pixel size of each slot in the HUD (vanilla hotbar uses 22px slot frames including
     *  the 2px outline). */
    static final int SLOT_PX = 22;
    /** Spacing between adjacent slots in HUD pixels. */
    static final int SLOT_GAP = 2;
    /** Total HUD block width at scale=1.0 (3 slots + 2 gaps). */
    static final int BLOCK_W = SLOT_PX * PocketInventory.SLOT_COUNT
        + SLOT_GAP * (PocketInventory.SLOT_COUNT - 1);
    /** Total HUD block height at scale=1.0. */
    static final int BLOCK_H = SLOT_PX;

    private static final int SLOT_BG = 0x80101010;
    private static final int SLOT_BORDER = 0xFF202020;
    private static final int SLOT_BORDER_EMPTY = 0xFF383838;

    private final Minecraft mc = Minecraft.getMinecraft();

    @SubscribeEvent
    public void onRenderHud(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.HOTBAR) {
            return;
        }
        if (!PocketHudConfig.isEnabled()) {
            return;
        }
        EntityPlayer player = mc.player;
        if (player == null || player.isSpectator()) {
            return;
        }
        PocketInventory pocket = PocketInventory.get(player);
        if (pocket == null) {
            return;
        }

        ScaledResolution sr = new ScaledResolution(mc);
        int screenW = sr.getScaledWidth();
        int screenH = sr.getScaledHeight();
        int[] origin = computeOrigin(screenW, screenH);
        renderSlotsAt(pocket, origin[0], origin[1], PocketHudConfig.getScale());
    }

    /**
     * Compute the HUD's top-left pixel coordinate from the configured anchor +
     * screen dimensions. Exposed package-private so {@link GuiPocketHudEditor} can match
     * positioning exactly while dragging.
     */
    static int[] computeOrigin(int screenW, int screenH) {
        float scale = PocketHudConfig.getScale();
        int w = (int) (BLOCK_W * scale);
        int h = (int) (BLOCK_H * scale);
        // The anchor is fractional 0..1 over the entire screen, and represents where the
        // HUD's top-left corner lands. Multiplying by (screen - block) keeps the HUD fully
        // on-screen at any anchor value.
        int x = Math.round(PocketHudConfig.getAnchorX() * Math.max(0, screenW - w));
        int y = Math.round(PocketHudConfig.getAnchorY() * Math.max(0, screenH - h));
        return new int[] { x, y };
    }

    /**
     * Pixel-pixel render of the three-slot block at the given screen position. Public so
     * the editor can call it during drag preview without round-tripping through the event
     * pipeline. {@code scale} multiplies all dimensions; passing 1.0 produces a hotbar-
     * sized block.
     */
    void renderSlotsAt(PocketInventory pocket, int x, int y, float scale) {
        int slotPx = Math.round(SLOT_PX * scale);
        int gap = Math.round(SLOT_GAP * scale);

        // Per-slot frames first (with the dim background + border).
        for (int i = 0; i < PocketInventory.SLOT_COUNT; i++) {
            int sx = x + i * (slotPx + gap);
            ItemStack stack = pocket.getStackInSlot(i);
            int border = stack.isEmpty() ? SLOT_BORDER_EMPTY : SLOT_BORDER;
            drawRect(sx, y, sx + slotPx, y + slotPx, border);
            drawRect(sx + 1, y + 1, sx + slotPx - 1, y + slotPx - 1, SLOT_BG);
        }

        // Item icons on top, centered in their frame. We can't scale ItemStack rendering
        // cleanly through GlStateManager.scale without breaking the depth/lighting state
        // the rest of the HUD relies on — so we always render the item at the vanilla 16px
        // size and let the surrounding frame grow/shrink with scale. The frame stays
        // proportional and the icon remains crisp.
        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < PocketInventory.SLOT_COUNT; i++) {
            ItemStack stack = pocket.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            int sx = x + i * (slotPx + gap);
            int iconX = sx + (slotPx - 16) / 2;
            int iconY = y + (slotPx - 16) / 2;
            mc.getRenderItem().renderItemAndEffectIntoGUI(stack, iconX, iconY);
            mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, stack,
                iconX, iconY, null);
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1F, 1F, 1F, 1F);
    }
}
