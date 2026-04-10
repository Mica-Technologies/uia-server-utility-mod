package com.micatechnologies.minecraft.sum.command;

import com.micatechnologies.minecraft.sum.SumConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public class CommandSum extends CommandBase {

    @Override
    public String getName() {
        return "sum";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/sum <reloadconfig|addroamerblock|rmroamerblock>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sendMessage(sender, TextFormatting.RED, getUsage(sender));
            return;
        }

        switch (args[0].toLowerCase()) {
            case "reloadconfig":
                handleReloadConfig(sender);
                break;
            case "addroamerblock":
                handleAddRoamerBlock(sender, args);
                break;
            case "rmroamerblock":
                handleRemoveRoamerBlock(sender, args);
                break;
            default:
                sendMessage(sender, TextFormatting.RED, "Unknown subcommand. Usage: " + getUsage(sender));
                break;
        }
    }

    private void handleReloadConfig(ICommandSender sender) {
        SumConfig.reloadConfig();
        sendMessage(sender, TextFormatting.GREEN, "SUM configuration reloaded.");
    }

    private void handleAddRoamerBlock(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, TextFormatting.RED, "Usage: /sum addroamerblock <block>");
            return;
        }
        String block = args[1];
        if (SumConfig.addRoamerWalkableBlock(block)) {
            sendMessage(sender, TextFormatting.GREEN, "Added '" + block + "' to roamer walkable blocks.");
        } else {
            sendMessage(sender, TextFormatting.YELLOW, "'" + block + "' is already a roamer walkable block.");
        }
    }

    private void handleRemoveRoamerBlock(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, TextFormatting.RED, "Usage: /sum rmroamerblock <block>");
            return;
        }
        String block = args[1];
        if (SumConfig.removeRoamerWalkableBlock(block)) {
            sendMessage(sender, TextFormatting.GREEN, "Removed '" + block + "' from roamer walkable blocks.");
        } else {
            sendMessage(sender, TextFormatting.YELLOW, "'" + block + "' is not a roamer walkable block.");
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "reloadconfig", "addroamerblock", "rmroamerblock");
        }
        if (args.length == 2 && "rmroamerblock".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, SumConfig.getRoamerWalkableBlocks());
        }
        return Collections.emptyList();
    }

    private static void sendMessage(ICommandSender sender, TextFormatting color, String message) {
        TextComponentString text = new TextComponentString(message);
        text.getStyle().setColor(color);
        sender.sendMessage(text);
    }
}
