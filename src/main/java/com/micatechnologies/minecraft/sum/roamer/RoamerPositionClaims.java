package com.micatechnologies.minecraft.sum.roamer;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Tracks which roamer has claimed which position, so emergency-response AI tasks spread out
 * instead of piling every roamer onto one block.
 *
 * <p>Claims are keyed by owner entity ID rather than held in a bare {@code Set<BlockPos>}, which
 * makes them self-healing. The previous set-based approach leaked: a claim was only ever removed
 * by the owning task's {@code resetTask}, and {@code resetTask} does not run when an entity is
 * unloaded with its chunk or otherwise removed mid-task. On a dedicated server — long uptime, lots
 * of chunk churn — phantom claims accumulated on exactly the positions roamers want (the handful
 * of viable shelter spots and doorways in each building), and later roamers skipped them as
 * "taken" by entities that no longer existed. Storm sheltering decayed toward never working; a
 * server restart temporarily fixed it. Singleplayer never showed it because the statics start
 * empty each session.
 *
 * <p>Now a claim is exactly as alive as its owner: {@link #isClaimedByOther} resolves the owner ID
 * against the world and drops the claim if that entity is dead or no longer loaded. Explicit
 * {@link #releaseAll} on task reset is still the fast path; the liveness check is the backstop
 * for every path that never gets there.
 *
 * <p>Synchronized because roamers in different dimensions tick on the same server thread but
 * pathfinding helpers may not; the maps are small and contention is nil.
 */
public class RoamerPositionClaims {

    private final Map<BlockPos, Integer> owners = new HashMap<>();

    /** Claims {@code pos} for {@code ownerEntityId}, replacing any existing claim. */
    public synchronized void claim(BlockPos pos, int ownerEntityId) {
        // toImmutable: callers evaluate candidates with a reused MutableBlockPos, which would
        // otherwise mutate out from under the map key.
        owners.put(pos.toImmutable(), ownerEntityId);
    }

    /** Releases every position held by {@code ownerEntityId}. */
    public synchronized void releaseAll(int ownerEntityId) {
        owners.values().removeIf(id -> id == ownerEntityId);
    }

    /**
     * True when {@code pos} is claimed by a <em>different</em>, still-live roamer. A claim whose
     * owner has died or unloaded is pruned and reported as unclaimed.
     */
    public synchronized boolean isClaimedByOther(World world, BlockPos pos, int selfEntityId) {
        Integer owner = owners.get(pos);
        if (owner == null || owner == selfEntityId) {
            return false;
        }
        Entity ownerEntity = world.getEntityByID(owner);
        if (ownerEntity == null || ownerEntity.isDead) {
            owners.remove(pos);
            return false;
        }
        return true;
    }

    /** Drops every claim. Called when the server stops (see {@code Sum.serverStopped}). */
    public synchronized void clear() {
        owners.clear();
    }
}
