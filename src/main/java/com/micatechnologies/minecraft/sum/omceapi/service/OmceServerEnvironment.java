package com.micatechnologies.minecraft.sum.omceapi.service;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.omceapi.OmceProtocol;
import com.micatechnologies.minecraft.sum.omceapi.client.OmceEnvironment;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * Live {@link OmceEnvironment} backed by the running {@link MinecraftServer}.
 *
 * <p>Everything reported here is derived from the actual runtime, never from configuration — the
 * spec requires {@code X-MCE-Environment} to be truthful even when an operator has deliberately
 * enabled the economy on an integrated server, so a service can still apply its own policy.
 */
public final class OmceServerEnvironment implements OmceEnvironment {

    private final MinecraftServer server;
    private final UUID sessionId = UUID.randomUUID();

    /** Cached so the header can be produced without touching world storage on every request. */
    @Nullable private final UUID worldId;
    private final AtomicLong sequence = new AtomicLong(-1L);
    @Nullable private final OmceWorldState worldState;

    public OmceServerEnvironment(MinecraftServer server, @Nullable OmceWorldState worldState) {
        this.server = server;
        this.worldState = worldState;
        this.worldId = worldState == null ? null : worldState.getWorldId();
        if (worldState != null) {
            this.sequence.set(worldState.getSequence());
        }
    }

    @Override
    public String getEnvironmentType() {
        return server.isDedicatedServer()
            ? OmceProtocol.ENVIRONMENT_DEDICATED
            : OmceProtocol.ENVIRONMENT_INTEGRATED;
    }

    @Override
    public boolean isOnlineMode() {
        // On an integrated server this reports the LAN setting, which is the honest answer:
        // a single-player world does not authenticate anyone.
        return server.isServerInOnlineMode();
    }

    @Nullable
    @Override
    public UUID getWorldId() {
        return worldId;
    }

    @Override
    public long getWorldSequence() {
        return sequence.get();
    }

    /**
     * Advances the persisted per-world counter. Called once per dispatched transaction, before the
     * request is built, so the value in the header is the one recorded in the save.
     */
    public long advanceSequence() {
        if (worldState == null) {
            return -1L;
        }
        long next = worldState.nextSequence();
        sequence.set(next);
        return next;
    }

    @Override
    public UUID getSessionId() {
        return sessionId;
    }

    @Override
    public int getPlayerCount() {
        return server.getPlayerList() == null ? 0 : server.getPlayerList().getCurrentPlayerCount();
    }

    @Override
    public String getModVersion() {
        return SumConstants.MOD_VERSION;
    }

    @Override
    public String getInitiatorState(@Nullable UUID initiator) {
        StringBuilder flags = new StringBuilder();
        if (!server.isDedicatedServer()) {
            append(flags, "singleplayer");
            // Only meaningful for an integrated server; a dedicated server has no cheats toggle.
            if (server.getWorld(0) != null && server.getWorld(0).getWorldInfo().areCommandsAllowed()) {
                append(flags, "cheats");
            }
        }
        if (initiator != null && server.getPlayerList() != null) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(initiator);
            if (player != null) {
                if (player.isCreative()) {
                    append(flags, "creative");
                }
                if (player.isSpectator()) {
                    append(flags, "spectator");
                }
                if (server.getPlayerList().canSendCommands(player.getGameProfile())) {
                    append(flags, "op");
                }
            }
        }
        return flags.toString();
    }

    private static void append(StringBuilder sb, String flag) {
        if (sb.length() > 0) {
            sb.append(',');
        }
        sb.append(flag);
    }
}
