package com.micatechnologies.minecraft.sum.jail;

import com.micatechnologies.minecraft.sum.SumConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextFormatting;

/**
 * Shared shape of the jail commands: ops only, and refused while {@code jail.enabled} is off.
 * Each command registers its own name; {@link JailEventHandler#JAIL_COMMANDS} must list it so a
 * jailed operator can still run it.
 */
abstract class JailCommandBase extends CommandBase {

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public final void execute(MinecraftServer server, ICommandSender sender, String[] args)
        throws CommandException {
        if (!SumConfig.isJailEnabled()) {
            JailService.send(sender, TextFormatting.RED,
                "Jail is turned off on this server (jail.enabled in sum.cfg).");
            return;
        }
        run(server, sender, args, JailService.data(server));
    }

    protected abstract void run(MinecraftServer server, ICommandSender sender, String[] args,
                                JailSavedData data) throws CommandException;

    static List<String> cellNames(JailSavedData data) {
        List<String> names = new ArrayList<>();
        for (JailCell cell : data.getCells()) names.add(cell.getName());
        return names;
    }
}
