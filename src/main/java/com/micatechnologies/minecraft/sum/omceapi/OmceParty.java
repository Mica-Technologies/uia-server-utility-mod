package com.micatechnologies.minecraft.sum.omceapi;

import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * One side of a transaction. A {@code player} party is <i>settled</i> — the service holds a real
 * balance for it. Every other type is <i>virtual</i>: the service records it as a counterparty for
 * audit but holds no balance, because SUM keeps that state in the world save (shop tills, job
 * escrow, physical bills).
 *
 * <p>Construct these through the static factories rather than the constructor, so the
 * type-specific required fields can't be missed.
 */
public final class OmceParty {

    public static final String TYPE_PLAYER = "player";
    public static final String TYPE_SHOP = "shop";
    public static final String TYPE_JOB = "job";
    public static final String TYPE_PLOT = "plot";
    public static final String TYPE_CASH = "cash";
    public static final String TYPE_SYSTEM = "system";
    public static final String TYPE_EXTERNAL = "external";

    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_PLAYER_NAME_LENGTH = 32;

    private final String type;
    @Nullable private final UUID playerUuid;
    @Nullable private final String playerName;
    @Nullable private final String accountId;
    @Nullable private final String id;
    @Nullable private final String name;
    @Nullable private final UUID ownerUuid;

    private OmceParty(String type, @Nullable UUID playerUuid, @Nullable String playerName,
        @Nullable String accountId, @Nullable String id, @Nullable String name,
        @Nullable UUID ownerUuid) {
        this.type = type;
        this.playerUuid = playerUuid;
        this.playerName = truncate(playerName, MAX_PLAYER_NAME_LENGTH);
        this.accountId = accountId;
        this.id = truncate(id, MAX_ID_LENGTH);
        this.name = truncate(name, MAX_NAME_LENGTH);
        this.ownerUuid = ownerUuid;
    }

    /** A settled player account. {@code accountId} may be null before it has been resolved. */
    public static OmceParty player(UUID playerUuid, @Nullable String playerName,
        @Nullable String accountId) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("A player party requires a UUID.");
        }
        return new OmceParty(TYPE_PLAYER, playerUuid, playerName, accountId, null, null, null);
    }

    /** A shop block. {@code id} should encode world and position so it is stable across restarts. */
    public static OmceParty shop(String id, @Nullable String name, @Nullable UUID ownerUuid) {
        return new OmceParty(TYPE_SHOP, null, null, null, requireId(id, TYPE_SHOP), name, ownerUuid);
    }

    /** A job listing holding escrowed reward. */
    public static OmceParty job(UUID listingId, @Nullable UUID posterUuid) {
        String id = "job:" + (listingId == null ? "unknown" : listingId.toString());
        return new OmceParty(TYPE_JOB, null, null, null, id, null, posterUuid);
    }

    /** A land plot being bought or refunded. */
    public static OmceParty plot(String plotId, @Nullable String name) {
        return new OmceParty(TYPE_PLOT, null, null, null, requireId(plotId, TYPE_PLOT), name, null);
    }

    /** Physical bill items in the world — the counterparty for ATM deposits and withdrawals. */
    public static OmceParty cash(String atmId) {
        return new OmceParty(TYPE_CASH, null, null, null, requireId(atmId, TYPE_CASH), null, null);
    }

    /** A money sink or faucet inside SUM, e.g. {@code sink.pay_fee} or {@code faucet.admin}. */
    public static OmceParty system(String sinkId) {
        return new OmceParty(TYPE_SYSTEM, null, null, null, requireId(sinkId, TYPE_SYSTEM), null, null);
    }

    /** Builds a world-and-position identifier of the form {@code shop:overworld:120:64:-33}. */
    public static String positionId(String kind, String worldName, int x, int y, int z) {
        String world = (worldName == null || worldName.isEmpty()) ? "world" : worldName;
        return kind + ":" + world.toLowerCase(Locale.ROOT) + ":" + x + ":" + y + ":" + z;
    }

    private static String requireId(String id, String type) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("A " + type + " party requires an id.");
        }
        return id;
    }

    @Nullable
    private static String truncate(@Nullable String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public String getType() {
        return type;
    }

    public boolean isPlayer() {
        return TYPE_PLAYER.equals(type);
    }

    @Nullable
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    @Nullable
    public String getAccountId() {
        return accountId;
    }

    /** Returns a copy carrying a resolved account id, so a cached mapping can be attached late. */
    public OmceParty withAccountId(@Nullable String resolvedAccountId) {
        if (resolvedAccountId == null || resolvedAccountId.equals(accountId)) {
            return this;
        }
        return new OmceParty(type, playerUuid, playerName, resolvedAccountId, id, name, ownerUuid);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        if (playerUuid != null) {
            // Canonical hyphenated lowercase, per spec section 6.3.
            o.addProperty("playerUuid", playerUuid.toString().toLowerCase(Locale.ROOT));
        }
        if (playerName != null && !playerName.isEmpty()) {
            o.addProperty("playerName", playerName);
        }
        if (accountId != null && !accountId.isEmpty()) {
            o.addProperty("accountId", accountId);
        }
        if (id != null && !id.isEmpty()) {
            o.addProperty("id", id);
        }
        if (name != null && !name.isEmpty()) {
            o.addProperty("name", name);
        }
        if (ownerUuid != null) {
            o.addProperty("ownerUuid", ownerUuid.toString().toLowerCase(Locale.ROOT));
        }
        return o;
    }

    @Override
    public String toString() {
        return type + "[" + (playerUuid != null ? playerUuid : String.valueOf(id)) + "]";
    }
}
