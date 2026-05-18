package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/** Current gamemode (Survival / Creative / Adventure / Spectator). Modeled on
 *  EvergreenHUD's gamemode element. */
public class GameModeHud extends SingleTextHud {

    public GameModeHud() {
        super("Mode:", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return "Creative";
        }
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null || player.capabilities == null) {
            return "—";
        }
        if (player.isSpectator()) {
            return "Spectator";
        }
        if (player.capabilities.isCreativeMode) {
            return "Creative";
        }
        if (player.capabilities.allowEdit) {
            return "Survival";
        }
        return "Adventure";
    }
}
