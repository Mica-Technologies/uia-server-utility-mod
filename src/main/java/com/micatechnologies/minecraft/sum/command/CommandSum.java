package com.micatechnologies.minecraft.sum.command;

import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.roamer.EntityRoamer;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

public class CommandSum extends CommandBase {

    @Override
    public String getName() {
        return "sum";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/sum <reloadconfig|addroamerblock|rmroamerblock|roamer>";
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
            case "roamer":
                handleRoamer(sender, args);
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

    // --- /sum roamer subcommands ---

    private void handleRoamer(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, TextFormatting.RED,
                "Usage: /sum roamer <greet> <add|list|clear> [nearest|<uuid>] [message]");
            return;
        }

        if ("greet".equalsIgnoreCase(args[1])) {
            handleRoamerGreet(sender, args);
        } else {
            sendMessage(sender, TextFormatting.RED,
                "Unknown roamer subcommand. Usage: /sum roamer greet <add|list|clear> [nearest|<uuid>]");
        }
    }

    private void handleRoamerGreet(ICommandSender sender, String[] args) {
        // /sum roamer greet <add|list|clear> [nearest|uuid] [message...]
        if (args.length < 4) {
            sendMessage(sender, TextFormatting.RED,
                "Usage: /sum roamer greet <add|list|clear> <nearest|uuid> [message]");
            return;
        }

        String action = args[2].toLowerCase();
        String target = args[3];

        EntityRoamer roamer = findRoamer(sender, target);
        if (roamer == null) {
            sendMessage(sender, TextFormatting.RED, "No roamer found for target '" + target + "'.");
            return;
        }

        switch (action) {
            case "add":
                if (args.length < 5) {
                    sendMessage(sender, TextFormatting.RED,
                        "Usage: /sum roamer greet add <nearest|uuid> <message>");
                    return;
                }
                StringBuilder message = new StringBuilder(args[4]);
                for (int i = 5; i < args.length; i++) {
                    message.append(' ').append(args[i]);
                }
                roamer.addGreeting(message.toString());
                sendMessage(sender, TextFormatting.GREEN,
                    "Added greeting to " + getRoamerDisplayName(roamer) + ": " + message);
                break;
            case "list":
                List<String> greetings = roamer.getGreetings();
                sendMessage(sender, TextFormatting.GOLD,
                    "Greetings for " + getRoamerDisplayName(roamer) + " (" + greetings.size() + "):");
                for (int i = 0; i < greetings.size(); i++) {
                    sendMessage(sender, TextFormatting.WHITE, "  " + (i + 1) + ". " + greetings.get(i));
                }
                break;
            case "clear":
                roamer.clearGreetings();
                sendMessage(sender, TextFormatting.GREEN,
                    "Cleared all greetings for " + getRoamerDisplayName(roamer) + ".");
                break;
            default:
                sendMessage(sender, TextFormatting.RED,
                    "Unknown action '" + action + "'. Use add, list, or clear.");
                break;
        }
    }

    @Nullable
    private EntityRoamer findRoamer(ICommandSender sender, String target) {
        World world = sender.getEntityWorld();
        if ("nearest".equalsIgnoreCase(target)) {
            BlockPos pos = sender.getPosition();
            double searchRadius = 32.0;
            AxisAlignedBB box = new AxisAlignedBB(pos).grow(searchRadius);
            List<EntityRoamer> roamers = world.getEntitiesWithinAABB(EntityRoamer.class, box);
            if (roamers.isEmpty()) {
                return null;
            }
            EntityRoamer nearest = null;
            double nearestDist = Double.MAX_VALUE;
            for (EntityRoamer r : roamers) {
                double dist = r.getDistanceSq(pos);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = r;
                }
            }
            return nearest;
        } else {
            // Try to parse as UUID
            UUID uuid;
            try {
                uuid = UUID.fromString(target);
            } catch (IllegalArgumentException e) {
                return null;
            }
            for (Entity entity : world.loadedEntityList) {
                if (entity instanceof EntityRoamer && entity.getUniqueID().equals(uuid)) {
                    return (EntityRoamer) entity;
                }
            }
            return null;
        }
    }

    private static String getRoamerDisplayName(EntityRoamer roamer) {
        return roamer.hasCustomName() ? roamer.getCustomNameTag() : "(unnamed)";
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "reloadconfig", "addroamerblock", "rmroamerblock", "roamer");
        }
        if (args.length == 2 && "rmroamerblock".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, SumConfig.getRoamerWalkableBlocks());
        }
        if (args.length == 2 && "roamer".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "greet");
        }
        if (args.length == 3 && "roamer".equalsIgnoreCase(args[0]) && "greet".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, "add", "list", "clear");
        }
        if (args.length == 4 && "roamer".equalsIgnoreCase(args[0]) && "greet".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, "nearest");
        }
        return Collections.emptyList();
    }

    private static void sendMessage(ICommandSender sender, TextFormatting color, String message) {
        TextComponentString text = new TextComponentString(message);
        text.getStyle().setColor(color);
        sender.sendMessage(text);
    }
}
