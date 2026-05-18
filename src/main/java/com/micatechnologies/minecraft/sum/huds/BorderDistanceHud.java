package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.border.BorderEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Distance (in blocks) to the nearest edge of SUM's configured world border in
 * the player's current dimension. Reads {@link SumConfig#getBorderForDim} —
 * positive when inside the border, negative (and red-flagged) when outside.
 *
 * <p>Caveat: in multiplayer, the client reads its OWN {@code sum.cfg}, which is
 * usually distributed with the modpack and matches the server, but technically
 * may drift if a server admin changes border config without updating clients.</p>
 */
public class BorderDistanceHud extends SingleTextHud {

    public BorderDistanceHud() {
        super("Border:", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) return "1248";
        EntityPlayer p = Minecraft.getMinecraft().player;
        if (p == null) return "—";
        if (!SumConfig.isBorderEnabled()) return "off";

        BorderEntry border = SumConfig.getBorderForDim(p.dimension);
        if (border == null) return "n/a";

        // Border is a square AABB centered at origin. Distance to the nearest
        // edge is radius - max(|x|, |z|) — positive inside, negative outside.
        double dist = border.getRadius() - Math.max(Math.abs(p.posX), Math.abs(p.posZ));
        return Long.toString(Math.round(dist));
    }
}
