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

    /** Number layout is AAA-EEE-XXXX where AAA is the area code, EEE is the exchange
     *  ("prefix") code, and XXXX is the subscriber number — matching the NANP/NPA-NXX-
     *  XXXX shape players are used to from real-world phones. */

    /** Area code 456 is the city's "primary" code. */
    public static final int AREA_CODE_PRIMARY = 456;
    /** Area code 987 is the secondary, used when the random roll misses the primary. */
    public static final int AREA_CODE_SECONDARY = 987;
    /** Probability of getting the primary area code. The complement goes to the
     *  secondary. Tuned to {@code 0.60} to give the primary a clear majority while still
     *  surfacing the secondary often enough that players notice it exists. */
    private static final double AREA_CODE_PRIMARY_WEIGHT = 0.60;

    /** Subscriber-number range, padded to 4 digits in the formatted output. */
    private static final int SUBSCRIBER_MIN = 0;
    private static final int SUBSCRIBER_RANGE = 10_000;
    /** Exchange-code range, padded to 3 digits. The full random range is used for
     *  player-allocated numbers; desk-phone exchanges are derived from chunk coordinates
     *  in a follow-up so phones placed in the same chunk share their middle 3 digits. */
    private static final int EXCHANGE_MIN = 0;
    private static final int EXCHANGE_RANGE = 1_000;
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

    /**
     * Allocate a fresh, unused phone number for a player. Tries random
     * AAA-EEE-XXXX combinations until one isn't taken, falling back to a linear scan
     * across the full subscriber + exchange space if (extremely unlikely) the random
     * pool is exhausted.
     */
    private String allocateNumber() {
        for (int tries = 0; tries < MAX_ALLOCATION_TRIES; tries++) {
            int area = pickAreaCode();
            int exchange = EXCHANGE_MIN + random.nextInt(EXCHANGE_RANGE);
            int subscriber = SUBSCRIBER_MIN + random.nextInt(SUBSCRIBER_RANGE);
            String formatted = formatNumber(area, exchange, subscriber);
            if (!numberIndex.containsKey(formatted)) {
                return formatted;
            }
        }
        // Linear fallback: walk every combination across both area codes. With 20M
        // available numbers this should never trigger in practice.
        for (int area : new int[] { AREA_CODE_PRIMARY, AREA_CODE_SECONDARY }) {
            for (int exchange = EXCHANGE_MIN;
                 exchange < EXCHANGE_MIN + EXCHANGE_RANGE; exchange++) {
                for (int subscriber = SUBSCRIBER_MIN;
                     subscriber < SUBSCRIBER_MIN + SUBSCRIBER_RANGE; subscriber++) {
                    String formatted = formatNumber(area, exchange, subscriber);
                    if (!numberIndex.containsKey(formatted)) {
                        return formatted;
                    }
                }
            }
        }
        throw new IllegalStateException("phone-number space exhausted");
    }

    /**
     * Allocate a fresh, unused number whose exchange (middle 3 digits) is the supplied
     * value, biasing toward "real-world phone exchange" semantics — every phone in the
     * same exchange shares its middle three digits. Used by desk phones whose exchange
     * is derived from their chunk coordinates. Caller is responsible for keeping the
     * resulting number reserved (the number IS recorded in {@link #numberIndex}, but
     * not in {@link #clouds}; lookups by player UUID will not find it).
     */
    public String allocateNumberWithFixedExchange(int exchange, UUID assignTo) {
        int safeExchange = Math.floorMod(exchange, EXCHANGE_RANGE);
        for (int tries = 0; tries < MAX_ALLOCATION_TRIES; tries++) {
            int area = pickAreaCode();
            int subscriber = SUBSCRIBER_MIN + random.nextInt(SUBSCRIBER_RANGE);
            String formatted = formatNumber(area, safeExchange, subscriber);
            if (!numberIndex.containsKey(formatted)) {
                numberIndex.put(formatted, assignTo);
                markDirty();
                return formatted;
            }
        }
        // Fall back to walking the whole fixed-exchange range.
        for (int area : new int[] { AREA_CODE_PRIMARY, AREA_CODE_SECONDARY }) {
            for (int subscriber = SUBSCRIBER_MIN;
                 subscriber < SUBSCRIBER_MIN + SUBSCRIBER_RANGE; subscriber++) {
                String formatted = formatNumber(area, safeExchange, subscriber);
                if (!numberIndex.containsKey(formatted)) {
                    numberIndex.put(formatted, assignTo);
                    markDirty();
                    return formatted;
                }
            }
        }
        throw new IllegalStateException(
            "phone-number space exhausted for exchange " + safeExchange);
    }

    /**
     * Release a previously-allocated number from the index. Used by desk phones when the
     * block is broken and its number can be handed out again.
     */
    public void releaseNumber(String number) {
        if (number == null || number.isEmpty()) {
            return;
        }
        if (numberIndex.remove(number) != null) {
            markDirty();
        }
    }

    /**
     * Pick an area code per the weighted distribution. {@code random.nextDouble()} < the
     * primary weight returns 456 ({@value #AREA_CODE_PRIMARY_WEIGHT} ≈ 60%); otherwise
     * 987.
     */
    private int pickAreaCode() {
        return random.nextDouble() < AREA_CODE_PRIMARY_WEIGHT
            ? AREA_CODE_PRIMARY : AREA_CODE_SECONDARY;
    }

    /** Format a fully-specified AAA-EEE-XXXX number. */
    public static String formatNumber(int area, int exchange, int subscriber) {
        return String.format(java.util.Locale.ROOT, "%03d-%03d-%04d",
            area, exchange, subscriber);
    }

    /** Legacy 7-digit allocator preserved for the NBT-migration path; converts to the
     *  new format by prepending a freshly-weighted area code. */
    public static String formatNumber(int n) {
        return String.format(java.util.Locale.ROOT, "%03d-%04d", n / 10000, n % 10000);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (PhoneCloudData d : clouds.values()) {
            list.appendTag(d.writeNbt());
        }
        nbt.setTag("clouds", list);

        // Persist desk-phone reservations so they survive a server restart: each entry
        // in numberIndex whose owner is the DESK_PHONE sentinel is a number a placed
        // block owns. Without persistence, the index repopulates only from `clouds` and
        // a future player-number allocation could collide with a desk phone in an
        // unloaded chunk.
        NBTTagList reservations = new NBTTagList();
        for (Map.Entry<String, UUID> entry : numberIndex.entrySet()) {
            if (clouds.containsKey(entry.getValue())) {
                continue; // covered by the `clouds` list already
            }
            NBTTagCompound r = new NBTTagCompound();
            r.setString("number", entry.getKey());
            r.setUniqueId("owner", entry.getValue());
            reservations.appendTag(r);
        }
        nbt.setTag("reservations", reservations);
        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        clouds.clear();
        numberIndex.clear();
        boolean migrated = false;
        NBTTagList list = nbt.getTagList("clouds", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            PhoneCloudData d = PhoneCloudData.readNbt(list.getCompoundTagAt(i));
            // Legacy 7-digit numbers (NNN-YYYY, 8 chars) are upgraded to AAA-EEE-XXXX
            // by prepending a weighted area code. The original middle 3 digits become
            // the exchange, the last 4 become the subscriber number — so the user's
            // number is recognizable: 123-4567 → 456-123-4567 (or 987-123-4567).
            if (d.phoneNumber != null && d.phoneNumber.length() == 8
                && d.phoneNumber.charAt(3) == '-') {
                int area = pickAreaCode();
                d.phoneNumber = area + "-" + d.phoneNumber;
                migrated = true;
                Sum.LOGGER.info("[phone] Migrated legacy number for {} → {}",
                    d.ownerUuid, d.phoneNumber);
            }
            clouds.put(d.ownerUuid, d);
            if (d.phoneNumber != null && !d.phoneNumber.isEmpty()) {
                numberIndex.put(d.phoneNumber, d.ownerUuid);
            }
        }
        // Load desk-phone reservations (entries owned by sentinel UUIDs, no matching
        // cloud).
        NBTTagList reservations = nbt.getTagList("reservations", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < reservations.tagCount(); i++) {
            NBTTagCompound r = reservations.getCompoundTagAt(i);
            if (!r.hasKey("number")) continue;
            String number = r.getString("number");
            UUID owner = r.hasUniqueId("owner") ? r.getUniqueId("owner") : new UUID(0L, 0L);
            // Apply the same 7-digit migration to legacy desk-phone reservations.
            if (number.length() == 8 && number.charAt(3) == '-') {
                number = pickAreaCode() + "-" + number;
                migrated = true;
            }
            if (!number.isEmpty()) {
                numberIndex.put(number, owner);
            }
        }
        if (migrated) {
            markDirty();
        }
    }
}
