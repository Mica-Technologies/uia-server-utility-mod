package com.micatechnologies.minecraft.sum.picture;

import com.micatechnologies.minecraft.sum.picture.PictureCache.Picture;
import com.micatechnologies.minecraft.sum.picture.PictureFrameSettings.Fit;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import org.lwjgl.opengl.GL11;

/**
 * Draws a picture frame's image, or a plate with a status line while there is none to draw.
 *
 * <p>Geometry is worked out in a local frame where the picture faces +Z and the viewer's right is
 * +X, which is the block's south-facing orientation; other facings rotate into it the way vanilla
 * wall signs do. In that frame the frame block spans x and z in [-0.5, 0.5] and y in [0, 1], the
 * plate sits against the wall at z = -0.5, and the picture is drawn just in front of the plate.
 *
 * <p>Pictures are lit by the light at the frame, or full-bright when the frame is set to glow,
 * and are mipmapped (see {@link PictureCache}) so a large picture seen from far away does not
 * shimmer.
 */
public class TESRPictureFrame extends TileEntitySpecialRenderer<TileEntityPictureFrame> {

    private static final double PLATE_FRONT = -0.5 + BlockPictureFrame.THICKNESS;
    private static final double PICTURE_Z = PLATE_FRONT + 0.002;

    @Override
    public boolean isGlobalRenderer(TileEntityPictureFrame te) {
        // A picture can be many times larger than its block; keep drawing it when the block is
        // off-screen. getRenderBoundingBox still culls it against the view frustum.
        return true;
    }

