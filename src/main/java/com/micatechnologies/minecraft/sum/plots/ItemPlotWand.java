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
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Plot-selection wand. Mirrors the WorldEdit convention: left-click a block to set corner A,
 * right-click to set corner B. The selected corners persist in the wand item's NBT so admins
 * can hand a single wand around or stash one in a chest.
 *
 * <p>Used by {@code /sum plots create} which reads the held wand's selection. Op-only —
 * non-ops who somehow get the wand can left/right-click but the create command won't accept
 * their input.
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

    /** Right-click block → corner B. */
    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos,
                                      EnumHand hand, EnumFacing facing,
                                      float hitX, float hitY, float hitZ) {
        if (world.isRemote) return EnumActionResult.SUCCESS;
        if (!player.canUseCommand(2, "sum.plots")) {
            return EnumActionResult.PASS;
        }
        ItemStack stack = player.getHeldItem(hand);
        setCorner(stack, NBT_B, pos, world.provider.getDimension());
        sendMessage(player, TextFormatting.AQUA, "Corner B set: " + describe(pos));
        return EnumActionResult.SUCCESS;
    }

    /** Left-click block → corner A. Driven by {@link PlayerInteractEvent.LeftClickBlock}
     *  in {@link Listener}, since {@code Item} has no left-click-block hook of its own. */
    private static void onLeftClick(EntityPlayer player, World world, BlockPos pos, ItemStack stack) {
        if (!player.canUseCommand(2, "sum.plots")) return;
        setCorner(stack, NBT_A, pos, world.provider.getDimension());
        sendMessage(player, TextFormatting.AQUA, "Corner A set: " + describe(pos));
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
            tooltip.add(TextFormatting.GRAY + "Left-click + right-click two corners.");
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

    /** Forge event hook: left-click a block while holding the wand sets corner A and
     *  cancels the break. Registered on the EVENT_BUS in {@code Sum.preInit}. */
    public static class Listener {

        @SubscribeEvent
        public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
            ItemStack stack = event.getEntityPlayer().getHeldItem(event.getHand());
            if (stack.isEmpty() || !(stack.getItem() instanceof ItemPlotWand)) return;
            // Server-side handling only.
            if (event.getWorld().isRemote) {
                event.setCanceled(true);
                return;
            }
            ItemPlotWand.onLeftClick(event.getEntityPlayer(), event.getWorld(),
                event.getPos(), stack);
            event.setCanceled(true);  // don't break the block
        }
    }
}
