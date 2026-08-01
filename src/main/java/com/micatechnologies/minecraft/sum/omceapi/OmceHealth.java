package com.micatechnologies.minecraft.sum.omceapi;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The negotiated view of a service, from {@code GET /health}. Every degrade-and-fallback decision
 * SUM makes is driven by this object, so it is fetched at startup, refreshed on a timer, and
 * re-fetched after any sustained failure.
 */
public final class OmceHealth {

    private final String status;
    private final String implementationName;
    private final Currency currency;
    private final Capabilities capabilities;
    private final Set<String> settledPartyTypes;
    private final Limits limits;
    private final List<String> requiredHeaders;

    public OmceHealth(String status, String implementationName, Currency currency,
        Capabilities capabilities, Set<String> settledPartyTypes, Limits limits,
        List<String> requiredHeaders) {
        this.status = (status == null || status.isEmpty()) ? OmceProtocol.STATUS_OK : status;
        this.implementationName = implementationName == null ? "unknown" : implementationName;
        this.currency = currency;
        this.capabilities = capabilities;
        this.settledPartyTypes = settledPartyTypes == null
            ? Collections.singleton(OmceParty.TYPE_PLAYER)
            : settledPartyTypes;
        this.limits = limits;
        this.requiredHeaders = requiredHeaders == null ? Collections.emptyList() : requiredHeaders;
    }

    public String getStatus() {
        return status;
    }

    public String getImplementationName() {
        return implementationName;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Capabilities getCapabilities() {
        return capabilities;
    }

    public Set<String> getSettledPartyTypes() {
        return settledPartyTypes;
    }

    public Limits getLimits() {
        return limits;
    }

    /** Headers the service rejects requests without. Empty for most services. */
    public List<String> getRequiredHeaders() {
        return requiredHeaders;
    }

    /** True when the service will accept writes. False during declared maintenance. */
    public boolean isWritable() {
        return OmceProtocol.STATUS_OK.equals(status) || OmceProtocol.STATUS_DEGRADED.equals(status);
    }

    public boolean isReadOnly() {
        return OmceProtocol.STATUS_READ_ONLY.equals(status);
    }

    /** Currency code, symbol, and scale. */
    public static final class Currency {

        private final String code;
        private final String symbol;
        private final int minorUnitDigits;

        public Currency(String code, String symbol, int minorUnitDigits) {
            this.code = (code == null || code.isEmpty()) ? "SUM" : code;
            this.symbol = symbol == null ? "$" : symbol;
            this.minorUnitDigits = OmceMoney.clampDigits(minorUnitDigits);
        }

        public String getCode() {
            return code;
        }

        public String getSymbol() {
            return symbol;
        }

        public int getMinorUnitDigits() {
            return minorUnitDigits;
        }
    }

    /** Optional-feature flags. An absent flag means false, so every getter defaults to off. */
    public static final class Capabilities {

        private final Set<String> enabled;

        public Capabilities(Set<String> enabled) {
            this.enabled = enabled == null ? Collections.emptySet() : enabled;
        }

        public boolean has(String flag) {
            return enabled.contains(flag);
        }

        public boolean canVoid() {
            return has("void");
        }

        public boolean canSetBalance() {
            return has("setBalance");
        }

        public boolean hasHolds() {
            return has("holds");
        }

        public boolean hasLedger() {
            return has("ledger");
        }

        public boolean hasEvents() {
            return has("events");
        }

        public boolean hasLeaderboard() {
            return has("leaderboard");
        }

        public boolean hasFees() {
            return has("fees");
        }

        public boolean hasPreflight() {
            return has("preflight");
        }

        public boolean hasHeaderPolicy() {
            return has("headerPolicy");
        }

        public boolean allowsNegativeBalances() {
            return has("negativeBalances");
        }

        public boolean autoProvisionsAccounts() {
            return has("autoProvisionAccounts");
        }

        public boolean isStrictTransactionTypes() {
            return has("strictTransactionTypes");
        }

        public boolean supportsOfflinePlayers() {
            return has("offlinePlayers");
        }

        public Set<String> all() {
            return enabled;
        }
    }

    /** Service-declared operational limits. */
    public static final class Limits {

        /** Spec floor: a service must accept batches of at least 50. */
        private static final int MIN_BATCH = 50;

        private final int maxBatchAccounts;
        private final long maxTransactionAmount;
        private final int requestsPerMinute;
        private final int idempotencyRetentionHours;

        public Limits(int maxBatchAccounts, long maxTransactionAmount, int requestsPerMinute,
            int idempotencyRetentionHours) {
            this.maxBatchAccounts = Math.max(MIN_BATCH, maxBatchAccounts);
            this.maxTransactionAmount = maxTransactionAmount > 0L ? maxTransactionAmount : Long.MAX_VALUE;
            this.requestsPerMinute = requestsPerMinute;
            this.idempotencyRetentionHours = idempotencyRetentionHours;
        }

        public int getMaxBatchAccounts() {
            return maxBatchAccounts;
        }

        public long getMaxTransactionAmount() {
            return maxTransactionAmount;
        }

        public int getRequestsPerMinute() {
            return requestsPerMinute;
        }

        public int getIdempotencyRetentionHours() {
            return idempotencyRetentionHours;
        }
    }
}
