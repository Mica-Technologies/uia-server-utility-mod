package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;

/**
 * Ping to the server in milliseconds. Reads the same value F3 displays — vanilla's tab-
 * list player info NetworkPlayerInfo.responseTime. Modeled on EvergreenHUD's ping
 * element.
 */
public class PingHud extends SingleTextHud {

    public PingHud() {
        super("Ping", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return "32 ms";
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return "—";
        }
        NetHandlerPlayClient conn = mc.getConnection();
        if (conn == null) {
            return "—";
        }
        NetworkPlayerInfo info = conn.getPlayerInfo(mc.player.getUniqueID());
        if (info == null) {
            return "—";
        }
        return info.getResponseTime() + " ms";
    }
}
