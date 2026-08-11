package com.micatechnologies.minecraft.sum.omceapi;

/**
 * Wire constants for the Open MCEconomic API. Everything in this class is dictated by
 * {@code docs/OPEN_MCECONOMIC_API_SPECIFICATION.md} — if a value here disagrees with that document, the document wins.
 *
 * <p>Deliberately free of Minecraft imports: the {@code omceapi} and {@code omceapi.client}
 * packages implement the protocol and nothing else, so they stay unit-testable without a running
 * game. Only {@code omceapi.service} touches Minecraft types.
 */
public final class OmceProtocol {

    private OmceProtocol() {}

    /** Protocol version this client speaks, sent as {@link #HEADER_PROTOCOL_VERSION}. */
    public static final String VERSION = "1.0";

    /** Path segment appended to the operator's configured base URL. */
    public static final String BASE_PATH = "/api/economic/v1";

    // ---------------------------------------------------------------------------------------
    // Headers
    // ---------------------------------------------------------------------------------------

    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_ACCEPT = "Accept";
    public static final String HEADER_USER_AGENT = "User-Agent";
    public static final String HEADER_PROTOCOL_VERSION = "X-MCE-Protocol-Version";
    public static final String HEADER_INSTANCE = "X-MCE-Instance";
    public static final String HEADER_ENVIRONMENT = "X-MCE-Environment";
    public static final String HEADER_REQUEST_ID = "X-MCE-Request-Id";
    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String HEADER_TIMESTAMP = "X-MCE-Timestamp";
    public static final String HEADER_SIGNATURE = "X-MCE-Signature";
    public static final String HEADER_RETRY_AFTER = "Retry-After";
    public static final String HEADER_DEPRECATION = "Deprecation";
    public static final String HEADER_SUNSET = "Sunset";

    // Optional integrity headers (spec section 4.3). Sent only when the operator leaves
    // economy_api.sendIntegrityHeaders enabled.
    public static final String HEADER_ONLINE_MODE = "X-MCE-Online-Mode";
    public static final String HEADER_WORLD_ID = "X-MCE-World-Id";
    public static final String HEADER_WORLD_SEQ = "X-MCE-World-Seq";
    public static final String HEADER_SESSION_ID = "X-MCE-Session-Id";
    public static final String HEADER_PLAYER_COUNT = "X-MCE-Player-Count";
    public static final String HEADER_INITIATOR_STATE = "X-MCE-Initiator-State";
    public static final String HEADER_MOD_VERSION = "X-MCE-Mod-Version";

    public static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";

    /** {@link #HEADER_ENVIRONMENT} value for a standalone Minecraft server process. */
    public static final String ENVIRONMENT_DEDICATED = "dedicated";

    /** {@link #HEADER_ENVIRONMENT} value for a server running inside a game client. */
    public static final String ENVIRONMENT_INTEGRATED = "integrated";

    // ---------------------------------------------------------------------------------------
    // Endpoints
    // ---------------------------------------------------------------------------------------

    public static final String EP_HEALTH = "/health";
    public static final String EP_REQUIRED_HEADERS = "/getRequiredHeaders";
    public static final String EP_RESOLVE_ACCOUNTS = "/resolveAccounts";
    public static final String EP_GET_BALANCE = "/getBalance";
    public static final String EP_GET_BALANCES = "/getBalances";
    public static final String EP_PROCESS_TRANSACTION = "/processTransaction";
    public static final String EP_VALIDATE_TRANSACTION = "/validateTransaction";
    public static final String EP_GET_TRANSACTION = "/getTransaction";
    public static final String EP_VOID_TRANSACTION = "/voidTransaction";
    public static final String EP_SET_BALANCE = "/setBalance";
    public static final String EP_AUTHORIZE_HOLD = "/authorizeHold";
    public static final String EP_CAPTURE_HOLD = "/captureHold";
    public static final String EP_RELEASE_HOLD = "/releaseHold";
    public static final String EP_GET_LEDGER = "/getLedger";
    public static final String EP_GET_EVENTS = "/getEvents";
    public static final String EP_GET_LEADERBOARD = "/getLeaderboard";

    // ---------------------------------------------------------------------------------------
    // Transaction types (spec section 6.5). Open vocabulary — a service must tolerate unknown
    // values, so these are constants rather than an enum.
    // ---------------------------------------------------------------------------------------

    public static final String TX_PLAYER_TRANSFER = "player_transfer";
    public static final String TX_ATM_WITHDRAW = "atm_withdraw";
    public static final String TX_ATM_DEPOSIT = "atm_deposit";
    public static final String TX_SHOP_PURCHASE = "shop_purchase";
    public static final String TX_SHOP_PAYOUT = "shop_payout";
    public static final String TX_JOB_POST_ESCROW = "job_post_escrow";
    public static final String TX_JOB_PAYOUT = "job_payout";
    public static final String TX_JOB_REFUND = "job_refund";
    public static final String TX_PLOT_PURCHASE = "plot_purchase";
    public static final String TX_PLOT_REFUND = "plot_refund";
    public static final String TX_LOYALTY_REWARD = "loyalty_reward";
    public static final String TX_ADMIN_CREDIT = "admin_credit";
    public static final String TX_ADMIN_DEBIT = "admin_debit";
    public static final String TX_ADMIN_MIGRATION = "admin_migration";

