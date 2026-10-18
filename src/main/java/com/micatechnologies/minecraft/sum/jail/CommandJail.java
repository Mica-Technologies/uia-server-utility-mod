package com.micatechnologies.minecraft.sum.jail;

import com.mojang.authlib.GameProfile;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;

/**
 * {@code /jail <player> [cell] [duration]} — jails a player.
 *
 * <p>With no cell named, the least occupied cell is used. The duration is minutes, or a number
 * with {@code s}/{@code m}/{@code h}/{@code d}; without one the sentence lasts until
 * {@code /unjail}. Cell and duration may come in either order.
 *
 * <p>The player need not be online: anyone who has played on the server can be jailed, and is put
 * in their cell when they next log in. Jailing someone already in jail moves them and restarts the
 * sentence.
 */
public class CommandJail extends JailCommandBase {

    @Override
    public String getName() {
        return "jail";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/jail <player> [cell] [duration, e.g. 30m, 2h]";
    }

    @Override
    protected void run(MinecraftServer server, ICommandSender sender, String[] args, JailSavedData data) {
        if (args.length < 1 || args.length > 3) {
            JailService.send(sender, TextFormatting.RED, "Usage: " + getUsage(sender));
            return;
        }
        if (data.getCells().isEmpty()) {
            JailService.send(sender, TextFormatting.RED, "There are no jail cells yet. Select one with "
                + "the jail wand, stand inside it and run /setjail <name>.");
            return;
        }
        GameProfile target = resolve(server, args[0]);
        if (target == null) {
            JailService.send(sender, TextFormatting.RED, "No player named " + args[0]
                + " has played on this server.");
            return;
        }

        JailCell cell = null;
        long duration = -1L;
        for (int i = 1; i < args.length; i++) {
            JailCell named = data.getCell(args[i]);
            if (cell == null && named != null) {
                cell = named;
                continue;
            }
            long parsed = JailService.parseDuration(args[i]);
            if (duration < 0 && parsed > 0) {
                duration = parsed;
                continue;
            }
            JailService.send(sender, TextFormatting.RED, "'" + args[i] + "' is neither a jail cell "
                + "nor a duration. Cells: " + String.join(", ", cellNames(data)));
            return;
        }
        if (cell == null) {
            cell = data.leastOccupiedCell();
        }

        long now = System.currentTimeMillis();
        boolean wasJailed = data.isJailed(target.getId());
        Inmate inmate = new Inmate(target.getId(), target.getName(), cell.getName(), sender.getName(),
            now, duration > 0 ? now + duration : Inmate.NO_RELEASE);
        JailService.jail(server, inmate);

        boolean online = server.getPlayerList().getPlayerByUUID(target.getId()) != null;
        JailService.send(sender, TextFormatting.GOLD, (wasJailed ? "Moved " : "Jailed ")
            + target.getName() + " to " + cell.getName() + JailService.sentenceSuffix(inmate)
            + (online ? "." : ". They are offline and will be put there when they log in."));
    }

    @Nullable
    private static GameProfile resolve(MinecraftServer server, String name) {
        EntityPlayerMP online = server.getPlayerList().getPlayerByUsername(name);
        if (online != null) {
            return online.getGameProfile();
        }
        return server.getPlayerProfileCache().getGameProfileForUsername(name);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 2 || args.length == 3) {
            List<String> options = cellNames(JailService.data(server));
            options.addAll(Arrays.asList("15m", "30m", "1h", "1d"));
            return getListOfStringsMatchingLastWord(args, options);
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return index == 0;
    }
}
