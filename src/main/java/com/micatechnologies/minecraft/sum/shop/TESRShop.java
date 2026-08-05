package com.micatechnologies.minecraft.sum.shop;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

/**
 * Floats a shop's sale item inside the cabinet, behind the glass, so a storefront can be read
 * from across the street without opening anything. Renders the template stack — the same item
 * the buyer GUI shows — and nothing at all when the shop is unconfigured.
 *
 * <p>The item is rendered with {@link ItemCameraTransforms.TransformType#NONE}, which is the
 * only transform that hands back an unmodified unit-sized model; every other one bakes in its
 * own scale (GROUND shrinks blocks to a quarter) and would make {@link #SCALE} mean something
 * different for blocks than for items.
 *
 * <p>{@link #SCALE} is set as large as the cabinet allows. Spinning about Y swings a cube's
 * corners out to {@code SCALE * 0.707}, and that half-diagonal has to clear the side pillars
 * (0.375 from centre), the glass in front and the back wall behind — so 0.5 is the ceiling, and
 * it lands with roughly a pixel of margin on every side.
 */
public class TESRShop extends TileEntitySpecialRenderer<TileEntityShop> {

    /** Cabinet cavity spans z 1..14 of the model; this centres the item in it. */
    private static final float FRONT_OFFSET = 0.031F;
    private static final float SCALE = 0.5F;
    private static final float ROTATE_DEG_PER_TICK = 1.5F;

    @Override
    public void render(TileEntityShop te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        ItemStack stack = te.getSaleTemplate();
        if (stack.isEmpty() || te.getWorld() == null) {
            return;
        }

        float worldTime = te.getWorld().getTotalWorldTime() + partialTicks;
        float spin = (worldTime * ROTATE_DEG_PER_TICK) % 360.0F;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);
        // Negating the horizontal angle lines local +Z up with the block's FACING, i.e. out
        // through the glass, so "forward" below means "toward the shopper" for all four
        // rotations of the model.
        GlStateManager.rotate(-facingOf(te).getHorizontalAngle(), 0.0F, 1.0F, 0.0F);
        GlStateManager.translate(0.0F, 0.0F, FRONT_OFFSET);
        GlStateManager.rotate(spin, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(SCALE, SCALE, SCALE);

        RenderHelper.enableStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItem(stack,
            ItemCameraTransforms.TransformType.NONE);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    /** Falls back to north on the tick after placement, before the state has been read back. */
    private static EnumFacing facingOf(TileEntityShop te) {
        IBlockState state = te.getWorld().getBlockState(te.getPos());
        return state.getBlock() instanceof BlockSumShop
            ? state.getValue(BlockSumShop.FACING)
            : EnumFacing.NORTH;
    }
}
