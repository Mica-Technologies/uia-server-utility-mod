package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;

/**
 * Light level at the player's feet — block light by default (the value that
 * matters for hostile-mob spawn checks; spawns are blocked when block light ≥ 8).
 * Optional sky-light toggle for surface builders worried about which blocks the
 * sun reaches.
 */
public class LightLevelHud extends SingleTextHud {

    /** Show sky light instead of block light. Block light is the more common need
     *  (mob-spawn checking) so it's the default. */
    @Switch(name = "Show sky light instead")
    public boolean skyLight = false;

    public LightLevelHud() {
        super("Light:", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) return "7";
        EntityPlayer p = Minecraft.getMinecraft().player;
        if (p == null) return "—";
        World world = p.world;
        BlockPos pos = new BlockPos(p.posX, p.posY, p.posZ);
        EnumSkyBlock kind = skyLight ? EnumSkyBlock.SKY : EnumSkyBlock.BLOCK;
        return Integer.toString(world.getLightFor(kind, pos));
    }
}
