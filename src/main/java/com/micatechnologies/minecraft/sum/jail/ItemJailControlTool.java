package com.micatechnologies.minecraft.sum.jail;

import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * Jail Control Tool: the warden's clipboard.
 *
 * <ul>
 *   <li>Right-click the air: a screen listing every cell, who is in it and for how long, with
 *       buttons to release inmates, open or close a cell's doors, go to a cell, silence its
 *       jailbreak alarm, and choose the cell to link things to.</li>
 *   <li>Sneak and right-click a door or a Jail Alarm Relay: link it to the chosen cell, or
 *       unlink it if it already is. Sneaking is needed because a door's own right-click opens
 *       the door, and the tool would never see the click.</li>
 * </ul>
 *
 * <p>Ops only, like the jail commands. The chosen cell is kept on the tool, so each warden can
 * be wiring up a different cell.
 */
public class ItemJailControlTool extends Item {

    private static final String NBT_LINK_CELL = "LinkCell";

    public ItemJailControlTool() {
        setRegistryName(SumConstants.MOD_NAMESPACE, "jail_control_tool");
        setTranslationKey(SumConstants.MOD_NAMESPACE + ".jail_control_tool");
        setMaxStackSize(1);
        setCreativeTab(SumTab.TAB);
        SumRegistry.registerItem(this);
    }

    static String getLinkCell(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null ? "" : tag.getString(NBT_LINK_CELL);
    }

    static void setLinkCell(ItemStack stack, String cell) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setString(NBT_LINK_CELL, cell);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote && player instanceof EntityPlayerMP && allowed(player)) {
            SumNetwork.CHANNEL.sendTo(new PacketOpenJailControl(JailControlSnapshot.build(
                player.getServer(), getLinkCell(stack))), (EntityPlayerMP) player);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        // Only a sneaking click links. Anything else must PASS on both sides: on the client a
        // SUCCESS here stops the click before it reaches onItemRightClick, so pointing the tool
        // at a wall would never open the screen.
        if (!player.isSneaking()) return EnumActionResult.PASS;
        if (world.isRemote) return EnumActionResult.SUCCESS;
        if (!allowed(player)) return EnumActionResult.PASS;
        ItemStack stack = player.getHeldItem(hand);
        JailSavedData data = JailService.data(player.getServer());
        JailCell cell = data.getCell(getLinkCell(stack));
        if (cell == null) {
            JailService.send(player, TextFormatting.RED, "Choose a cell to link to first: right-click "
                + "the air with this tool and press \"Link here\" on a cell.");
            return EnumActionResult.SUCCESS;
        }
        if (world.provider.getDimension() != cell.getDimensionId()) {
            JailService.send(player, TextFormatting.RED, cell.getName() + " is in another dimension.");
            return EnumActionResult.SUCCESS;
        }
        IBlockState state = world.getBlockState(pos);
        String where = ItemJailWand.describe(pos);
        if (state.getBlock() instanceof BlockJailAlarmRelay) {
            boolean linked = data.toggleAlarm(cell, pos);
            JailService.send(player, TextFormatting.GOLD, (linked ? "Linked" : "Unlinked")
                + " the alarm relay at " + where + (linked ? " to " : " from ") + cell.getName() + ".");
        } else if (JailDoors.isDoor(state)) {
            BlockPos anchor = doorAnchor(world, pos, state);
            boolean linked = data.toggleDoor(cell, anchor);
            JailService.send(player, TextFormatting.GOLD, (linked ? "Linked" : "Unlinked")
                + " the door at " + ItemJailWand.describe(anchor) + (linked ? " to " : " from ")
                + cell.getName() + ".");
        } else {
            JailService.send(player, TextFormatting.RED,
                "That isn't a door, gate, trapdoor or Jail Alarm Relay.");
        }
        return EnumActionResult.SUCCESS;
    }

    /**
     * The block a door is recorded by, so clicking either half links the same door: a vanilla
     * door's lower half, or the bottom of a stack of the same block (a CSM cell door).
     */
    private static BlockPos doorAnchor(World world, BlockPos pos, IBlockState state) {
        if (state.getBlock() instanceof BlockDoor) {
            return state.getValue(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.UPPER ? pos.down() : pos;
        }
        BlockPos bottom = pos;
        while (world.getBlockState(bottom.down()).getBlock() == state.getBlock()) {
            bottom = bottom.down();
        }
        return bottom;
    }

    private static boolean allowed(EntityPlayer player) {
        if (!SumConfig.isJailEnabled()) {
            JailService.send(player, TextFormatting.RED, "Jail is turned off on this server.");
            return false;
        }
        return player.canUseCommand(2, "sum.jail");
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip,
                               ITooltipFlag flag) {
        String cell = getLinkCell(stack);
        tooltip.add(TextFormatting.GRAY + "Right-click: cells, inmates and alarms.");
        tooltip.add(TextFormatting.GRAY + "Sneak+right-click a door or alarm relay: link it.");
        tooltip.add(TextFormatting.GRAY + "Linking to: " + (cell.isEmpty() ? "(none chosen)" : cell));
    }
}
