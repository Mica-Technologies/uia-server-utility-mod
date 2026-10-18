package com.micatechnologies.minecraft.sum.jail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;

/**
 * {@code /unjail <player>} — ends a sentence early. The player keeps their position inside the
 * cell and has to walk out; see {@link JailService#release}. Works on offline inmates too, by the
 * name they were jailed under.
 */
public class CommandUnjail extends JailCommandBase {

    @Override
    public String getName() {
        return "unjail";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/unjail <player>";
    }

    @Override
    protected void run(MinecraftServer server, ICommandSender sender, String[] args, JailSavedData data) {
        if (args.length != 1) {
            JailService.send(sender, TextFormatting.RED, "Usage: " + getUsage(sender));
            return;
        }
        Inmate inmate = data.findInmateByName(args[0]);
        if (inmate == null) {
            EntityPlayerMP online = server.getPlayerList().getPlayerByUsername(args[0]);
            if (online != null) {
                inmate = data.getInmate(online.getUniqueID());
            }
        }
        if (inmate == null) {
            JailService.send(sender, TextFormatting.RED, args[0] + " is not in jail.");
            return;
        }
        JailService.release(server, inmate, "released by " + sender.getName());
        JailService.send(sender, TextFormatting.GOLD, "Released " + inmate.getPlayerName() + " from "
            + inmate.getCellName() + ".");
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Inmate inmate : JailService.data(server).getInmates()) names.add(inmate.getPlayerName());
            return getListOfStringsMatchingLastWord(args, names);
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return index == 0;
    }
}
