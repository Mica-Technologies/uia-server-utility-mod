package com.micatechnologies.minecraft.sum.economy.apiimpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.sum.api.EscrowTicket;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Persistence of held money.
 *
 * <p>This is the part of escrow that justifies its existence. A stake leaves the player's wallet
 * the instant they commit it, so between opening a hold and settling it the money exists <b>only
 * here</b> — not in the wallet it came from, not in anyone else's. If the round trip through NBT
 * loses a ticket, or loses its amount or its owner, that is a player's money deleted by a server
 * restart.
 *
 * <p>{@code WorldSavedData} works off-runtime with a null world ({@code markDirty} no-ops), so the
 * store can be exercised directly without Minecraft.
 */
class EscrowSavedDataTest {

    private static EscrowTicket ticket(String modId, double amount, long openedAt) {
        return new EscrowTicket(UUID.randomUUID(), UUID.randomUUID(), amount, modId,
            "[" + modId + "] stake", openedAt);
    }

    /** Writes the store to NBT and reads it back into a fresh one, as a restart would. */
    private static EscrowSavedData roundTrip(EscrowSavedData original) {
        NBTTagCompound nbt = original.writeToNBT(new NBTTagCompound());
        EscrowSavedData reloaded = new EscrowSavedData();
        reloaded.readFromNBT(nbt);
        return reloaded;
    }

    @Test
    @DisplayName("a held stake survives a restart with its amount, owner and mod intact")
    void ticketSurvivesRoundTrip() {
        EscrowSavedData data = new EscrowSavedData();
        EscrowTicket original = ticket("mycasino", 25.5, 1_700_000_000_000L);
        data.put(original);

        EscrowTicket reloaded = roundTrip(data).getTicket(original.getId());
        assertNotNull(reloaded, "a crash between the bet and the payout must not eat the stake");
        assertEquals(original.getId(), reloaded.getId());
        assertEquals(original.getOwner(), reloaded.getOwner(), "who to refund");
        assertEquals(25.5, reloaded.getAmount(), 1.0e-9, "how much to refund");
        assertEquals("mycasino", reloaded.getOwningModId());
        assertEquals(original.getReason(), reloaded.getReason());
        assertEquals(1_700_000_000_000L, reloaded.getOpenedAtMillis());
    }

    @Test
    @DisplayName("many holds survive together")
    void manyTicketsSurvive() {
        EscrowSavedData data = new EscrowSavedData();
        for (int i = 0; i < 25; i++) {
            data.put(ticket("mycasino", i + 1, 1_000L + i));
        }
        assertEquals(25, roundTrip(data).size());
    }

    @Test
    @DisplayName("an empty store round-trips to an empty store rather than failing")
    void emptyRoundTrip() {
        assertEquals(0, roundTrip(new EscrowSavedData()).size());
    }

    @Test
    @DisplayName("a hold with no id or owner is dropped, since nothing could ever settle it")
    void unusableEntriesAreDropped() {
        // Money recorded against nobody cannot be released or refunded to anyone; keeping the
        // entry would only make it look like the server still owed someone something.
        EscrowSavedData data = new EscrowSavedData();
        data.put(ticket("mycasino", 10.0, 1_000L));
        NBTTagCompound nbt = data.writeToNBT(new NBTTagCompound());
        nbt.getTagList("tickets", 10).getCompoundTagAt(0).removeTag("ownerMost");

        EscrowSavedData reloaded = new EscrowSavedData();
        reloaded.readFromNBT(nbt);
        assertEquals(0, reloaded.size());
    }

    @Test
    @DisplayName("removing a hold returns it, and removing it again returns nothing")
    void removeIsIdempotent() {
        EscrowSavedData data = new EscrowSavedData();
        EscrowTicket t = ticket("mycasino", 10.0, 1_000L);
        data.put(t);

        assertEquals(t, data.remove(t.getId()), "the first close hands back the ticket");
        assertNull(data.remove(t.getId()), "a second close must find nothing to pay out twice");
        assertEquals(0, data.size());
    }

    @Test
    @DisplayName("a mod only sees its own holds")
    void listForIsolatesMods() {
        EscrowSavedData data = new EscrowSavedData();
        data.put(ticket("mycasino", 10.0, 1_000L));
        data.put(ticket("mycasino", 20.0, 2_000L));
        data.put(ticket("othermod", 30.0, 3_000L));

        List<EscrowTicket> mine = data.listFor("mycasino");
        assertEquals(2, mine.size());
        assertTrue(mine.stream().allMatch(t -> "mycasino".equals(t.getOwningModId())),
            "one integration must not be handed another's tickets");
        assertEquals(1, data.listFor("othermod").size());
        assertTrue(data.listFor("nosuchmod").isEmpty());
    }

    @Test
    @DisplayName("listings are oldest first, so a sweep is deterministic")
    void listingsAreOldestFirst() {
        EscrowSavedData data = new EscrowSavedData();
        data.put(ticket("mycasino", 10.0, 3_000L));
        data.put(ticket("mycasino", 20.0, 1_000L));
        data.put(ticket("mycasino", 30.0, 2_000L));

        List<EscrowTicket> all = data.listAll();
        assertEquals(1_000L, all.get(0).getOpenedAtMillis());
        assertEquals(2_000L, all.get(1).getOpenedAtMillis());
        assertEquals(3_000L, all.get(2).getOpenedAtMillis());
    }

    @Test
    @DisplayName("a null id looks up nothing instead of throwing")
    void nullIdIsSafe() {
        assertNull(new EscrowSavedData().getTicket(null));
    }
}
