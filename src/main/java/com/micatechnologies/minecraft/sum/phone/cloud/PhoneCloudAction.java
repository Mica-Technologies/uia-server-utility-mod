package com.micatechnologies.minecraft.sum.phone.cloud;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client→server tagged-union packet for everything the phone wants to do that mutates the
 * cloud: send a message, add a contact (by player name), remove a contact, mark a thread
 * read. One packet class keeps the network registration table short.
 *
 * <p>SEND_MESSAGE is rate-limited to {@link #SEND_COOLDOWN_MS}; other actions aren't (contact
 * management is rare and self-affecting).
 */
public class PhoneCloudAction implements IMessage {

    public static final byte ACTION_SEND_MESSAGE   = 0;
    public static final byte ACTION_ADD_CONTACT    = 1;
    public static final byte ACTION_REMOVE_CONTACT = 2;
    public static final byte ACTION_MARK_READ      = 3;
    public static final byte ACTION_UPDATE_NOTES   = 4;
    public static final byte ACTION_SEND_MONEY     = 5;

    public static final long SEND_COOLDOWN_MS = 5_000L;

    private static final Map<UUID, Long> LAST_SEND_AT = new HashMap<>();

    private byte action;
    private UUID targetUuid;
    private String text;        // payload for SEND_MESSAGE
    private String playerName;  // payload for ADD_CONTACT
    private List<String> notes; // payload for UPDATE_NOTES
    private double amount;      // payload for SEND_MONEY

    public PhoneCloudAction() {}

    /** Build a SEND_MESSAGE. */
    public static PhoneCloudAction sendMessage(UUID partner, String text) {
        PhoneCloudAction a = new PhoneCloudAction();
        a.action = ACTION_SEND_MESSAGE;
        a.targetUuid = partner;
        a.text = text;
        return a;
    }

    /** Build an ADD_CONTACT (resolved by player name on the server). */
    public static PhoneCloudAction addContact(String playerName) {
        PhoneCloudAction a = new PhoneCloudAction();
        a.action = ACTION_ADD_CONTACT;
        a.playerName = playerName;
        return a;
    }

    /** Build a REMOVE_CONTACT. */
    public static PhoneCloudAction removeContact(UUID target) {
        PhoneCloudAction a = new PhoneCloudAction();
        a.action = ACTION_REMOVE_CONTACT;
        a.targetUuid = target;
        return a;
    }

    /** Build a MARK_READ for a thread. */
    public static PhoneCloudAction markRead(UUID partner) {
        PhoneCloudAction a = new PhoneCloudAction();
        a.action = ACTION_MARK_READ;
        a.targetUuid = partner;
        return a;
    }

    /** Build an UPDATE_NOTES (replaces the notes list wholesale). */
    public static PhoneCloudAction updateNotes(List<String> notes) {
        PhoneCloudAction a = new PhoneCloudAction();
        a.action = ACTION_UPDATE_NOTES;
        a.notes = notes;
        return a;
    }

    /** Build a SEND_MONEY transfer to another player (both must be online). */
    public static PhoneCloudAction sendMoney(UUID target, double amount) {
        PhoneCloudAction a = new PhoneCloudAction();
        a.action = ACTION_SEND_MONEY;
        a.targetUuid = target;
        a.amount = amount;
        return a;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readByte();
        switch (action) {
            case ACTION_SEND_MESSAGE:
                this.targetUuid = new UUID(buf.readLong(), buf.readLong());
                this.text = ByteBufUtils.readUTF8String(buf);
                break;
            case ACTION_ADD_CONTACT:
                this.playerName = ByteBufUtils.readUTF8String(buf);
                break;
            case ACTION_REMOVE_CONTACT:
            case ACTION_MARK_READ:
                this.targetUuid = new UUID(buf.readLong(), buf.readLong());
                break;
            case ACTION_SEND_MONEY:
                this.targetUuid = new UUID(buf.readLong(), buf.readLong());
                this.amount = buf.readDouble();
                break;
            case ACTION_UPDATE_NOTES: {
                int count = Math.min(buf.readByte() & 0xFF,
                    com.micatechnologies.minecraft.sum.phone.cloud.PhoneCloudData.MAX_NOTES);
                this.notes = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    this.notes.add(ByteBufUtils.readUTF8String(buf));
                }
                break;
            }
            default:
                // Unknown action — leave payload null. Server handler will reject.
                break;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        switch (action) {
            case ACTION_SEND_MESSAGE:
                buf.writeLong(targetUuid.getMostSignificantBits());
                buf.writeLong(targetUuid.getLeastSignificantBits());
                ByteBufUtils.writeUTF8String(buf, text == null ? "" : text);
                break;
            case ACTION_ADD_CONTACT:
                ByteBufUtils.writeUTF8String(buf, playerName == null ? "" : playerName);
                break;
            case ACTION_REMOVE_CONTACT:
            case ACTION_MARK_READ:
                buf.writeLong(targetUuid.getMostSignificantBits());
                buf.writeLong(targetUuid.getLeastSignificantBits());
                break;
            case ACTION_SEND_MONEY:
                buf.writeLong(targetUuid.getMostSignificantBits());
                buf.writeLong(targetUuid.getLeastSignificantBits());
                buf.writeDouble(amount);
                break;
            case ACTION_UPDATE_NOTES: {
                int count = notes == null ? 0 : Math.min(notes.size(),
                    com.micatechnologies.minecraft.sum.phone.cloud.PhoneCloudData.MAX_NOTES);
                buf.writeByte(count);
                for (int i = 0; i < count; i++) {
                    String n = notes.get(i);
                    if (n == null) n = "";
                    if (n.length() > com.micatechnologies.minecraft.sum.phone.cloud.PhoneCloudData.MAX_NOTE_LENGTH) {
                        n = n.substring(0, com.micatechnologies.minecraft.sum.phone.cloud.PhoneCloudData.MAX_NOTE_LENGTH);
                    }
                    ByteBufUtils.writeUTF8String(buf, n);
                }
                break;
            }
            default: break;
        }
    }

