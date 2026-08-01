package com.micatechnologies.minecraft.sum.omceapi;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * An authoritative balance snapshot for one account, as returned by {@code /getBalances},
 * {@code /processTransaction}, and the {@code /getEvents} feed.
 *
 * <p>{@link #getVersion()} is the field that makes SUM's asynchronous cache correct: the service
 * guarantees it strictly increases per account across every balance change, so a response that
 * arrives out of order can be recognised as stale and discarded rather than rolling a player's
 * displayed balance backwards. {@code OmceBalanceCache} in the service layer enforces this.
 */
public final class OmceBalance {

    private final String accountId;
    @Nullable private final UUID playerUuid;
    private final long balance;
    private final long available;
    private final long held;
    private final long version;
    private final String status;

    public OmceBalance(String accountId, @Nullable UUID playerUuid, long balance, long available,
        long held, long version, String status) {
        this.accountId = accountId == null ? "" : accountId;
        this.playerUuid = playerUuid;
        this.balance = balance;
        this.available = available;
        this.held = held;
        this.version = version;
        this.status = (status == null || status.isEmpty()) ? OmceProtocol.ACCOUNT_ACTIVE : status;
    }

    public String getAccountId() {
        return accountId;
    }

    @Nullable
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    /** Total balance in minor units. */
    public long getBalance() {
        return balance;
    }

    /** Spendable balance in minor units — {@code balance - held}. Equals {@link #getBalance()}
     *  on services without hold support. */
    public long getAvailable() {
        return available;
    }

    public long getHeld() {
        return held;
    }

    /** Monotonic per-account revision. Higher always means newer. */
    public long getVersion() {
        return version;
    }

    public String getStatus() {
        return status;
    }

    /** False when the account is frozen or closed, in which case SUM refuses to spend from it. */
    public boolean isActive() {
        return OmceProtocol.ACCOUNT_ACTIVE.equals(status);
    }

    @Override
    public String toString() {
        return "OmceBalance{" + accountId + " balance=" + balance + " v=" + version + "}";
    }
}
