package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * Biome HUD — displays the registered name of the biome the player currently stands in.
 * Modeled on EvergreenHUD's biome element.
 */
public class BiomeHud extends SingleTextHud {

    public BiomeHud() {
        super("Biome:", true, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return "Plains";
        }
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        if (world == null || mc.player == null) {
            return "—";
        }
        BlockPos pos = mc.player.getPosition();
        Biome biome = world.getBiome(pos);
        return biome != null ? biome.getBiomeName() : "—";
    }
}
