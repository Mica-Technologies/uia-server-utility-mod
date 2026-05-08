package com.micatechnologies.minecraft.sum.contacts;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * Business card item: a personal-contact slip that another player can hold onto. Three states
 * across the lifecycle:
 *
 * <ol>
 *   <li><b>Blank.</b> No NBT. Tooltip says "right-click air to personalize."</li>
 *   <li><b>Personalized.</b> Right-clicking air on a blank card stamps the holder's UUID,
 *       name, dimension, and coordinates into NBT. The card now belongs to that player as a
 *       master copy.</li>
 *   <li><b>Distributed.</b> Right-clicking a personalized card on another player gives that
 *       player a copy of the card, preserving all NBT (including any anvil-set display name).</li>
 * </ol>
 *
 * <p>Anvil-renaming the personalized card lets the owner set a "title" (e.g. "Architect for
 * hire — call me at coords"); the title shows up on the tooltip via vanilla's display-name
 * mechanism, no extra NBT key needed.
 */
public class ItemBusinessCard extends Item {

    private static final String NBT_OWNER = "OwnerUUID";
    private static final String NBT_OWNER_NAME = "OwnerName";
    private static final String NBT_DIM = "DimensionId";
    private static final String NBT_X = "X";
    private static final String NBT_Y = "Y";
    private static final String NBT_Z = "Z";

    public ItemBusinessCard() {
        setRegistryName(SumConstants.MOD_NAMESPACE, "business_card");
        setTranslationKey(SumConstants.MOD_NAMESPACE + ".business_card");
        setMaxStackSize(16);
        setCreativeTab(SumTab.TAB);
        SumRegistry.registerItem(this);
    }

    public static boolean isPersonalized(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null
            && tag.hasKey(NBT_OWNER + "Most")
            && tag.hasKey(NBT_OWNER + "Least");
    }

    private static void personalize(ItemStack stack, EntityPlayer player) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setUniqueId(NBT_OWNER, player.getUniqueID());
        tag.setString(NBT_OWNER_NAME, player.getName());
        tag.setInteger(NBT_DIM, player.dimension);
        tag.setInteger(NBT_X, (int) Math.floor(player.posX));
        tag.setInteger(NBT_Y, (int) Math.floor(player.posY));
        tag.setInteger(NBT_Z, (int) Math.floor(player.posZ));
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        if (!isPersonalized(stack)) {
            // Personalize a single card; if the stack has multiple, only one becomes
            // personalized and the rest stay blank in the player's inventory.
            ItemStack one = stack.copy();
            one.setCount(1);
            personalize(one, player);
            stack.shrink(1);
            if (!player.inventory.addItemStackToInventory(one)) {
                player.dropItem(one, false);
            }
            sendMessage(player, TextFormatting.GREEN,
                "Card personalized for " + player.getName() + ".");
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        // Already personalized — no air-click action.
        return new ActionResult<>(EnumActionResult.PASS, stack);
    }

    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer giver,
                                            EntityLivingBase target, EnumHand hand) {
        if (giver.world.isRemote) return false;
        if (!(target instanceof EntityPlayer)) return false;
        EntityPlayer recipient = (EntityPlayer) target;
        if (!isPersonalized(stack)) {
            sendMessage(giver, TextFormatting.YELLOW,
                "Personalize the card first (right-click air with it in hand).");
            return true;
        }
        if (recipient.getUniqueID().equals(giver.getUniqueID())) {
            return false;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        if (!recipient.inventory.addItemStackToInventory(copy)) {
            recipient.dropItem(copy, false);
        }
        sendMessage(giver, TextFormatting.GREEN, "Gave a card to " + recipient.getName() + ".");
        sendMessage(recipient, TextFormatting.AQUA,
            giver.getName() + " gave you a business card.");
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip,
                               ITooltipFlag flag) {
        if (!isPersonalized(stack)) {
            tooltip.add(TextFormatting.GRAY + "Blank — right-click air to personalize.");
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        tooltip.add(TextFormatting.GRAY + "Owner: "
            + TextFormatting.WHITE + tag.getString(NBT_OWNER_NAME));
        tooltip.add(TextFormatting.GRAY + "Dimension: " + tag.getInteger(NBT_DIM));
        tooltip.add(TextFormatting.GRAY + "Coords: "
            + tag.getInteger(NBT_X) + ", "
            + tag.getInteger(NBT_Y) + ", "
            + tag.getInteger(NBT_Z));
        // Anvil-set names render automatically as the item's display name; no extra hint.
    }

    private static void sendMessage(EntityPlayer player, TextFormatting color, String text) {
        TextComponentString tcs = new TextComponentString(text);
        tcs.getStyle().setColor(color);
        player.sendMessage(tcs);
    }
}
