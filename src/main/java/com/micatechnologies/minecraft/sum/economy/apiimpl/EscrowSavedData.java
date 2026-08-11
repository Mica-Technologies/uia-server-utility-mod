package com.micatechnologies.minecraft.sum.economy.apiimpl;

import com.micatechnologies.minecraft.sum.api.EscrowTicket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

/**
 * Money held in escrow on behalf of integrating mods, persisted in the world save.
 *
 * <p>Persistence is the entire point. A stake is debited from a player's wallet the moment they
 * commit it, so if the server dies mid-round that money exists nowhere else — not in the wallet it
 * came from, and not yet in the winner's. Holding it only in memory would quietly delete a
 * player's money on every crash.
 *
 * <p>Stored per-world alongside {@link com.micatechnologies.minecraft.sum.bank.BankSavedData} and
 * for the same reasons: an administrator can inspect and repair every outstanding hold in one
 * file, and a hold survives a player entity being reset.
 */
public class EscrowSavedData extends WorldSavedData {

    private static final String NAME = "sum_escrow";
    private static final String NBT_TICKETS = "tickets";
    private static final String NBT_ID = "id";
    private static final String NBT_OWNER = "owner";
    private static final String NBT_AMOUNT = "amount";
    private static final String NBT_MOD = "mod";
    private static final String NBT_REASON = "reason";
    private static final String NBT_OPENED_AT = "openedAt";

    /** Insertion-ordered so listings and sweeps are stable rather than hash-order. */
    private final Map<UUID, EscrowTicket> tickets = new LinkedHashMap<>();

    public EscrowSavedData() {
        super(NAME);
    }

    public EscrowSavedData(String name) {
        super(name);
    }

    /** Loads or creates the store for a world. Call with the overworld so one ledger of holds
     *  serves all dimensions rather than each keeping its own. */
    public static EscrowSavedData get(World world) {
        MapStorage storage = world.getMapStorage();
        if (storage == null) {
            return new EscrowSavedData();
        }
        EscrowSavedData data =
            (EscrowSavedData) storage.getOrLoadData(EscrowSavedData.class, NAME);
        if (data == null) {
            data = new EscrowSavedData();
            storage.setData(NAME, data);
        }
        return data;
    }

    /** The open ticket with this id, or null if there is none. Named apart from the {@link
     *  #get(World)} factory so {@code get(null)} cannot be ambiguous at a call site. */
    @Nullable
    public EscrowTicket getTicket(UUID ticketId) {
        return ticketId == null ? null : tickets.get(ticketId);
    }

    /** Records a newly opened hold. */
    public void put(EscrowTicket ticket) {
        tickets.put(ticket.getId(), ticket);
        markDirty();
    }

    /** Closes a hold. @return the ticket that was removed, or null if it was not open. */
    @Nullable
    public EscrowTicket remove(UUID ticketId) {
        EscrowTicket removed = tickets.remove(ticketId);
        if (removed != null) {
            markDirty();
        }
        return removed;
    }

    /** Every open hold, oldest first. */
    public List<EscrowTicket> listAll() {
        List<EscrowTicket> all = new ArrayList<>(tickets.values());
        all.sort(Comparator.comparingLong(EscrowTicket::getOpenedAtMillis));
        return all;
    }

    /** Open holds belonging to one mod, oldest first. */
    public List<EscrowTicket> listFor(String modId) {
        List<EscrowTicket> mine = new ArrayList<>();
        for (EscrowTicket ticket : tickets.values()) {
            if (ticket.getOwningModId().equals(modId)) {
                mine.add(ticket);
            }
        }
        mine.sort(Comparator.comparingLong(EscrowTicket::getOpenedAtMillis));
        return mine;
    }

    public int size() {
        return tickets.size();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        tickets.clear();
        NBTTagList list = nbt.getTagList(NBT_TICKETS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (!entry.hasUniqueId(NBT_ID) || !entry.hasUniqueId(NBT_OWNER)) {
                // A hold with no id or no owner cannot be released or refunded to anyone, so
                // keeping it would only leave money stranded with no way to reach it.
                continue;
            }
            EscrowTicket ticket = new EscrowTicket(
                entry.getUniqueId(NBT_ID),
                entry.getUniqueId(NBT_OWNER),
                entry.getDouble(NBT_AMOUNT),
                entry.getString(NBT_MOD),
                entry.getString(NBT_REASON),
                entry.getLong(NBT_OPENED_AT));
            tickets.put(ticket.getId(), ticket);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (EscrowTicket ticket : tickets.values()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setUniqueId(NBT_ID, ticket.getId());
            tag.setUniqueId(NBT_OWNER, ticket.getOwner());
            tag.setDouble(NBT_AMOUNT, ticket.getAmount());
            tag.setString(NBT_MOD, ticket.getOwningModId());
            tag.setString(NBT_REASON, ticket.getReason() == null ? "" : ticket.getReason());
            tag.setLong(NBT_OPENED_AT, ticket.getOpenedAtMillis());
            list.appendTag(tag);
        }
        nbt.setTag(NBT_TICKETS, list);
        return nbt;
    }
}
