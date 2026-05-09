package com.micatechnologies.minecraft.sum.loyalty;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.economy.EconomyBridge;
import java.util.Collection;
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
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class LoyaltyHandler {

    private static final String NBT_ROOT = "sum_loyalty";
    private static final String NBT_TICKS = "ticks";
    private static final String NBT_FIRED = "fired";

    private static final int CHECK_INTERVAL_TICKS = 100;

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
        NBTTagCompound persisted = persisted(player);
        NBTTagCompound state = persisted.hasKey(NBT_ROOT, Constants.NBT.TAG_COMPOUND)
            ? persisted.getCompoundTag(NBT_ROOT)
            : new NBTTagCompound();

        int ticks = state.getInteger(NBT_TICKS) + 1;
        state.setInteger(NBT_TICKS, ticks);

        if (ticks % CHECK_INTERVAL_TICKS == 0) {
            checkMilestones(player, state, ticks);
        }

        persisted.setTag(NBT_ROOT, state);
        player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }

    private void checkMilestones(EntityPlayer player, NBTTagCompound state, int ticks) {
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
            if (fireReward(player, milestone)) {
                fired.appendTag(new NBTTagInt(milestone.getMinutes()));
                changed = true;
            }
        }
        if (changed) {
            state.setTag(NBT_FIRED, fired);
        }
    }

    private boolean fireReward(EntityPlayer player, LoyaltyMilestone milestone) {
        switch (milestone.getType()) {
            case MONEY:
                return fireMoneyReward(player, milestone);
            case COMMAND:
                return fireCommandReward(player, milestone);
            default:
                return false;
        }
    }

    private boolean fireMoneyReward(EntityPlayer player, LoyaltyMilestone milestone) {
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
        if (!EconomyBridge.adjustBalance(player, amount)) {
            return false;
        }
        notify(player, TextFormatting.GOLD + "[Loyalty] " + TextFormatting.GREEN
            + "+$" + formatAmount(amount) + TextFormatting.GRAY
            + " for reaching " + milestone.getMinutes() + " minutes online.");
        return true;
    }

    private boolean fireCommandReward(EntityPlayer player, LoyaltyMilestone milestone) {
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
            + "Reached " + milestone.getMinutes() + " minutes online — reward delivered.");
        return true;
    }

    private static void notify(EntityPlayer player, String message) {
        if (player instanceof EntityPlayerMP) {
            player.sendMessage(new TextComponentString(message));
        }
    }

    private static String formatAmount(double amount) {
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

    private static boolean containsInt(NBTTagList list, int value) {
        for (int i = 0; i < list.tagCount(); i++) {
            if (list.getIntAt(i) == value) {
                return true;
            }
        }
        return false;
    }
}
