package com.micatechnologies.minecraft.sum.plots;

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
 * Plot-selection wand. Shift+right-click a block to set corner A; right-click (without sneak)
 * to set corner B. The selected corners persist in the wand item's NBT so admins can hand a
 * single wand around or stash it in a chest.
 *
 * <p>Earlier versions used a left-click-for-A/right-click-for-B WorldEdit-style gesture, but
 * the {@code PlayerInteractEvent.LeftClickBlock} path is unreliable in 1.12.2 — client-side
 * cancellation can suppress the server packet, leaving NBT updates only on the client where
 * they're invisible to the {@code /sum plots create} command (which runs server-side). The
 * sneak/no-sneak right-click pattern uses only {@code onItemUse} which fires identically on
 * both sides and is bulletproof.
 *
 * <p>Used by {@code /sum plots create} which reads the held wand's selection. Op-only —
 * non-ops who somehow get the wand can click but the create command won't accept their input.
 */
public class ItemPlotWand extends Item {

    private static final String NBT_A = "CornerA";
    private static final String NBT_B = "CornerB";
    private static final String NBT_DIM = "Dim";

    public ItemPlotWand() {
        setRegistryName(SumConstants.MOD_NAMESPACE, "plot_wand");
        setTranslationKey(SumConstants.MOD_NAMESPACE + ".plot_wand");
        setMaxStackSize(1);
        setCreativeTab(SumTab.TAB);
        SumRegistry.registerItem(this);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos,
                                      EnumHand hand, EnumFacing facing,
                                      float hitX, float hitY, float hitZ) {
        if (world.isRemote) return EnumActionResult.SUCCESS;
        if (!player.canUseCommand(2, "sum.plots")) {
            return EnumActionResult.PASS;
        }
        ItemStack stack = player.getHeldItem(hand);
        if (player.isSneaking()) {
            setCorner(stack, NBT_A, pos, world.provider.getDimension());
            sendMessage(player, TextFormatting.AQUA, "Corner A set: " + describe(pos));
        } else {
            setCorner(stack, NBT_B, pos, world.provider.getDimension());
            sendMessage(player, TextFormatting.AQUA, "Corner B set: " + describe(pos));
        }
        return EnumActionResult.SUCCESS;
    }

    private static void setCorner(ItemStack stack, String key, BlockPos pos, int dimensionId) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setLong(key, pos.toLong());
        tag.setInteger(NBT_DIM, dimensionId);
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
            return;
        }
        tooltip.add(TextFormatting.GRAY + "A: " + (a == null ? "?" : describe(a)));
        tooltip.add(TextFormatting.GRAY + "B: " + (b == null ? "?" : describe(b)));
    }

    private static String describe(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static void sendMessage(EntityPlayer player, TextFormatting color, String text) {
        TextComponentString tcs = new TextComponentString(text);
        tcs.getStyle().setColor(color);
        player.sendMessage(tcs);
    }
}