    public static class Handler implements IMessageHandler<PhoneCloudAction, IMessage> {

        @Override
        public IMessage onMessage(PhoneCloudAction msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServer().addScheduledTask(() -> handle(player, msg));
            return null;
        }

        private void handle(EntityPlayerMP player, PhoneCloudAction msg) {
            PhoneCloudSavedData data = PhoneCloudSavedData.get(player.world);
            PhoneCloudData cloud = data.getOrCreateForPlayer(player);

            switch (msg.action) {
                case ACTION_SEND_MESSAGE:
                    handleSend(player, data, cloud, msg);
                    break;
                case ACTION_ADD_CONTACT:
                    handleAddContact(player, data, msg);
                    break;
                case ACTION_REMOVE_CONTACT:
                    data.removeContact(player.getUniqueID(), msg.targetUuid);
                    pushSync(player, data);
                    break;
                case ACTION_MARK_READ:
                    data.markThreadRead(player.getUniqueID(), msg.targetUuid);
                    pushSync(player, data);
                    break;
                case ACTION_SEND_MONEY:
                    handleSendMoney(player, msg);
                    break;
                case ACTION_UPDATE_NOTES:
                    cloud.notes.clear();
                    if (msg.notes != null) {
                        int limit = Math.min(msg.notes.size(),
                            com.micatechnologies.minecraft.sum.phone.cloud.PhoneCloudData.MAX_NOTES);
                        for (int i = 0; i < limit; i++) {
                            String n = msg.notes.get(i);
                            if (n == null) n = "";
                            if (n.length() > com.micatechnologies.minecraft.sum.phone.cloud.PhoneCloudData.MAX_NOTE_LENGTH) {
                                n = n.substring(0, com.micatechnologies.minecraft.sum.phone.cloud.PhoneCloudData.MAX_NOTE_LENGTH);
                            }
                            cloud.notes.add(n);
                        }
                    }
                    data.markDirty();
                    pushSync(player, data);
                    break;
                default:
                    Sum.LOGGER.warn("[phone] unknown cloud action {} from {}",
                        msg.action, player.getName());
                    break;
            }
        }

        private void handleSend(EntityPlayerMP sender, PhoneCloudSavedData data,
                                PhoneCloudData senderCloud, PhoneCloudAction msg) {
            long now = System.currentTimeMillis();
            Long last = LAST_SEND_AT.get(sender.getUniqueID());
            if (last != null && now - last < SEND_COOLDOWN_MS) {
                long waitS = (SEND_COOLDOWN_MS - (now - last) + 999) / 1000;
                tell(sender, TextFormatting.YELLOW,
                    "Slow down — wait " + waitS + "s before sending another message.");
                return;
            }
            String text = Message.clip(msg.text);
            if (text.trim().isEmpty()) {
                tell(sender, TextFormatting.RED, "Empty message — not sent.");
                return;
            }
            if (msg.targetUuid == null || msg.targetUuid.equals(sender.getUniqueID())) {
                tell(sender, TextFormatting.RED, "You can't message yourself.");
                return;
            }

            LAST_SEND_AT.put(sender.getUniqueID(), now);
            PhoneCloudData recipientCloud = data.recordMessage(sender, msg.targetUuid, text, now);

            // Always push sync back to sender so their UI updates.
            pushSync(sender, data);

            // If recipient is online, push their updated cloud + a chat ping.
            MinecraftServer server = sender.getServer();
            if (server == null) return;
            EntityPlayerMP recipient = server.getPlayerList().getPlayerByUUID(msg.targetUuid);
            if (recipient != null) {
                SumNetwork.CHANNEL.sendTo(new PhoneCloudSync(recipientCloud), recipient);
                tell(recipient, TextFormatting.AQUA,
                    "[phone] " + sender.getName() + ": " + truncate(text, 80));
            }
        }

