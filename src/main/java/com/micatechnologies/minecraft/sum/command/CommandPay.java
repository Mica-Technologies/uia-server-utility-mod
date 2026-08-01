package com.micatechnologies.minecraft.sum.command;

import com.micatechnologies.minecraft.sum.economy.MoneyTransfer;
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
 * {@code /pay <player> <amount>} — transfers money from the sender to another online player.
 *
 * <p>Anyone can run it; both participants must be online. All validation, rounding, the optional
 * server fee, and rollback live in {@link MoneyTransfer}, so this class is just argument parsing
 * and the success/failure chat lines for both parties.
 */
public class CommandPay extends CommandBase {

    @Override
    public String getName() {
        return "pay";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("transfer");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/pay <player> <amount>";
    }

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
        if (!(sender instanceof EntityPlayerMP)) {
            sendMessage(sender, TextFormatting.RED,
                "Only players can send payments. Use /sum econ to adjust balances from console.");
            return;
        }
        if (args.length < 2) {
            sendMessage(sender, TextFormatting.RED, "Usage: /pay <player> <amount>");
            return;
        }
        EntityPlayerMP from = (EntityPlayerMP) sender;
        EntityPlayerMP to = getPlayer(server, sender, args[0]);   // throws if not online
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sendMessage(sender, TextFormatting.RED, "'" + args[1] + "' is not a number.");
            return;
        }

        // Both messages are sent from the callback so neither player is told the payment
        // succeeded before the economy has actually settled it.
        MoneyTransfer.transfer(from, to, amount, result -> {
            if (!result.ok) {
                sendMessage(from, TextFormatting.RED, result.error);
                return;
            }
            sendMessage(from, TextFormatting.GREEN,
                "Paid $" + money(result.credited) + " to " + to.getName()
                    + (result.fee > 0.0 ? " ($" + money(result.fee) + " fee)" : "") + ".");
            sendMessage(to, TextFormatting.GREEN,
                "Received $" + money(result.credited) + " from " + from.getName() + ".");
        });
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }

    private static String money(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static void sendMessage(ICommandSender sender, TextFormatting color, String text) {
        TextComponentString tcs = new TextComponentString(text);
        tcs.getStyle().setColor(color);
        sender.sendMessage(tcs);
    }
}
