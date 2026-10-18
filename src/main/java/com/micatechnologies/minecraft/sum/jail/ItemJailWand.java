package com.micatechnologies.minecraft.sum.jail;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * Jail-cell selection wand, read by {@code /setjail}. Sneak+right-click a block for corner A,
 * right-click for corner B; the corners live in the wand's NBT.
 *
 * <p>Same gesture as the plot wand, for the same reason: {@code onItemUse} fires on both sides,
 * where a left-click gesture can be cancelled client-side before the server ever sees it. Its own
 * item rather than the plot wand so a jail selection and a plot selection held in the same
 * inventory cannot be confused. Ops only.
 */
public class ItemJailWand extends Item {

    private static final String NBT_A = "CornerA";
    private static final String NBT_B = "CornerB";
    private static final String NBT_DIM = "Dim";

    public ItemJailWand() {
        setRegistryName(SumConstants.MOD_NAMESPACE, "jail_wand");
        setTranslationKey(SumConstants.MOD_NAMESPACE + ".jail_wand");
        setMaxStackSize(1);
        setCreativeTab(SumTab.TAB);
        SumRegistry.registerItem(this);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos,
                                      EnumHand hand, EnumFacing facing,
                                      float hitX, float hitY, float hitZ) {
        if (world.isRemote) return EnumActionResult.SUCCESS;
        if (!player.canUseCommand(2, "sum.jail")) {
            return EnumActionResult.PASS;
        }
        ItemStack stack = player.getHeldItem(hand);
        String key = player.isSneaking() ? NBT_A : NBT_B;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setLong(key, pos.toLong());
        tag.setInteger(NBT_DIM, world.provider.getDimension());
        TextComponentString msg = new TextComponentString(
            "Jail corner " + (key.equals(NBT_A) ? "A" : "B") + " set: " + describe(pos));
        msg.getStyle().setColor(TextFormatting.GOLD);
        player.sendMessage(msg);
        return EnumActionResult.SUCCESS;
    }

    @Nullable
    public static BlockPos getCornerA(ItemStack stack) { return getCorner(stack, NBT_A); }

    @Nullable
    public static BlockPos getCornerB(ItemStack stack) { return getCorner(stack, NBT_B); }

    @Nullable
    private static BlockPos getCorner(ItemStack stack, String key) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(key)) return null;
        return BlockPos.fromLong(tag.getLong(key));
    }

    public static int getDimensionId(ItemStack stack, int fallback) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null || !tag.hasKey(NBT_DIM) ? fallback : tag.getInteger(NBT_DIM);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip,
                               ITooltipFlag flag) {
        BlockPos a = getCornerA(stack);
        BlockPos b = getCornerB(stack);
        if (a == null && b == null) {
            tooltip.add(TextFormatting.GRAY + "Sneak+right-click = A · right-click = B.");
            tooltip.add(TextFormatting.GRAY + "Then stand inside and run /setjail <name>.");
            return;
        }
        tooltip.add(TextFormatting.GRAY + "A: " + (a == null ? "?" : describe(a)));
        tooltip.add(TextFormatting.GRAY + "B: " + (b == null ? "?" : describe(b)));
    }

    static String describe(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
