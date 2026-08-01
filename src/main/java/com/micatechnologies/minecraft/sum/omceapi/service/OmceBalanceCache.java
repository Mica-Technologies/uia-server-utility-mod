package com.micatechnologies.minecraft.sum.omceapi.service;

import com.micatechnologies.minecraft.sum.omceapi.OmceBalance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/**
 * The in-memory view of authoritative balances that every in-game read is served from.
 *
 * <p>Two invariants make this safe under concurrency:
 *
 * <ol>
 *   <li><b>Version monotonicity.</b> An authoritative balance is applied only if its
 *       {@code version} exceeds the cached one. Responses can and do arrive out of order — a slow
 *       {@code /getBalances} sweep landing after a fast {@code /processTransaction} would
 *       otherwise roll a player's displayed balance backwards.</li>
 *   <li><b>Separated optimistic deltas.</b> An in-flight spend is held in {@code pendingDelta},
 *       apart from the authoritative figure. That means an authoritative update arriving
 *       <i>while</i> a spend is in flight neither loses the pending change nor double-counts it,
 *       and a rollback subtracts exactly what it added.</li>
 * </ol>
 *
 * <p>All state is per-player and guarded by {@link ConcurrentHashMap} compute operations, so the
 * game thread can read without locking while the executor writes.
 */
public final class OmceBalanceCache {

    /** One player's cached economy state. Immutable; replaced wholesale on every change. */
    public static final class Entry {

        private final String accountId;
        private final long authoritativeBalance;
        private final long available;
        private final long version;
        private final String status;
        private final long pendingDelta;
        private final long updatedAtMillis;

        Entry(String accountId, long authoritativeBalance, long available, long version,
            String status, long pendingDelta, long updatedAtMillis) {
            this.accountId = accountId;
            this.authoritativeBalance = authoritativeBalance;
            this.available = available;
            this.version = version;
            this.status = status;
            this.pendingDelta = pendingDelta;
            this.updatedAtMillis = updatedAtMillis;
        }

        /** The last balance the service confirmed, in minor units. */
        public long getAuthoritativeBalance() {
            return authoritativeBalance;
        }

        /**
         * What the player should see: the authoritative balance adjusted by any spend still in
         * flight, so the HUD responds immediately to their own actions.
         */
        public long getEffectiveBalance() {
            return authoritativeBalance + pendingDelta;
        }

        /** Spendable balance including in-flight changes; accounts for service-side holds. */
        public long getEffectiveAvailable() {
            return available + pendingDelta;
        }

        public long getVersion() {
            return version;
        }

        @Nullable
        public String getAccountId() {
            return accountId;
        }

        public String getStatus() {
            return status;
        }

        public long getPendingDelta() {
            return pendingDelta;
        }

        /** True while at least one optimistic change has not been confirmed or rolled back. */
        public boolean hasPending() {
            return pendingDelta != 0L;
        }

        public long getUpdatedAtMillis() {
            return updatedAtMillis;
        }

        public boolean isStale(long nowMillis, long ttlMillis) {
            return nowMillis - updatedAtMillis >= ttlMillis;
        }
    }

    private final ConcurrentHashMap<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    /** Indirection over {@code System.currentTimeMillis} so staleness is testable. */
    public interface Clock {
        long nowMillis();
    }

    public OmceBalanceCache() {
        this(System::currentTimeMillis);
    }

