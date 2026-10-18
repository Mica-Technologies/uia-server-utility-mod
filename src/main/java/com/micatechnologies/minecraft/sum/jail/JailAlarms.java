package com.micatechnologies.minecraft.sum.jail;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.SumConfig;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Jailbreak alarms: which cells' alarms are sounding and until when.
 *
 * <p>An alarm is raised when the escape check finds an inmate outside their cell. It powers every
 * {@link BlockJailAlarmRelay} linked to the cell for {@code jail.alarmSeconds}, a fresh escape
 * restarting the clock, and tells every online player allowed to run {@code /jail}. What the
 * relays drive is up to the builder: a CSM station bell or siren controller, a fire alarm
 * panel's storm input, lamps, doors.
 *
 * <p>Held in memory only. A restart silences everything, which is what you would want of an
 * alarm whose cause has already been put back in its cell.
 */
public final class JailAlarms {

    /** Cell key to the world time the alarm runs until. */
    private static final Map<String, Long> SOUNDING = new HashMap<>();

    private JailAlarms() {}

    /** Raises (or extends) the alarm for the cell an inmate has just left. */
    static void raise(MinecraftServer server, JailCell cell, Inmate inmate, BlockPos where) {
        WorldServer world = server.getWorld(cell.getDimensionId());
        boolean wasSounding = isSounding(world, cell);
        long until = world.getTotalWorldTime() + SumConfig.getJailAlarmSeconds() * 20L;
        SOUNDING.put(JailCell.key(cell.getName()), until);
        for (BlockPos relay : cell.getAlarms()) {
            BlockJailAlarmRelay.power(world, relay);
        }
        if (!wasSounding) {
            String text = "[Jail] Jailbreak: " + inmate.getPlayerName() + " left " + cell.getName()
                + " at " + where.getX() + ", " + where.getY() + ", " + where.getZ() + ".";
            for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                if (player.canUseCommand(2, "sum.jail")) {
                    JailService.send(player, TextFormatting.RED, text);
                }
            }
            Sum.LOGGER.info(text);
        }
    }

    /** Silences a cell's alarm now and switches its relays off. */
    public static void silence(MinecraftServer server, JailCell cell) {
        SOUNDING.remove(JailCell.key(cell.getName()));
        WorldServer world = server.getWorld(cell.getDimensionId());
        for (BlockPos relay : cell.getAlarms()) {
            BlockJailAlarmRelay.unpower(world, relay);
        }
    }

    public static boolean isSounding(World world, JailCell cell) {
        Long until = SOUNDING.get(JailCell.key(cell.getName()));
        return until != null && world.getTotalWorldTime() < until;
    }

    /** Whether any cell this relay is linked to is still sounding. */
    static boolean isSoundingFor(World world, BlockPos relay) {
        JailSavedData data = JailSavedData.get(world);
        for (JailCell cell : data.getCells()) {
            if (cell.getDimensionId() == world.provider.getDimension()
                && cell.getAlarms().contains(relay) && isSounding(world, cell)) {
                return true;
            }
        }
        return false;
    }

    /** Called when the server stops. */
    public static void clear() {
        SOUNDING.clear();
    }
}
