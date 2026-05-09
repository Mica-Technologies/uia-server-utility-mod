package com.micatechnologies.minecraft.sum.signpost;

import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.text.TextFormatting;

/**
 * Renders each arm of a {@link TileEntitySignpost} as a label text floating at the arm's
 * configured compass angle. Arms stack vertically up the post; the first arm sits low on
 * the post, later arms higher. Text reads outward along the arm direction.
 */
public class TESRSignpost extends TileEntitySpecialRenderer<TileEntitySignpost> {

    private static final float ARM_BASE_Y = 0.55F;
    private static final float ARM_STEP_Y = 0.10F;
    private static final float ARM_OUT_DISTANCE = 0.55F;
    private static final float TEXT_SCALE = 0.012F;

    @Override
    public void render(TileEntitySignpost te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        List<SignpostArm> arms = te.getArms();
        if (arms.isEmpty()) {
            return;
        }
        FontRenderer fr = getFontRenderer();
        if (fr == null) {
            return;
        }

        for (int i = 0; i < arms.size(); i++) {
            SignpostArm arm = arms.get(i);
            String label = arm.getLabel();
            if (label.isEmpty()) {
                continue;
            }
            float armY = ARM_BASE_Y + i * ARM_STEP_Y;
            // Compass convention: 0=N/-Z, 90=E/+X, 180=S/+Z, 270=W/-X.
            // OpenGL Y-rotation around (0,1,0) with positive degree rotates X axis toward -Z.
            // After rotating by `angle`, the new +X points in the compass direction.
            float angle = arm.getAngleDegrees() - 90.0F;

            GlStateManager.pushMatrix();
            GlStateManager.translate(x + 0.5, y + armY, z + 0.5);
            GlStateManager.rotate(-angle, 0.0F, 1.0F, 0.0F);
            GlStateManager.translate(ARM_OUT_DISTANCE, 0.0F, 0.0F);
            // Yaw the text plate so it faces outward along the arm and reads left-to-right
            // when viewed from someone walking up to the side of the arm.
            GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
            GlStateManager.disableLighting();

            int width = fr.getStringWidth(label);
            int color = 0xFFFFFFFF;
            int shadowColor = 0xFF202020;
            // Dark backdrop pass for legibility
            fr.drawString(label, -width / 2 + 1, 1, shadowColor);
            fr.drawString(label, -width / 2, 0, color);

            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }
    }

    @Override
    public boolean isGlobalRenderer(TileEntitySignpost te) {
        // Render even when the TE's bounding box is offscreen — labels can extend past
        // the post's bounds, especially with longer text.
        return false;
    }

    @SuppressWarnings("unused")
    private static String dim(String s) {
        return TextFormatting.GRAY + s;
    }
}
