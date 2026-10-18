package com.micatechnologies.minecraft.sum.jail;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;

/**
 * {@code /jails [cell]} — every cell with who is in it and how long they have left, or just one
 * cell when named.
 */
public class CommandJails extends JailCommandBase {

    @Override
    public String getName() {
        return "jails";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/jails [cell]";
    }

    @Override
    protected void run(MinecraftServer server, ICommandSender sender, String[] args, JailSavedData data) {
        if (data.getCells().isEmpty()) {
            JailService.send(sender, TextFormatting.GRAY, "There are no jail cells.");
            return;
        }
        JailCell only = null;
        if (args.length >= 1) {
            only = data.getCell(args[0]);
            if (only == null) {
                JailService.send(sender, TextFormatting.RED, "There is no jail cell named " + args[0] + ".");
                return;
            }
        }
        long now = System.currentTimeMillis();
        JailService.send(sender, TextFormatting.GOLD, only == null
            ? "Jail cells (" + data.getCells().size() + "), " + data.getInmates().size() + " inmates:"
            : "Jail cell " + only.getName() + ":");
        for (JailCell cell : data.getCells()) {
            if (only != null && cell != only) continue;
            List<Inmate> inmates = data.getInmatesIn(cell.getName());
            JailService.send(sender, TextFormatting.YELLOW, " " + cell.getName() + " — dim "
                + cell.getDimensionId() + ", " + ItemJailWand.describe(cell.getMin()) + " to "
                + ItemJailWand.describe(cell.getMax()) + ", "
                + (inmates.isEmpty() ? "empty" : inmates.size() + " held"));
            for (Inmate inmate : inmates) {
                long left = inmate.remainingMillis(now);
                JailService.send(sender, TextFormatting.GRAY, "   " + inmate.getPlayerName()
                    + " (by " + inmate.getJailedBy() + ", "
                    + (left < 0 ? "until released" : JailService.formatDuration(left) + " left") + ")");
            }
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, cellNames(JailService.data(server)));
        }
        return Collections.emptyList();
    }
}
