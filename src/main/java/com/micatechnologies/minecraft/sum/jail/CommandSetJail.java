package com.micatechnologies.minecraft.sum.jail;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;

/**
 * {@code /setjail <name>} — saves the jail wand's selection as a cell.
 *
 * <p>The operator holds the wand and stands inside the selection; where they stand, and the way
 * they face, becomes the point inmates are put at. Running it again with an existing name
 * redraws that cell, and its inmates are held to the new box from the next check.
 */
public class CommandSetJail extends JailCommandBase {

    static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    @Override
    public String getName() {
        return "setjail";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/setjail <name>  (hold the jail wand and stand inside the selection)";
    }

    @Override
    protected void run(MinecraftServer server, ICommandSender sender, String[] args, JailSavedData data) {
        if (!(sender instanceof EntityPlayerMP)) {
            JailService.send(sender, TextFormatting.RED, "Only a player holding the jail wand can set a cell.");
            return;
        }
        if (args.length != 1) {
            JailService.send(sender, TextFormatting.RED, "Usage: " + getUsage(sender));
            return;
        }
        String name = args[0];
        if (!NAME.matcher(name).matches()) {
            JailService.send(sender, TextFormatting.RED,
                "Cell names are 1-32 letters, digits, '-' or '_'.");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        ItemStack wand = heldWand(player);
        if (wand.isEmpty()) {
            JailService.send(sender, TextFormatting.RED, "Hold the jail wand with both corners selected.");
            return;
        }
        BlockPos a = ItemJailWand.getCornerA(wand);
        BlockPos b = ItemJailWand.getCornerB(wand);
        if (a == null || b == null) {
            JailService.send(sender, TextFormatting.RED, "Select both corners first: sneak+right-click "
                + "for A, right-click for B.");
            return;
        }
        int dim = ItemJailWand.getDimensionId(wand, player.dimension);
        JailCell cell = new JailCell(name, dim, a, b, player.posX, player.posY, player.posZ,
            player.rotationYaw);
        if (!cell.containsPlayerAt(player.dimension, player.posX, player.posY, player.posZ)) {
            JailService.send(sender, TextFormatting.RED, "Stand inside the selection ("
                + ItemJailWand.describe(cell.getMin()) + " to " + ItemJailWand.describe(cell.getMax())
                + ") when you run this; that spot is where inmates will be put. If you are inside, "
                + "draw the box one block higher so it includes the space above the floor.");
            return;
        }
        boolean replaced = data.putCell(cell) != null;
        JailService.send(sender, TextFormatting.GOLD, (replaced ? "Redrew" : "Created") + " jail cell "
            + name + " (" + ItemJailWand.describe(cell.getMin()) + " to "
            + ItemJailWand.describe(cell.getMax()) + ").");
    }

    private static ItemStack heldWand(EntityPlayerMP player) {
        for (EnumHand hand : EnumHand.values()) {
            ItemStack stack = player.getHeldItem(hand);
            if (stack.getItem() instanceof ItemJailWand) return stack;
        }
        return ItemStack.EMPTY;
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