    public OmceBalanceCache(Clock clock) {
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    @Nullable
    public Entry get(UUID playerUuid) {
        return playerUuid == null ? null : entries.get(playerUuid);
    }

    public boolean contains(UUID playerUuid) {
        return playerUuid != null && entries.containsKey(playerUuid);
    }

    /**
     * Applies an authoritative balance, honouring the version guard.
     *
     * @return true if it was applied, false if it was stale and discarded.
     */
    public boolean applyAuthoritative(OmceBalance balance) {
        UUID uuid = balance == null ? null : balance.getPlayerUuid();
        if (uuid == null) {
            return false;
        }
        final boolean[] applied = { false };
        entries.compute(uuid, (key, existing) -> {
            if (existing != null && balance.getVersion() <= existing.getVersion()
                && existing.getVersion() != 0L) {
                // Stale or duplicate: keep what we have. Version 0 means the service does not
                // version this account, in which case last-write-wins is all we can do.
                return existing;
            }
            applied[0] = true;
            long pending = existing == null ? 0L : existing.pendingDelta;
            String accountId = balance.getAccountId().isEmpty() && existing != null
                ? existing.accountId
                : balance.getAccountId();
            return new Entry(accountId, balance.getBalance(), balance.getAvailable(),
                balance.getVersion(), balance.getStatus(), pending, clock.nowMillis());
        });
        return applied[0];
    }

    /** Records the account id for a player before any balance is known. */
    public void putAccountId(UUID playerUuid, String accountId, String status) {
        if (playerUuid == null) {
            return;
        }
        entries.compute(playerUuid, (key, existing) -> existing == null
            ? new Entry(accountId, 0L, 0L, 0L, status, 0L, 0L)
            : new Entry(accountId, existing.authoritativeBalance, existing.available,
                existing.version, status, existing.pendingDelta, existing.updatedAtMillis));
    }

    /**
     * Adds an optimistic delta for a spend that has been dispatched but not yet confirmed, so the
     * player's HUD reacts immediately.
     *
     * <p>Every call must be paired with exactly one {@link #settlePending} once the outcome is
     * known, or the pending amount leaks and the displayed balance drifts permanently.
     */
    public void addPending(UUID playerUuid, long deltaMinorUnits) {
        if (playerUuid == null || deltaMinorUnits == 0L) {
            return;
        }
        entries.compute(playerUuid, (key, existing) -> existing == null
            ? new Entry(null, 0L, 0L, 0L,
                com.micatechnologies.minecraft.sum.omceapi.OmceProtocol.ACCOUNT_ACTIVE,
                deltaMinorUnits, clock.nowMillis())
            : new Entry(existing.accountId, existing.authoritativeBalance, existing.available,
                existing.version, existing.status, existing.pendingDelta + deltaMinorUnits,
                existing.updatedAtMillis));
    }

    /**
     * Clears an optimistic delta once its transaction has been confirmed or rejected.
     *
     * <p>Subtracts exactly the amount that was added rather than resetting to zero, so a second
     * spend dispatched while the first was in flight keeps its own pending amount.
     *
     * @param deltaMinorUnits the same value passed to {@link #addPending}.
     */
    public void settlePending(UUID playerUuid, long deltaMinorUnits) {
        if (playerUuid == null || deltaMinorUnits == 0L) {
            return;
        }
        entries.computeIfPresent(playerUuid, (key, existing) ->
            new Entry(existing.accountId, existing.authoritativeBalance, existing.available,
                existing.version, existing.status, existing.pendingDelta - deltaMinorUnits,
                existing.updatedAtMillis));
    }

    /** Drops a player's entry, e.g. on logout. */
    public void remove(UUID playerUuid) {
        if (playerUuid != null) {
            entries.remove(playerUuid);
        }
    }

    /** Drops everything, e.g. when the event cursor expires and the cache can no longer be trusted. */
    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    /** @return players whose entry is older than {@code ttlMillis}, for the refresh sweep. */
    public List<UUID> findStale(Collection<UUID> candidates, long ttlMillis) {
        long now = clock.nowMillis();
        List<UUID> stale = new ArrayList<>();
        for (UUID uuid : candidates) {
            Entry entry = entries.get(uuid);
            if (entry == null || entry.isStale(now, ttlMillis)) {
                stale.add(uuid);
            }
        }
        return stale;
    }

    /** @return an immutable snapshot, for diagnostics. */
    public Map<UUID, Entry> snapshot() {
        return java.util.Collections.unmodifiableMap(new java.util.HashMap<>(entries));
    }
}