    // Bank moves made by another mod through SUM's public economy API. Distinct from the atm_*
    // types so a ledger can tell "the player went to an ATM" from "an integration moved this on
    // their behalf" — the metadata key `sum.source_mod` names which mod. A service running in
    // strictTransactionTypes mode must be told about these before enabling integrations.
    public static final String TX_MOD_DEPOSIT = "mod_deposit";
    public static final String TX_MOD_WITHDRAW = "mod_withdraw";

    // ---------------------------------------------------------------------------------------
    // Error codes (spec section 6.7)
    // ---------------------------------------------------------------------------------------

    public static final String ERR_UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String ERR_SIGNATURE_INVALID = "SIGNATURE_INVALID";
    public static final String ERR_TIMESTAMP_OUT_OF_RANGE = "TIMESTAMP_OUT_OF_RANGE";
    public static final String ERR_FORBIDDEN = "FORBIDDEN";
    public static final String ERR_ENVIRONMENT_REJECTED = "ENVIRONMENT_REJECTED";
    public static final String ERR_PROTOCOL_VERSION_UNSUPPORTED = "PROTOCOL_VERSION_UNSUPPORTED";
    public static final String ERR_MALFORMED_REQUEST = "MALFORMED_REQUEST";
    public static final String ERR_MISSING_REQUIRED_HEADER = "MISSING_REQUIRED_HEADER";
    public static final String ERR_AMOUNT_INVALID = "AMOUNT_INVALID";
    public static final String ERR_CURRENCY_MISMATCH = "CURRENCY_MISMATCH";
    public static final String ERR_NO_SETTLED_PARTY = "NO_SETTLED_PARTY";
    public static final String ERR_UNSUPPORTED_PARTY_TYPE = "UNSUPPORTED_PARTY_TYPE";
    public static final String ERR_UNSUPPORTED_TRANSACTION_TYPE = "UNSUPPORTED_TRANSACTION_TYPE";
    public static final String ERR_UNKNOWN_ACCOUNT = "UNKNOWN_ACCOUNT";
    public static final String ERR_ACCOUNT_NOT_LINKED = "ACCOUNT_NOT_LINKED";
    public static final String ERR_ACCOUNT_FROZEN = "ACCOUNT_FROZEN";
    public static final String ERR_INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";
    public static final String ERR_LIMIT_EXCEEDED = "LIMIT_EXCEEDED";
    public static final String ERR_IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";
    public static final String ERR_IDEMPOTENCY_IN_PROGRESS = "IDEMPOTENCY_IN_PROGRESS";
    public static final String ERR_TRANSACTION_NOT_FOUND = "TRANSACTION_NOT_FOUND";
    public static final String ERR_NOT_VOIDABLE = "NOT_VOIDABLE";
    public static final String ERR_ALREADY_VOIDED = "ALREADY_VOIDED";
    public static final String ERR_HOLD_EXPIRED = "HOLD_EXPIRED";
    public static final String ERR_HOLD_ALREADY_SETTLED = "HOLD_ALREADY_SETTLED";
    public static final String ERR_VERSION_CONFLICT = "VERSION_CONFLICT";
    public static final String ERR_CURSOR_EXPIRED = "CURSOR_EXPIRED";
    public static final String ERR_RATE_LIMITED = "RATE_LIMITED";
    public static final String ERR_ECONOMY_READ_ONLY = "ECONOMY_READ_ONLY";
    public static final String ERR_SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    public static final String ERR_INTERNAL_ERROR = "INTERNAL_ERROR";

    /** Locally generated code for a transport failure that never reached the service. */
    public static final String ERR_TRANSPORT = "TRANSPORT_FAILURE";

    /** Locally generated code for a response the client could not parse. */
    public static final String ERR_BAD_RESPONSE = "BAD_RESPONSE";

    /** Locally generated code for "the economy backend is not connected". */
    public static final String ERR_NOT_CONNECTED = "NOT_CONNECTED";

    // ---------------------------------------------------------------------------------------
    // Health status values
    // ---------------------------------------------------------------------------------------

    public static final String STATUS_OK = "ok";
    public static final String STATUS_DEGRADED = "degraded";
    public static final String STATUS_READ_ONLY = "read_only";

    /** Account status values returned by {@code /resolveAccounts} and balance reads. */
    public static final String ACCOUNT_ACTIVE = "active";

    /**
     * Maps an error code to whether an identical retry could plausibly succeed. Used when a
     * service omits the {@code retryable} field, which it should not do but might.
     */
    public static boolean isRetryableByDefault(String code) {
        if (code == null) {
            return false;
        }
        switch (code) {
            case ERR_IDEMPOTENCY_IN_PROGRESS:
            case ERR_RATE_LIMITED:
            case ERR_ECONOMY_READ_ONLY:
            case ERR_SERVICE_UNAVAILABLE:
            case ERR_INTERNAL_ERROR:
            case ERR_TRANSPORT:
                return true;
            default:
                return false;
        }
    }
}
