package com.micatechnologies.minecraft.sum.loyalty;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.economy.EconomyBridge;
import com.micatechnologies.minecraft.sum.omceapi.OmceParty;
import com.micatechnologies.minecraft.sum.omceapi.OmceProtocol;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Loyalty rewards. Two reward tracks:
 *
 * <ul>
 *   <li><b>Lifetime</b> — milestones in {@link SumConfig#getLoyaltyMilestones()}. Tick counter
 *       and fired-list persist in player NBT under {@code PERSISTED_NBT_TAG}. Each milestone
 *       fires once ever for a given player. Adding a low-threshold milestone to a player who
 *       has accumulated playtime fires it on next tick (intentional for "thanks for X hours
 *       total" rewards).</li>
 *   <li><b>Session</b> — milestones in {@link SumConfig#getLoyaltySessionMilestones()}. Tick
 *       counter and fired-list live in memory only, keyed by player UUID. Reset on every
 *       {@link PlayerLoggedInEvent}; cleared on logout. Each milestone fires once per session
 *       per player. Use this for recurring per-session rewards.</li>
 * </ul>
 */
public class LoyaltyHandler {

    private static final String NBT_ROOT = "sum_loyalty";
    private static final String NBT_TICKS = "ticks";
    private static final String NBT_FIRED = "fired";

    private static final int CHECK_INTERVAL_TICKS = 100;

    /** Per-player session state. Reset on {@link PlayerLoggedInEvent}, cleared on logout.
     *  Both reads and writes are on the server thread (PlayerTickEvent / login / logout all
     *  fire on the main thread), so no synchronization needed. */
    private final Map<UUID, SessionState> sessions = new HashMap<>();

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            // Fresh session every login — overwrites any stale entry from a previous session.
            sessions.put(event.player.getUniqueID(), new SessionState());
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            sessions.remove(event.player.getUniqueID());
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!SumConfig.isLoyaltyEnabled()) {
            return;
        }
        EntityPlayer player = event.player;
        if (!(player instanceof EntityPlayerMP) || player.world.isRemote) {
            return;
        }

        // --- Lifetime track (NBT-persisted) ---
        NBTTagCompound persisted = persisted(player);
        NBTTagCompound state = persisted.hasKey(NBT_ROOT, Constants.NBT.TAG_COMPOUND)
            ? persisted.getCompoundTag(NBT_ROOT)
            : new NBTTagCompound();

        int lifetimeTicks = state.getInteger(NBT_TICKS) + 1;
        state.setInteger(NBT_TICKS, lifetimeTicks);

        if (lifetimeTicks % CHECK_INTERVAL_TICKS == 0) {
            checkLifetimeMilestones(player, state, lifetimeTicks);
        }

        persisted.setTag(NBT_ROOT, state);
        player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);

        // --- Session track (in-memory) ---
        SessionState session = sessions.computeIfAbsent(
            player.getUniqueID(), uuid -> new SessionState());
        session.ticks++;
        if (session.ticks % CHECK_INTERVAL_TICKS == 0) {
            checkSessionMilestones(player, session);
        }
    }

    private void checkLifetimeMilestones(EntityPlayer player, NBTTagCompound state, int ticks) {
        Collection<LoyaltyMilestone> milestones = SumConfig.getLoyaltyMilestones();
        if (milestones.isEmpty()) {
            return;
        }
        NBTTagList fired = state.getTagList(NBT_FIRED, Constants.NBT.TAG_INT);
        boolean changed = false;
        for (LoyaltyMilestone milestone : milestones) {
            if (ticks < milestone.getTicks()) {
                continue;
            }
            if (containsInt(fired, milestone.getMinutes())) {
                continue;
            }
            if (fireReward(player, milestone, /*sessionTrack=*/false)) {
                fired.appendTag(new NBTTagInt(milestone.getMinutes()));
                changed = true;
            }
        }
        if (changed) {
            state.setTag(NBT_FIRED, fired);
        }
    }

    private void checkSessionMilestones(EntityPlayer player, SessionState session) {
        Collection<LoyaltyMilestone> milestones = SumConfig.getLoyaltySessionMilestones();
        if (milestones.isEmpty()) {
            return;
        }
        for (LoyaltyMilestone milestone : milestones) {
            if (session.ticks < milestone.getTicks()) {
                continue;
            }
            if (session.firedMinutes.contains(milestone.getMinutes())) {
                continue;
            }
            if (fireReward(player, milestone, /*sessionTrack=*/true)) {
                session.firedMinutes.add(milestone.getMinutes());
            }
        }
    }

    private boolean fireReward(EntityPlayer player, LoyaltyMilestone milestone, boolean sessionTrack) {
        switch (milestone.getType()) {
            case MONEY:
                return fireMoneyReward(player, milestone, sessionTrack);
            case COMMAND:
                return fireCommandReward(player, milestone, sessionTrack);
            default:
                return false;
        }
    }

    private boolean fireMoneyReward(EntityPlayer player, LoyaltyMilestone milestone, boolean sessionTrack) {
        double amount;
        try {
            amount = Double.parseDouble(milestone.getValue());
        } catch (NumberFormatException e) {
            Sum.LOGGER.warn("[loyalty] milestone {}min has non-numeric money value '{}'",
                milestone.getMinutes(), milestone.getValue());
            return false;
        }
        if (!EconomyBridge.isAvailable()) {
            Sum.LOGGER.warn("[loyalty] no economy backend available; skipping {}min milestone for {}",
                milestone.getMinutes(), player.getName());
            return false;
        }
        // Money entering the economy from nothing — a faucet, so the remote ledger can tell
        // loyalty payouts apart from player-to-player movement when auditing inflation.
        //
        // The caller marks this milestone as fired on a true return, and a remote economy can
        // refuse afterwards. Unmark it in that case so the reward is retried on the next check
        // rather than silently lost forever — these are once-per-player rewards.
        if (!EconomyBridge.adjustBalance(player, amount,
            OmceProtocol.TX_LOYALTY_REWARD, OmceParty.system("faucet.loyalty"),
            "Loyalty milestone: " + milestone.getMinutes() + " minutes",
            () -> unmarkMilestone(player, milestone, sessionTrack))) {
            return false;
        }
        notify(player, TextFormatting.GOLD + "[Loyalty] " + TextFormatting.GREEN
            + "+$" + formatAmount(amount) + TextFormatting.GRAY
            + " for reaching " + milestone.getMinutes() + " minutes "
            + (sessionTrack ? "this session." : "online."));
        return true;
    }

    /**
     * Clears a milestone's "already fired" mark after a refused reward, so it is retried on the
     * next check (at most {@value #CHECK_INTERVAL_TICKS} ticks later).
     *
     * <p>Lifetime milestones live in player NBT; session ones live in the in-memory map. Both are
     * touched only from the server thread, and the rejection callback is delivered there too.
     */
    private void unmarkMilestone(EntityPlayer player, LoyaltyMilestone milestone,
        boolean sessionTrack) {
        if (sessionTrack) {
            SessionState session = sessions.get(player.getUniqueID());
            if (session != null) {
                session.firedMinutes.remove(milestone.getMinutes());
            }
            return;
        }
        NBTTagCompound persisted = persisted(player);
        if (!persisted.hasKey(NBT_ROOT, Constants.NBT.TAG_COMPOUND)) {
            return;
        }
        NBTTagCompound state = persisted.getCompoundTag(NBT_ROOT);
        NBTTagList fired = state.getTagList(NBT_FIRED, Constants.NBT.TAG_INT);
        NBTTagList kept = new NBTTagList();
        for (int i = 0; i < fired.tagCount(); i++) {
            int minutes = fired.getIntAt(i);
            if (minutes != milestone.getMinutes()) {
                kept.appendTag(new NBTTagInt(minutes));
            }
        }
        state.setTag(NBT_FIRED, kept);
        persisted.setTag(NBT_ROOT, state);
        player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        Sum.LOGGER.warn("[loyalty] {}min reward for {} was refused by the economy; it will be "
            + "retried.", milestone.getMinutes(), player.getName());
    }

    private boolean fireCommandReward(EntityPlayer player, LoyaltyMilestone milestone, boolean sessionTrack) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        String command = milestone.getValue().replace("{player}", player.getName());
        try {
            server.commandManager.executeCommand(server, command);
        } catch (Throwable t) {
            Sum.LOGGER.warn("[loyalty] command failed for {}min milestone: {}",
                milestone.getMinutes(), command, t);
            return false;
        }
        notify(player, TextFormatting.GOLD + "[Loyalty] " + TextFormatting.GRAY
            + "Reached " + milestone.getMinutes() + " minutes "
            + (sessionTrack ? "this session" : "online")
            + " — reward delivered.");
        return true;
    }

    private static void notify(EntityPlayer player, String message) {
        if (player instanceof EntityPlayerMP) {
            player.sendMessage(new TextComponentString(message));
        }
    }

    // Package-private (not private) so the pure formatting/search helpers are unit-testable.
    static String formatAmount(double amount) {
        if (amount == Math.floor(amount)) {
            return Long.toString((long) amount);
        }
        return String.format("%.2f", amount);
    }

    private static NBTTagCompound persisted(EntityPlayer player) {
        NBTTagCompound entityData = player.getEntityData();
        if (!entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG, Constants.NBT.TAG_COMPOUND)) {
            entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    static boolean containsInt(NBTTagList list, int value) {
        for (int i = 0; i < list.tagCount(); i++) {
            if (list.getIntAt(i) == value) {
                return true;
            }
        }
        return false;
    }

    private static final class SessionState {
        int ticks;
        final Set<Integer> firedMinutes = new HashSet<>();
    }
}
