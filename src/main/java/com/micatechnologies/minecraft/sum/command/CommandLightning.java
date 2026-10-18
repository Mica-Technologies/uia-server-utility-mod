package com.micatechnologies.minecraft.sum.command;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * {@code /lightning [player]} — strikes harmless lightning.
 *
 * <p>With no argument the bolt lands on the block the sender is looking at, up to
 * {@link #MAX_REACH} blocks away, or {@link #FALLBACK_DISTANCE} blocks straight ahead when they
 * are looking at the sky. With a player name it lands on that player, which is also the only form
 * the console can use.
 *
 * <p>Every bolt is {@code effectOnly}: the flash and thunder happen, but it lights no fire,
 * damages no one, and turns no pig or creeper. Ops only.
 */
public class CommandLightning extends CommandBase {

    static final double MAX_REACH = 64.0;
    static final double FALLBACK_DISTANCE = 6.0;

    @Override
    public String getName() {
        return "lightning";
    }

    // No "smite" alias: ForgeEssentials, which the Alto server runs, already owns /smite.

    @Override
    public String getUsage(ICommandSender sender) {
        return "/lightning [player]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length >= 1) {
            EntityPlayerMP target = getPlayer(server, sender, args[0]);   // throws if not online
            strike(target.world, target.posX, target.posY, target.posZ);
            sendMessage(sender, TextFormatting.YELLOW, "Struck lightning at " + target.getName() + ".");
            return;
        }
        if (!(sender instanceof EntityPlayerMP)) {
            sendMessage(sender, TextFormatting.RED, "From the console, name a player: /lightning <player>");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        Vec3d at = targetFor(player);
        strike(player.world, at.x, at.y, at.z);
    }

    /**
     * Where a no-argument bolt lands: the looked-at block's top face, or a point ahead of the
     * player at their feet height when nothing is in reach. {@code Entity#rayTrace} is client-only
     * in 1.12.2, so the trace is done by hand.
     */
    private static Vec3d targetFor(EntityPlayerMP player) {
        Vec3d eyes = player.getPositionEyes(1.0F);
        Vec3d look = player.getLook(1.0F);
        Vec3d end = eyes.add(look.x * MAX_REACH, look.y * MAX_REACH, look.z * MAX_REACH);
        RayTraceResult hit = player.world.rayTraceBlocks(eyes, end, false, true, false);
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            return new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        }
        Vec3d flat = new Vec3d(look.x, 0.0, look.z);
        if (flat.lengthSquared() < 1.0E-6) {
            // Looking straight up: there is no "ahead", so strike where they stand.
            return player.getPositionVector();
        }
        flat = flat.normalize();
        return new Vec3d(player.posX + flat.x * FALLBACK_DISTANCE, player.posY,
            player.posZ + flat.z * FALLBACK_DISTANCE);
    }

    private static void strike(World world, double x, double y, double z) {
        world.addWeatherEffect(new EntityLightningBolt(world, x, y, z, true));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return index == 0;
    }

    private static void sendMessage(ICommandSender sender, TextFormatting color, String text) {
        TextComponentString tcs = new TextComponentString(text);
        tcs.getStyle().setColor(color);
        sender.sendMessage(tcs);
    }
}