    @Override
    public void render(TileEntityPictureFrame te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        IBlockState state = te.getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof BlockPictureFrame)) return;
        EnumFacing facing = state.getValue(BlockPictureFrame.FACING);
        PictureFrameSettings s = te.getSettings();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-rotationFor(facing), 0.0F, 1.0F, 0.0F);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO);
        applyLight(te, s.isGlow());

        double x0 = -0.5 + s.getOffsetX();
        double y0 = s.getOffsetY();
        double x1 = x0 + s.getWidth();
        double y1 = y0 + s.getHeight();

        Picture picture = null;
        if (s.hasUrl() && PictureClientSettings.enabled()) {
            picture = PictureCache.INSTANCE.get(s.getUrl());
        }
        if (picture != null && picture.getStatus() == PictureCache.Status.READY) {
            drawPicture(picture, s, x0, y0, x1, y1);
        } else {
            drawPlate();
            drawStatus(statusText(s, picture));
        }

        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /** Same mapping as vanilla's wall sign renderer: SOUTH is the unrotated frame. */
    static float rotationFor(EnumFacing facing) {
        switch (facing) {
            case NORTH: return 180.0F;
            case WEST:  return 90.0F;
            case EAST:  return -90.0F;
            case SOUTH:
            default:    return 0.0F;
        }
    }

    private void applyLight(TileEntityPictureFrame te, boolean glow) {
        if (glow) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
            return;
        }
        int light = te.getWorld().getCombinedLight(te.getPos(), 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
            (float) (light & 0xFFFF), (float) (light >> 16));
    }

    private void drawPicture(Picture picture, PictureFrameSettings s,
                             double x0, double y0, double x1, double y1) {
        int rotation = s.getRotation();
        // The image's on-wall aspect: a quarter turn swaps its width and height.
        double imageW = rotation % 2 == 0 ? picture.getWidth() : picture.getHeight();
        double imageH = rotation % 2 == 0 ? picture.getHeight() : picture.getWidth();
        double areaW = x1 - x0;
        double areaH = y1 - y0;
        double imageAspect = imageW / imageH;
        double areaAspect = areaW / areaH;

        // Fit shrinks the quad, Fill shrinks the texture window; Stretch does neither.
        double u0 = 0.0, v0 = 0.0, u1 = 1.0, v1 = 1.0;
        if (s.getFit() == Fit.FIT) {
            if (imageAspect > areaAspect) {
                double h = areaW / imageAspect;
                y0 += (areaH - h) / 2.0;
                y1 = y0 + h;
            } else {
                double w = areaH * imageAspect;
                x0 += (areaW - w) / 2.0;
                x1 = x0 + w;
            }
        } else if (s.getFit() == Fit.FILL) {
            // Crop on-wall width or height, then map the crop back onto the unrotated texture.
            double cropW = 1.0, cropH = 1.0;
            if (imageAspect > areaAspect) {
                cropW = areaAspect / imageAspect;
            } else {
                cropH = imageAspect / areaAspect;
            }
            double cu = rotation % 2 == 0 ? cropW : cropH;
            double cv = rotation % 2 == 0 ? cropH : cropW;
            u0 = (1.0 - cu) / 2.0;
            u1 = u0 + cu;
            v0 = (1.0 - cv) / 2.0;
            v1 = v0 + cv;
        }

        // Texture coordinates for the bottom-left, bottom-right, top-right and top-left corners
        // of the quad, rotated clockwise a quarter turn per step. Image row 0 is the top (v0).
        double[][] corners = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        double[][] uv = new double[4][];
        for (int i = 0; i < 4; i++) {
            uv[i] = corners[(i + rotation) % 4];
        }

        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(picture.textureAt(Minecraft.getSystemTime()));
        GlStateManager.color(1.0F, 1.0F, 1.0F, s.getOpacity());
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buf.pos(x0, y0, PICTURE_Z).tex(uv[0][0], uv[0][1]).endVertex();
        buf.pos(x1, y0, PICTURE_Z).tex(uv[1][0], uv[1][1]).endVertex();
        buf.pos(x1, y1, PICTURE_Z).tex(uv[2][0], uv[2][1]).endVertex();
        buf.pos(x0, y1, PICTURE_Z).tex(uv[3][0], uv[3][1]).endVertex();
        tess.draw();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** A dark plate the size of the block, so an empty or loading frame is still visible. */
    private void drawPlate() {
        GlStateManager.disableTexture2D();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        // Wooden rim, then the dark panel inside it.
        quad(buf, -0.5, 0.0, 0.5, 1.0, PICTURE_Z - 0.001, 0.42F, 0.30F, 0.18F, 1.0F);
        quad(buf, -0.4375, 0.0625, 0.4375, 0.9375, PICTURE_Z, 0.08F, 0.08F, 0.10F, 1.0F);
        tess.draw();
        GlStateManager.enableTexture2D();
    }

    private static void quad(BufferBuilder buf, double x0, double y0, double x1, double y1, double z,
                             float r, float g, float b, float a) {
        buf.pos(x0, y0, z).color(r, g, b, a).endVertex();
        buf.pos(x1, y0, z).color(r, g, b, a).endVertex();
        buf.pos(x1, y1, z).color(r, g, b, a).endVertex();
        buf.pos(x0, y1, z).color(r, g, b, a).endVertex();
    }

    static String statusText(PictureFrameSettings s, Picture picture) {
        if (!s.hasUrl()) return "Right-click to choose a picture";
        if (!PictureClientSettings.enabled()) return "Pictures are turned off in SUM settings";
        if (picture == null || picture.getStatus() == PictureCache.Status.LOADING) return "Loading...";
        return picture.getError();
    }

    /** Wrapped, centred text on the plate's panel. */
    private void drawStatus(String text) {
        FontRenderer font = getFontRenderer();
        if (font == null) return;
        float scale = 1.0F / 160.0F;
        int wrapWidth = 130;
        List<String> lines = font.listFormattedStringToWidth(text, wrapWidth);
        if (lines.size() > 8) lines = lines.subList(0, 8);
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0, 0.5, PICTURE_Z + 0.002);
        GlStateManager.scale(scale, -scale, scale);
        GlStateManager.depthMask(false);
        int lineHeight = font.FONT_HEIGHT + 1;
        int top = -(lines.size() * lineHeight) / 2;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            font.drawString(line, -font.getStringWidth(line) / 2, top + i * lineHeight, 0xFFE0E0E0);
        }
        GlStateManager.depthMask(true);
        GlStateManager.popMatrix();
    }
}
