package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.RayTraceResult;

/**
 * Display name of the block the crosshair is on. Replaces F3 for one of the most
 * common debug-screen lookups (builders identifying blocks, players checking
 * what they're about to mine), and works without flooding the screen with the
 * full debug overlay.
 */
public class LookingAtBlockHud extends SingleTextHud {

    public LookingAtBlockHud() {
        super("Block:", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) return "Polished Andesite";
        Minecraft mc = Minecraft.getMinecraft();
        RayTraceResult ray = mc.objectMouseOver;
        if (ray == null || ray.typeOfHit != RayTraceResult.Type.BLOCK || mc.world == null) {
            return "—";
        }
        IBlockState state = mc.world.getBlockState(ray.getBlockPos());
        return state.getBlock().getLocalizedName();
    }
}
