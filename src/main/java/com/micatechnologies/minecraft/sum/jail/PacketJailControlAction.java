package com.micatechnologies.minecraft.sum.jail;

import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client→server: a button on the Jail Control Tool's screen. The server re-checks that the
 * player may run {@code /jail} and still holds the tool, carries the action out, and answers with
 * a fresh {@link PacketOpenJailControl} so the screen shows the result.
 */
public class PacketJailControlAction implements IMessage {

    public enum Action { REFRESH, RELEASE, OPEN_DOORS, CLOSE_DOORS, GOTO, SILENCE, SELECT_FOR_LINKING }

    private Action action;
    private String cell;
    private UUID player;

    public PacketJailControlAction() {}

    public PacketJailControlAction(Action action, String cell, UUID player) {
        this.action = action;
        this.cell = cell == null ? "" : cell;
        this.player = player == null ? new UUID(0L, 0L) : player;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int ordinal = buf.readByte();
        action = ordinal >= 0 && ordinal < Action.values().length ? Action.values()[ordinal] : Action.REFRESH;
        cell = ByteBufUtils.readUTF8String(buf);
        player = new UUID(buf.readLong(), buf.readLong());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action.ordinal());
        ByteBufUtils.writeUTF8String(buf, cell);
        buf.writeLong(player.getMostSignificantBits());
        buf.writeLong(player.getLeastSignificantBits());
    }

    public static class Handler implements IMessageHandler<PacketJailControlAction, IMessage> {

        @Override
        public IMessage onMessage(PacketJailControlAction msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServer().addScheduledTask(() -> handle(player, msg));
            return null;
        }

        private static void handle(EntityPlayerMP op, PacketJailControlAction msg) {
            ItemStack tool = heldTool(op);
            if (tool.isEmpty() || !op.canUseCommand(2, "sum.jail") || !SumConfig.isJailEnabled()) {
                return;
            }
            JailSavedData data = JailService.data(op.getServer());
            JailCell cell = data.getCell(msg.cell);
            switch (msg.action) {
                case RELEASE: {
                    Inmate inmate = data.getInmate(msg.player);
                    if (inmate != null) {
                        JailService.release(op.getServer(), inmate, "released by " + op.getName());
                        JailService.send(op, TextFormatting.GOLD, "Released " + inmate.getPlayerName() + ".");
                    }
                    break;
                }
                case OPEN_DOORS:
                case CLOSE_DOORS:
                    if (cell != null) {
                        boolean open = msg.action == Action.OPEN_DOORS;
                        int set = JailService.setDoors(op.getServer(), cell, open);
                        JailService.send(op, TextFormatting.GOLD, (open ? "Opened " : "Closed ") + set
                            + " of " + cell.getDoors().size() + " door(s) of " + cell.getName()
                            + (set < cell.getDoors().size() ? " (the rest are unloaded or gone)." : "."));
                    }
                    break;
                case GOTO:
                    if (cell != null) {
                        JailService.putInCell(op, cell);
                    }
                    break;
                case SILENCE:
                    if (cell != null) {
                        JailAlarms.silence(op.getServer(), cell);
                        JailService.send(op, TextFormatting.GOLD, "Silenced the alarm for " + cell.getName() + ".");
                    }
                    break;
                case SELECT_FOR_LINKING:
                    ItemJailControlTool.setLinkCell(tool, cell == null ? "" : cell.getName());
                    break;
                case REFRESH:
                default:
                    break;
            }
            SumNetwork.CHANNEL.sendTo(new PacketOpenJailControl(JailControlSnapshot.build(
                op.getServer(), ItemJailControlTool.getLinkCell(tool))), op);
        }

        private static ItemStack heldTool(EntityPlayerMP player) {
            for (EnumHand hand : EnumHand.values()) {
                ItemStack stack = player.getHeldItem(hand);
                if (stack.getItem() instanceof ItemJailControlTool) return stack;
            }
            return ItemStack.EMPTY;
        }
    }
}
