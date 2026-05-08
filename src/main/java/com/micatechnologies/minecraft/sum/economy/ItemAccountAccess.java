package com.micatechnologies.minecraft.sum.economy;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import com.micatechnologies.minecraft.sum.atm.SumGuiHandler;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.util.ITooltipFlag;
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
 * Personal-account access item — right-click anywhere to open {@link
 * com.micatechnologies.minecraft.sum.atm.GuiSumAtm}, no ATM block needed. Registered twice
 * (phone, debit card) for two visual styles; behavior is identical.
 *
 * <p><b>Owner binding.</b> A fresh item is unowned. The first player to right-click claims it
 * by stamping their UUID + display name into NBT. From then on, only that player can open the
 * GUI; other holders see a "belongs to X" message. The owner sees the GUI on every subsequent
 * right-click.
 *
 * <p>Binding is one-way in v1: there is no unbind/transfer flow. Lost items can be replaced
 * by an admin via {@code /give}.
 */
public class ItemAccountAccess extends Item {

    private static final String NBT_OWNER_UUID = "OwnerUUID";
    private static final String NBT_OWNER_NAME = "OwnerName";

    public ItemAccountAccess(String registryPath) {
        setRegistryName(SumConstants.MOD_NAMESPACE, registryPath);
        setTranslationKey(SumConstants.MOD_NAMESPACE + "." + registryPath);
        setMaxStackSize(1);
        setCreativeTab(SumTab.TAB);
        SumRegistry.registerItem(this);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }

        UUID owner = getOwnerUuid(stack);
        UUID playerId = player.getUniqueID();

        if (owner == null) {
            setOwner(stack, playerId, player.getName());
            sendMessage(player, TextFormatting.GREEN,
                "Bound to " + player.getName() + ". Right-click again to access your account.");
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }

        if (!owner.equals(playerId)) {
            String ownerName = getOwnerName(stack);
            sendMessage(player, TextFormatting.RED,
                "This belongs to " + (ownerName != null ? ownerName : "another player") + ".");
            return new ActionResult<>(EnumActionResult.PASS, stack);
        }

        // Owner using their own item. Coordinates are unused by GuiSumAtm.
        player.openGui(Sum.instance, SumGuiHandler.GUI_ATM, world, 0, 0, 0);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip,
                               ITooltipFlag flag) {
        UUID owner = getOwnerUuid(stack);
        if (owner == null) {
            tooltip.add(TextFormatting.GRAY + "Unowned — right-click to bind");
        } else {
            String name = getOwnerName(stack);
            tooltip.add(TextFormatting.GRAY + "Owner: "
                + TextFormatting.WHITE + (name != null ? name : owner.toString()));
        }
    }

    /** True if this stack has an owner stamped into NBT. */
    public static boolean isOwned(ItemStack stack) {
        return getOwnerUuid(stack) != null;
    }

    @Nullable
    public static UUID getOwnerUuid(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return null;
        // hasUniqueId checks both <key>Most and <key>Least keys.
        if (!tag.hasKey(NBT_OWNER_UUID + "Most") || !tag.hasKey(NBT_OWNER_UUID + "Least")) {
            return null;
        }
        return tag.getUniqueId(NBT_OWNER_UUID);
    }

    @Nullable
    public static String getOwnerName(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_OWNER_NAME)) return null;
        return tag.getString(NBT_OWNER_NAME);
    }

    private static void setOwner(ItemStack stack, UUID uuid, String name) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setUniqueId(NBT_OWNER_UUID, uuid);
        tag.setString(NBT_OWNER_NAME, name);
    }

    private static void sendMessage(EntityPlayer player, TextFormatting color, String text) {
        TextComponentString tcs = new TextComponentString(text);
        tcs.getStyle().setColor(color);
        player.sendMessage(tcs);
    }
}
