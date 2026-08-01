package com.micatechnologies.minecraft.sum.omceapi;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * One entry from {@code /resolveAccounts}: the mapping from a Minecraft identity to the service's
 * own opaque account id, or an explanation of why there isn't one.
 *
 * <p>An unresolved player is <b>not</b> an error — {@code /resolveAccounts} returns 200 with
 * {@code resolved: false} for that entry, because a service may legitimately require players to
 * link an account first.
 */
public final class OmceAccount {

    private final UUID playerUuid;
    private final boolean resolved;
    @Nullable private final String accountId;
    @Nullable private final String displayName;
    private final String status;
    @Nullable private final String reason;
    @Nullable private final String linkInstructions;

    public OmceAccount(UUID playerUuid, boolean resolved, @Nullable String accountId,
        @Nullable String displayName, @Nullable String status, @Nullable String reason,
        @Nullable String linkInstructions) {
        this.playerUuid = playerUuid;
        this.resolved = resolved;
        this.accountId = accountId;
        this.displayName = displayName;
        this.status = (status == null || status.isEmpty()) ? OmceProtocol.ACCOUNT_ACTIVE : status;
        this.reason = reason;
        this.linkInstructions = linkInstructions;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public boolean isResolved() {
        return resolved;
    }

    @Nullable
    public String getAccountId() {
        return accountId;
    }

    @Nullable
    public String getDisplayName() {
        return displayName;
    }

    public String getStatus() {
        return status;
    }

    public boolean isActive() {
        return OmceProtocol.ACCOUNT_ACTIVE.equals(status);
    }

    /** Error code explaining an unresolved entry, e.g. {@code ACCOUNT_NOT_LINKED}. */
    @Nullable
    public String getReason() {
        return reason;
    }

    /** Service-supplied instruction for linking, relayed verbatim into chat. */
    @Nullable
    public String getLinkInstructions() {
        return linkInstructions;
    }

    /** True when this player can transact right now. */
    public boolean isUsable() {
        return resolved && accountId != null && !accountId.isEmpty() && isActive();
    }

    @Override
    public String toString() {
        return "OmceAccount{" + playerUuid + " -> " + (resolved ? accountId : reason) + "}";
    }
}
