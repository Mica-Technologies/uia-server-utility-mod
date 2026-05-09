package com.micatechnologies.minecraft.sum.command;

import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.bank.BlockVaultDoor;
import com.micatechnologies.minecraft.sum.bank.TileEntityVaultDoor;
import com.micatechnologies.minecraft.sum.economy.EconomyBridge;
import com.micatechnologies.minecraft.sum.favorites.FavoriteKey;
import com.micatechnologies.minecraft.sum.favorites.FavoritesStore;
import com.micatechnologies.minecraft.sum.jobs.JobBoardSavedData;
import com.micatechnologies.minecraft.sum.jobs.JobListing;
import com.micatechnologies.minecraft.sum.plots.ItemPlotWand;
import com.micatechnologies.minecraft.sum.plots.PlotStatus;
import com.micatechnologies.minecraft.sum.plots.SumPlot;
import com.micatechnologies.minecraft.sum.plots.SumPlotsWorldSavedData;
import com.micatechnologies.minecraft.sum.roamer.EntityRoamer;
import com.micatechnologies.minecraft.sum.roamer.RoamerRole;
import com.micatechnologies.minecraft.sum.signpost.SignpostArm;
import com.micatechnologies.minecraft.sum.signpost.TileEntitySignpost;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
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
        return "/sum <help|reloadconfig|addroamerblock|rmroamerblock|roamer|favorites|econ|vault|migrate-economy|job|plots|signpost>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    /** Most subcommands stay op-only via {@link #getRequiredPermissionLevel}, but the
     *  {@code job} subcommand lets any player post a listing. We override
     *  {@code checkPermission} to short-circuit to true when args[0] == "job" and otherwise
     *  defer to the default level-2 check. */
    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;  // dispatcher allows entry; per-subcommand checks gate the rest
    }

    private static boolean requireOp(ICommandSender sender, String name) {
        if (sender.canUseCommand(2, name)) return true;
        sendMessage(sender, TextFormatting.RED, "You don't have permission for /sum " + name + ".");
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            sendMessage(sender, TextFormatting.RED, getUsage(sender));
            return;
        }

        String sub = args[0].toLowerCase();
        // Open subcommands (any player): help, job, plots. plots dispatches per-action perms below.
        boolean isOpenSub = "help".equals(sub) || "job".equals(sub) || "plots".equals(sub);
        if (!isOpenSub && !requireOp(sender, sub)) {
            return;
        }
        switch (sub) {
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
            case "migrate-economy":
                handleMigrateEconomy(server, sender, args);
                break;
            case "job":
                handleJob(sender, args);
                break;
            case "plots":
                handlePlots(sender, args);
                break;
            case "signpost":
                handleSignpost(sender, args);
                break;
            case "help":
                handleHelp(sender, args);
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

    // --- /sum signpost subcommands ---

    private void handleSignpost(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, TextFormatting.RED,
                "Usage: /sum signpost <add|remove|edit|clear|list> [args...]");
            return;
        }
        if (!(sender instanceof EntityPlayerMP)) {
            sendMessage(sender, TextFormatting.RED, "Signpost commands must be run by a player.");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        switch (args[1].toLowerCase()) {
            case "add":
                handleSignpostAdd(player, args);
                break;
            case "remove":
                handleSignpostRemove(player, args);
                break;
            case "edit":
                handleSignpostEdit(player, args);
                break;
            case "clear":
                handleSignpostClear(player, args);
                break;
            case "list":
                handleSignpostList(player);
                break;
            default:
                sendMessage(sender, TextFormatting.RED,
                    "Unknown signpost subcommand. Usage: /sum signpost <add|remove|edit|clear|list>");
                break;
        }
    }

    private void handleSignpostAdd(EntityPlayerMP player, String[] args) {
        if (args.length < 4) {
            sendMessage(player, TextFormatting.RED,
                "Usage: /sum signpost add <angle> <label...>");
            return;
        }
        TileEntitySignpost signpost = getTargetedSignpost(player);
        if (signpost == null) {
            return;
        }
        Float angle = parseAngle(player, args[2]);
        if (angle == null) {
            return;
        }
        if (signpost.getArms().size() >= TileEntitySignpost.MAX_ARMS) {
            sendMessage(player, TextFormatting.RED,
                "Signpost is full (" + TileEntitySignpost.MAX_ARMS + " arms max). Remove one first.");
            return;
        }
        String label = joinFromIndex(args, 3);
        if (signpost.addArm(new SignpostArm(label, angle))) {
            sendMessage(player, TextFormatting.GREEN,
                "Added arm: " + label + " @ " + String.format("%.0f", angle) + "°");
        }
    }

    private void handleSignpostRemove(EntityPlayerMP player, String[] args) {
        if (args.length < 3) {
            sendMessage(player, TextFormatting.RED, "Usage: /sum signpost remove <index>");
            return;
        }
        TileEntitySignpost signpost = getTargetedSignpost(player);
        if (signpost == null) {
            return;
        }
        Integer index = parseIndex(player, args[2], signpost.getArms().size());
        if (index == null) {
            return;
        }
        if (signpost.removeArm(index)) {
            sendMessage(player, TextFormatting.GREEN, "Removed arm " + index + ".");
        }
    }

    private void handleSignpostEdit(EntityPlayerMP player, String[] args) {
        if (args.length < 5) {
            sendMessage(player, TextFormatting.RED,
                "Usage: /sum signpost edit <index> <angle> <label...>");
            return;
        }
        TileEntitySignpost signpost = getTargetedSignpost(player);
        if (signpost == null) {
            return;
        }
        Integer index = parseIndex(player, args[2], signpost.getArms().size());
        if (index == null) {
            return;
        }
        Float angle = parseAngle(player, args[3]);
        if (angle == null) {
            return;
        }
        String label = joinFromIndex(args, 4);
        if (signpost.updateArm(index, label, angle)) {
            sendMessage(player, TextFormatting.GREEN,
                "Updated arm " + index + ": " + label + " @ " + String.format("%.0f", angle) + "°");
        }
    }

    private void handleSignpostClear(EntityPlayerMP player, String[] args) {
        TileEntitySignpost signpost = getTargetedSignpost(player);
        if (signpost == null) {
            return;
        }
        if (signpost.getArms().isEmpty()) {
            sendMessage(player, TextFormatting.YELLOW, "Signpost already has no arms.");
            return;
        }
        if (args.length < 3 || !"yes".equalsIgnoreCase(args[2])) {
            sendMessage(player, TextFormatting.YELLOW,
                "This will remove " + signpost.getArms().size()
                    + " arms. Confirm with: /sum signpost clear yes");
            return;
        }
        signpost.clearArms();
        sendMessage(player, TextFormatting.GREEN, "Signpost arms cleared.");
    }

    private void handleSignpostList(EntityPlayerMP player) {
        TileEntitySignpost signpost = getTargetedSignpost(player);
        if (signpost == null) {
            return;
        }
        if (signpost.getArms().isEmpty()) {
            sendMessage(player, TextFormatting.GRAY, "Signpost has no arms.");
            return;
        }
        sendMessage(player, TextFormatting.GOLD,
            "Signpost (" + signpost.getArms().size() + "/" + TileEntitySignpost.MAX_ARMS + " arms):");
        int i = 0;
        for (SignpostArm arm : signpost.getArms()) {
            sendMessage(player, TextFormatting.WHITE,
                "  " + i + ". " + (arm.getLabel().isEmpty() ? "(no label)" : arm.getLabel())
                    + " @ " + String.format("%.0f", arm.getAngleDegrees()) + "°");
            i++;
        }
    }

    private TileEntitySignpost getTargetedSignpost(EntityPlayer player) {
        RayTraceResult result = player.rayTrace(5.0, 1.0F);
        if (result == null || result.typeOfHit != RayTraceResult.Type.BLOCK) {
            sendMessage(player, TextFormatting.RED, "Look at a signpost first (within 5 blocks).");
            return null;
        }
        TileEntity te = player.world.getTileEntity(result.getBlockPos());
        if (!(te instanceof TileEntitySignpost)) {
            sendMessage(player, TextFormatting.RED, "That's not a signpost.");
            return null;
        }
        return (TileEntitySignpost) te;
    }

    private Float parseAngle(ICommandSender sender, String raw) {
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException e) {
            sendMessage(sender, TextFormatting.RED,
                "Angle must be a number in degrees (0=N, 90=E, 180=S, 270=W).");
            return null;
        }
    }

    private Integer parseIndex(ICommandSender sender, String raw, int upperBoundExclusive) {
        int index;
        try {
            index = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            sendMessage(sender, TextFormatting.RED, "Arm index must be an integer.");
            return null;
        }
        if (index < 0 || index >= upperBoundExclusive) {
            sendMessage(sender, TextFormatting.RED,
                "Arm index " + index + " out of range. Use 0 through "
                    + (upperBoundExclusive - 1) + ".");
            return null;
        }
        return index;
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

    // --- /sum migrate-economy ---

    private void handleMigrateEconomy(MinecraftServer server, ICommandSender sender, String[] args) {
        boolean verify = args.length >= 2 && "verify".equalsIgnoreCase(args[1]);
        if (!EconomyBridge.isEconomyIncBackend()) {
            sendMessage(sender, TextFormatting.YELLOW,
                "EconomyInc is not loaded — there's nothing to migrate from. SUM is already the active economy.");
            return;
        }
        if (server.getPlayerList().getCurrentPlayerCount() == 0) {
            sendMessage(sender, TextFormatting.YELLOW,
                "No online players to migrate. Migration only runs for currently-online players; have everyone log in, then run this command.");
            return;
        }
        sendMessage(sender, TextFormatting.GOLD,
            (verify ? "[verify] " : "") + "Migrating EconomyInc data → SUM for "
            + server.getPlayerList().getCurrentPlayerCount() + " online players...");

        int playersDone = 0;
        double totalBalance = 0.0;
        int totalBills = 0;

        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            // Read EconomyInc balance (the bridge's getBalance routes to EconomyInc when it's
            // the active backend, which it is at this point).
            double balance = EconomyBridge.getBalance(player);
            if (Double.isNaN(balance)) balance = 0.0;
            int bills = countEconomyIncBills(player);

            if (verify) {
                sendMessage(sender, TextFormatting.AQUA, "  " + player.getName()
                    + ": balance=$" + String.format(Locale.ROOT, "%.2f", balance)
                    + ", bill items=" + bills);
            } else {
                // Move balance: zero EconomyInc, credit SUM.
                if (balance > 0.0) {
                    EconomyBridge.adjustBalance(player, -balance);
                    com.micatechnologies.minecraft.sum.economy.ISumMoney sum =
                        player.getCapability(
                            com.micatechnologies.minecraft.sum.economy.CapabilitySumMoney.CAPABILITY,
                            null);
                    if (sum != null) {
                        sum.setBalance(sum.getBalance() + balance);
                        sum.sync(player);
                    }
                }
                // Convert any EconomyInc bills in main inventory + offhand to SUM bills.
                int converted = convertEconomyIncBillsToSum(player);
                player.inventoryContainer.detectAndSendChanges();
                sendMessage(sender, TextFormatting.AQUA, "  " + player.getName()
                    + ": migrated $" + String.format(Locale.ROOT, "%.2f", balance)
                    + ", converted " + converted + " bill items");
            }

            playersDone++;
            totalBalance += balance;
            totalBills += bills;
        }

        if (verify) {
            sendMessage(sender, TextFormatting.GREEN,
                "[verify] Total: $" + String.format(Locale.ROOT, "%.2f", totalBalance)
                + " across " + playersDone + " players, " + totalBills + " bill items would be converted.");
            sendMessage(sender, TextFormatting.GRAY,
                "Run without 'verify' to actually migrate. After migration, restart the server with EconomyInc removed from the modpack.");
        } else {
            sendMessage(sender, TextFormatting.GREEN, "Migration complete. Total: $"
                + String.format(Locale.ROOT, "%.2f", totalBalance)
                + " across " + playersDone + " players.");
            sendMessage(sender, TextFormatting.GRAY,
                "Now safe to remove EconomyInc from the modpack. Restart the server; SUM will own the economy.");
        }
    }

    /** Returns the total count of EconomyInc bill items in the player's main inventory + offhand. */
    private static int countEconomyIncBills(EntityPlayerMP player) {
        int total = 0;
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            net.minecraft.item.ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            net.minecraft.util.ResourceLocation reg = stack.getItem().getRegistryName();
            if (reg != null && "economy".equals(reg.getNamespace())
                && com.micatechnologies.minecraft.sum.atm.Bills.denominationOf(stack.getItem()) > 0) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Replaces every EconomyInc bill in the player's main inventory + offhand with the
     *  equivalent SUM bill of the same denomination. Returns the total bill count converted. */
    private static int convertEconomyIncBillsToSum(EntityPlayerMP player) {
        int converted = 0;
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            net.minecraft.item.ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            net.minecraft.util.ResourceLocation reg = stack.getItem().getRegistryName();
            if (reg == null || !"economy".equals(reg.getNamespace())) continue;
            int denom = com.micatechnologies.minecraft.sum.atm.Bills.denominationOf(stack.getItem());
            if (denom <= 0) continue;
            net.minecraft.item.Item sumBill = net.minecraft.item.Item.REGISTRY.getObject(
                new net.minecraft.util.ResourceLocation("sum", "bill_" + denom));
            if (sumBill == null) continue;
            int count = stack.getCount();
            player.inventory.setInventorySlotContents(slot, new net.minecraft.item.ItemStack(sumBill, count));
            converted += count;
        }
        return converted;
    }

    // --- /sum job ---

    /** Default 7-day expiry for new listings. */
    private static final long JOB_EXPIRY_MILLIS = 7L * 24L * 60L * 60L * 1000L;
    /** Maximum description length to avoid spam/oversize NBT. */
    private static final int JOB_MAX_DESCRIPTION = 200;

    private void handleJob(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, TextFormatting.RED,
                "Usage: /sum job <post|list|clear-mine> ...");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "post":
                handleJobPost(sender, args);
                break;
            case "list":
                handleJobList(sender);
                break;
            case "clear-mine":
                handleJobClearMine(sender);
                break;
            default:
                sendMessage(sender, TextFormatting.RED,
                    "Unknown action. Usage: /sum job <post|list|clear-mine>");
                break;
        }
    }

    private void handleJobPost(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sendMessage(sender, TextFormatting.RED, "Only players can post job listings.");
            return;
        }
        if (args.length < 4) {
            sendMessage(sender, TextFormatting.RED,
                "Usage: /sum job post <reward> <description...>");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        double reward;
        try {
            reward = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sendMessage(sender, TextFormatting.RED, "Reward must be a number.");
            return;
        }
        if (reward < 0) {
            sendMessage(sender, TextFormatting.RED, "Reward can't be negative.");
            return;
        }
        StringBuilder desc = new StringBuilder();
        for (int i = 3; i < args.length; i++) {
            if (desc.length() > 0) desc.append(' ');
            desc.append(args[i]);
        }
        String description = desc.toString().trim();
        if (description.isEmpty()) {
            sendMessage(sender, TextFormatting.RED, "Description can't be empty.");
            return;
        }
        if (description.length() > JOB_MAX_DESCRIPTION) {
            description = description.substring(0, JOB_MAX_DESCRIPTION);
        }
        long now = System.currentTimeMillis();
        JobListing listing = new JobListing(
            UUID.randomUUID(),
            player.getUniqueID(),
            player.getName(),
            description,
            reward,
            now,
            now + JOB_EXPIRY_MILLIS);
        JobBoardSavedData.get(player.world).addListing(listing);
        sendMessage(sender, TextFormatting.GREEN,
            "Posted listing — $" + String.format(Locale.ROOT, "%.2f", reward)
            + " — expires in 7 days.");
    }

    private void handleJobList(ICommandSender sender) {
        JobBoardSavedData data = JobBoardSavedData.get(sender.getEntityWorld());
        List<JobListing> active = data.getActive(System.currentTimeMillis());
        if (active.isEmpty()) {
            sendMessage(sender, TextFormatting.YELLOW, "No active job listings.");
            return;
        }
        sendMessage(sender, TextFormatting.GOLD, "Active listings (" + active.size() + "):");
        for (JobListing l : active) {
            sendMessage(sender, TextFormatting.AQUA,
                "  $" + String.format(Locale.ROOT, "%.2f", l.reward)
                + " — " + l.description + " (by " + l.posterName + ")");
        }
    }

    private void handleJobClearMine(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) {
            sendMessage(sender, TextFormatting.RED, "Only players can clear their listings.");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        JobBoardSavedData data = JobBoardSavedData.get(player.world);
        int removed = data.removeByPoster(player.getUniqueID());
        sendMessage(sender, TextFormatting.GREEN,
            "Removed " + removed + " listing(s).");
    }

    // --- /sum plots ---

    private void handlePlots(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, TextFormatting.RED,
                "Usage: /sum plots <create|delete|list|info|buy|sell|trust|untrust|transfer>");
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            // Op-only actions
            case "create":
            case "delete":
                if (!requireOp(sender, "plots " + action)) return;
                if ("create".equals(action)) handlePlotsCreate(sender, args);
                else handlePlotsDelete(sender, args);
                break;
            // Open actions
            case "list":
                handlePlotsList(sender, args);
                break;
            case "info":
                handlePlotsInfo(sender, args);
                break;
            case "buy":
                handlePlotsBuy(sender, args);
                break;
            case "sell":
                handlePlotsSell(sender, args);
                break;
            case "trust":
                handlePlotsTrust(sender, args, true);
                break;
            case "untrust":
                handlePlotsTrust(sender, args, false);
                break;
            case "transfer":
                handlePlotsTransfer(sender, args);
                break;
            default:
                sendMessage(sender, TextFormatting.RED, "Unknown plots action.");
                break;
        }
    }

    private void handlePlotsCreate(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sendMessage(sender, TextFormatting.RED, "Run from a player; the plot wand selection is per-player.");
            return;
        }
        if (args.length < 3) {
            sendMessage(sender, TextFormatting.RED, "Usage: /sum plots create <name> [price]");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        ItemStack wand = findHeldWand(player);
        if (wand.isEmpty()) {
            sendMessage(sender, TextFormatting.RED,
                "Hold a plot wand. Get one with /give @s sum:plot_wand.");
            return;
        }
        BlockPos a = ItemPlotWand.getCornerA(wand);
        BlockPos b = ItemPlotWand.getCornerB(wand);
        if (a == null || b == null) {
            sendMessage(sender, TextFormatting.RED,
                "Set both corners with the wand first (left-click + right-click two blocks).");
            return;
        }
        int dim = ItemPlotWand.getDimensionId(wand, player.dimension);
        if (dim != player.dimension) {
            sendMessage(sender, TextFormatting.RED,
                "Wand selection is in a different dimension. Re-select in the current dimension.");
            return;
        }
        // Default to full-column claim (y0=0..255). Custom 3D claims are supported by passing
        // a wand selection that explicitly sets non-default y values; we keep it simple here
        // and always full-column for the v1 admin UX.
        BlockPos cornerA = new BlockPos(a.getX(), 0, a.getZ());
        BlockPos cornerB = new BlockPos(b.getX(), 255, b.getZ());

        SumPlotsWorldSavedData data = SumPlotsWorldSavedData.get(player.world);
        if (data.overlapsAny(cornerA, cornerB, dim)) {
            sendMessage(sender, TextFormatting.RED,
                "That selection overlaps an existing plot. Delete the conflict first.");
            return;
        }

        String name = args[2];
        double price = 0.0;
        if (args.length >= 4) {
            try {
                price = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                sendMessage(sender, TextFormatting.RED, "Price must be a number.");
                return;
            }
            if (price < 0) price = 0;
        }
        PlotStatus status = price > 0 ? PlotStatus.FOR_SALE : PlotStatus.RESERVED;
        SumPlot plot = new SumPlot(UUID.randomUUID(), name, cornerA, cornerB,
            dim, price, status, System.currentTimeMillis());
        data.addPlot(plot);
        sendMessage(sender, TextFormatting.GREEN, "Plot created: " + name
            + " (id " + plot.getPlotId().toString().substring(0, 8) + ", "
            + (price > 0 ? "$" + String.format(Locale.ROOT, "%.2f", price) + " for sale" : "reserved")
            + ").");
    }

    private void handlePlotsDelete(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            sendMessage(sender, TextFormatting.RED, "Usage: /sum plots delete <id-prefix>");
            return;
        }
        SumPlotsWorldSavedData data = SumPlotsWorldSavedData.get(sender.getEntityWorld());
        SumPlot plot = findPlotByPrefix(data, args[2]);
        if (plot == null) {
            sendMessage(sender, TextFormatting.RED, "No plot matches '" + args[2] + "'.");
            return;
        }
        data.removePlot(plot.getPlotId());
        sendMessage(sender, TextFormatting.GREEN, "Deleted plot " + plot.getDisplayName() + ".");
    }

    private void handlePlotsList(ICommandSender sender, String[] args) {
        SumPlotsWorldSavedData data = SumPlotsWorldSavedData.get(sender.getEntityWorld());
        boolean nearOnly = args.length >= 3 && "near".equalsIgnoreCase(args[2]);
        java.util.Collection<SumPlot> plots;
        if (nearOnly && sender instanceof EntityPlayerMP) {
            plots = data.getPlotsNear(((EntityPlayerMP) sender).getPosition(), 64);
        } else {
            plots = data.getAll();
        }
        if (plots.isEmpty()) {
            sendMessage(sender, TextFormatting.YELLOW, "No plots.");
            return;
        }
        sendMessage(sender, TextFormatting.GOLD, "Plots (" + plots.size() + "):");
        for (SumPlot p : plots) {
            String idHash = p.getPlotId().toString().substring(0, 8);
            String summary = idHash + "  " + p.getDisplayName()
                + " [" + p.getStatus().name() + "]"
                + (p.getOwnerName().isEmpty() ? "" : " — owned by " + p.getOwnerName())
                + (p.getStatus() == PlotStatus.FOR_SALE
                    ? " — $" + String.format(Locale.ROOT, "%.2f", p.getPrice())
                    : "");
            sendMessage(sender, TextFormatting.AQUA, "  " + summary);
        }
    }

    private void handlePlotsInfo(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            sendMessage(sender, TextFormatting.RED, "Usage: /sum plots info <id-prefix>");
            return;
        }
        SumPlotsWorldSavedData data = SumPlotsWorldSavedData.get(sender.getEntityWorld());
        SumPlot plot = findPlotByPrefix(data, args[2]);
        if (plot == null) {
            sendMessage(sender, TextFormatting.RED, "No plot matches '" + args[2] + "'.");
            return;
        }
        sendMessage(sender, TextFormatting.GOLD, "--- " + plot.getDisplayName() + " ---");
        sendMessage(sender, TextFormatting.AQUA, "Id: " + plot.getPlotId());
        sendMessage(sender, TextFormatting.AQUA, "Status: " + plot.getStatus().name());
        sendMessage(sender, TextFormatting.AQUA,
            "Owner: " + (plot.getOwnerName().isEmpty() ? "(none)" : plot.getOwnerName()));
        sendMessage(sender, TextFormatting.AQUA, "Dimension: " + plot.getDimensionId());
        sendMessage(sender, TextFormatting.AQUA, "Corner A: " + describePos(plot.getCornerA()));
        sendMessage(sender, TextFormatting.AQUA, "Corner B: " + describePos(plot.getCornerB()));
        sendMessage(sender, TextFormatting.AQUA, "Volume: " + plot.volume() + " blocks");
        sendMessage(sender, TextFormatting.AQUA, "Price: $"
            + String.format(Locale.ROOT, "%.2f", plot.getPrice()));
        sendMessage(sender, TextFormatting.AQUA, "Trusted: " + plot.getTrustedBuilders().size());
    }

    private void handlePlotsBuy(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sendMessage(sender, TextFormatting.RED, "Only players can buy plots.");
            return;
        }
        if (args.length < 3) {
            sendMessage(sender, TextFormatting.RED, "Usage: /sum plots buy <id-prefix>");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        SumPlotsWorldSavedData data = SumPlotsWorldSavedData.get(player.world);
        SumPlot plot = findPlotByPrefix(data, args[2]);
        if (plot == null) {
            sendMessage(sender, TextFormatting.RED, "No plot matches '" + args[2] + "'.");
            return;
        }
        if (plot.getDimensionId() != player.dimension) {
            sendMessage(sender, TextFormatting.RED,
                "That plot is in a different dimension. Travel there to buy it.");
            return;
        }
        if (plot.getStatus() != PlotStatus.FOR_SALE) {
            sendMessage(sender, TextFormatting.RED,
                "That plot isn't for sale (status: " + plot.getStatus() + ").");
            return;
        }
        if (plot.getPrice() <= 0) {
            sendMessage(sender, TextFormatting.RED,
                "That plot has no listed price; ask an admin to set one.");
            return;
        }
        if (!EconomyBridge.isAvailable()) {
            sendMessage(sender, TextFormatting.RED, "No economy backend is loaded.");
            return;
        }
        double balance = EconomyBridge.getBalance(player);
        if (Double.isNaN(balance) || balance < plot.getPrice()) {
            sendMessage(sender, TextFormatting.RED,
                "Insufficient funds. Need $"
                + String.format(Locale.ROOT, "%.2f", plot.getPrice())
                + ", have $" + String.format(Locale.ROOT, "%.2f", balance) + ".");
            return;
        }
        if (!EconomyBridge.adjustBalance(player, -plot.getPrice())) {
            sendMessage(sender, TextFormatting.RED, "Charge failed; purchase aborted.");
            return;
        }
        plot.setOwner(player.getUniqueID(), player.getName());
        plot.setStatus(PlotStatus.OWNED);
        data.touch();
        sendMessage(sender, TextFormatting.GREEN, "Bought " + plot.getDisplayName()
            + " for $" + String.format(Locale.ROOT, "%.2f", plot.getPrice()) + ".");
    }

    private void handlePlotsSell(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sendMessage(sender, TextFormatting.RED, "Only players can list plots.");
            return;
        }
        if (args.length < 4) {
            sendMessage(sender, TextFormatting.RED, "Usage: /sum plots sell <id-prefix> <price>");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        SumPlotsWorldSavedData data = SumPlotsWorldSavedData.get(player.world);
        SumPlot plot = findPlotByPrefix(data, args[2]);
        if (plot == null) {
            sendMessage(sender, TextFormatting.RED, "No plot matches '" + args[2] + "'.");
            return;
        }
        if (!player.getUniqueID().equals(plot.getOwnerUuid())
            && !player.canUseCommand(2, "sum.plots.admin")) {
            sendMessage(sender, TextFormatting.RED, "You don't own that plot.");
            return;
        }
        double price;
        try {
            price = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sendMessage(sender, TextFormatting.RED, "Price must be a number.");
            return;
        }
        if (price < 0) {
            sendMessage(sender, TextFormatting.RED, "Price can't be negative.");
            return;
        }
        plot.setPrice(price);
        plot.setStatus(PlotStatus.FOR_SALE);
        data.touch();
        sendMessage(sender, TextFormatting.GREEN, "Listed " + plot.getDisplayName()
            + " for sale at $" + String.format(Locale.ROOT, "%.2f", price) + ".");
    }

    private void handlePlotsTrust(ICommandSender sender, String[] args, boolean trust) {
        if (!(sender instanceof EntityPlayerMP)) {
            sendMessage(sender, TextFormatting.RED, "Only players can manage plot trust.");
            return;
        }
        if (args.length < 4) {
            sendMessage(sender, TextFormatting.RED,
                "Usage: /sum plots " + (trust ? "trust" : "untrust") + " <id-prefix> <player>");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        SumPlotsWorldSavedData data = SumPlotsWorldSavedData.get(player.world);
        SumPlot plot = findPlotByPrefix(data, args[2]);
        if (plot == null) {
            sendMessage(sender, TextFormatting.RED, "No plot matches '" + args[2] + "'.");
            return;
        }
        if (!player.getUniqueID().equals(plot.getOwnerUuid())
            && !player.canUseCommand(2, "sum.plots.admin")) {
            sendMessage(sender, TextFormatting.RED, "You don't own that plot.");
            return;
        }
        UUID targetUuid = resolvePlayerUuid(player.getServer(), args[3]);
        if (targetUuid == null) {
            sendMessage(sender, TextFormatting.RED,
                "Couldn't resolve player '" + args[3] + "'. They must have logged in once.");
            return;
        }
        if (trust) {
            plot.addTrustedBuilder(targetUuid);
            data.touch();
            sendMessage(sender, TextFormatting.GREEN,
                "Trusted " + args[3] + " on " + plot.getDisplayName() + ".");
        } else {
            if (plot.removeTrustedBuilder(targetUuid)) {
                data.touch();
                sendMessage(sender, TextFormatting.GREEN,
                    "Removed " + args[3] + " from " + plot.getDisplayName() + "'s trust list.");
            } else {
                sendMessage(sender, TextFormatting.YELLOW,
                    args[3] + " wasn't trusted on that plot.");
            }
        }
    }

    private void handlePlotsTransfer(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sendMessage(sender, TextFormatting.RED, "Only players can transfer plot ownership.");
            return;
        }
        if (args.length < 4) {
            sendMessage(sender, TextFormatting.RED,
                "Usage: /sum plots transfer <id-prefix> <player>");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        SumPlotsWorldSavedData data = SumPlotsWorldSavedData.get(player.world);
        SumPlot plot = findPlotByPrefix(data, args[2]);
        if (plot == null) {
            sendMessage(sender, TextFormatting.RED, "No plot matches '" + args[2] + "'.");
            return;
        }
        if (!player.getUniqueID().equals(plot.getOwnerUuid())
            && !player.canUseCommand(2, "sum.plots.admin")) {
            sendMessage(sender, TextFormatting.RED, "You don't own that plot.");
            return;
        }
        UUID targetUuid = resolvePlayerUuid(player.getServer(), args[3]);
        if (targetUuid == null) {
            sendMessage(sender, TextFormatting.RED,
                "Couldn't resolve player '" + args[3] + "'. They must have logged in once.");
            return;
        }
        plot.setOwner(targetUuid, args[3]);
        plot.setStatus(PlotStatus.OWNED);
        data.touch();
        sendMessage(sender, TextFormatting.GREEN,
            "Transferred " + plot.getDisplayName() + " to " + args[3] + ".");
    }

    /** Resolve a player name to a UUID via the online players first, then the server's
     *  profile cache. Returns null if the name isn't recognized. */
    @Nullable
    private static UUID resolvePlayerUuid(MinecraftServer server, String name) {
        if (server == null) return null;
        EntityPlayerMP online = server.getPlayerList().getPlayerByUsername(name);
        if (online != null) return online.getUniqueID();
        com.mojang.authlib.GameProfile profile = server.getPlayerProfileCache().getGameProfileForUsername(name);
        return profile == null ? null : profile.getId();
    }

    private static String describePos(BlockPos p) {
        return p.getX() + ", " + p.getY() + ", " + p.getZ();
    }

    // --- /sum help ---

    /** A single help entry: a usage line and a one-sentence description. The {@code [op]}
     *  tag in the usage is shown verbatim; we don't filter by permission so non-ops still
     *  see what's available, just with the op tag attached. */
    private static final class HelpEntry {
        final String usage;
        final String description;
        HelpEntry(String usage, String description) {
            this.usage = usage;
            this.description = description;
        }
    }

    private static final class HelpPage {
        final String title;
        final HelpEntry[] entries;
        HelpPage(String title, HelpEntry[] entries) {
            this.title = title;
            this.entries = entries;
        }
    }

    private static HelpEntry h(String usage, String description) {
        return new HelpEntry(usage, description);
    }

    private static final HelpPage[] HELP_PAGES = new HelpPage[] {
        new HelpPage("Index", new HelpEntry[] {
            h("Bank & ATM", "page 2 — balance, econ, vault, migrate"),
            h("Roamer NPCs", "page 3 — greetings, roles, walkable blocks"),
            h("Jobs & Mail", "page 4 — job listings, mailbox notes"),
            h("Plots", "page 5 — land claims, buy/sell, trust"),
            h("Items & blocks", "page 6 — phone/card/wand, shop, changer, signs"),
            h("Admin", "page 7 — reloadconfig, favorites, migrate-economy"),
        }),
        new HelpPage("Bank & ATM", new HelpEntry[] {
            h("/balance [player]", "Check balance. Self with no arg; admin (op) can target another player."),
            h("/sum econ balance [player]", "[op] Same as /balance with explicit subcommand."),
            h("/sum econ add <player> <amount>", "[op] Add to a player's balance."),
            h("/sum econ set <player> <amount>", "[op] Set a player's balance to an exact amount."),
            h("/sum vault unlock <code>", "Unlock the vault door you're looking at (5s auto-close)."),
            h("/sum vault setcode <code>", "Set passcode on a vault door you own."),
            h("/sum vault info", "Show owner + passcode-set status of the targeted vault."),
            h("/sum vault disown", "[op] Wipe owner + passcode on the targeted vault."),
            h("/sum migrate-economy [verify]", "[op] Move EconomyInc balances + bills to SUM. 'verify' for dry-run."),
        }),
        new HelpPage("Roamer NPCs", new HelpEntry[] {
            h("/sum roamer greet add <target> <message>", "[op] Append a greeting line. <target> = nearest|<uuid>."),
            h("/sum roamer greet list <target>", "[op] List a roamer's greetings."),
            h("/sum roamer greet clear <target>", "[op] Clear a roamer's greetings."),
            h("/sum roamer role set <target> <role-id>", "[op] Set role (generic, bank_teller). Also auto-names unnamed roamers."),
            h("/sum roamer role get <target>", "[op] Show a roamer's current role."),
            h("/sum addroamerblock <block>", "[op] Add a block (e.g. minecraft:concrete) to roamer-walkable list."),
            h("/sum rmroamerblock <block>", "[op] Remove a block from the roamer-walkable list."),
        }),
        new HelpPage("Jobs & Mail", new HelpEntry[] {
            h("/sum job post <reward> <description>", "Post a job listing. Anyone can post; expires in 7 days."),
            h("/sum job list", "Print active listings to chat."),
            h("/sum job clear-mine", "Remove all listings you posted."),
            h("Mailbox block", "Place + right-click to claim. Owner reads inbox; non-owners can deposit only."),
            h("Job board block", "Place + right-click to browse server-wide listings. Posters get a Remove button."),
        }),
        new HelpPage("Plots", new HelpEntry[] {
            h("/give @s sum:plot_wand", "(creative) Get the plot wand. Sneak+right-click = corner A, right-click = corner B."),
            h("/sum plots create <name> [price]", "[op] Create a plot from the held wand's selection. price=0 → reserved."),
            h("/sum plots delete <id>", "[op] Delete a plot by id-prefix or display name."),
            h("/sum plots list [near]", "List plots in this dimension; 'near' filters to ~64 blocks of you."),
            h("/sum plots info <id>", "Show name, owner, status, corners, volume, price, trust count."),
            h("/sum plots buy <id>", "Buy a FOR_SALE plot in this dimension; deducts the listed price."),
            h("/sum plots sell <id> <price>", "Re-list your owned plot at a chosen price (FOR_SALE again)."),
            h("/sum plots trust <id> <player>", "Owner adds a trusted builder (bypasses your protection)."),
            h("/sum plots untrust <id> <player>", "Remove a trusted builder."),
            h("/sum plots transfer <id> <player>", "Transfer ownership outright. Trust list carries over."),
        }),
        new HelpPage("Items & blocks", new HelpEntry[] {
            h("Phone / Debit card", "Right-click air to bind to you, then right-click again to open the ATM GUI anywhere."),
            h("Shop block", "Vending machine. Owner sets template+stock+price; buyers see item + price + Buy button."),
            h("Bill changer", "Drop 64 bills + Bundle → packet. Drop a packet + Unbundle → 64 bills."),
            h("Bills display", "Decorative tray; right-click bills/packet to insert, right-click empty to take."),
            h("Trash can", "Right-click for ephemeral 9-slot inventory. Closing the GUI destroys contents."),
            h("Storm-shelter sign", "Roamers seek this during storm alarms; preferred over auto-discovered shelters."),
            h("Business card", "Right-click air to personalize; right-click another player to give them a copy."),
            h("Auto dropper", "Drops items continuously unless redstone-powered. Inverse of vanilla; no scatter."),
            h("Signpost", "Wayfinding post; right-click to open the arm editor GUI. Sneak+right-click for a chat dump."),
            h("/sum signpost add <angle> <label>", "Add a labeled arm pointing at a compass angle (0=N, 90=E, 180=S, 270=W)."),
            h("/sum signpost remove <index>", "Remove arm at index from the signpost you're looking at."),
            h("/sum signpost edit <index> <angle> <label>", "Replace an arm's angle and label."),
            h("/sum signpost clear", "Remove all arms (with 'yes' confirmation)."),
        }),
        new HelpPage("Admin", new HelpEntry[] {
            h("/sum reloadconfig", "[op] Re-read sum.cfg without a server restart."),
            h("/sum favorites list", "[op] List server-wide favorites file contents."),
            h("/sum favorites clear", "[op] Wipe the server-wide favorites file."),
            h("/sum favorites export", "[op] Print the favorites file path."),
            h("/sum favorites importfile <path>", "[op] Replace favorites with the contents of a file."),
            h("/sum favorites file", "[op] Print the favorites file path."),
            h("Permissions: sum.plots.bypass", "Op-2+ default. Lets ops build inside protected plots; revoke via permissions mod if not wanted."),
            h("Permissions: sum.plots.admin", "Lets non-owners run trust/untrust/transfer/sell on any plot."),
            h("Permissions: sum.jobs.remove_any", "Lets a holder remove any job listing, not just their own."),
        }),
    };

    private void handleHelp(ICommandSender sender, String[] args) {
        int totalPages = HELP_PAGES.length;
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sendMessage(sender, TextFormatting.RED, "Page must be a number 1-" + totalPages + ".");
                return;
            }
        }
        if (page < 1 || page > totalPages) {
            sendMessage(sender, TextFormatting.RED, "Page out of range. Use 1-" + totalPages + ".");
            return;
        }
        HelpPage p = HELP_PAGES[page - 1];
        sendMessage(sender, TextFormatting.GOLD,
            "─── SUM Help: " + p.title + " (" + page + "/" + totalPages + ") ───");
        for (HelpEntry e : p.entries) {
            sendMessage(sender, TextFormatting.AQUA, e.usage);
            sendMessage(sender, TextFormatting.GRAY, "  " + e.description);
        }
        if (page < totalPages) {
            sendMessage(sender, TextFormatting.YELLOW,
                "Next: /sum help " + (page + 1));
        } else {
            sendMessage(sender, TextFormatting.YELLOW,
                "Back to index: /sum help 1");
        }
    }

    @Nullable
    private static SumPlot findPlotByPrefix(SumPlotsWorldSavedData data, String prefix) {
        String p = prefix.toLowerCase();
        SumPlot match = null;
        for (SumPlot plot : data.getAll()) {
            String id = plot.getPlotId().toString().toLowerCase();
            if (id.startsWith(p) || plot.getDisplayName().equalsIgnoreCase(prefix)) {
                if (match != null) {
                    // ambiguous; bail
                    return null;
                }
                match = plot;
            }
        }
        return match;
    }

    private static ItemStack findHeldWand(EntityPlayerMP player) {
        ItemStack main = player.getHeldItemMainhand();
        if (!main.isEmpty() && main.getItem() instanceof ItemPlotWand) return main;
        ItemStack off = player.getHeldItemOffhand();
        if (!off.isEmpty() && off.getItem() instanceof ItemPlotWand) return off;
        return ItemStack.EMPTY;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args,
                "help", "reloadconfig", "addroamerblock", "rmroamerblock", "roamer", "favorites", "econ", "vault",
                "migrate-economy", "job", "plots");
        }
        if (args.length == 2 && "job".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "post", "list", "clear-mine");
        }
        if (args.length == 2 && "migrate-economy".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "verify");
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