        private void handleSendMoney(EntityPlayerMP sender, PhoneCloudAction msg) {
            if (msg.targetUuid == null) {
                tell(sender, TextFormatting.RED, "No recipient selected.");
                return;
            }
            MinecraftServer server = sender.getServer();
            if (server == null) return;
            EntityPlayerMP recipient = server.getPlayerList().getPlayerByUUID(msg.targetUuid);
            if (recipient == null) {
                tell(sender, TextFormatting.RED,
                    "That player is offline — they must be online to receive a payment.");
                return;
            }
            com.micatechnologies.minecraft.sum.economy.MoneyTransfer.Result result =
                com.micatechnologies.minecraft.sum.economy.MoneyTransfer.transfer(
                    sender, recipient, msg.amount);
            if (!result.ok) {
                tell(sender, TextFormatting.RED, result.error);
                return;
            }
            tell(sender, TextFormatting.GREEN,
                "Paid $" + money(result.credited) + " to " + recipient.getName()
                    + (result.fee > 0.0 ? " ($" + money(result.fee) + " fee)" : "") + ".");
            tell(recipient, TextFormatting.GREEN,
                "Received $" + money(result.credited) + " from " + sender.getName() + ".");
        }

        private static String money(double v) {
            return String.format(java.util.Locale.ROOT, "%.2f", v);
        }

        private void handleAddContact(EntityPlayerMP player, PhoneCloudSavedData data,
                                      PhoneCloudAction msg) {
            String name = msg.playerName == null ? "" : msg.playerName.trim();
            if (name.isEmpty()) {
                tell(player, TextFormatting.RED, "Type a player name to add.");
                return;
            }
            MinecraftServer server = player.getServer();
            if (server == null) return;
            GameProfile profile = server.getPlayerProfileCache().getGameProfileForUsername(name);
            if (profile == null) {
                tell(player, TextFormatting.RED,
                    "No player called \"" + name + "\" has been seen on this server.");
                return;
            }
            if (profile.getId().equals(player.getUniqueID())) {
                tell(player, TextFormatting.RED, "That's you.");
                return;
            }
            // Ensure the target has a cloud + number (so we can cache it on the contact).
            PhoneCloudData targetCloud = data.getCloud(profile.getId());
            if (targetCloud == null) {
                // Offline → eager-allocate via recordMessage-style code path.
                targetCloud = new PhoneCloudData(profile.getId(), null);
                // Use a no-op message to trigger the eager number allocation: cleaner to just
                // duplicate the allocation logic here, but the savedData treats add-time
                // allocations specially. We instead call addContact which doesn't allocate,
                // so we need to ensure the cloud exists first. Easiest: place an empty cloud
                // and let it get a number on their first phone open. Until then the cached
                // number stays blank for the contact.
                // (No need to mark dirty here since addContact below will.)
            }
            String cachedNumber = targetCloud != null && targetCloud.phoneNumber != null
                ? targetCloud.phoneNumber : "";
            data.addContact(player.getUniqueID(),
                new Contact(profile.getId(), profile.getName(), cachedNumber));
            tell(player, TextFormatting.GREEN, "Added " + profile.getName() + " to contacts.");
            pushSync(player, data);
        }

        private static void pushSync(EntityPlayerMP player, PhoneCloudSavedData data) {
            PhoneCloudData cloud = data.getCloud(player.getUniqueID());
            if (cloud != null) {
                data.refreshContactNumbers(cloud);
                SumNetwork.CHANNEL.sendTo(new PhoneCloudSync(cloud), player);
            }
        }

        private static void tell(EntityPlayerMP player, TextFormatting color, String text) {
            TextComponentString tcs = new TextComponentString(text);
            tcs.getStyle().setColor(color);
            player.sendMessage(tcs);
        }

        private static String truncate(String s, int max) {
            return s.length() > max ? s.substring(0, max - 1) + "…" : s;
        }
    }
}
