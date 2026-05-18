package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Connected server address — shows the host from the multiplayer connection, or
 * "Singleplayer" / "Realms" / "LAN" depending on the session type. Modeled on
 * EvergreenHUD's server-IP element.
 */
public class ServerIpHud extends SingleTextHud {

    public ServerIpHud() {
        super("Server:", true, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return "play.example.com";
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.isSingleplayer()) {
            return "Singleplayer";
        }
        ServerData data = mc.getCurrentServerData();
        if (data == null) {
            return "—";
        }
        if (data.isOnLAN()) {
            return "LAN";
        }
        return data.serverIP != null ? data.serverIP : "—";
    }
}
