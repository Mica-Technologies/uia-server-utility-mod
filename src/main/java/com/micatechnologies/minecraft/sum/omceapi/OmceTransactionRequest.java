package com.micatechnologies.minecraft.sum.omceapi;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * A transaction SUM is asking the service to apply. Built through {@link Builder}, then serialised
 * by {@link #toJson}.
 *
 * <p>The {@link #getIdempotencyKey() idempotency key} is generated once per <i>logical</i>
 * operation and reused across every retry of it, which is what makes a timed-out purchase safe to
 * repeat. Callers must not mint a fresh request object per attempt.
 */
public final class OmceTransactionRequest {

    /** Spec limits (section 6.5) — enforced here so a service never has to reject us for them. */
    private static final int MAX_REASON_LENGTH = 256;
    private static final int MAX_METADATA_ENTRIES = 16;
    private static final int MAX_METADATA_KEY_LENGTH = 64;
    private static final int MAX_METADATA_VALUE_LENGTH = 256;

    public static final String ROLE_PLAYER = "player";
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_SYSTEM = "system";

    private final String idempotencyKey;
    private final String type;
    private final long amount;
    private final OmceParty source;
    private final OmceParty destination;
    private final long feeAmount;
    @Nullable private final OmceParty feeDestination;
    @Nullable private final UUID initiatorUuid;
    @Nullable private final String initiatorName;
    private final String initiatorRole;
    @Nullable private final String reason;
    private final Map<String, String> metadata;
    private final boolean allowNegative;

    private OmceTransactionRequest(Builder b) {
        this.idempotencyKey = b.idempotencyKey;
        this.type = b.type;
        this.amount = b.amount;
        this.source = b.source;
        this.destination = b.destination;
        this.feeAmount = b.feeAmount;
        this.feeDestination = b.feeDestination;
        this.initiatorUuid = b.initiatorUuid;
        this.initiatorName = b.initiatorName;
        this.initiatorRole = b.initiatorRole;
        this.reason = b.reason;
        this.metadata = b.metadata;
        this.allowNegative = b.allowNegative;
    }

    public static Builder builder(String type, long amount, OmceParty source, OmceParty destination) {
        return new Builder(type, amount, source, destination);
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }

    public OmceParty getSource() {
        return source;
    }

    public OmceParty getDestination() {
        return destination;
    }

    public boolean isAdminInitiated() {
        return ROLE_ADMIN.equals(initiatorRole);
    }

    /**
     * Returns a copy with resolved account ids attached to whichever parties are players. Called
     * just before dispatch so a freshly resolved mapping is included without rebuilding the
     * request — and, critically, <b>without changing the idempotency key</b>, so this is safe to
     * apply on a retry.
     */
    public OmceTransactionRequest withResolvedAccounts(@Nullable String sourceAccountId,
        @Nullable String destinationAccountId) {
        Builder b = copyToBuilder();
        b.source = source.withAccountId(sourceAccountId);
        b.destination = destination.withAccountId(destinationAccountId);
        return new OmceTransactionRequest(b);
    }

    private Builder copyToBuilder() {
        Builder b = new Builder(type, amount, source, destination);
        b.idempotencyKey = idempotencyKey;
        b.feeAmount = feeAmount;
        b.feeDestination = feeDestination;
        b.initiatorUuid = initiatorUuid;
        b.initiatorName = initiatorName;
        b.initiatorRole = initiatorRole;
        b.reason = reason;
        b.metadata.putAll(metadata);
        b.allowNegative = allowNegative;
        return b;
    }

    /**
     * @param currencyCode the service's declared currency code.
     * @param instanceId this server's instance identifier; must match the X-MCE-Instance header.
     * @param occurredAt RFC 3339 timestamp of the in-game action, or null to omit.
     */
    public JsonObject toJson(String currencyCode, String instanceId, @Nullable String occurredAt) {
        JsonObject o = new JsonObject();
        o.addProperty("idempotencyKey", idempotencyKey);
        o.addProperty("type", type);
        o.addProperty("amount", amount);
        o.addProperty("currency", currencyCode);
        o.add("source", source.toJson());
        o.add("destination", destination.toJson());
        if (feeAmount > 0L) {
            JsonObject fee = new JsonObject();
            fee.addProperty("amount", feeAmount);
            if (feeDestination != null) {
                fee.add("destination", feeDestination.toJson());
            }
            o.add("fee", fee);
        }
        if (initiatorUuid != null || initiatorName != null) {
            JsonObject initiator = new JsonObject();
            if (initiatorUuid != null) {
                initiator.addProperty("playerUuid", initiatorUuid.toString().toLowerCase(Locale.ROOT));
            }
            if (initiatorName != null) {
                initiator.addProperty("playerName", initiatorName);
            }
            initiator.addProperty("role", initiatorRole);
            o.add("initiator", initiator);
        }
        if (reason != null && !reason.isEmpty()) {
            o.addProperty("reason", reason);
        }
        if (!metadata.isEmpty()) {
            JsonObject meta = new JsonObject();
            for (Map.Entry<String, String> e : metadata.entrySet()) {
                meta.addProperty(e.getKey(), e.getValue());
            }
            o.add("metadata", meta);
        }
        if (occurredAt != null) {
            o.addProperty("occurredAt", occurredAt);
        }
        o.addProperty("instance", instanceId);
        if (allowNegative) {
            o.addProperty("allowNegative", true);
        }
        return o;
    }

    @Override
    public String toString() {
        return "OmceTransactionRequest{" + type + " " + amount + " " + source + "->" + destination
            + " key=" + idempotencyKey + "}";
    }

    /** Fluent builder. {@code type}, {@code amount}, {@code source}, and {@code destination} are
     *  required; everything else is optional. */
    public static final class Builder {

        private String idempotencyKey = UUID.randomUUID().toString();
        private final String type;
        private final long amount;
        private OmceParty source;
        private OmceParty destination;
        private long feeAmount;
        @Nullable private OmceParty feeDestination;
        @Nullable private UUID initiatorUuid;
        @Nullable private String initiatorName;
        private String initiatorRole = ROLE_SYSTEM;
        @Nullable private String reason;
        private final Map<String, String> metadata = new LinkedHashMap<>();
        private boolean allowNegative;

        private Builder(String type, long amount, OmceParty source, OmceParty destination) {
            if (type == null || type.isEmpty()) {
                throw new IllegalArgumentException("Transaction type is required.");
            }
            if (amount < 0L) {
                throw new IllegalArgumentException("Amount must be non-negative; direction comes "
                    + "from source/destination, not the sign. Got " + amount);
            }
            if (source == null || destination == null) {
                throw new IllegalArgumentException("Both source and destination are required.");
            }
            if (!source.isPlayer() && !destination.isPlayer()) {
                // The service would reject this with NO_SETTLED_PARTY; failing here gives a stack
                // trace pointing at the actual mistake instead of a network round trip.
                throw new IllegalArgumentException("At least one party must be a player: "
                    + source + " -> " + destination);
            }
            this.type = type;
            this.amount = amount;
            this.source = source;
            this.destination = destination;
        }

        /** Overrides the generated key. Use when replaying a persisted operation after a restart. */
        public Builder idempotencyKey(String key) {
            if (key != null && !key.isEmpty()) {
                this.idempotencyKey = key;
            }
            return this;
        }

        /** Adds a fee deducted from what the destination receives. Requires the {@code fees}
         *  capability; callers must check it and split the transaction otherwise. */
        public Builder fee(long feeMinorUnits, @Nullable OmceParty destinationOfFee) {
            if (feeMinorUnits < 0L || feeMinorUnits > amount) {
                throw new IllegalArgumentException(
                    "Fee must satisfy 0 <= fee <= amount; got " + feeMinorUnits + " of " + amount);
            }
            this.feeAmount = feeMinorUnits;
            this.feeDestination = destinationOfFee;
            return this;
        }

        public Builder initiator(@Nullable UUID uuid, @Nullable String name, String role) {
            this.initiatorUuid = uuid;
            this.initiatorName = name;
            this.initiatorRole = (role == null || role.isEmpty()) ? ROLE_SYSTEM : role;
            return this;
        }

        public Builder reason(@Nullable String text) {
            this.reason = clamp(text, MAX_REASON_LENGTH);
            return this;
        }

        /** Adds a metadata entry. Silently ignored once the spec's 16-entry limit is reached —
         *  metadata is diagnostic, never worth failing a real transaction over. */
        public Builder meta(String key, @Nullable String value) {
            if (key == null || key.isEmpty() || value == null) {
                return this;
            }
            if (metadata.size() >= MAX_METADATA_ENTRIES && !metadata.containsKey(key)) {
                return this;
            }
            metadata.put(clamp(key, MAX_METADATA_KEY_LENGTH), clamp(value, MAX_METADATA_VALUE_LENGTH));
            return this;
        }

        /** Permits an overdraft. Honoured only by services declaring {@code negativeBalances},
         *  and only for an admin initiator. */
        public Builder allowNegative(boolean allow) {
            this.allowNegative = allow;
            return this;
        }

        public OmceTransactionRequest build() {
            return new OmceTransactionRequest(this);
        }

        private static String clamp(String v, int max) {
            if (v == null) {
                return null;
            }
            return v.length() <= max ? v : v.substring(0, max);
        }
    }
}
