package com.micatechnologies.minecraft.sum.omceapi.service;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/**
 * Persistent per-world identity and transaction counter backing the {@code X-MCE-World-Id} and
 * {@code X-MCE-World-Seq} integrity headers.
 *
 * <p>Living <b>in the world save</b> is the entire point. A restored backup takes the counter back
 * with it, so the service sees a sequence number at or below one it has already recorded and can
 * flag a rollback — the classic Minecraft economy duplication exploit, where in-world state
 * (shop tills, job escrow, chest contents) reverts while the service's ledger does not.
 *
 * <p>Storing this anywhere outside the save — a config file, a sidecar, memory — would defeat the
 * check entirely, because it would survive the very rollback it exists to detect.
 */
public class OmceWorldState extends WorldSavedData {

    private static final String NAME = "sum_omce_world";

    private UUID worldId;
    private long sequence;

    public OmceWorldState() {
        super(NAME);
    }

    public OmceWorldState(String name) {
        super(name);
    }

    /**
     * Loads or creates the state for a world. Call with the overworld so a multi-dimension server
     * keeps one counter rather than one per dimension.
     */
    public static OmceWorldState get(World world) {
        MapStorage storage = world.getMapStorage();
        if (storage == null) {
            // No map storage means no persistence; a transient instance keeps the headers present
            // but the rollback check inert, which is better than failing the request.
            return new OmceWorldState();
        }
        OmceWorldState data = (OmceWorldState) storage.getOrLoadData(OmceWorldState.class, NAME);
        if (data == null) {
            data = new OmceWorldState();
            data.worldId = UUID.randomUUID();
            data.markDirty();
            storage.setData(NAME, data);
        }
        if (data.worldId == null) {
            data.worldId = UUID.randomUUID();
            data.markDirty();
        }
        return data;
    }

    /** Stable identifier for this world save, generated once on first use. */
    public UUID getWorldId() {
        return worldId;
    }

    public long getSequence() {
        return sequence;
    }

    /**
     * Increments and returns the counter. Called once per transaction dispatched to the service.
     *
     * <p>Synchronized because transactions are dispatched from a background pool, and a lost
     * increment would weaken exactly the signal this exists to provide.
     */
    public synchronized long nextSequence() {
        sequence++;
        markDirty();
        return sequence;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        this.worldId = nbt.hasUniqueId("worldId") ? nbt.getUniqueId("worldId") : null;
        this.sequence = nbt.getLong("sequence");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        if (worldId != null) {
            nbt.setUniqueId("worldId", worldId);
        }
        nbt.setLong("sequence", sequence);
        return nbt;
    }
}
