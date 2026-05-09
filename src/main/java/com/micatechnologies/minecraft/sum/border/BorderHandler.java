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
        if (Math.abs(x) <= r && Math.abs(z) <= r) {
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
        double inset = radius - 1.0;
        double newX = clamp(x, -inset, inset);
        double newZ = clamp(z, -inset, inset);
        player.setPositionAndUpdate(newX, player.posY, newZ);
        notify(player, TextFormatting.RED + "You've reached the world border.");
    }

    private void applyLoop(EntityPlayer player, double x, double z, double radius) {
        double inset = radius - 1.0;
        double newX = x;
        double newZ = z;
        if (Math.abs(x) > radius) {
            newX = x > 0 ? -inset : inset;
        }
        if (Math.abs(z) > radius) {
            newZ = z > 0 ? -inset : inset;
        }
        player.setPositionAndUpdate(newX, player.posY, newZ);
        notify(player, TextFormatting.AQUA + "You wrapped to the other side of the world.");
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
