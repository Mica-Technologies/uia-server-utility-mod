package com.micatechnologies.minecraft.sum.roamer;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public class ItemRoamerConfigurator extends Item {

    public ItemRoamerConfigurator() {
        this.setRegistryName(SumConstants.MOD_NAMESPACE, "roamer_configurator");
        this.setTranslationKey(SumConstants.MOD_NAMESPACE + ".roamer_configurator");
        this.setCreativeTab(SumTab.TAB);
        this.maxStackSize = 1;
        SumRegistry.registerItem(this);
    }

    @Override
    public boolean itemInteractionForEntity(net.minecraft.item.ItemStack stack, EntityPlayer player,
                                            net.minecraft.entity.EntityLivingBase target,
                                            net.minecraft.util.EnumHand hand) {
        if (!(target instanceof EntityRoamer) || player.world.isRemote) {
            return false;
        }

        EntityRoamer roamer = (EntityRoamer) target;

        if (player.isSneaking()) {
            // Sneak + right-click: toggle greetings
            boolean newState = !roamer.isGreetEnabled();
            roamer.setGreetEnabled(newState);
            String stateText = newState ? (TextFormatting.GREEN + "enabled") : (TextFormatting.RED + "disabled");
            player.sendMessage(new TextComponentString(
                TextFormatting.GOLD + "Greetings " + stateText + TextFormatting.GOLD + " for "
                + getRoamerDisplayName(roamer)));
        } else {
            // Right-click: show current settings
            player.sendMessage(new TextComponentString(
                TextFormatting.GOLD + "--- Roamer Settings ---"));
            player.sendMessage(new TextComponentString(
                TextFormatting.YELLOW + "Name: " + TextFormatting.WHITE + getRoamerDisplayName(roamer)));
            player.sendMessage(new TextComponentString(
                TextFormatting.YELLOW + "Greetings: "
                + (roamer.isGreetEnabled() ? (TextFormatting.GREEN + "enabled") : (TextFormatting.RED + "disabled"))));
            player.sendMessage(new TextComponentString(
                TextFormatting.YELLOW + "Greet radius: " + TextFormatting.WHITE + roamer.getGreetRadius() + " blocks"));
            player.sendMessage(new TextComponentString(
                TextFormatting.YELLOW + "Greet cooldown: " + TextFormatting.WHITE
                + (roamer.getGreetCooldown() / 20) + " seconds"));

            List<String> greetings = roamer.getGreetings();
            player.sendMessage(new TextComponentString(
                TextFormatting.YELLOW + "Messages (" + greetings.size() + "):"));
            for (int i = 0; i < greetings.size(); i++) {
                player.sendMessage(new TextComponentString(
                    TextFormatting.GRAY + "  " + (i + 1) + ". " + TextFormatting.WHITE + greetings.get(i)));
            }

            player.sendMessage(new TextComponentString(
                TextFormatting.GOLD + "UUID: " + TextFormatting.GRAY + roamer.getUniqueID().toString()));
        }
        return true;
    }

    private static String getRoamerDisplayName(EntityRoamer roamer) {
        return roamer.hasCustomName() ? roamer.getCustomNameTag() : "(unnamed)";
    }
}
