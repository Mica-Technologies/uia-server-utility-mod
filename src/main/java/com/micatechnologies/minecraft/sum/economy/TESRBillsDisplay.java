package com.micatechnologies.minecraft.sum.economy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;

/**
 * Renders the contained bill/packet stack hovering above {@link BlockBillsDisplay}. Pile
 * height tiers up with the stack count so a single bill shows as one floating note while a
 * full stack reads as a thick wad.
 *
 * <p>The bill items themselves are flat 16×16 sprites; rendered as 3D ItemStacks they show
 * up as thin slabs. Slowly rotating the pile gives the eye something to lock onto and makes
 * the denomination readable from any angle without requiring full 3D models.
 */
public class TESRBillsDisplay extends TileEntitySpecialRenderer<TileEntityBillsDisplay> {

    private static final float BASE_Y = 0.30F;
    private static final float LAYER_Y_GAP = 0.04F;
    private static final float SCALE = 0.6F;
    private static final float ROTATE_DEG_PER_TICK = 1.5F;

    @Override
    public void render(TileEntityBillsDisplay te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        ItemStack stack = te.getDisplayStack();
        if (stack.isEmpty()) {
            return;
        }

        int layers = pileLayersFor(stack);
        float worldTime = te.getWorld().getTotalWorldTime() + partialTicks;
        float rot = (worldTime * ROTATE_DEG_PER_TICK) % 360.0F;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + BASE_Y, z + 0.5);
        GlStateManager.scale(SCALE, SCALE, SCALE);
        RenderHelper.enableStandardItemLighting();

        for (int i = 0; i < layers; i++) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0.0, i * LAYER_Y_GAP / SCALE, 0.0);
            // Each layer rotates 45° offset from the previous so the stack reads as a
            // crossed/woven pile rather than identical sheets.
            GlStateManager.rotate(rot + i * 45.0F, 0.0F, 1.0F, 0.0F);
            // Render as a flat-on-the-ground item.
            GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
            Minecraft.getMinecraft().getRenderItem().renderItem(stack,
                ItemCameraTransforms.TransformType.GROUND);
            GlStateManager.popMatrix();
        }

        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    /** Number of overlaid sheets to render based on count. */
    private static int pileLayersFor(ItemStack stack) {
        int count = stack.getCount();
        if (count <= 1) return 1;
        if (count <= 5) return 2;
        if (count <= 16) return 3;
        if (count <= 32) return 4;
        return 5;
    }
}
