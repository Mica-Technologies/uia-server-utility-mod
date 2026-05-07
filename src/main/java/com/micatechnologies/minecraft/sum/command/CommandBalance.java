package com.micatechnologies.minecraft.sum.command;

import com.micatechnologies.minecraft.sum.economy.EconomyBridge;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

/**
 * {@code /balance [player]} — user-facing balance lookup. Shows the sender's own balance with
 * no args; admins (permission level 2) can target another player by name.
 *
 * <p>Routes through {@link EconomyBridge} so it works with either the EconomyInc capability
 * (when that mod is loaded) or SUM's own {@code ISumMoney} fallback.
 */
public class CommandBalance extends CommandBase {

    @Override
    public String getName() {
        return "balance";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("bal", "money");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/balance [player]";
    }

    /** Anyone can run; the [player] arg path re-checks for op level inside {@link #execute}. */
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP target;
        boolean isSelfLookup;
        if (args.length == 0) {
            target = asPlayer(sender);
            isSelfLookup = true;
        } else {
            if (!sender.canUseCommand(2, getName())) {
                sendMessage(sender, TextFormatting.RED,
                    "You can only check your own balance. Run /balance with no arguments.");
                return;
            }
            target = getPlayer(server, sender, args[0]);
            isSelfLookup = (sender instanceof EntityPlayerMP) && sender == target;
        }
        if (target == null) {
            return;
        }
        if (!EconomyBridge.isAvailable()) {
            sendMessage(sender, TextFormatting.YELLOW,
                "No economy backend is loaded. Install EconomyInc, or wait for SUM's money "
                    + "system to register on world load.");
            return;
        }
        double balance = EconomyBridge.getBalance(target);
        if (Double.isNaN(balance)) {
            sendMessage(sender, TextFormatting.YELLOW,
                target.getName() + " has no balance handler attached (offline, or capability "
                    + "missing).");
            return;
        }
        String prefix = isSelfLookup ? "Your balance" : target.getName() + "'s balance";
        sendMessage(sender, TextFormatting.GREEN,
            prefix + ": $" + String.format(Locale.ROOT, "%.2f", balance));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1 && sender.canUseCommand(2, getName())) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }

    @Nullable
    private EntityPlayerMP asPlayer(ICommandSender sender) {
        if (sender instanceof EntityPlayerMP) {
            return (EntityPlayerMP) sender;
        }
        sendMessage(sender, TextFormatting.RED, "Console must specify a player name: /balance <player>");
        return null;
    }

    private static void sendMessage(ICommandSender sender, TextFormatting color, String text) {
        TextComponentString tcs = new TextComponentString(text);
        tcs.getStyle().setColor(color);
        sender.sendMessage(tcs);
    }
}
