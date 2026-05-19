package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.config.annotations.Slider;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import com.micatechnologies.minecraft.sum.roamer.EntityRoamer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * Name + distance of the nearest {@link EntityRoamer}. Useful RP context — at a
 * glance you know who's nearby in the lobby of whichever building you just
 * walked into.
 *
 * <p>Custom name (set via Roamer Configurator item) takes precedence over the
 * role's default label. Scan radius is configurable so server-Op users can crank
 * it up for a crowd-aware HUD, or shrink it down on busy public servers to keep
 * the readout focused on the local room.</p>
 */
public class NearestRoamerHud extends SingleTextHud {

    @Slider(name = "Scan radius (blocks)", min = 4, max = 64, step = 1)
    public int radius = 16;

    public NearestRoamerHud() {
        super("Roamer", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) return "Bank Teller 6m";
        EntityPlayer p = Minecraft.getMinecraft().player;
        if (p == null || p.world == null) return "—";

        AxisAlignedBB box = p.getEntityBoundingBox().grow(radius);
        List<EntityRoamer> roamers = p.world.getEntitiesWithinAABB(EntityRoamer.class, box);
        if (roamers.isEmpty()) return "none";

        EntityRoamer closest = null;
        double closestDistSq = Double.MAX_VALUE;
        for (EntityRoamer r : roamers) {
            double d = r.getDistanceSq(p);
            if (d < closestDistSq) {
                closest = r;
                closestDistSq = d;
            }
        }
        if (closest == null) return "none";

        String name = closest.hasCustomName()
            ? closest.getCustomNameTag()
            : closest.getRole().getDefaultName();
        if (name == null || name.isEmpty()) {
            name = closest.getRole().getId();
        }
        return name + " " + (int) Math.round(Math.sqrt(closestDistSq)) + "m";
    }
}
