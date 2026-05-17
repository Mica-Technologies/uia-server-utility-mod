package com.micatechnologies.minecraft.sum.phone.cloud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

/**
 * One player's "phone cloud": their assigned phone number, their contacts, and one
 * {@link MessageThread} per conversation partner. Persisted on the server in {@link
 * PhoneCloudSavedData} so phone items can be lost/replaced without losing data.
 */
public class PhoneCloudData {

    public static final int MAX_NOTES = 12;
    public static final int MAX_NOTE_LENGTH = 200;

    public UUID ownerUuid;
    public String phoneNumber;
    public final List<Contact> contacts = new ArrayList<>();
    /** Keyed by partner UUID. LinkedHashMap preserves recent-first ordering when we touch it. */
    public final Map<UUID, MessageThread> threads = new LinkedHashMap<>();
    /** Per-player notes — stored in the cloud rather than on the phone item so they're
     *  available from the desk phone too. */
    public final List<String> notes = new ArrayList<>();

    public PhoneCloudData() {}

    public PhoneCloudData(UUID ownerUuid, String phoneNumber) {
        this.ownerUuid = ownerUuid;
        this.phoneNumber = phoneNumber;
    }

    /** Gets the thread with {@code partner}, creating a fresh one on first use. */
    public MessageThread getOrCreateThread(UUID partner) {
        return threads.computeIfAbsent(partner, MessageThread::new);
    }

    /** Total unread across all threads — driver for the home-screen Messages-tile badge. */
    public int totalUnread() {
        int total = 0;
        for (MessageThread t : threads.values()) total += t.unreadCount;
        return total;
    }

    public NBTTagCompound writeNbt() {
        NBTTagCompound t = new NBTTagCompound();
        t.setUniqueId("owner", ownerUuid);
        t.setString("num", phoneNumber == null ? "" : phoneNumber);

        NBTTagList cList = new NBTTagList();
        for (Contact c : contacts) cList.appendTag(c.writeNbt());
        t.setTag("contacts", cList);

        NBTTagList thList = new NBTTagList();
        for (MessageThread th : threads.values()) thList.appendTag(th.writeNbt());
        t.setTag("threads", thList);

        NBTTagList nList = new NBTTagList();
        for (String n : notes) nList.appendTag(new NBTTagString(n == null ? "" : n));
        t.setTag("notes", nList);
        return t;
    }

    public static PhoneCloudData readNbt(NBTTagCompound t) {
        PhoneCloudData d = new PhoneCloudData();
        d.ownerUuid = t.getUniqueId("owner");
        d.phoneNumber = t.getString("num");

        NBTTagList cList = t.getTagList("contacts", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < cList.tagCount(); i++) {
            d.contacts.add(Contact.readNbt(cList.getCompoundTagAt(i)));
        }
        NBTTagList thList = t.getTagList("threads", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < thList.tagCount(); i++) {
            MessageThread th = MessageThread.readNbt(thList.getCompoundTagAt(i));
            d.threads.put(th.partnerUuid, th);
        }
        NBTTagList nList = t.getTagList("notes", Constants.NBT.TAG_STRING);
        for (int i = 0; i < nList.tagCount() && i < MAX_NOTES; i++) {
            String s = nList.getStringTagAt(i);
            d.notes.add(s.length() > MAX_NOTE_LENGTH ? s.substring(0, MAX_NOTE_LENGTH) : s);
        }
        return d;
    }
}
