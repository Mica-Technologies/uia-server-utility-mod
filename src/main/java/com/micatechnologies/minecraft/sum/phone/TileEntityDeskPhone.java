package com.micatechnologies.minecraft.sum.phone;

import com.micatechnologies.minecraft.sum.phone.cloud.PhoneCloudSavedData;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/**
 * Per-block tile entity for the desk phone. Stores the phone's own number — distinct
 * from the right-clicking player's personal phone number — so anyone who uses the desk
 * phone sees the same number to share / hand out / paint on a sign next to it.
 *
 * <p>The number is allocated on first activation (cheaper than allocating on placement,
 * since the player's first right-click is the first moment we actually need it) and
 * released back to the pool by {@code BlockDeskPhone.breakBlock} when the block is
 * destroyed.</p>
 *
 * <p>Exchange (middle 3 digits) is biased toward the block's chunk: every desk phone in
 * one chunk shares its middle 3 digits, mirroring how real-world exchange codes cluster
 * geographically. See {@link #deriveExchangeForChunk}.</p>
 */
public class TileEntityDeskPhone extends TileEntity {

    private static final String NBT_NUMBER = "Number";

    /** Sentinel owner UUID used in PhoneCloudSavedData's number index for desk-phone
     *  reservations — distinguishes them from player allocations. */
    public static final UUID DESK_PHONE_OWNER = new UUID(0L, 0L);

    private String phoneNumber;

    public String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * Lazily allocate the desk phone's number if it doesn't yet have one. Idempotent on
     * subsequent calls.
     */
    public String ensureNumberAllocated(PhoneCloudSavedData cloud) {
        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            return phoneNumber;
        }
        int exchange = deriveExchangeForChunk(getPos().getX() >> 4, getPos().getZ() >> 4);
        phoneNumber = cloud.allocateNumberWithFixedExchange(exchange, DESK_PHONE_OWNER);
        markDirty();
        return phoneNumber;
    }

    /**
     * Release this desk phone's number back to the pool. Called from BlockDeskPhone's
     * breakBlock so a placed-then-broken block doesn't permanently strand a number.
     */
    public void releaseNumber(PhoneCloudSavedData cloud) {
        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            cloud.releaseNumber(phoneNumber);
            phoneNumber = null;
            markDirty();
        }
    }

    /**
     * Hash chunk coords down to an exchange in [0, 1000). Uses a small mixing function so
     * neighboring chunks don't trivially pile onto the same exchange — distant chunks
     * may still collide (the modular reduction guarantees that), which is fine: an
     * exchange is shared, not unique.
     *
     * <p>Stable: same chunk → same exchange forever, so a player who memorizes "all
     * 456-242-XXXX numbers are in the airport" stays right.</p>
     */
    public static int deriveExchangeForChunk(int chunkX, int chunkZ) {
        // Standard "splitmix32-ish" finalizer applied to a pair-packing of chunkX/chunkZ.
        // Chosen for cheap bit-mixing rather than cryptographic strength; the resulting
        // 32-bit value modulo 1000 gives the exchange.
        long packed = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
        long h = packed;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        // Math.floorMod for positive mod even with a negative seed.
        return (int) Math.floorMod(h, 1000L);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            compound.setString(NBT_NUMBER, phoneNumber);
        }
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey(NBT_NUMBER)) {
            phoneNumber = compound.getString(NBT_NUMBER);
        }
    }

    /** Used by the SPacketUpdateTileEntity client-sync path so the desk phone's number
     *  is visible client-side without requiring a separate sync packet. */
    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(getPos(), 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }
}
