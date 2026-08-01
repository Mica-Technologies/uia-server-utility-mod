package com.micatechnologies.minecraft.sum.omceapi.client;

import com.micatechnologies.minecraft.sum.omceapi.OmceProtocol;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Supplies the environment and integrity headers described in spec section 4.3.
 *
 * <p>An interface rather than a concrete class so the client package stays free of Minecraft
 * imports: the service layer provides a live implementation backed by {@code MinecraftServer} and
 * the world save, while tests supply a fixed one.
 *
 * <p>Everything here is self-reported and so is not a security boundary — with the single
 * exception of {@link #getWorldSequence()}, which has value because the service validates it
 * against its own ledger to spot a world rollback.
 */
public interface OmceEnvironment {

    /** {@link OmceProtocol#ENVIRONMENT_DEDICATED} or {@link OmceProtocol#ENVIRONMENT_INTEGRATED}.
     *  Derived from the actual runtime, never from configuration. */
    String getEnvironmentType();

    /** Whether this Minecraft server authenticates players against Mojang. */
    boolean isOnlineMode();

    /** Stable identifier for the world save, persisted in it. Null if not yet available. */
    @Nullable
    UUID getWorldId();

    /** Monotonic counter persisted in the world save, incremented per transaction. Negative to
     *  omit the header. */
    long getWorldSequence();

    /** Identifier regenerated on every server boot, distinguishing a restart from a rollback. */
    UUID getSessionId();

    /** Players currently online. */
    int getPlayerCount();

    /** Mod version acting as client, for the {@code X-MCE-Mod-Version} header. */
    String getModVersion();

    /**
     * Risk-relevant state of the player initiating a request, as a comma-separated list drawn from
     * {@code creative}, {@code spectator}, {@code op}, {@code cheats}, {@code singleplayer}.
     *
     * @param initiator the acting player, or null for an automated transaction.
     * @return the flag list, or an empty string when nothing applies.
     */
    String getInitiatorState(@Nullable UUID initiator);

    /**
     * Builds the header map for one request.
     *
     * @param includeIntegrity false when the operator disabled {@code sendIntegrityHeaders}.
     *     {@code X-MCE-Environment} is sent regardless — the spec requires it unconditionally so
     *     a service can always apply environment policy.
     * @param initiator the acting player, for {@code X-MCE-Initiator-State}; may be null.
     */
    default Map<String, String> buildHeaders(boolean includeIntegrity, @Nullable UUID initiator) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(OmceProtocol.HEADER_ENVIRONMENT, getEnvironmentType());
        if (!includeIntegrity) {
            return headers;
        }
        headers.put(OmceProtocol.HEADER_ONLINE_MODE, Boolean.toString(isOnlineMode()));
        UUID worldId = getWorldId();
        if (worldId != null) {
            headers.put(OmceProtocol.HEADER_WORLD_ID, worldId.toString());
        }
        long seq = getWorldSequence();
        if (seq >= 0L) {
            headers.put(OmceProtocol.HEADER_WORLD_SEQ, Long.toString(seq));
        }
        headers.put(OmceProtocol.HEADER_SESSION_ID, getSessionId().toString());
        headers.put(OmceProtocol.HEADER_PLAYER_COUNT, Integer.toString(getPlayerCount()));
        String state = getInitiatorState(initiator);
        if (state != null && !state.isEmpty()) {
            headers.put(OmceProtocol.HEADER_INITIATOR_STATE, state);
        }
        headers.put(OmceProtocol.HEADER_MOD_VERSION, getModVersion());
        return headers;
    }
}
