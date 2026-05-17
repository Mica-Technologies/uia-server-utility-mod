package com.micatechnologies.minecraft.sum.phone.cloud;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;

/**
 * A single message in a {@link MessageThread}. Stored in both participants' clouds; the
 * recipient's copy starts unread and is marked read when they open the thread.
 */
public class Message {

    public static final int MAX_TEXT_LENGTH = 240;

    public UUID senderUuid;
    public String text;
    public long timestamp;

    public Message() {}

    public Message(UUID senderUuid, String text, long timestamp) {
        this.senderUuid = senderUuid;
        this.text = clip(text);
        this.timestamp = timestamp;
    }

    public NBTTagCompound writeNbt() {
        NBTTagCompound t = new NBTTagCompound();
        t.setUniqueId("from", senderUuid);
        t.setString("txt", text == null ? "" : text);
        t.setLong("ts", timestamp);
        return t;
    }

    public static Message readNbt(NBTTagCompound t) {
        Message m = new Message();
        m.senderUuid = t.getUniqueId("from");
        m.text = t.getString("txt");
        m.timestamp = t.getLong("ts");
        return m;
    }

    public static String clip(String s) {
        if (s == null) return "";
        return s.length() > MAX_TEXT_LENGTH ? s.substring(0, MAX_TEXT_LENGTH) : s;
    }
}
