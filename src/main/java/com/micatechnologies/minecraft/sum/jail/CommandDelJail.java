package com.micatechnologies.minecraft.sum.jail;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;

/**
 * {@code /deljail <name>} — deletes a cell. Refused while anyone is held in it, so an inmate is
 * never silently freed by a cell disappearing; release or move them first.
 */
public class CommandDelJail extends JailCommandBase {

    @Override
    public String getName() {
        return "deljail";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/deljail <name>";
    }

    @Override
    protected void run(MinecraftServer server, ICommandSender sender, String[] args, JailSavedData data) {
        if (args.length != 1) {
            JailService.send(sender, TextFormatting.RED, "Usage: " + getUsage(sender));
            return;
        }
        JailCell cell = data.getCell(args[0]);
        if (cell == null) {
            JailService.send(sender, TextFormatting.RED, "There is no jail cell named " + args[0] + ".");
            return;
        }
        int held = data.countInmates(cell.getName());
        if (held > 0) {
            JailService.send(sender, TextFormatting.RED, cell.getName() + " still holds " + held
                + (held == 1 ? " inmate" : " inmates") + ". /unjail them, or /jail them into another "
                + "cell, first.");
            return;
        }
        data.removeCell(cell.getName());
        JailService.send(sender, TextFormatting.GOLD, "Deleted jail cell " + cell.getName() + ".");
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
