package com.micatechnologies.minecraft.sum.command;

import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.bank.BlockVaultDoor;
import com.micatechnologies.minecraft.sum.bank.TileEntityVaultDoor;
import com.micatechnologies.minecraft.sum.economy.EconomyBridge;
import com.micatechnologies.minecraft.sum.favorites.FavoriteKey;
import com.micatechnologies.minecraft.sum.favorites.FavoritesStore;
import com.micatechnologies.minecraft.sum.roamer.EntityRoamer;
import com.micatechnologies.minecraft.sum.roamer.RoamerRole;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
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
        return "/sum <reloadconfig|addroamerblock|rmroamerblock|roamer|favorites|econ|vault>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
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
            case "favorites":
                handleFavorites(sender, args);
                break;
            case "econ":
                handleEcon(server, sender, args);
                break;
            case "vault":
                handleVault(sender, args);
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

    // --- /sum favorites subcommands ---

    private void handleFavorites(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, TextFormatting.RED,
                "Usage: /sum favorites <list|clear|export|importfile|file>");
            return;
        }
        if (!FavoritesStore.isLoaded()) {
            sendMessage(sender, TextFormatting.YELLOW,
                "Favorites are stored client-side. Use the Toggle Favorite keybind in your creative inventory.");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "list":
                handleFavoritesList(sender);
                break;
            case "clear":
                handleFavoritesClear(sender, args);
                break;
            case "export":
                handleFavoritesExport(sender);
                break;
            case "importfile":
                handleFavoritesImportFile(sender, args);
                break;
            case "file":
                handleFavoritesFile(sender);
                break;
            default:
                sendMessage(sender, TextFormatting.RED,
                    "Unknown favorites subcommand. Usage: /sum favorites <list|clear|export|importfile|file>");
                break;
        }
    }

    private void handleFavoritesList(ICommandSender sender) {
        List<FavoriteKey> all = FavoritesStore.snapshot();
        sendMessage(sender, TextFormatting.GOLD, "Favorites (" + all.size() + "):");
        if (all.isEmpty()) {
            sendMessage(sender, TextFormatting.GRAY, "  (none)");
            return;
        }
        for (int i = 0; i < all.size(); i++) {
            sendMessage(sender, TextFormatting.WHITE, "  " + (i + 1) + ". " + all.get(i).toString());
        }
    }

    private void handleFavoritesClear(ICommandSender sender, String[] args) {
        if (args.length < 3 || !"yes".equalsIgnoreCase(args[2])) {
            sendMessage(sender, TextFormatting.YELLOW,
                "This will wipe " + FavoritesStore.size()
                    + " favorites. Confirm with: /sum favorites clear yes");
            return;
        }
        int count = FavoritesStore.size();
        FavoritesStore.clear();
        FavoritesStore.save();
        sendMessage(sender, TextFormatting.GREEN, "Cleared " + count + " favorites.");
    }

    private void handleFavoritesExport(ICommandSender sender) {
        List<FavoriteKey> all = FavoritesStore.snapshot();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < all.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('"').append(all.get(i).toString()).append('"');
        }
        sb.append("]");
        File file = FavoritesStore.getStorageFile();
        sendMessage(sender, TextFormatting.GOLD,
            "Favorites JSON array (or read the file at " + (file != null ? file.getAbsolutePath() : "(unset)") + "):");
        sendMessage(sender, TextFormatting.WHITE, sb.toString());
    }

    private void handleFavoritesImportFile(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            sendMessage(sender, TextFormatting.RED, "Usage: /sum favorites importfile <path>");
            return;
        }
        StringBuilder pathBuilder = new StringBuilder(args[2]);
        for (int i = 3; i < args.length; i++) {
            pathBuilder.append(' ').append(args[i]);
        }
        File source = new File(pathBuilder.toString());
        if (!source.isFile()) {
            sendMessage(sender, TextFormatting.RED, "File does not exist: " + source.getAbsolutePath());
            return;
        }
        File target = FavoritesStore.getStorageFile();
        if (target == null) {
            sendMessage(sender, TextFormatting.RED, "Favorites storage file is not configured.");
            return;
        }
        try {
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            FavoritesStore.load();
            sendMessage(sender, TextFormatting.GREEN,
                "Imported " + FavoritesStore.size() + " favorites from " + source.getName() + ".");
        } catch (IOException e) {
            sendMessage(sender, TextFormatting.RED, "Import failed: " + e.getMessage());
        }
    }

    private void handleFavoritesFile(ICommandSender sender) {
        File file = FavoritesStore.getStorageFile();
        sendMessage(sender, TextFormatting.GOLD,
            "Favorites file: " + (file != null ? file.getAbsolutePath() : "(not configured)"));
    }

    // --- /sum econ subcommands (EconomyInc smoke-test surface) ---

    private void handleEcon(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            sendMessage(sender, TextFormatting.RED, "Usage: /sum econ <balance|add|set> [args...]");
            return;
        }
        if (!EconomyBridge.isAvailable()) {
            sendMessage(sender, TextFormatting.YELLOW,
                "EconomyInc bridge unavailable (mod not loaded or reflection bind failed). "
                    + "See server log for details.");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "balance":
                handleEconBalance(server, sender, args);
                break;
            case "add":
                handleEconAdd(server, sender, args);
                break;
            case "set":
                handleEconSet(server, sender, args);
                break;
            default:
                sendMessage(sender, TextFormatting.RED,
                    "Unknown econ subcommand. Usage: /sum econ <balance|add|set> [args...]");
                break;
        }
    }

    private void handleEconBalance(MinecraftServer server, ICommandSender sender, String[] args)
        throws CommandException {
        EntityPlayerMP target = (args.length >= 3)
            ? getPlayer(server, sender, args[2])
            : asPlayer(sender);
        if (target == null) {
            return;
        }
        double balance = EconomyBridge.getBalance(target);
        if (Double.isNaN(balance)) {
            sendMessage(sender, TextFormatting.YELLOW,
                target.getName() + " has no IMoney capability (offline or capability not attached).");
            return;
        }
        sendMessage(sender, TextFormatting.GREEN,
            target.getName() + "'s balance: $" + formatMoney(balance));
    }

    private void handleEconAdd(MinecraftServer server, ICommandSender sender, String[] args)
        throws CommandException {
        if (args.length < 3) {
            sendMessage(sender, TextFormatting.RED, "Usage: /sum econ add <amount> [player]");
            return;
        }
        double delta = parseMoneyArg(sender, args[2]);
        if (Double.isNaN(delta)) {
            return;
        }
        EntityPlayerMP target = (args.length >= 4) ? getPlayer(server, sender, args[3]) : asPlayer(sender);
        if (target == null) {
            return;
        }
        if (EconomyBridge.adjustBalance(target, delta)) {
            sendMessage(sender, TextFormatting.GREEN,
                "Adjusted " + target.getName() + "'s balance by $" + formatMoney(delta)
                    + " (now $" + formatMoney(EconomyBridge.getBalance(target)) + ").");
        } else {
            sendMessage(sender, TextFormatting.RED,
                "Adjustment failed (capability missing or would overdraft).");
        }
    }

    private void handleEconSet(MinecraftServer server, ICommandSender sender, String[] args)
        throws CommandException {
        if (args.length < 3) {
            sendMessage(sender, TextFormatting.RED, "Usage: /sum econ set <amount> [player]");
            return;
        }
        double amount = parseMoneyArg(sender, args[2]);
        if (Double.isNaN(amount) || amount < 0.0) {
            sendMessage(sender, TextFormatting.RED, "Amount must be a non-negative number.");
            return;
        }
        EntityPlayerMP target = (args.length >= 4) ? getPlayer(server, sender, args[3]) : asPlayer(sender);
        if (target == null) {
            return;
        }
        double current = EconomyBridge.getBalance(target);
        if (Double.isNaN(current)) {
            sendMessage(sender, TextFormatting.YELLOW,
                target.getName() + " has no IMoney capability.");
            return;
        }
        if (EconomyBridge.adjustBalance(target, amount - current)) {
            sendMessage(sender, TextFormatting.GREEN,
                "Set " + target.getName() + "'s balance to $" + formatMoney(amount) + ".");
        } else {
            sendMessage(sender, TextFormatting.RED, "Set failed (capability missing).");
        }
    }

    @Nullable
    private EntityPlayerMP asPlayer(ICommandSender sender) {
        if (sender instanceof EntityPlayerMP) {
            return (EntityPlayerMP) sender;
        }
        sendMessage(sender, TextFormatting.RED, "This subcommand requires a player target when run from console.");
        return null;
    }

    private double parseMoneyArg(ICommandSender sender, String arg) {
        try {
            return Double.parseDouble(arg);
        } catch (NumberFormatException e) {
            sendMessage(sender, TextFormatting.RED, "'" + arg + "' is not a number.");
            return Double.NaN;
        }
    }

    private static String formatMoney(double amount) {
        return String.format(java.util.Locale.ROOT, "%.2f", amount);
    }

    // --- /sum vault subcommands ---

    private void handleVault(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, TextFormatting.RED,
                "Usage: /sum vault <unlock|setcode|info|disown> [args...]");
            return;
        }
        if (!(sender instanceof EntityPlayerMP)) {
            sendMessage(sender, TextFormatting.RED, "Vault commands must be run by a player.");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        switch (args[1].toLowerCase()) {
            case "unlock":
                handleVaultUnlock(player, args);
                break;
            case "setcode":
                handleVaultSetCode(player, args);
                break;
            case "info":
                handleVaultInfo(player);
                break;
            case "disown":
                handleVaultDisown(player);
                break;
            default:
                sendMessage(sender, TextFormatting.RED,
                    "Unknown vault subcommand. Usage: /sum vault <unlock|setcode|info|disown> [args...]");
                break;
        }
    }

    private void handleVaultUnlock(EntityPlayerMP player, String[] args) {
        if (args.length < 3) {
            sendMessage(player, TextFormatting.RED, "Usage: /sum vault unlock <code>");
            return;
        }
        TileEntityVaultDoor vault = getTargetedVault(player);
        if (vault == null) {
            return;
        }
        if (!vault.hasPasscode()) {
            sendMessage(player, TextFormatting.YELLOW,
                "This vault has no passcode set. Right-click it to open.");
            return;
        }
        // Reconstruct the code in case it had spaces (already stripped on chat parse, but defensive)
        String code = joinFromIndex(args, 2);
        if (vault.checkPasscode(code)) {
            BlockVaultDoor.openDoor(vault.getWorld(), vault.getPos());
            sendMessage(player, TextFormatting.GREEN, "Vault unlocked.");
        } else {
            sendMessage(player, TextFormatting.RED, "Incorrect code.");
        }
    }

    private void handleVaultSetCode(EntityPlayerMP player, String[] args) {
        TileEntityVaultDoor vault = getTargetedVault(player);
        if (vault == null) {
            return;
        }
        if (!vault.isClaimed()) {
            sendMessage(player, TextFormatting.YELLOW,
                "This vault is not claimed yet. Right-click it to claim.");
            return;
        }
        if (!vault.isOwner(player)) {
            sendMessage(player, TextFormatting.RED,
                "Only the owner (" + vault.getOwnerName() + ") can change this vault's passcode.");
            return;
        }
        if (args.length < 3) {
            // Empty code = clear
            vault.setPasscode("");
            sendMessage(player, TextFormatting.GREEN,
                "Vault passcode cleared. Anyone with right-click can now open it.");
            return;
        }
        String code = joinFromIndex(args, 2);
        vault.setPasscode(code);
        sendMessage(player, TextFormatting.GREEN, "Vault passcode set.");
    }

    private void handleVaultInfo(EntityPlayerMP player) {
        TileEntityVaultDoor vault = getTargetedVault(player);
        if (vault == null) {
            return;
        }
        if (!vault.isClaimed()) {
            sendMessage(player, TextFormatting.GOLD, "Vault: unclaimed.");
            return;
        }
        sendMessage(player, TextFormatting.GOLD, "Vault owner: " + vault.getOwnerName());
        sendMessage(player, TextFormatting.GOLD,
            "Passcode: " + (vault.hasPasscode() ? "set" : "(none)"));
    }

    private void handleVaultDisown(EntityPlayerMP player) {
        TileEntityVaultDoor vault = getTargetedVault(player);
        if (vault == null) {
            return;
        }
        if (!vault.isClaimed()) {
            sendMessage(player, TextFormatting.YELLOW, "This vault is not claimed.");
            return;
        }
        if (!vault.isOwner(player)) {
            sendMessage(player, TextFormatting.RED,
                "Only the owner (" + vault.getOwnerName() + ") can disown this vault.");
            return;
        }
        vault.disown();
        sendMessage(player, TextFormatting.GREEN,
            "Vault disowned. The next player to right-click claims it.");
    }

    /** Ray-trace from the player's eye for up to 5 blocks; return the targeted vault TE
     *  or null (with a chat error sent on miss). */
    @Nullable
    private TileEntityVaultDoor getTargetedVault(EntityPlayer player) {
        RayTraceResult result = player.rayTrace(5.0, 1.0F);
        if (result == null || result.typeOfHit != RayTraceResult.Type.BLOCK) {
            sendMessage(player, TextFormatting.RED, "Look at a vault door first (within 5 blocks).");
            return null;
        }
        TileEntity te = player.world.getTileEntity(result.getBlockPos());
        if (!(te instanceof TileEntityVaultDoor)) {
            sendMessage(player, TextFormatting.RED, "That's not a vault door.");
            return null;
        }
        return (TileEntityVaultDoor) te;
    }

    private static String joinFromIndex(String[] args, int from) {
        StringBuilder sb = new StringBuilder(args[from]);
        for (int i = from + 1; i < args.length; i++) {
            sb.append(' ').append(args[i]);
        }
        return sb.toString();
    }

    // --- /sum roamer subcommands ---

    private void handleRoamer(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, TextFormatting.RED,
                "Usage: /sum roamer <greet|role> ...");
            return;
        }

        if ("greet".equalsIgnoreCase(args[1])) {
            handleRoamerGreet(sender, args);
        } else if ("role".equalsIgnoreCase(args[1])) {
            handleRoamerRole(sender, args);
        } else {
            sendMessage(sender, TextFormatting.RED,
                "Unknown roamer subcommand. Usage: /sum roamer <greet|role> ...");
        }
    }

    private void handleRoamerRole(ICommandSender sender, String[] args) {
        // /sum roamer role <set|get> <nearest|uuid> [role-id]
        if (args.length < 4) {
            sendMessage(sender, TextFormatting.RED,
                "Usage: /sum roamer role <set|get> <nearest|uuid> [role]");
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
            case "set":
                if (args.length < 5) {
                    sendMessage(sender, TextFormatting.RED,
                        "Usage: /sum roamer role set <nearest|uuid> <role>");
                    return;
                }
                RoamerRole newRole = RoamerRole.fromId(args[4]);
                roamer.setRole(newRole);
                sendMessage(sender, TextFormatting.GREEN,
                    "Set " + getRoamerDisplayName(roamer) + " role to " + newRole.getId()
                        + ". Greetings replaced with the role defaults.");
                break;
            case "get":
                sendMessage(sender, TextFormatting.GOLD,
                    getRoamerDisplayName(roamer) + " role: " + roamer.getRole().getId());
                break;
            default:
                sendMessage(sender, TextFormatting.RED,
                    "Unknown action '" + action + "'. Use set or get.");
                break;
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
            return getListOfStringsMatchingLastWord(args,
                "reloadconfig", "addroamerblock", "rmroamerblock", "roamer", "favorites", "econ", "vault");
        }
        if (args.length == 2 && "vault".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "unlock", "setcode", "info", "disown");
        }
        if (args.length == 2 && "rmroamerblock".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, SumConfig.getRoamerWalkableBlocks());
        }
        if (args.length == 2 && "roamer".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "greet", "role");
        }
        if (args.length == 3 && "roamer".equalsIgnoreCase(args[0]) && "role".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, "set", "get");
        }
        if (args.length == 4 && "roamer".equalsIgnoreCase(args[0]) && "role".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, "nearest");
        }
        if (args.length == 5 && "roamer".equalsIgnoreCase(args[0]) && "role".equalsIgnoreCase(args[1])
            && "set".equalsIgnoreCase(args[2])) {
            String[] roleIds = new String[RoamerRole.values().length];
            for (int i = 0; i < roleIds.length; i++) {
                roleIds[i] = RoamerRole.values()[i].getId();
            }
            return getListOfStringsMatchingLastWord(args, roleIds);
        }
        if (args.length == 2 && "favorites".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args,
                "list", "clear", "export", "importfile", "file");
        }
        if (args.length == 2 && "econ".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "balance", "add", "set");
        }
        if (args.length >= 3 && "econ".equalsIgnoreCase(args[0])
            && ("balance".equalsIgnoreCase(args[1]) && args.length == 3
                || ("add".equalsIgnoreCase(args[1]) || "set".equalsIgnoreCase(args[1])) && args.length == 4)) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
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
