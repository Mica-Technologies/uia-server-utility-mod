package com.micatechnologies.minecraft.sum.border;

import com.micatechnologies.minecraft.sum.SumConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class BorderHandler {

    private static final long NOTIFY_THROTTLE_TICKS = 60;

    private final Map<UUID, Long> lastNotifyTick = new HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        EntityPlayer player = event.player;
        if (!(player instanceof EntityPlayerMP) || player.world.isRemote) {
            return;
        }
        if (!SumConfig.isBorderEnabled()) {
            return;
        }
        BorderEntry border = SumConfig.getBorderForDim(player.dimension);
        if (border == null) {
            return;
        }
        double x = player.posX;
        double z = player.posZ;
        double r = border.getRadius();
        if (isInside(x, z, r)) {
            return;
        }
        switch (border.getMode()) {
            case BOUNCE:
                applyBounce(player, x, z, r);
                break;
            case LOOP:
                applyLoop(player, x, z, r);
                break;
        }
    }

    private void applyBounce(EntityPlayer player, double x, double z, double radius) {
        player.setPositionAndUpdate(clampToInset(x, radius), player.posY, clampToInset(z, radius));
        notify(player, TextFormatting.RED + "You've reached the world border.");
    }

    private void applyLoop(EntityPlayer player, double x, double z, double radius) {
        player.setPositionAndUpdate(loopWrap(x, radius), player.posY, loopWrap(z, radius));
        notify(player, TextFormatting.AQUA + "You wrapped to the other side of the world.");
    }

    /** True if (x, z) is within the square, origin-centered border of the given radius (inclusive). */
    static boolean isInside(double x, double z, double radius) {
        return Math.abs(x) <= radius && Math.abs(z) <= radius;
    }

    /** Bounce: clamp an axis to one block inside the border edge. */
    static double clampToInset(double v, double radius) {
        double inset = radius - 1.0;
        return clamp(v, -inset, inset);
    }

    /** Loop: an axis beyond the radius wraps to one block inside the opposite edge; else unchanged. */
    static double loopWrap(double v, double radius) {
        if (Math.abs(v) > radius) {
            return v > 0 ? -(radius - 1.0) : (radius - 1.0);
        }
        return v;
    }

    private void notify(EntityPlayer player, String message) {
        UUID id = player.getUniqueID();
        long now = player.world.getTotalWorldTime();
        Long last = lastNotifyTick.get(id);
        if (last != null && now - last < NOTIFY_THROTTLE_TICKS) {
            return;
        }
        lastNotifyTick.put(id, now);
        player.sendMessage(new TextComponentString(message));
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
