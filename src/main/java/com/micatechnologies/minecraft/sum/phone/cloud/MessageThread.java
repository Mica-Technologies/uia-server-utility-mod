package com.micatechnologies.minecraft.sum.phone.cloud;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/**
 * The owning player's view of a conversation with one other player ({@link #partnerUuid}).
 *
 * <p>Each participant has their own MessageThread instance — they're mirrors but distinct
 * objects, so {@code unreadCount} can advance independently per side. Messages are appended
 * in arrival order and FIFO-evicted past {@link #MAX_MESSAGES}: capping keeps NBT bounded
 * without ever rejecting a send, which matches the "truncate, don't break" requirement.
 */
public class MessageThread {

    public static final int MAX_MESSAGES = 50;

    public UUID partnerUuid;
    public final List<Message> messages = new ArrayList<>();
    public int unreadCount;

    public MessageThread() {}

    public MessageThread(UUID partnerUuid) {
        this.partnerUuid = partnerUuid;
    }

    public void append(Message m) {
        messages.add(m);
        while (messages.size() > MAX_MESSAGES) {
            messages.remove(0);
        }
    }

    public Message lastMessage() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    public NBTTagCompound writeNbt() {
        NBTTagCompound t = new NBTTagCompound();
        t.setUniqueId("partner", partnerUuid);
        t.setInteger("unread", unreadCount);
        NBTTagList list = new NBTTagList();
        for (Message m : messages) list.appendTag(m.writeNbt());
        t.setTag("msgs", list);
        return t;
    }

    public static MessageThread readNbt(NBTTagCompound t) {
        MessageThread th = new MessageThread();
        th.partnerUuid = t.getUniqueId("partner");
        th.unreadCount = t.getInteger("unread");
        NBTTagList list = t.getTagList("msgs", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            th.messages.add(Message.readNbt(list.getCompoundTagAt(i)));
        }
        return th;
    }
}
