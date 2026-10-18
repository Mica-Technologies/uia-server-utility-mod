package com.micatechnologies.minecraft.sum.jail;

import com.micatechnologies.minecraft.sum.SumConfig;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.command.ICommand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemChorusFruit;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.EnderTeleportEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Holds inmates: keeps them in their cell and takes away everything that could get them out.
 *
 * <p>An inmate cannot break or place blocks, use or open blocks (so no doors, buttons or chests),
 * hit or use entities, use items other than food, ender-pearl, chat, or run commands outside
 * {@code jail.allowedCommands}. Food stays usable so a long sentence cannot starve anyone, though
 * chorus fruit, which teleports, does not count.
 *
 * <p>Anything those rules miss (being pushed, a /tp from a careless op, dying and respawning at a
 * bed) is caught by the once-a-second position check, which puts the inmate back at the cell's
 * spawn point. That check also ends sentences that have run out.
 *
 * <p>The jail commands stay usable by an inmate who has permission to run them, so an operator
 * who jails themself to try a cell out can still {@code /unjail} themself.
 */
public class JailEventHandler {

    /** Names of the jail family of commands; see the class comment. */
    static final Set<String> JAIL_COMMANDS = Collections.unmodifiableSet(new HashSet<>(
        Arrays.asList("jail", "unjail", "setjail", "deljail", "jails")));

    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final long NOTICE_COOLDOWN_MS = 2000L;

    private final Map<UUID, Long> lastNoticeTime = new HashMap<>();

    // --- Keeping them in ---

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) return;
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (player.ticksExisted % CHECK_INTERVAL_TICKS != 0) return;
        enforce(player, false);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (!SumConfig.isJailEnabled()) return;
        JailSavedData data = JailService.data(player.getServer());
        Inmate inmate = data.getInmate(player.getUniqueID());
        if (inmate == null) return;
        data.refreshName(player.getUniqueID(), player.getName());
        if (enforce(player, true)) {
            long left = inmate.remainingMillis(System.currentTimeMillis());
            JailService.send(player, TextFormatting.RED, "You are in jail (" + inmate.getCellName()
                + ")" + (left < 0 ? "." : ", with " + JailService.formatDuration(left) + " left."));
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            enforce((EntityPlayerMP) event.player, true);
        }
    }

    /**
     * Releases an inmate whose sentence is over, or puts one who has left their cell back.
     *
     * @param quiet skip the "can't leave" notice, for logins and respawns, where being moved is expected
     * @return true if the player is still an inmate afterwards
     */
    private boolean enforce(EntityPlayerMP player, boolean quiet) {
        if (!SumConfig.isJailEnabled()) return false;
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        JailSavedData data = JailService.data(server);
        if (data.getInmates().isEmpty()) return false;
        Inmate inmate = data.getInmate(player.getUniqueID());
        if (inmate == null) return false;
        if (inmate.isSentenceOver(System.currentTimeMillis())) {
            JailService.release(server, inmate, "sentence served");
            return false;
        }
        JailCell cell = data.getCell(inmate.getCellName());
        if (cell == null) {
            // /deljail refuses an occupied cell, so this only happens if the save was edited.
            JailService.release(server, inmate, "cell no longer exists");
            return false;
        }
        if (!cell.containsPlayerAt(player.dimension, player.posX, player.posY, player.posZ)) {
            JailService.putInCell(player, cell);
            if (!quiet) {
                notify(player, "You can't leave your cell.");
            }
        }
        return true;
    }

    // --- Taking away the ways out ---

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (deny(event.getPlayer(), "You can't break blocks while in jail.")) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlace(BlockEvent.PlaceEvent event) {
        if (deny(event.getPlayer(), "You can't place blocks while in jail.")) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isInmate(event.getEntityPlayer())) return;
        // Deny the block always (doors, levers, chests); deny the held item unless it is food,
        // which is what lets an inmate eat while looking at the floor.
        event.setUseBlock(Event.Result.DENY);
        if (!isAllowedItem(event.getItemStack())) {
            event.setUseItem(Event.Result.DENY);
            deny(event.getEntityPlayer(), "You can't do that while in jail.");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isInmate(event.getEntityPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isInmate(event.getEntityPlayer()) && !isAllowedItem(event.getItemStack())) {
            event.setCanceled(true);
            deny(event.getEntityPlayer(), "You can't use that while in jail.");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isInmate(event.getEntityPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isInmate(event.getEntityPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(AttackEntityEvent event) {
        if (deny(event.getEntityPlayer(), "You can't attack while in jail.")) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEnderTeleport(EnderTeleportEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayer
            && isInmate((EntityPlayer) event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChat(ServerChatEvent event) {
        if (deny(event.getPlayer(), "You can't chat while in jail.")) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onCommand(CommandEvent event) {
        if (!(event.getSender() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getSender();
        if (!isInmate(player)) return;
        ICommand command = event.getCommand();
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (SumConfig.getJailAllowedCommands().contains(name)) return;
        if (JAIL_COMMANDS.contains(name)
            && command.checkPermission(player.getServer(), player)) {
            return;
        }
        event.setCanceled(true);
        JailService.send(player, TextFormatting.RED, "You can't use /" + command.getName()
            + " while in jail.");
    }

    // --- Helpers ---

    private static boolean isInmate(@Nullable EntityPlayer player) {
        if (player == null || player.world.isRemote || !SumConfig.isJailEnabled()) return false;
        MinecraftServer server = player.getServer();
        return server != null && JailService.data(server).isJailed(player.getUniqueID());
    }

    /** Food, but not chorus fruit, which teleports whoever eats it. */
    static boolean isAllowedItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemFood
            && !(stack.getItem() instanceof ItemChorusFruit);
    }

    /** True, after telling the player why, if they are an inmate and the action must stop. */
    private boolean deny(@Nullable EntityPlayer player, String why) {
        if (!isInmate(player)) return false;
        notify(player, why);
        return true;
    }

    private void notify(EntityPlayer player, String text) {
        long now = System.currentTimeMillis();
        Long last = lastNoticeTime.get(player.getUniqueID());
        if (last != null && now - last < NOTICE_COOLDOWN_MS) return;
        lastNoticeTime.put(player.getUniqueID(), now);
        if (lastNoticeTime.size() > 256) {
            lastNoticeTime.entrySet().removeIf(e -> now - e.getValue() > NOTICE_COOLDOWN_MS * 5);
        }
        JailService.send(player, TextFormatting.RED, text);
    }
}
