package com.micatechnologies.minecraft.sum.jail;

import com.micatechnologies.minecraft.sum.Sum;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ITeleporter;

/**
 * Jailing, releasing and returning inmates to their cells. The commands and
 * {@link JailEventHandler} both go through here, so the rules live in one place.
 */
public final class JailService {

    private JailService() {}

    public static JailSavedData data(MinecraftServer server) {
        return JailSavedData.get(server.getEntityWorld());
    }

    /**
     * Records the inmate and, if they are online, puts them in the cell now. An offline inmate
     * is put there by {@link JailEventHandler} when they next log in.
     */
    public static void jail(MinecraftServer server, Inmate inmate) {
        JailSavedData data = data(server);
        data.putInmate(inmate);
        EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(inmate.getPlayerId());
        JailCell cell = data.getCell(inmate.getCellName());
        if (cell != null) {
            setDoors(server, cell, false);
        }
        if (online != null && cell != null) {
            putInCell(online, cell);
            send(online, TextFormatting.RED, "You have been jailed in " + cell.getName()
                + " by " + inmate.getJailedBy() + sentenceSuffix(inmate) + ".");
        }
        Sum.LOGGER.info("[jail] {} jailed {} in '{}'{}", inmate.getJailedBy(),
            inmate.getPlayerName(), inmate.getCellName(), sentenceSuffix(inmate));
    }

    /**
     * Ends a sentence. The player stays exactly where they are, inside the cell: release
     * restores their privileges, not their freedom, and they walk out on their own.
     */
    @Nullable
    public static Inmate release(MinecraftServer server, Inmate inmate, String reason) {
        JailSavedData data = data(server);
        Inmate removed = data.removeInmate(inmate.getPlayerId());
        if (removed == null) {
            return null;
        }
        // Open the cell once nobody is left in it, so the released can walk out: free of the
        // cell, but still inside the facility and finding their own way out of it.
        JailCell cell = data.getCell(inmate.getCellName());
        if (cell != null && data.countInmates(cell.getName()) == 0) {
            setDoors(server, cell, true);
        }
        EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(inmate.getPlayerId());
        if (online != null) {
            send(online, TextFormatting.GREEN, "You have been released from jail (" + reason
                + "). You can build, chat and use commands again; find your own way out.");
        }
        Sum.LOGGER.info("[jail] released {} from '{}' ({})", inmate.getPlayerName(),
            inmate.getCellName(), reason);
        return removed;
    }

    /**
     * Opens or closes every door linked to the cell. Doors in unloaded chunks are skipped.
     *
     * @return how many doors were found and set
     */
    public static int setDoors(MinecraftServer server, JailCell cell, boolean open) {
        World world = server.getWorld(cell.getDimensionId());
        int set = 0;
        for (net.minecraft.util.math.BlockPos door : cell.getDoors()) {
            if (JailDoors.setOpen(world, door, open)) set++;
        }
        return set;
    }

    /** Moves a player to the cell's spawn point, across dimensions if need be. */
    public static void putInCell(EntityPlayerMP player, JailCell cell) {
        if (player.isRiding()) {
            player.dismountRidingEntity();
        }
        player.fallDistance = 0.0F;
        if (player.dimension != cell.getDimensionId()) {
            player.changeDimension(cell.getDimensionId(), new CellTeleporter(cell));
        }
        player.connection.setPlayerLocation(cell.getSpawnX(), cell.getSpawnY(), cell.getSpawnZ(),
            cell.getSpawnYaw(), player.rotationPitch);
        // A teleport keeps momentum, so a sprinting or flying inmate would coast straight back
        // out of the cell before the next check. Stop them where they land.
        player.motionX = 0.0;
        player.motionY = 0.0;
        player.motionZ = 0.0;
        player.connection.sendPacket(new net.minecraft.network.play.server.SPacketEntityVelocity(player));
    }

    /** Places a player at a cell without building or searching for a portal. */
    private static final class CellTeleporter implements ITeleporter {
        private final JailCell cell;

        CellTeleporter(JailCell cell) { this.cell = cell; }

        @Override
        public void placeEntity(World world, Entity entity, float yaw) {
            entity.moveToBlockPosAndAngles(new net.minecraft.util.math.BlockPos(
                cell.getSpawnX(), cell.getSpawnY(), cell.getSpawnZ()), cell.getSpawnYaw(),
                entity.rotationPitch);
        }

        @Override
        public boolean isVanilla() { return false; }
    }

    // --- Sentence durations ---

    /**
     * Parses a sentence length: a bare number of minutes ({@code 30}), or a number with a unit
     * suffix, {@code s}, {@code m}, {@code h} or {@code d} ({@code 90s}, {@code 2h}, {@code 1d}).
     *
     * @return the length in milliseconds, or -1 if the text is not a positive duration
     */
    public static long parseDuration(String text) {
        if (text == null) return -1L;
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return -1L;
        long unitMillis = 60_000L;
        char last = t.charAt(t.length() - 1);
        if (Character.isLetter(last)) {
            switch (last) {
                case 's': unitMillis = 1_000L; break;
                case 'm': unitMillis = 60_000L; break;
                case 'h': unitMillis = 3_600_000L; break;
                case 'd': unitMillis = 86_400_000L; break;
                default: return -1L;
            }
            t = t.substring(0, t.length() - 1);
        }
        long amount;
        try {
            amount = Long.parseLong(t);
        } catch (NumberFormatException e) {
            return -1L;
        }
        if (amount <= 0 || amount > Long.MAX_VALUE / unitMillis) return -1L;
        return amount * unitMillis;
    }

    /** Short human form of a duration, e.g. {@code 1h 5m}, {@code 40s}. */
    public static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, (millis + 999L) / 1000L);
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append('s');
        return sb.toString().trim();
    }

    static String sentenceSuffix(Inmate inmate) {
        if (!inmate.hasRelease()) {
            return "";
        }
        return " for " + formatDuration(inmate.getReleaseAtMillis() - inmate.getJailedAtMillis());
    }

    public static void send(ICommandSender sender, TextFormatting color, String text) {
        TextComponentString tcs = new TextComponentString(text);
        tcs.getStyle().setColor(color);
        sender.sendMessage(tcs);
    }
}
