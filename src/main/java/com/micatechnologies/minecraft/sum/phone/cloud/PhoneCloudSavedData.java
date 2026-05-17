package com.micatechnologies.minecraft.sum.phone.cloud;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.SumConstants;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

/**
 * Server-wide persistent storage for phone clouds. One instance attached to the overworld's
 * {@link MapStorage}, shared across dimensions. Provides:
 *
 * <ul>
 *   <li>{@link #getOrCreateForPlayer} — lazy-allocates a phone number on first phone open.</li>
 *   <li>{@link #lookupByNumber} — reverse mapping for "add contact by number" if we ever want
 *       it; today we add by player name and the number is stored alongside in the contact.</li>
 *   <li>{@link #recordMessage} — appends to both participants' threads, applies FIFO eviction,
 *       and bumps recipient unread.</li>
 * </ul>
 */
public class PhoneCloudSavedData extends WorldSavedData {

    public static final String NAME = SumConstants.MOD_NAMESPACE + "_phone_cloud";

    /** Phone numbers are 7 digits in [1000000, 9999999]; the leading digit is never zero so
     *  the formatted display ("XXX-YYYY") is always 8 chars. */
    private static final int NUMBER_MIN = 1_000_000;
    private static final int NUMBER_RANGE = 9_000_000;
    private static final int MAX_ALLOCATION_TRIES = 64;

    private final Map<UUID, PhoneCloudData> clouds = new HashMap<>();
    /** Reverse index of {@code phoneNumber → owner UUID}. Rebuilt from {@link #clouds} on
     *  load; mutated alongside {@code clouds} on allocation. */
    private final Map<String, UUID> numberIndex = new HashMap<>();

    private final Random random = new Random();

    public PhoneCloudSavedData() {
        super(NAME);
    }

    public PhoneCloudSavedData(String name) {
        super(name);
    }

    public static PhoneCloudSavedData get(World world) {
        MapStorage storage = world.getMapStorage();
        if (storage == null) {
            return new PhoneCloudSavedData();
        }
        PhoneCloudSavedData data = (PhoneCloudSavedData) storage.getOrLoadData(
            PhoneCloudSavedData.class, NAME);
        if (data == null) {
            data = new PhoneCloudSavedData();
            storage.setData(NAME, data);
        }
        return data;
    }

    /** Returns the cloud for {@code player}, allocating a number on first use. */
    public PhoneCloudData getOrCreateForPlayer(EntityPlayerMP player) {
        UUID uuid = player.getUniqueID();
        PhoneCloudData existing = clouds.get(uuid);
        if (existing != null) return existing;

        String number = allocateNumber();
        PhoneCloudData fresh = new PhoneCloudData(uuid, number);
        clouds.put(uuid, fresh);
        numberIndex.put(number, uuid);
        markDirty();
        Sum.LOGGER.info("[phone] Allocated number {} to {}", number, player.getName());
        return fresh;
    }

    @Nullable
    public PhoneCloudData getCloud(UUID uuid) {
        return clouds.get(uuid);
    }

    /** Reverse lookup. Number is the formatted "XXX-YYYY" string. */
    @Nullable
    public UUID lookupByNumber(String number) {
        return numberIndex.get(number);
    }

    /** Refreshes the cached number on every contact in the given cloud so a contact's
     *  cachedNumber stays in sync with the partner's current cloud number. */
    public void refreshContactNumbers(PhoneCloudData cloud) {
        for (Contact c : cloud.contacts) {
            PhoneCloudData partner = clouds.get(c.targetUuid);
            if (partner != null) {
                c.cachedNumber = partner.phoneNumber;
            }
        }
    }

    /**
     * Appends a message to both participants' threads. Returns the recipient's cloud so the
     * caller can push a sync if they're online. The sender's thread {@code unreadCount} is
     * NOT bumped (it's their own outgoing message); the recipient's is.
     */
    public PhoneCloudData recordMessage(EntityPlayerMP sender, UUID recipientUuid, String text,
                                        long nowMillis) {
        PhoneCloudData senderCloud = getOrCreateForPlayer(sender);

        // Recipient may never have opened a phone — they don't get a cloud yet (and thus no
        // number). The message will land if/when they do. We still need a cloud to write to,
        // so allocate one with a placeholder owner UUID but no allocated number until they
        // open the phone themselves. Hmm — we can't reach an EntityPlayerMP for them if
        // offline. Just allocate a number eagerly here so they can be replied-to by number.
        PhoneCloudData recipientCloud = clouds.get(recipientUuid);
        if (recipientCloud == null) {
            String number = allocateNumber();
            recipientCloud = new PhoneCloudData(recipientUuid, number);
            clouds.put(recipientUuid, recipientCloud);
            numberIndex.put(number, recipientUuid);
            Sum.LOGGER.info("[phone] Eagerly allocated number {} for offline recipient {}",
                number, recipientUuid);
        }

        Message m = new Message(sender.getUniqueID(), text, nowMillis);
        senderCloud.getOrCreateThread(recipientUuid).append(m);
        MessageThread recipThread = recipientCloud.getOrCreateThread(sender.getUniqueID());
        recipThread.append(m);
        recipThread.unreadCount++;
        markDirty();
        return recipientCloud;
    }

    public void markThreadRead(UUID owner, UUID partner) {
        PhoneCloudData c = clouds.get(owner);
        if (c == null) return;
        MessageThread t = c.threads.get(partner);
        if (t != null && t.unreadCount != 0) {
            t.unreadCount = 0;
            markDirty();
        }
    }

    public void addContact(UUID owner, Contact contact) {
        PhoneCloudData c = clouds.get(owner);
        if (c == null) return;
        // Replace if a contact for the same target already exists.
        for (int i = 0; i < c.contacts.size(); i++) {
            if (c.contacts.get(i).targetUuid.equals(contact.targetUuid)) {
                c.contacts.set(i, contact);
                markDirty();
                return;
            }
        }
        c.contacts.add(contact);
        markDirty();
    }

    public void removeContact(UUID owner, UUID target) {
        PhoneCloudData c = clouds.get(owner);
        if (c == null) return;
        if (c.contacts.removeIf(x -> x.targetUuid.equals(target))) {
            markDirty();
        }
    }

    private String allocateNumber() {
        for (int tries = 0; tries < MAX_ALLOCATION_TRIES; tries++) {
            int n = NUMBER_MIN + random.nextInt(NUMBER_RANGE);
            String formatted = formatNumber(n);
            if (!numberIndex.containsKey(formatted)) {
                return formatted;
            }
        }
        // Fallback: linear scan. Extremely unlikely with 9M numbers.
        for (int n = NUMBER_MIN; n < NUMBER_MIN + NUMBER_RANGE; n++) {
            String formatted = formatNumber(n);
            if (!numberIndex.containsKey(formatted)) {
                return formatted;
            }
        }
        throw new IllegalStateException("phone-number space exhausted");
    }

    public static String formatNumber(int n) {
        // NNN-YYYY
        return String.format(java.util.Locale.ROOT, "%03d-%04d", n / 10000, n % 10000);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (PhoneCloudData d : clouds.values()) {
            list.appendTag(d.writeNbt());
        }
        nbt.setTag("clouds", list);
        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        clouds.clear();
        numberIndex.clear();
        NBTTagList list = nbt.getTagList("clouds", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            PhoneCloudData d = PhoneCloudData.readNbt(list.getCompoundTagAt(i));
            clouds.put(d.ownerUuid, d);
            if (d.phoneNumber != null && !d.phoneNumber.isEmpty()) {
                numberIndex.put(d.phoneNumber, d.ownerUuid);
            }
        }
    }
}
