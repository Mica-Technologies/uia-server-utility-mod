package com.micatechnologies.minecraft.sum;

import com.micatechnologies.minecraft.sum.api.EconomyScope;
import com.micatechnologies.minecraft.sum.border.BorderEntry;
import com.micatechnologies.minecraft.sum.loyalty.LoyaltyMilestone;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraftforge.common.config.Configuration;

public class SumConfig {

    private static final String CATEGORY_ROAMER = "roamer";

    private static final String FIELD_KEY_ROAMER_WALKABLE_BLOCKS = "walkableBlocks";
    private static final String FIELD_DESCRIPTION_ROAMER_WALKABLE_BLOCKS =
        "List of block registry names (e.g. minecraft:grass, minecraft:stone) that roamer NPCs are allowed to walk on.";
    private static final String[] FIELD_DEFAULT_ROAMER_WALKABLE_BLOCKS = {
        "minecraft:grass",
        "minecraft:stone",
        "minecraft:cobblestone",
        "minecraft:planks",
        "minecraft:brick_block",
        "minecraft:sand",
        "minecraft:gravel",
        "minecraft:dirt",
        "minecraft:sandstone",
        "minecraft:stonebrick",
        "minecraft:concrete"
    };

    private static final String CATEGORY_FAVORITES = "favorites";

    private static final String FIELD_KEY_FAVORITES_STAR_OVERLAY = "enableStarOverlay";
    private static final String FIELD_DESCRIPTION_FAVORITES_STAR_OVERLAY =
        "Whether to render a small gold star in the upper-right of every creative-inventory slot "
            + "whose item is in your favorites list. Set to false to disable the overlay without "
            + "removing favorites themselves.";
    private static final boolean FIELD_DEFAULT_FAVORITES_STAR_OVERLAY = true;

    private static final String CATEGORY_ROADRUNNER = "roadrunner";

    private static final String FIELD_KEY_ROADRUNNER_SPEED_BLOCKS = "speedBlocks";
    private static final String FIELD_DESCRIPTION_ROADRUNNER_SPEED_BLOCKS =
        "List of block-to-speed-multiplier mappings. Each entry is in the format 'blockRegistryName=multiplier', "
            + "where multiplier is a decimal number representing the speed boost factor (e.g. 1.5 = 50% faster). "
            + "Both vanilla and modded blocks are supported. There is no limit on the number of entries. "
            + "Example: minecraft:concrete=1.8";
    private static final String[] FIELD_DEFAULT_ROADRUNNER_SPEED_BLOCKS = {
        "minecraft:concrete=1.25"
    };

    private static final String CATEGORY_BORDER = "border";

    private static final String FIELD_KEY_BORDER_ENABLED = "enabled";
    private static final String FIELD_DESCRIPTION_BORDER_ENABLED =
        "When true, players crossing the configured per-dimension borders are bounced back inside or "
            + "wrapped to the opposite axis depending on the entry's mode.";
    private static final boolean FIELD_DEFAULT_BORDER_ENABLED = true;

    private static final String FIELD_KEY_BORDER_BORDERS = "borders";
    private static final String FIELD_DESCRIPTION_BORDER_BORDERS =
        "Per-dimension borders. Each entry is '<dimId>=<radius>:<mode>'. radius is a square half-width "
            + "in blocks (radius 10000 = 20000-block-wide square centered on origin). mode is 'bounce' "
            + "(player gets pushed back inside) or 'loop' (Pac-Man-style wrap to opposite side). "
            + "Empty list disables all borders.";
    private static final String[] FIELD_DEFAULT_BORDER_BORDERS = {
        "0=10000:bounce"
    };

    private static final String CATEGORY_AUTO_DROPPER = "autodropper";

    private static final String FIELD_KEY_AUTO_DROPPER_ENABLED = "enabled";
    private static final String FIELD_DESCRIPTION_AUTO_DROPPER_ENABLED =
        "Whether placed Auto Dropper blocks tick automatically. Set false to disable the feature globally "
            + "without removing existing blocks.";
    private static final boolean FIELD_DEFAULT_AUTO_DROPPER_ENABLED = true;

    private static final String FIELD_KEY_AUTO_DROPPER_INTERVAL = "tickInterval";
    private static final String FIELD_DESCRIPTION_AUTO_DROPPER_INTERVAL =
        "Number of server ticks between auto-dispense attempts. 20 ticks = 1 second. Lower values "
            + "dispense more aggressively at higher CPU cost.";
    private static final int FIELD_DEFAULT_AUTO_DROPPER_INTERVAL = 8;

    private static final String CATEGORY_LOYALTY = "loyalty";

    private static final String FIELD_KEY_LOYALTY_ENABLED = "enabled";
    private static final String FIELD_DESCRIPTION_LOYALTY_ENABLED =
        "When true, players receive configured rewards after crossing playtime milestones. "
            + "Milestones fire once per player; tracking is persisted in the player's NBT data.";
    private static final boolean FIELD_DEFAULT_LOYALTY_ENABLED = true;

    private static final String FIELD_KEY_LOYALTY_MILESTONES = "milestones";
    private static final String FIELD_DESCRIPTION_LOYALTY_MILESTONES =
        "Lifetime / cumulative-playtime milestones — fire ONCE per player, ever. The fired list "
            + "persists in player NBT, so a milestone configured later than a player's accumulated "
            + "playtime fires immediately on next login (e.g. adding a 1-min milestone for a player "
            + "who already played 5 hours fires it on next tick). Use 'sessionMilestones' for "
            + "per-session rewards. Format: '<minutes>=<type>:<value>'. Reward types: "
            + "'money:<amount>' deposits via EconomyBridge; 'command:<command>' runs as console "
            + "with {player} replaced by the player's name. Examples: 30=money:10, "
            + "60=command:give {player} minecraft:diamond 1.";
    private static final String[] FIELD_DEFAULT_LOYALTY_MILESTONES = {
        "30=money:10",
        "120=money:50"
    };

    private static final String FIELD_KEY_LOYALTY_SESSION_MILESTONES = "sessionMilestones";
    private static final String FIELD_DESCRIPTION_LOYALTY_SESSION_MILESTONES =
        "Per-session playtime milestones — fire ONCE per session per player. Session resets each "
            + "time the player logs in; milestones fire when the player has been online "
            + "continuously for the configured minutes. Same format as 'milestones'. Use this for "
            + "rewards that should recur each session (e.g. \"$10 after 30 minutes online this "
            + "session\"). Empty by default.";
    private static final String[] FIELD_DEFAULT_LOYALTY_SESSION_MILESTONES = new String[0];

    private static final String CATEGORY_MOVEMENT = "movement";

    private static final String FIELD_KEY_MOVEMENT_ENABLED = "toleranceEnabled";
    private static final String FIELD_DESCRIPTION_MOVEMENT_ENABLED =
        "When true, vanilla's hardcoded \"moved too quickly\" thresholds in NetHandlerPlayServer "
            + "are multiplied by 'toleranceMultiplier', so lag-induced position desyncs stop "
            + "rubberband-snapping players back. With multiplier 1, behavior matches vanilla. "
            + "Inspired by Moving Quickly (coremod by thiakil et al.); SUM patches the same "
            + "constants via Mixin and gates by this flag.";
    private static final boolean FIELD_DEFAULT_MOVEMENT_ENABLED = true;

    private static final String FIELD_KEY_MOVEMENT_MULTIPLIER = "toleranceMultiplier";
    private static final String FIELD_DESCRIPTION_MOVEMENT_MULTIPLIER =
        "Factor applied to vanilla's 100.0 (player) / 100.0 (vehicle) / 300.0 (elytra) thresholds. "
            + "Vanilla compares SQUARED distance, so allowed travel per server tick only grows by "
            + "the square root of this: 1.0 = 10 blocks (vanilla), 10.0 = 31.6 blocks, 1024.0 = 320 "
            + "blocks. Default 1024.0 matches the Moving Quickly coremod SUM replaced, which "
            + "multiplied the same two constants by 1024 — effectively retiring the check. Drop to "
            + "16.0-100.0 if you want lag tolerance while keeping some speed-hack protection.";
    private static final double FIELD_DEFAULT_MOVEMENT_MULTIPLIER = 1024.0;
    private static final double FIELD_MIN_MOVEMENT_MULTIPLIER = 1.0;
    private static final double FIELD_MAX_MOVEMENT_MULTIPLIER = 4096.0;

    private static final String CATEGORY_PAUSER = "pauser";

    private static final String FIELD_KEY_PAUSER_ENABLED = "enabled";
    private static final String FIELD_DESCRIPTION_PAUSER_ENABLED =
        "When true, every WorldServer tick is cancelled while zero players are online — halting time, "
            + "weather, mob spawning, scheduled block updates, TileEntity ticks (so chunk-loaded farms "
            + "freeze), and entity ticks. Resumes immediately when the first player logs in. Inspired "
            + "by Server Pauser (Smileycorp, LGPL-2.1); SUM uses a Mixin on WorldServer.tick(), gated "
            + "by this flag.";
    private static final boolean FIELD_DEFAULT_PAUSER_ENABLED = true;

    private static final String CATEGORY_BEACHES = "beaches";

    private static final String FIELD_KEY_BEACHES_ENABLED = "enabled";
    private static final String FIELD_DESCRIPTION_BEACHES_ENABLED =
        "When true, breaking a configured 'affectedBlock' adjacent to a water source replaces the broken block with "
            + "water and animates flooding the surrounding column at sea level. Inspired by Pretty Beaches "
            + "(BlayTheNinth, MIT).";
    private static final boolean FIELD_DEFAULT_BEACHES_ENABLED = true;

    private static final String FIELD_KEY_BEACHES_AFFECTED_BLOCKS = "affectedBlocks";
    private static final String FIELD_DESCRIPTION_BEACHES_AFFECTED_BLOCKS =
        "List of block registry names whose breakage near water triggers the flooding behavior. "
            + "Use \"*\" to affect every block.";
    private static final String[] FIELD_DEFAULT_BEACHES_AFFECTED_BLOCKS = {
        "minecraft:sand"
    };

    private static final String FIELD_KEY_BEACHES_ANIMATED = "animatedFlooding";
    private static final String FIELD_DESCRIPTION_BEACHES_ANIMATED =
        "When true, flooding spreads one step every " + 10 + " ticks (animated). When false, the entire column "
            + "fills instantly on break.";
    private static final boolean FIELD_DEFAULT_BEACHES_ANIMATED = true;

    private static final String FIELD_KEY_BEACHES_INFINITE_BUCKET = "infiniteBucketWater";
    private static final String FIELD_DESCRIPTION_BEACHES_INFINITE_BUCKET =
        "When true, filling a bucket at a flowing-water tile adjacent to a source restores the source instead "
            + "of leaving an empty pocket. Off by default to preserve vanilla bucket behavior.";
    private static final boolean FIELD_DEFAULT_BEACHES_INFINITE_BUCKET = false;

    private static final String FIELD_KEY_BEACHES_REALISTIC = "realisticErosion";
    private static final String FIELD_DESCRIPTION_BEACHES_REALISTIC =
        "When true, the flooding behavior also fires for a curated set of \"erodible\" blocks: dirt, "
            + "grass, gravel, clay, mycelium, soul sand, snow blocks, and snow layers. Lets water "
            + "carve through riverbanks and other soft terrain, not just sand. The 'affectedBlocks' "
            + "list is still respected — this just adds the realistic-erosion set on top.";
    private static final boolean FIELD_DEFAULT_BEACHES_REALISTIC = false;

    /** Hardcoded set of blocks that get the beaches treatment when {@code realisticErosion}
     *  is enabled. Curated for materials water plausibly erodes — soft soil, gravel, snow.
     *  Stone and harder materials are deliberately excluded since flooding any hole next to
     *  water in stone caves would be unwanted. */
    private static final Set<String> REALISTIC_EROSION_BLOCKS = new HashSet<>(Arrays.asList(
        "minecraft:sand",
        "minecraft:dirt",
        "minecraft:grass",
        "minecraft:gravel",
        "minecraft:clay",
        "minecraft:mycelium",
        "minecraft:soul_sand",
        "minecraft:snow",
        "minecraft:snow_layer"
    ));

    private static final String CATEGORY_SLEEP_VOTE = "sleep_vote";

    private static final String FIELD_KEY_SLEEP_VOTE_ENABLED = "enabled";
    private static final String FIELD_DESCRIPTION_SLEEP_VOTE_ENABLED =
        "When true, the night ends if at least 'thresholdPercent' of online players are asleep "
            + "in the overworld. Set to false to fall back to vanilla's all-must-sleep behavior.";
    private static final boolean FIELD_DEFAULT_SLEEP_VOTE_ENABLED = true;

    private static final String FIELD_KEY_SLEEP_VOTE_THRESHOLD = "thresholdPercent";
    private static final String FIELD_DESCRIPTION_SLEEP_VOTE_THRESHOLD =
        "Percentage of online players (1-100) who must be in bed for the night to be skipped. "
            + "Default 50. The required count is rounded up so 50% on a server of 3 needs 2.";
    private static final int FIELD_DEFAULT_SLEEP_VOTE_THRESHOLD = 50;

    private static final String FIELD_KEY_SLEEP_VOTE_ACTION_BAR = "actionBarProgress";
    private static final String FIELD_DESCRIPTION_SLEEP_VOTE_ACTION_BAR =
        "When true, sleep-vote progress ('3/5 sleeping (need 3 to skip)') is shown live on the "
            + "action bar of every overworld player while anyone is in bed. When false, falls "
            + "back to a chat line that only fires when the sleeper count changes.";
    private static final boolean FIELD_DEFAULT_SLEEP_VOTE_ACTION_BAR = true;

    private static final String CATEGORY_PAY = "pay";

    private static final String FIELD_KEY_PAY_ENABLED = "enabled";
    private static final String FIELD_DESCRIPTION_PAY_ENABLED =
        "When true, players can transfer money to each other with /pay <player> <amount>. Both "
            + "players must be online. Set false to disable the command.";
    private static final boolean FIELD_DEFAULT_PAY_ENABLED = true;

    private static final String FIELD_KEY_PAY_FEE_PERCENT = "feePercent";
    private static final String FIELD_DESCRIPTION_PAY_FEE_PERCENT =
        "Percentage (0-100) skimmed off each /pay transfer as a money sink. The sender is charged "
            + "the full amount; the recipient receives the amount minus this fee. 0 (default) = no "
            + "fee, a pure transfer.";
    private static final double FIELD_DEFAULT_PAY_FEE_PERCENT = 0.0;

    private static final String CATEGORY_AFK = "afk";

    private static final String FIELD_KEY_AFK_ENABLED = "enabled";
    private static final String FIELD_DESCRIPTION_AFK_ENABLED =
        "Master toggle for AFK tracking. When true, players who don't move, look around, or chat "
            + "for 'thresholdSeconds' are flagged AFK, which other features (sleep vote, pauser) "
            + "can react to.";
    private static final boolean FIELD_DEFAULT_AFK_ENABLED = true;

    private static final String FIELD_KEY_AFK_THRESHOLD = "thresholdSeconds";
    private static final String FIELD_DESCRIPTION_AFK_THRESHOLD =
        "Seconds of no movement, rotation, or chat before a player is flagged AFK. Default 300 (5 minutes).";
    private static final int FIELD_DEFAULT_AFK_THRESHOLD = 300;

    private static final String FIELD_KEY_AFK_EXCLUDE_SLEEP = "excludeFromSleepVote";
    private static final String FIELD_DESCRIPTION_AFK_EXCLUDE_SLEEP =
        "When true, AFK players are removed from the sleep-vote head count, so one idle player "
            + "can't block the night skip. Requires the sleep_vote feature to be enabled.";
    private static final boolean FIELD_DEFAULT_AFK_EXCLUDE_SLEEP = true;

    private static final String FIELD_KEY_AFK_ANNOUNCE = "announce";
    private static final String FIELD_DESCRIPTION_AFK_ANNOUNCE =
        "When true, a chat message is broadcast when a player goes AFK or returns.";
    private static final boolean FIELD_DEFAULT_AFK_ANNOUNCE = true;

    private static final String FIELD_KEY_AFK_PAUSE_ALL = "pauseWorldWhenAllAfk";
    private static final String FIELD_DESCRIPTION_AFK_PAUSE_ALL =
        "When true, the server pauser also freezes the world while every online player is AFK "
            + "(not just when the server is empty). Requires the pauser feature to be enabled. Off "
            + "by default since some servers want AFK farms to keep running.";
    private static final boolean FIELD_DEFAULT_AFK_PAUSE_ALL = false;

    // ------------------------------------------------------------------------------------------
    // Open MCEconomic API — remote authoritative economy backend.
    //
    // When enabled, a remote HTTP service owns player balances and the transaction ledger, and
    // SUM becomes a client of it. The full wire protocol is specified in docs/OPEN_MCECONOMIC_API_SPECIFICATION.md;
    // this category is the client half of section 10 of that document.
    // ------------------------------------------------------------------------------------------

    private static final String CATEGORY_ECONOMY_API = "economy_api";

    private static final String FIELD_KEY_ECONOMY_API_ENABLED = "enabled";
    private static final String FIELD_DESCRIPTION_ECONOMY_API_ENABLED =
        "Master toggle for the Open MCEconomic API. When true, a remote HTTP service is the "
            + "authority for every player balance and SUM only mirrors it; when false (default), "
            + "SUM uses its local economy backends and never contacts the network. Requires "
            + "'baseUrl' and 'authToken' to be set. See docs/OPEN_MCECONOMIC_API_SPECIFICATION.md.";
    private static final boolean FIELD_DEFAULT_ECONOMY_API_ENABLED = false;

    private static final String FIELD_KEY_ECONOMY_API_ALLOW_INTEGRATED = "allowIntegratedServer";
    private static final String FIELD_DESCRIPTION_ECONOMY_API_ALLOW_INTEGRATED =
        "When false (default), the economy API is only contacted by a DEDICATED server. Opening a "
            + "single-player or LAN world runs an integrated server inside your game client, and "
            + "without this guard that world would connect to the shared economy service and spend "
            + "real balances from a local save. Set true only if you deliberately want a "
            + "single-player world to transact against the live economy. Physical Minecraft "
            + "clients NEVER contact the service regardless of this setting - they receive "
            + "balances from their server over SUM's own sync packet.";
    private static final boolean FIELD_DEFAULT_ECONOMY_API_ALLOW_INTEGRATED = false;

    private static final String FIELD_KEY_ECONOMY_API_BASE_URL = "baseUrl";
    private static final String FIELD_DESCRIPTION_ECONOMY_API_BASE_URL =
        "Root URL of the economy service, WITHOUT the /api/economic/v1 suffix - for example "
            + "'https://economy.example.net'. Must be https:// unless 'enableHttp' is true.";
    private static final String FIELD_DEFAULT_ECONOMY_API_BASE_URL = "";

    private static final String FIELD_KEY_ECONOMY_API_AUTH_TOKEN = "authToken";
    private static final String FIELD_DESCRIPTION_ECONOMY_API_AUTH_TOKEN =
        "Bearer token issued by the economy service operator, sent on every request. THIS IS A "
            + "SECRET: anyone holding it can move money. Protect this config file accordingly and "
            + "use a per-server token so one Minecraft server can be revoked on its own.";
    private static final String FIELD_DEFAULT_ECONOMY_API_AUTH_TOKEN = "";

    private static final String FIELD_KEY_ECONOMY_API_INSTANCE_ID = "instanceId";
    private static final String FIELD_DESCRIPTION_ECONOMY_API_INSTANCE_ID =
        "Identifies this Minecraft server to the economy service (sent as the X-MCE-Instance "
            + "header and recorded on every ledger entry). Give each server sharing a service a "
            + "distinct value, e.g. 'alto-main' or 'alto-creative'.";
    private static final String FIELD_DEFAULT_ECONOMY_API_INSTANCE_ID = "default";

    private static final String FIELD_KEY_ECONOMY_API_HMAC_SECRET = "hmacSecret";
    private static final String FIELD_DESCRIPTION_ECONOMY_API_HMAC_SECRET =
        "Optional shared secret for HMAC-SHA256 request signing, layered on top of the bearer "
            + "token. Leave blank to disable signing. THIS IS A SECRET. Only set it if the "
            + "economy service verifies signatures.";
    private static final String FIELD_DEFAULT_ECONOMY_API_HMAC_SECRET = "";

    private static final String FIELD_KEY_ECONOMY_API_CERT_PATH = "certificatePath";
    private static final String FIELD_DESCRIPTION_ECONOMY_API_CERT_PATH =
        "Optional path to a certificate or CA bundle to trust IN ADDITION to the system trust "
            + "store, for services using a self-signed or private-CA certificate. Resolved "
            + "relative to this config file; absolute paths also work. Accepts PEM (.pem/.crt/"
            + ".cer), PKCS#12 (.p12/.pfx), or JKS (.jks). Leave blank to use only the system "
            + "trust store. Certificate and hostname verification are ALWAYS performed - supply "
            + "the certificate here instead of trying to disable verification.";
    private static final String FIELD_DEFAULT_ECONOMY_API_CERT_PATH = "";

    private static final String FIELD_KEY_ECONOMY_API_CERT_PASSWORD = "certificatePassword";
    private static final String FIELD_DESCRIPTION_ECONOMY_API_CERT_PASSWORD =
        "Password for 'certificatePath' when it points at a PKCS#12 or JKS keystore. Not needed "
            + "for PEM files. THIS IS A SECRET.";
    private static final String FIELD_DEFAULT_ECONOMY_API_CERT_PASSWORD = "";

    private static final String FIELD_KEY_ECONOMY_API_ENABLE_HTTP = "enableHttp";
    private static final String FIELD_DESCRIPTION_ECONOMY_API_ENABLE_HTTP =
        "When true, allows a plaintext http:// baseUrl. LOCAL DEVELOPMENT ONLY - the auth token "
            + "and every player balance travel in the clear and can be read or forged by anything "
            + "on the network path. Leave false (default) in production; for a self-signed "
            + "certificate use 'certificatePath' instead of turning this on.";
    private static final boolean FIELD_DEFAULT_ECONOMY_API_ENABLE_HTTP = false;

    private static final String FIELD_KEY_ECONOMY_API_INTEGRITY_HEADERS = "sendIntegrityHeaders";
    private static final String FIELD_DESCRIPTION_ECONOMY_API_INTEGRITY_HEADERS =
        "When true (default), sends optional anomaly-detection headers the economy service may use "
            + "for strict policies: whether this server runs in online-mode, a per-world id and "
            + "monotonic sequence number (which lets the service spot a world rollback, the "
            + "classic duplication exploit), a per-boot session id, the online player count, "
            + "risk-relevant state of the acting player (creative/op/cheats), and the mod version. "
            + "No IP addresses, machine fingerprints, mod lists, player rosters, or world data are "
            + "ever sent. Set false to omit them; services must work without them.";
    private static final boolean FIELD_DEFAULT_ECONOMY_API_INTEGRITY_HEADERS = true;

    private static final String FIELD_KEY_ECONOMY_API_CONNECT_TIMEOUT = "connectTimeoutMs";
    private static final String FIELD_DESCRIPTION_ECONOMY_API_CONNECT_TIMEOUT =
        "Milliseconds to wait for the TCP and TLS connection to the economy service before giving "
            + "up. All requests run off the game thread, so this never stalls a tick.";
    private static final int FIELD_DEFAULT_ECONOMY_API_CONNECT_TIMEOUT = 3000;

    private static final String FIELD_KEY_ECONOMY_API_READ_TIMEOUT = "readTimeoutMs";
    private static final String FIELD_DESCRIPTION_ECONOMY_API_READ_TIMEOUT =
        "Milliseconds to wait for a response body after the request is sent.";
    private static final int FIELD_DEFAULT_ECONOMY_API_READ_TIMEOUT = 5000;

    private static final String FIELD_KEY_ECONOMY_API_MAX_RETRIES = "maxRetries";
    private static final String FIELD_DESCRIPTION_ECONOMY_API_MAX_RETRIES =
        "How many times to retry a retryable failure (timeout, 429, 5xx) before giving up. Retries "
            + "reuse the same idempotency key, so a retried payment can never double-charge.";
    private static final int FIELD_DEFAULT_ECONOMY_API_MAX_RETRIES = 2;

    private static final String FIELD_KEY_ECONOMY_API_BALANCE_TTL = "balanceCacheTtlSeconds";
    private static final String FIELD_DESCRIPTION_ECONOMY_API_BALANCE_TTL =
        "How old a cached balance may get before SUM refreshes it from the service. In-game "
            + "balance reads (HUDs, ATM, shop GUIs) always hit this cache, never the network.";
    private static final int FIELD_DEFAULT_ECONOMY_API_BALANCE_TTL = 30;

    private static final String FIELD_KEY_ECONOMY_API_EVENT_POLL = "eventPollSeconds";
    private static final String FIELD_DESCRIPTION_ECONOMY_API_EVENT_POLL =
        "How often to poll the service's change feed, which is how balance changes made OUTSIDE "
            + "Minecraft reach the game. Set 0 to disable polling and rely on the periodic balance "
            + "refresh instead. Ignored if the service doesn't advertise the 'events' capability.";
    private static final int FIELD_DEFAULT_ECONOMY_API_EVENT_POLL = 10;

    private static final String FIELD_KEY_ECONOMY_API_HEALTH_POLL = "healthPollSeconds";
    private static final String FIELD_DESCRIPTION_ECONOMY_API_HEALTH_POLL =
        "How often to re-check the service's /health endpoint for liveness and capability changes.";
    private static final int FIELD_DEFAULT_ECONOMY_API_HEALTH_POLL = 60;

    private static final String FIELD_KEY_ECONOMY_API_UNAVAILABLE_POLICY = "unavailablePolicy";
    private static final String FIELD_DESCRIPTION_ECONOMY_API_UNAVAILABLE_POLICY =
        "What to do while the economy service is unreachable. 'deny' (default) shows stale cached "
            + "balances and refuses all spending, so no money moves without the authority. "
            + "'cached' additionally queues transactions in memory and flushes them on recovery. "
            + "'local' falls back to SUM's built-in balances, which WILL diverge from the service "
            + "and need manual reconciliation - intended for single-player and offline testing.";
    private static final String FIELD_DEFAULT_ECONOMY_API_UNAVAILABLE_POLICY = "deny";
    private static final String[] ECONOMY_API_UNAVAILABLE_POLICIES = { "deny", "cached", "local" };

    private static final String FIELD_KEY_ECONOMY_API_VERBOSE = "verboseLogging";
    private static final String FIELD_DESCRIPTION_ECONOMY_API_VERBOSE =
        "When true, logs every economy API request and response for troubleshooting. The auth "
            + "token, HMAC secret, and signature are never logged regardless of this setting.";
    private static final boolean FIELD_DEFAULT_ECONOMY_API_VERBOSE = false;

    // Economy integration - SUM's public economy API for OTHER MODS.
    //
    // Not to be confused with 'economy_api' above. That category is SUM's client for a remote
    // economy SERVICE, and decides where player money is kept. This one decides which other
    // MODS are allowed to move that money, wherever it is kept. A server may use either, both,
    // or neither. See docs/SUM_ECONOMY_API.md.
    // ------------------------------------------------------------------------------------------

    private static final String CATEGORY_ECONOMY_INTEGRATION = "economy_integration";

    private static final String FIELD_KEY_ECONOMY_INTEGRATION_ALLOWED_MODS = "allowedMods";
    private static final String FIELD_DESCRIPTION_ECONOMY_INTEGRATION_ALLOWED_MODS =
        "Which other mods may move money through SUM's economy API, and what each may do. "
            + "Format: '<modid>=<scope>,<scope>' - for example 'mycasino=wallet_read,wallet_write,"
            + "escrow'. Use '<modid>=*' to grant every scope. EMPTY BY DEFAULT, which denies every "
            + "mod: a mod must be listed here before it can touch a single player's balance. "
            + "Scopes: 'wallet_read' (see what a player is carrying), 'wallet_write' (spend from "
            + "and credit to it), 'bank_read' (see a bank balance), 'bank_write' (deposit and "
            + "withdraw), 'escrow' (hold wallet money while a wager or trade resolves). Some imply "
            + "others, so you need not list them: wallet_write grants wallet_read, bank_write "
            + "grants bank_read, and escrow grants wallet_write. "
            + "WHAT THIS IS: an operator control and an audit trail, NOT a security boundary. It "
            + "lets you revoke one integration without a code change, makes a mod that forgot to "
            + "ask fail loudly instead of half-working, and tags every transaction with the mod "
            + "that made it. It CANNOT stop a mod that is determined to reach into SUM directly - "
            + "no setting inside Minecraft could. Install only mods you trust with your economy.";
    private static final String[] FIELD_DEFAULT_ECONOMY_INTEGRATION_ALLOWED_MODS = new String[0];

    private static final String FIELD_KEY_ECONOMY_INTEGRATION_LOG_TRANSACTIONS = "logTransactions";
    private static final String FIELD_DESCRIPTION_ECONOMY_INTEGRATION_LOG_TRANSACTIONS =
        "When true (default), logs every wallet, bank and escrow operation another mod performs, "
            + "with the mod id, player, amount and outcome. This is the audit trail for money that "
            + "SUM itself did not move; turning it off makes an integration's mistakes very hard "
            + "to reconstruct after the fact.";
    private static final boolean FIELD_DEFAULT_ECONOMY_INTEGRATION_LOG_TRANSACTIONS = true;

    private static final String FIELD_KEY_ECONOMY_INTEGRATION_MAX_WALLET = "maxWalletTransaction";
    private static final String FIELD_DESCRIPTION_ECONOMY_INTEGRATION_MAX_WALLET =
        "Largest wallet amount another mod may move in a single call, in dollars. Applies to both "
            + "spending and crediting, because a bug that credits too much is the more expensive "
            + "direction - it invents money the economy never had. Set 0 for no limit (default). "
            + "This does not limit SUM's own shops, jobs or ATM.";
    private static final double FIELD_DEFAULT_ECONOMY_INTEGRATION_MAX_WALLET = 0.0;

    private static final String FIELD_KEY_ECONOMY_INTEGRATION_MAX_BANK = "maxBankTransaction";
    private static final String FIELD_DESCRIPTION_ECONOMY_INTEGRATION_MAX_BANK =
        "Largest bank amount another mod may deposit or withdraw in a single call, in dollars. "
            + "Set 0 for no limit (default). This does not limit SUM's own ATM.";
    private static final double FIELD_DEFAULT_ECONOMY_INTEGRATION_MAX_BANK = 0.0;

    private static final double FIELD_MIN_ECONOMY_INTEGRATION_LIMIT = 0.0;
    private static final double FIELD_MAX_ECONOMY_INTEGRATION_LIMIT = 1.0e12;

    private static final String FIELD_KEY_ECONOMY_INTEGRATION_ESCROW_REFUND_ORPHANED =
        "refundOrphanedEscrow";
    private static final String FIELD_DESCRIPTION_ECONOMY_INTEGRATION_ESCROW_REFUND_ORPHANED =
        "When true (default), money still held in escrow by a mod that has been removed or "
            + "de-authorized is refunded to the players who put it up, once "
            + "'orphanedEscrowGraceMinutes' has passed. Set false to leave it held for manual "
            + "handling with '/sum econ api escrow'. Leaving it false means a removed mod's "
            + "players never see their stakes again without operator action.";
    private static final boolean FIELD_DEFAULT_ECONOMY_INTEGRATION_ESCROW_REFUND_ORPHANED = true;

    private static final String FIELD_KEY_ECONOMY_INTEGRATION_ESCROW_GRACE =
        "orphanedEscrowGraceMinutes";
    private static final String FIELD_DESCRIPTION_ECONOMY_INTEGRATION_ESCROW_GRACE =
        "How long after server start to wait before refunding escrow whose owning mod is missing. "
            + "The delay exists so a mod that simply loads late, or is temporarily removed for a "
            + "restart, does not have its in-flight wagers refunded out from under it.";
    private static final int FIELD_DEFAULT_ECONOMY_INTEGRATION_ESCROW_GRACE = 15;
    private static final int FIELD_MIN_ECONOMY_INTEGRATION_ESCROW_GRACE = 0;
    private static final int FIELD_MAX_ECONOMY_INTEGRATION_ESCROW_GRACE = 10080;

    private static String[] roamerWalkableBlocks;
    private static Set<String> roamerWalkableBlockSet;
    // Resolved lazily on first use. Block instances aren't available at preInit, since the block
    // registry is populated between preInit and init. The pathfinder hot path only runs after
    // entities exist in the world (post-init), so lazy resolution is safe.
    private static volatile Set<Block> roamerWalkableBlockResolved;

    private static Map<String, Double> roadRunnerSpeedBlocks;

    private static boolean favoritesStarOverlay;
    private static boolean sleepVoteEnabled;
    private static int sleepVoteThresholdPercent;
    private static boolean sleepVoteActionBarProgress;

    private static boolean beachesEnabled;
    private static boolean beachesAnimatedFlooding;
    private static boolean beachesInfiniteBucketWater;
    private static boolean beachesRealisticErosion;
    private static Set<String> beachesAffectedBlockNames;
    private static boolean beachesAffectsAllBlocks;

    private static boolean pauserEnabled;

    private static boolean movementToleranceEnabled;
    private static double movementToleranceMultiplier;

    private static boolean loyaltyEnabled;
    private static List<LoyaltyMilestone> loyaltyMilestones = Collections.emptyList();
    private static List<LoyaltyMilestone> loyaltySessionMilestones = Collections.emptyList();

    private static boolean autoDropperEnabled;
    private static int autoDropperTickInterval;

    private static boolean borderEnabled;
    private static Map<Integer, BorderEntry> borderEntries = Collections.emptyMap();

    private static boolean payEnabled;
    private static double payFeePercent;

    private static boolean afkEnabled;
    private static int afkThresholdSeconds;
    private static boolean afkExcludeFromSleepVote;
    private static boolean afkAnnounce;
    private static boolean afkPauseWorldWhenAllAfk;

    private static boolean economyApiEnabled;
    private static boolean economyApiAllowIntegratedServer;
    private static String economyApiBaseUrl;
    private static String economyApiAuthToken;
    private static String economyApiInstanceId;
    private static String economyApiHmacSecret;
    private static String economyApiCertificatePath;
    private static String economyApiCertificatePassword;
    private static boolean economyApiEnableHttp;
    private static boolean economyApiSendIntegrityHeaders;
    private static int economyApiConnectTimeoutMs;
    private static int economyApiReadTimeoutMs;
    private static int economyApiMaxRetries;
    private static int economyApiBalanceCacheTtlSeconds;
    private static int economyApiEventPollSeconds;
    private static int economyApiHealthPollSeconds;
    private static String economyApiUnavailablePolicy;
    private static boolean economyApiVerboseLogging;

    private static String[] economyIntegrationAllowedMods;
    /** Parsed and scope-expanded form of {@link #economyIntegrationAllowedMods}, keyed by mod id. */
    private static Map<String, Set<EconomyScope>> economyIntegrationScopes = Collections.emptyMap();
    private static boolean economyIntegrationLogTransactions;
    private static double economyIntegrationMaxWalletTransaction;
    private static double economyIntegrationMaxBankTransaction;
    private static boolean economyIntegrationRefundOrphanedEscrow;
    private static int economyIntegrationOrphanedEscrowGraceMinutes;

    private static Configuration config;

    /** Directory holding the config file. {@link #getEconomyApiCertificateFile()} resolves the
     *  operator's relative certificate path against this. */
    private static File configDirectory;

    static void init(File configFile) {
        if (config == null) {
            configDirectory = configFile.getParentFile();
            config = new Configuration(configFile);
            loadConfig();
        }
    }

    private static void loadConfig() {
        roamerWalkableBlocks = config.getStringList(
            FIELD_KEY_ROAMER_WALKABLE_BLOCKS, CATEGORY_ROAMER,
            FIELD_DEFAULT_ROAMER_WALKABLE_BLOCKS, FIELD_DESCRIPTION_ROAMER_WALKABLE_BLOCKS);
        roamerWalkableBlockSet = new HashSet<>(Arrays.asList(roamerWalkableBlocks));
        roamerWalkableBlockResolved = null;

        String[] speedBlockEntries = config.getStringList(
            FIELD_KEY_ROADRUNNER_SPEED_BLOCKS, CATEGORY_ROADRUNNER,
            FIELD_DEFAULT_ROADRUNNER_SPEED_BLOCKS, FIELD_DESCRIPTION_ROADRUNNER_SPEED_BLOCKS);
        roadRunnerSpeedBlocks = parseSpeedBlocks(speedBlockEntries);

        favoritesStarOverlay = config.getBoolean(
            FIELD_KEY_FAVORITES_STAR_OVERLAY, CATEGORY_FAVORITES,
            FIELD_DEFAULT_FAVORITES_STAR_OVERLAY, FIELD_DESCRIPTION_FAVORITES_STAR_OVERLAY);

        sleepVoteEnabled = config.getBoolean(
            FIELD_KEY_SLEEP_VOTE_ENABLED, CATEGORY_SLEEP_VOTE,
            FIELD_DEFAULT_SLEEP_VOTE_ENABLED, FIELD_DESCRIPTION_SLEEP_VOTE_ENABLED);
        sleepVoteThresholdPercent = config.getInt(
            FIELD_KEY_SLEEP_VOTE_THRESHOLD, CATEGORY_SLEEP_VOTE,
            FIELD_DEFAULT_SLEEP_VOTE_THRESHOLD, 1, 100,
            FIELD_DESCRIPTION_SLEEP_VOTE_THRESHOLD);
        sleepVoteActionBarProgress = config.getBoolean(
            FIELD_KEY_SLEEP_VOTE_ACTION_BAR, CATEGORY_SLEEP_VOTE,
            FIELD_DEFAULT_SLEEP_VOTE_ACTION_BAR, FIELD_DESCRIPTION_SLEEP_VOTE_ACTION_BAR);

        beachesEnabled = config.getBoolean(
            FIELD_KEY_BEACHES_ENABLED, CATEGORY_BEACHES,
            FIELD_DEFAULT_BEACHES_ENABLED, FIELD_DESCRIPTION_BEACHES_ENABLED);
        beachesAnimatedFlooding = config.getBoolean(
            FIELD_KEY_BEACHES_ANIMATED, CATEGORY_BEACHES,
            FIELD_DEFAULT_BEACHES_ANIMATED, FIELD_DESCRIPTION_BEACHES_ANIMATED);
        beachesInfiniteBucketWater = config.getBoolean(
            FIELD_KEY_BEACHES_INFINITE_BUCKET, CATEGORY_BEACHES,
            FIELD_DEFAULT_BEACHES_INFINITE_BUCKET, FIELD_DESCRIPTION_BEACHES_INFINITE_BUCKET);
        String[] beachesAffectedEntries = config.getStringList(
            FIELD_KEY_BEACHES_AFFECTED_BLOCKS, CATEGORY_BEACHES,
            FIELD_DEFAULT_BEACHES_AFFECTED_BLOCKS, FIELD_DESCRIPTION_BEACHES_AFFECTED_BLOCKS);
        beachesAffectedBlockNames = new HashSet<>(Arrays.asList(beachesAffectedEntries));
        beachesAffectsAllBlocks = beachesAffectedBlockNames.contains("*");
        beachesRealisticErosion = config.getBoolean(
            FIELD_KEY_BEACHES_REALISTIC, CATEGORY_BEACHES,
            FIELD_DEFAULT_BEACHES_REALISTIC, FIELD_DESCRIPTION_BEACHES_REALISTIC);

        pauserEnabled = config.getBoolean(
            FIELD_KEY_PAUSER_ENABLED, CATEGORY_PAUSER,
            FIELD_DEFAULT_PAUSER_ENABLED, FIELD_DESCRIPTION_PAUSER_ENABLED);

        movementToleranceEnabled = config.getBoolean(
            FIELD_KEY_MOVEMENT_ENABLED, CATEGORY_MOVEMENT,
            FIELD_DEFAULT_MOVEMENT_ENABLED, FIELD_DESCRIPTION_MOVEMENT_ENABLED);
        movementToleranceMultiplier = config.get(CATEGORY_MOVEMENT, FIELD_KEY_MOVEMENT_MULTIPLIER,
            FIELD_DEFAULT_MOVEMENT_MULTIPLIER, FIELD_DESCRIPTION_MOVEMENT_MULTIPLIER,
            FIELD_MIN_MOVEMENT_MULTIPLIER, FIELD_MAX_MOVEMENT_MULTIPLIER).getDouble();

        loyaltyEnabled = config.getBoolean(
            FIELD_KEY_LOYALTY_ENABLED, CATEGORY_LOYALTY,
            FIELD_DEFAULT_LOYALTY_ENABLED, FIELD_DESCRIPTION_LOYALTY_ENABLED);
        String[] milestoneEntries = config.getStringList(
            FIELD_KEY_LOYALTY_MILESTONES, CATEGORY_LOYALTY,
            FIELD_DEFAULT_LOYALTY_MILESTONES, FIELD_DESCRIPTION_LOYALTY_MILESTONES);
        loyaltyMilestones = parseLoyaltyMilestones(milestoneEntries, "lifetime");
        String[] sessionEntries = config.getStringList(
            FIELD_KEY_LOYALTY_SESSION_MILESTONES, CATEGORY_LOYALTY,
            FIELD_DEFAULT_LOYALTY_SESSION_MILESTONES, FIELD_DESCRIPTION_LOYALTY_SESSION_MILESTONES);
        loyaltySessionMilestones = parseLoyaltyMilestones(sessionEntries, "session");

        autoDropperEnabled = config.getBoolean(
            FIELD_KEY_AUTO_DROPPER_ENABLED, CATEGORY_AUTO_DROPPER,
            FIELD_DEFAULT_AUTO_DROPPER_ENABLED, FIELD_DESCRIPTION_AUTO_DROPPER_ENABLED);
        autoDropperTickInterval = config.getInt(
            FIELD_KEY_AUTO_DROPPER_INTERVAL, CATEGORY_AUTO_DROPPER,
            FIELD_DEFAULT_AUTO_DROPPER_INTERVAL, 1, 1200,
            FIELD_DESCRIPTION_AUTO_DROPPER_INTERVAL);

        borderEnabled = config.getBoolean(
            FIELD_KEY_BORDER_ENABLED, CATEGORY_BORDER,
            FIELD_DEFAULT_BORDER_ENABLED, FIELD_DESCRIPTION_BORDER_ENABLED);
        String[] borderEntryStrings = config.getStringList(
            FIELD_KEY_BORDER_BORDERS, CATEGORY_BORDER,
            FIELD_DEFAULT_BORDER_BORDERS, FIELD_DESCRIPTION_BORDER_BORDERS);
        borderEntries = parseBorderEntries(borderEntryStrings);

        payEnabled = config.getBoolean(
            FIELD_KEY_PAY_ENABLED, CATEGORY_PAY,
            FIELD_DEFAULT_PAY_ENABLED, FIELD_DESCRIPTION_PAY_ENABLED);
        payFeePercent = config.get(CATEGORY_PAY, FIELD_KEY_PAY_FEE_PERCENT,
            FIELD_DEFAULT_PAY_FEE_PERCENT, FIELD_DESCRIPTION_PAY_FEE_PERCENT, 0.0, 100.0).getDouble();

        afkEnabled = config.getBoolean(
            FIELD_KEY_AFK_ENABLED, CATEGORY_AFK,
            FIELD_DEFAULT_AFK_ENABLED, FIELD_DESCRIPTION_AFK_ENABLED);
        afkThresholdSeconds = config.getInt(
            FIELD_KEY_AFK_THRESHOLD, CATEGORY_AFK,
            FIELD_DEFAULT_AFK_THRESHOLD, 10, 86400, FIELD_DESCRIPTION_AFK_THRESHOLD);
        afkExcludeFromSleepVote = config.getBoolean(
            FIELD_KEY_AFK_EXCLUDE_SLEEP, CATEGORY_AFK,
            FIELD_DEFAULT_AFK_EXCLUDE_SLEEP, FIELD_DESCRIPTION_AFK_EXCLUDE_SLEEP);
        afkAnnounce = config.getBoolean(
            FIELD_KEY_AFK_ANNOUNCE, CATEGORY_AFK,
            FIELD_DEFAULT_AFK_ANNOUNCE, FIELD_DESCRIPTION_AFK_ANNOUNCE);
        afkPauseWorldWhenAllAfk = config.getBoolean(
            FIELD_KEY_AFK_PAUSE_ALL, CATEGORY_AFK,
            FIELD_DEFAULT_AFK_PAUSE_ALL, FIELD_DESCRIPTION_AFK_PAUSE_ALL);

        economyApiEnabled = config.getBoolean(
            FIELD_KEY_ECONOMY_API_ENABLED, CATEGORY_ECONOMY_API,
            FIELD_DEFAULT_ECONOMY_API_ENABLED, FIELD_DESCRIPTION_ECONOMY_API_ENABLED);
        economyApiAllowIntegratedServer = config.getBoolean(
            FIELD_KEY_ECONOMY_API_ALLOW_INTEGRATED, CATEGORY_ECONOMY_API,
            FIELD_DEFAULT_ECONOMY_API_ALLOW_INTEGRATED,
            FIELD_DESCRIPTION_ECONOMY_API_ALLOW_INTEGRATED);
        economyApiBaseUrl = trimTrailingSlashes(config.getString(
            FIELD_KEY_ECONOMY_API_BASE_URL, CATEGORY_ECONOMY_API,
            FIELD_DEFAULT_ECONOMY_API_BASE_URL, FIELD_DESCRIPTION_ECONOMY_API_BASE_URL));
        economyApiAuthToken = config.getString(
            FIELD_KEY_ECONOMY_API_AUTH_TOKEN, CATEGORY_ECONOMY_API,
            FIELD_DEFAULT_ECONOMY_API_AUTH_TOKEN, FIELD_DESCRIPTION_ECONOMY_API_AUTH_TOKEN).trim();
        economyApiInstanceId = config.getString(
            FIELD_KEY_ECONOMY_API_INSTANCE_ID, CATEGORY_ECONOMY_API,
            FIELD_DEFAULT_ECONOMY_API_INSTANCE_ID, FIELD_DESCRIPTION_ECONOMY_API_INSTANCE_ID).trim();
        economyApiHmacSecret = config.getString(
            FIELD_KEY_ECONOMY_API_HMAC_SECRET, CATEGORY_ECONOMY_API,
            FIELD_DEFAULT_ECONOMY_API_HMAC_SECRET, FIELD_DESCRIPTION_ECONOMY_API_HMAC_SECRET).trim();
        economyApiCertificatePath = config.getString(
            FIELD_KEY_ECONOMY_API_CERT_PATH, CATEGORY_ECONOMY_API,
            FIELD_DEFAULT_ECONOMY_API_CERT_PATH, FIELD_DESCRIPTION_ECONOMY_API_CERT_PATH).trim();
        economyApiCertificatePassword = config.getString(
            FIELD_KEY_ECONOMY_API_CERT_PASSWORD, CATEGORY_ECONOMY_API,
            FIELD_DEFAULT_ECONOMY_API_CERT_PASSWORD, FIELD_DESCRIPTION_ECONOMY_API_CERT_PASSWORD);
        economyApiEnableHttp = config.getBoolean(
            FIELD_KEY_ECONOMY_API_ENABLE_HTTP, CATEGORY_ECONOMY_API,
            FIELD_DEFAULT_ECONOMY_API_ENABLE_HTTP, FIELD_DESCRIPTION_ECONOMY_API_ENABLE_HTTP);
        economyApiSendIntegrityHeaders = config.getBoolean(
            FIELD_KEY_ECONOMY_API_INTEGRITY_HEADERS, CATEGORY_ECONOMY_API,
            FIELD_DEFAULT_ECONOMY_API_INTEGRITY_HEADERS,
            FIELD_DESCRIPTION_ECONOMY_API_INTEGRITY_HEADERS);
        economyApiConnectTimeoutMs = config.getInt(
            FIELD_KEY_ECONOMY_API_CONNECT_TIMEOUT, CATEGORY_ECONOMY_API,
            FIELD_DEFAULT_ECONOMY_API_CONNECT_TIMEOUT, 250, 60000,
            FIELD_DESCRIPTION_ECONOMY_API_CONNECT_TIMEOUT);
        economyApiReadTimeoutMs = config.getInt(
            FIELD_KEY_ECONOMY_API_READ_TIMEOUT, CATEGORY_ECONOMY_API,
            FIELD_DEFAULT_ECONOMY_API_READ_TIMEOUT, 250, 60000,
            FIELD_DESCRIPTION_ECONOMY_API_READ_TIMEOUT);
        economyApiMaxRetries = config.getInt(
            FIELD_KEY_ECONOMY_API_MAX_RETRIES, CATEGORY_ECONOMY_API,
            FIELD_DEFAULT_ECONOMY_API_MAX_RETRIES, 0, 10,
            FIELD_DESCRIPTION_ECONOMY_API_MAX_RETRIES);
        economyApiBalanceCacheTtlSeconds = config.getInt(
            FIELD_KEY_ECONOMY_API_BALANCE_TTL, CATEGORY_ECONOMY_API,
            FIELD_DEFAULT_ECONOMY_API_BALANCE_TTL, 1, 3600,
            FIELD_DESCRIPTION_ECONOMY_API_BALANCE_TTL);
        economyApiEventPollSeconds = config.getInt(
            FIELD_KEY_ECONOMY_API_EVENT_POLL, CATEGORY_ECONOMY_API,
            FIELD_DEFAULT_ECONOMY_API_EVENT_POLL, 0, 3600,
            FIELD_DESCRIPTION_ECONOMY_API_EVENT_POLL);
        economyApiHealthPollSeconds = config.getInt(
            FIELD_KEY_ECONOMY_API_HEALTH_POLL, CATEGORY_ECONOMY_API,
            FIELD_DEFAULT_ECONOMY_API_HEALTH_POLL, 5, 3600,
            FIELD_DESCRIPTION_ECONOMY_API_HEALTH_POLL);
        economyApiUnavailablePolicy = config.getString(
            FIELD_KEY_ECONOMY_API_UNAVAILABLE_POLICY, CATEGORY_ECONOMY_API,
            FIELD_DEFAULT_ECONOMY_API_UNAVAILABLE_POLICY,
            FIELD_DESCRIPTION_ECONOMY_API_UNAVAILABLE_POLICY, ECONOMY_API_UNAVAILABLE_POLICIES)
            .trim().toLowerCase(java.util.Locale.ROOT);
        economyApiVerboseLogging = config.getBoolean(
            FIELD_KEY_ECONOMY_API_VERBOSE, CATEGORY_ECONOMY_API,
            FIELD_DEFAULT_ECONOMY_API_VERBOSE, FIELD_DESCRIPTION_ECONOMY_API_VERBOSE);

        economyIntegrationAllowedMods = config.getStringList(
            FIELD_KEY_ECONOMY_INTEGRATION_ALLOWED_MODS, CATEGORY_ECONOMY_INTEGRATION,
            FIELD_DEFAULT_ECONOMY_INTEGRATION_ALLOWED_MODS,
            FIELD_DESCRIPTION_ECONOMY_INTEGRATION_ALLOWED_MODS);
        economyIntegrationScopes = parseAllowedMods(economyIntegrationAllowedMods);
        economyIntegrationLogTransactions = config.getBoolean(
            FIELD_KEY_ECONOMY_INTEGRATION_LOG_TRANSACTIONS, CATEGORY_ECONOMY_INTEGRATION,
            FIELD_DEFAULT_ECONOMY_INTEGRATION_LOG_TRANSACTIONS,
            FIELD_DESCRIPTION_ECONOMY_INTEGRATION_LOG_TRANSACTIONS);
        economyIntegrationMaxWalletTransaction = config.get(CATEGORY_ECONOMY_INTEGRATION,
            FIELD_KEY_ECONOMY_INTEGRATION_MAX_WALLET,
            FIELD_DEFAULT_ECONOMY_INTEGRATION_MAX_WALLET,
            FIELD_DESCRIPTION_ECONOMY_INTEGRATION_MAX_WALLET,
            FIELD_MIN_ECONOMY_INTEGRATION_LIMIT, FIELD_MAX_ECONOMY_INTEGRATION_LIMIT).getDouble();
        economyIntegrationMaxBankTransaction = config.get(CATEGORY_ECONOMY_INTEGRATION,
            FIELD_KEY_ECONOMY_INTEGRATION_MAX_BANK,
            FIELD_DEFAULT_ECONOMY_INTEGRATION_MAX_BANK,
            FIELD_DESCRIPTION_ECONOMY_INTEGRATION_MAX_BANK,
            FIELD_MIN_ECONOMY_INTEGRATION_LIMIT, FIELD_MAX_ECONOMY_INTEGRATION_LIMIT).getDouble();
        economyIntegrationRefundOrphanedEscrow = config.getBoolean(
            FIELD_KEY_ECONOMY_INTEGRATION_ESCROW_REFUND_ORPHANED, CATEGORY_ECONOMY_INTEGRATION,
            FIELD_DEFAULT_ECONOMY_INTEGRATION_ESCROW_REFUND_ORPHANED,
            FIELD_DESCRIPTION_ECONOMY_INTEGRATION_ESCROW_REFUND_ORPHANED);
        economyIntegrationOrphanedEscrowGraceMinutes = config.getInt(
            FIELD_KEY_ECONOMY_INTEGRATION_ESCROW_GRACE, CATEGORY_ECONOMY_INTEGRATION,
            FIELD_DEFAULT_ECONOMY_INTEGRATION_ESCROW_GRACE,
            FIELD_MIN_ECONOMY_INTEGRATION_ESCROW_GRACE, FIELD_MAX_ECONOMY_INTEGRATION_ESCROW_GRACE,
            FIELD_DESCRIPTION_ECONOMY_INTEGRATION_ESCROW_GRACE);

        if (config.hasChanged()) {
            config.save();
        }
    }

    /**
     * Parses the {@code economy_integration.allowedMods} list into scope sets, keyed by mod id.
     *
     * <p>Entries look like {@code mycasino=wallet_write,escrow}, or {@code mycasino=*} for
     * everything. Scopes are {@linkplain EconomyScope#expand expanded} here, so a later
     * authorization check is a plain {@code contains} rather than a rule walk.
     *
     * <p>Nothing here throws. A typo in one line must not cost an operator every other
     * integration on the server, so a bad line is warned about, named, and skipped. The one case
     * that is deliberately strict is an entry granting <i>no</i> valid scopes: it is dropped
     * entirely rather than recorded as an authorized mod with an empty scope set, because the
     * latter would report the mod as authorized while every call it makes fails.
     */
    private static Map<String, Set<EconomyScope>> parseAllowedMods(String[] entries) {
        Map<String, Set<EconomyScope>> result = new HashMap<>();
        if (entries == null) {
            return result;
        }
        for (String raw : entries) {
            if (raw == null) {
                continue;
            }
            String entry = raw.trim();
            if (entry.isEmpty() || entry.startsWith("#")) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                Sum.LOGGER.warn("[economy-api] invalid allowedMods entry '{}': expected "
                    + "'<modid>=<scope>,<scope>' (or '<modid>=*')", entry);
                continue;
            }
            String modId = entry.substring(0, eq).trim().toLowerCase(java.util.Locale.ROOT);
            if (modId.isEmpty()) {
                Sum.LOGGER.warn("[economy-api] invalid allowedMods entry '{}': no mod id before "
                    + "the '='", entry);
                continue;
            }
            String scopeList = entry.substring(eq + 1).trim();
            Set<EconomyScope> granted = new HashSet<>();
            if (EconomyScope.WILDCARD_TOKEN.equals(scopeList)) {
                granted.addAll(EconomyScope.all());
            } else {
                for (String token : scopeList.split(",")) {
                    String trimmed = token.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    EconomyScope scope = EconomyScope.fromToken(trimmed);
                    if (scope == null) {
                        Sum.LOGGER.warn("[economy-api] allowedMods entry '{}': unknown scope '{}', "
                            + "ignoring it. Valid scopes are wallet_read, wallet_write, bank_read, "
                            + "bank_write, escrow, or * for all.", entry, trimmed);
                        continue;
                    }
                    granted.add(scope);
                }
            }
            if (granted.isEmpty()) {
                Sum.LOGGER.warn("[economy-api] allowedMods entry '{}' grants no valid scopes, so "
                    + "'{}' stays unauthorized. Remove the line or give it a scope.", entry, modId);
                continue;
            }
            Set<EconomyScope> expanded = EconomyScope.expand(granted);
            Set<EconomyScope> previous = result.put(modId, expanded);
            if (previous != null) {
                Sum.LOGGER.warn("[economy-api] allowedMods lists '{}' more than once; the last "
                    + "entry wins, granting {}", modId, expanded);
            } else {
                Sum.LOGGER.info("[economy-api] '{}' is authorized for {}", modId, expanded);
            }
        }
        return result;
    }

    /** Strips trailing slashes from a base URL so endpoint paths can be appended directly. */
    private static String trimTrailingSlashes(String url) {
        if (url == null) return "";
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public static boolean isSleepVoteEnabled() {
        return sleepVoteEnabled;
    }

    public static boolean isSleepVoteActionBarProgress() {
        return sleepVoteActionBarProgress;
    }

    public static boolean isBeachesEnabled() {
        return beachesEnabled;
    }

    public static boolean isBeachesAnimatedFlooding() {
        return beachesAnimatedFlooding;
    }

    public static boolean isBeachesInfiniteBucketWater() {
        return beachesInfiniteBucketWater;
    }

    public static boolean isBeachesRealisticErosion() {
        return beachesRealisticErosion;
    }

    public static Set<String> getBeachesAffectedBlockNames() {
        return beachesAffectedBlockNames;
    }

    public static boolean isBorderEnabled() {
        return borderEnabled;
    }

    public static BorderEntry getBorderForDim(int dimId) {
        return borderEntries.get(dimId);
    }

    public static Map<Integer, BorderEntry> getAllBorderEntries() {
        return borderEntries;
    }

    /**
     * Parses RoadRunner {@code block=multiplier} entries into a map. Entries without an {@code =}
     * or with a non-numeric multiplier are skipped with a warning. Package-private + pure so it's
     * unit-testable like the border/loyalty parsers. Keys/values are trimmed.
     */
    static Map<String, Double> parseSpeedBlocks(String[] entries) {
        Map<String, Double> result = new HashMap<>();
        for (String entry : entries) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2) {
                try {
                    double multiplier = Double.parseDouble(parts[1].trim());
                    result.put(parts[0].trim(), multiplier);
                } catch (NumberFormatException e) {
                    Sum.LOGGER.warn("Invalid RoadRunner speed entry '{}': multiplier is not a number", entry);
                }
            } else {
                Sum.LOGGER.warn("Invalid RoadRunner speed entry '{}': expected format 'block=multiplier'", entry);
            }
        }
        return result;
    }

    private static Map<Integer, BorderEntry> parseBorderEntries(String[] entries) {
        Map<Integer, BorderEntry> result = new HashMap<>();
        for (String raw : entries) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                Sum.LOGGER.warn("[border] invalid entry '{}': expected '<dimId>=<radius>:<mode>'", entry);
                continue;
            }
            int dimId;
            try {
                dimId = Integer.parseInt(entry.substring(0, eq).trim());
            } catch (NumberFormatException e) {
                Sum.LOGGER.warn("[border] invalid entry '{}': dimId is not an integer", entry);
                continue;
            }
            String body = entry.substring(eq + 1).trim();
            int colon = body.indexOf(':');
            if (colon <= 0) {
                Sum.LOGGER.warn("[border] invalid entry '{}': expected '<radius>:<mode>'", entry);
                continue;
            }
            double radius;
            try {
                radius = Double.parseDouble(body.substring(0, colon).trim());
            } catch (NumberFormatException e) {
                Sum.LOGGER.warn("[border] invalid entry '{}': radius is not a number", entry);
                continue;
            }
            if (radius <= 0) {
                Sum.LOGGER.warn("[border] invalid entry '{}': radius must be positive", entry);
                continue;
            }
            String modeRaw = body.substring(colon + 1).trim().toUpperCase();
            BorderEntry.Mode mode;
            try {
                mode = BorderEntry.Mode.valueOf(modeRaw);
            } catch (IllegalArgumentException e) {
                Sum.LOGGER.warn("[border] invalid entry '{}': unknown mode '{}' (expected bounce or loop)",
                    entry, modeRaw);
                continue;
            }
            if (result.containsKey(dimId)) {
                Sum.LOGGER.warn("[border] duplicate border for dim {}; keeping the first entry", dimId);
                continue;
            }
            result.put(dimId, new BorderEntry(dimId, radius, mode));
        }
        return Collections.unmodifiableMap(result);
    }

    public static boolean isAutoDropperEnabled() {
        return autoDropperEnabled;
    }

    public static int getAutoDropperTickInterval() {
        return autoDropperTickInterval;
    }

    public static boolean isLoyaltyEnabled() {
        return loyaltyEnabled;
    }

    public static Collection<LoyaltyMilestone> getLoyaltyMilestones() {
        return loyaltyMilestones;
    }

    public static Collection<LoyaltyMilestone> getLoyaltySessionMilestones() {
        return loyaltySessionMilestones;
    }

    private static List<LoyaltyMilestone> parseLoyaltyMilestones(String[] entries, String labelForLogs) {
        List<LoyaltyMilestone> result = new ArrayList<>();
        Set<Integer> seenMinutes = new HashSet<>();
        for (String raw : entries) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                Sum.LOGGER.warn("[loyalty:{}] invalid milestone '{}': expected '<minutes>=<type>:<value>'", labelForLogs, entry);
                continue;
            }
            int minutes;
            try {
                minutes = Integer.parseInt(entry.substring(0, eq).trim());
            } catch (NumberFormatException e) {
                Sum.LOGGER.warn("[loyalty:{}] invalid milestone '{}': minutes is not an integer", labelForLogs, entry);
                continue;
            }
            if (minutes <= 0) {
                Sum.LOGGER.warn("[loyalty:{}] invalid milestone '{}': minutes must be positive", labelForLogs, entry);
                continue;
            }
            String body = entry.substring(eq + 1).trim();
            int colon = body.indexOf(':');
            if (colon <= 0) {
                Sum.LOGGER.warn("[loyalty:{}] invalid milestone '{}': expected '<type>:<value>'", labelForLogs, entry);
                continue;
            }
            String typeRaw = body.substring(0, colon).trim().toUpperCase();
            String value = body.substring(colon + 1).trim();
            LoyaltyMilestone.Type type;
            try {
                type = LoyaltyMilestone.Type.valueOf(typeRaw);
            } catch (IllegalArgumentException e) {
                Sum.LOGGER.warn("[loyalty:{}] invalid milestone '{}': unknown type '{}' (expected money or command)",
                    labelForLogs, entry, typeRaw);
                continue;
            }
            if (!seenMinutes.add(minutes)) {
                Sum.LOGGER.warn("[loyalty:{}] duplicate milestone for {}min; keeping the first entry", labelForLogs, minutes);
                continue;
            }
            result.add(new LoyaltyMilestone(minutes, type, value));
        }
        return Collections.unmodifiableList(result);
    }

    public static boolean isPauserEnabled() {
        return pauserEnabled;
    }

    public static boolean isMovementToleranceEnabled() {
        return movementToleranceEnabled;
    }

    public static double getMovementToleranceMultiplier() {
        return movementToleranceMultiplier;
    }

    public static boolean isBeachesAffectedBlock(Block block) {
        if (block == null || beachesAffectedBlockNames == null) {
            return false;
        }
        if (beachesAffectsAllBlocks) {
            return true;
        }
        net.minecraft.util.ResourceLocation registryName = block.getRegistryName();
        if (registryName == null) {
            return false;
        }
        String name = registryName.toString();
        if (beachesAffectedBlockNames.contains(name)) {
            return true;
        }
        return beachesRealisticErosion && REALISTIC_EROSION_BLOCKS.contains(name);
    }

    public static int getSleepVoteThresholdPercent() {
        return sleepVoteThresholdPercent;
    }

    public static boolean isPayEnabled() {
        return payEnabled;
    }

    // ------------------------------------------------------------------------------------------
    // Open MCEconomic API accessors. Protocol reference: docs/OPEN_MCECONOMIC_API_SPECIFICATION.md
    // ------------------------------------------------------------------------------------------

    /** Operator's master toggle. See {@link #isEconomyApiUsable()} for the "actually configured
     *  and safe to connect" check the client should gate on. */
    public static boolean isEconomyApiEnabled() {
        return economyApiEnabled;
    }

    /**
     * Whether an integrated server (single-player or LAN host) may contact the economy service.
     * Off by default: single-player runs a server inside the game client, and a shared economy
     * should not be spendable from a local world save. Physical clients never contact the service
     * at all — see {@link #isEconomyApiActiveOn(net.minecraft.server.MinecraftServer)}.
     */
    public static boolean isEconomyApiAllowedOnIntegratedServer() {
        return economyApiAllowIntegratedServer;
    }

    /**
     * The single gate the economy client should call before constructing the remote backend.
     *
     * <p>SUM's balances are server-authoritative and pushed to clients over
     * {@code PacketSyncSumMoney}, so the remote backend belongs on the logical server only. This
     * additionally refuses integrated servers unless the operator opted in, so opening a
     * single-player world with a production config does not transact against the live economy.
     *
     * @param server the running server, or null if none (pure client context).
     * @return true only when the config is usable and this side should own the remote connection.
     */
    public static boolean isEconomyApiActiveOn(net.minecraft.server.MinecraftServer server) {
        if (server == null || !isEconomyApiUsable()) {
            return false;
        }
        return server.isDedicatedServer() || economyApiAllowIntegratedServer;
    }

    /** Service root with no trailing slash and no {@code /api/economic/v1} suffix. */
    public static String getEconomyApiBaseUrl() {
        return economyApiBaseUrl;
    }

    public static String getEconomyApiAuthToken() {
        return economyApiAuthToken;
    }

    public static String getEconomyApiInstanceId() {
        return economyApiInstanceId;
    }

    /** Blank when HMAC request signing is disabled. */
    public static String getEconomyApiHmacSecret() {
        return economyApiHmacSecret;
    }

    public static boolean isEconomyApiHmacEnabled() {
        return !economyApiHmacSecret.isEmpty();
    }

    /** Raw configured value; use {@link #getEconomyApiCertificateFile()} to resolve it. */
    public static String getEconomyApiCertificatePath() {
        return economyApiCertificatePath;
    }

    /**
     * Resolves {@code certificatePath} against the SUM config directory, so operators can drop a
     * self-signed certificate next to the config file and reference it by name.
     *
     * @return the certificate file, or null if no certificate is configured. The file is not
     *     checked for existence here — the economy client reports that as a startup error so the
     *     operator sees a specific message rather than a silent fallback to system trust.
     */
    public static File getEconomyApiCertificateFile() {
        if (economyApiCertificatePath.isEmpty()) {
            return null;
        }
        File candidate = new File(economyApiCertificatePath);
        if (candidate.isAbsolute() || configDirectory == null) {
            return candidate;
        }
        return new File(configDirectory, economyApiCertificatePath);
    }

    public static String getEconomyApiCertificatePassword() {
        return economyApiCertificatePassword;
    }

    /**
     * Whether to send the optional anomaly-detection headers a service may use for strict
     * policies. See the field description for exactly what is and is not included; services must
     * function without them.
     */
    public static boolean isEconomyApiSendIntegrityHeaders() {
        return economyApiSendIntegrityHeaders;
    }

    /** True only when the operator has explicitly opted into plaintext HTTP. Development only. */
    public static boolean isEconomyApiHttpEnabled() {
        return economyApiEnableHttp;
    }

    public static int getEconomyApiConnectTimeoutMs() {
        return economyApiConnectTimeoutMs;
    }

    public static int getEconomyApiReadTimeoutMs() {
        return economyApiReadTimeoutMs;
    }

    public static int getEconomyApiMaxRetries() {
        return economyApiMaxRetries;
    }

    public static int getEconomyApiBalanceCacheTtlSeconds() {
        return economyApiBalanceCacheTtlSeconds;
    }

    /** 0 disables change-feed polling. */
    public static int getEconomyApiEventPollSeconds() {
        return economyApiEventPollSeconds;
    }

    public static int getEconomyApiHealthPollSeconds() {
        return economyApiHealthPollSeconds;
    }

    /** One of {@code deny}, {@code cached}, {@code local}. */
    public static String getEconomyApiUnavailablePolicy() {
        return economyApiUnavailablePolicy;
    }

    public static boolean isEconomyApiVerboseLogging() {
        return economyApiVerboseLogging;
    }

    /**
     * Validates the economy API configuration as a unit. Enabling the feature with a blank URL or
     * token, or pointing it at a plaintext endpoint without {@code enableHttp}, is a
     * misconfiguration rather than something to fail on at the first request.
     *
     * @return null if the configuration is usable, otherwise an operator-facing explanation of
     *     what is wrong. Callers log this once at startup and leave the remote backend inert.
     */
    public static String validateEconomyApiConfig() {
        if (!economyApiEnabled) {
            return null;
        }
        if (economyApiBaseUrl.isEmpty()) {
            return "economy_api.enabled is true but economy_api.baseUrl is blank.";
        }
        String lower = economyApiBaseUrl.toLowerCase(java.util.Locale.ROOT);
        boolean https = lower.startsWith("https://");
        boolean http = lower.startsWith("http://");
        if (!https && !http) {
            return "economy_api.baseUrl must start with https:// (got '" + economyApiBaseUrl + "').";
        }
        if (http && !economyApiEnableHttp) {
            return "economy_api.baseUrl uses plaintext http://, which is refused for security. "
                + "Use https://, or for a self-signed certificate set economy_api.certificatePath. "
                + "Set economy_api.enableHttp=true only for local development.";
        }
        if (economyApiAuthToken.isEmpty()) {
            return "economy_api.enabled is true but economy_api.authToken is blank.";
        }
        return null;
    }

    /** True when the API is enabled and its configuration passes {@link #validateEconomyApiConfig()}. */
    public static boolean isEconomyApiUsable() {
        return economyApiEnabled && validateEconomyApiConfig() == null;
    }

    /**
     * Scopes granted to an integrating mod, already expanded under
     * {@link EconomyScope#expand}.
     *
     * @return an empty set for any mod not in {@code allowedMods} — the deny-by-default case.
     *     Never null, so callers need no null check before a {@code contains}.
     */
    public static Set<EconomyScope> getEconomyIntegrationScopes(String modId) {
        if (modId == null) {
            return Collections.emptySet();
        }
        Set<EconomyScope> scopes =
            economyIntegrationScopes.get(modId.trim().toLowerCase(java.util.Locale.ROOT));
        return scopes != null ? scopes : Collections.emptySet();
    }

    /** Every authorized mod id and its scopes. Unmodifiable; used by {@code /sum econ api mods}. */
    public static Map<String, Set<EconomyScope>> getEconomyIntegrationAllowedMods() {
        return Collections.unmodifiableMap(economyIntegrationScopes);
    }

    public static boolean isEconomyIntegrationLoggingEnabled() {
        return economyIntegrationLogTransactions;
    }

    /** Per-call wallet cap for integrating mods, in dollars. 0 means no limit. */
    public static double getEconomyIntegrationMaxWalletTransaction() {
        return economyIntegrationMaxWalletTransaction;
    }

    /** Per-call bank cap for integrating mods, in dollars. 0 means no limit. */
    public static double getEconomyIntegrationMaxBankTransaction() {
        return economyIntegrationMaxBankTransaction;
    }

    public static boolean isEconomyIntegrationRefundOrphanedEscrow() {
        return economyIntegrationRefundOrphanedEscrow;
    }

    public static int getEconomyIntegrationOrphanedEscrowGraceMinutes() {
        return economyIntegrationOrphanedEscrowGraceMinutes;
    }

    public static double getPayFeePercent() {
        return payFeePercent;
    }

    public static boolean isAfkEnabled() {
        return afkEnabled;
    }

    public static int getAfkThresholdSeconds() {
        return afkThresholdSeconds;
    }

    public static boolean isAfkExcludeFromSleepVote() {
        return afkExcludeFromSleepVote;
    }

    public static boolean isAfkAnnounce() {
        return afkAnnounce;
    }

    public static boolean isAfkPauseWorldWhenAllAfk() {
        return afkPauseWorldWhenAllAfk;
    }

    /**
     * Favorites star overlay is now owned by {@link com.micatechnologies.minecraft.sum
     * .pocket.SumOneConfig#favoritesStarOverlay} (OneConfig-managed). The legacy Forge
     * Configuration value is kept as a fallback for environments where SumOneConfig
     * hasn't initialized (e.g. server-side capability checks that happen to call into
     * this path — uncommon since the overlay is purely client-side UI).
     *
     * <p>Migration semantics: existing installs that had favorites.enableStarOverlay set
     * in the Forge config will see their preference reset to OneConfig's default on the
     * first boot after this migration. Users can re-set it via the SUM OneConfig page.
     * No automated forge→oneconfig migration because OneConfig's own persistence kicks in
     * before we get a chance to inspect whether its value is "default" or "loaded".</p>
     */
    public static boolean isFavoritesStarOverlayEnabled() {
        try {
            if (com.micatechnologies.minecraft.sum.pocket.SumOneConfig.INSTANCE != null) {
                return com.micatechnologies.minecraft.sum.pocket.SumOneConfig.INSTANCE.favoritesStarOverlay;
            }
        } catch (NoClassDefFoundError oneConfigMissing) {
            // SUM declares OneConfig as required-after in @Mod, so this branch shouldn't
            // fire in a real install — defensive fallback for stripped dev envs.
        }
        return favoritesStarOverlay;
    }

    public static Set<String> getRoamerWalkableBlocks() {
        return roamerWalkableBlockSet;
    }

    public static boolean isBlockWalkableByRoamer(String registryName) {
        return roamerWalkableBlockSet != null && roamerWalkableBlockSet.contains(registryName);
    }

    /**
     * Hot-path overload used by the roamer pathfinder and AI. Resolves the configured registry
     * names into {@link Block} instances on first call (after registries are populated) and
     * matches by reference identity, avoiding {@code getRegistryName().toString()} allocations
     * on every node expansion.
     */
    public static boolean isBlockWalkableByRoamer(Block block) {
        Set<Block> resolved = roamerWalkableBlockResolved;
        if (resolved == null) {
            resolved = resolveWalkableBlocks();
            roamerWalkableBlockResolved = resolved;
        }
        return resolved.contains(block);
    }

    private static synchronized Set<Block> resolveWalkableBlocks() {
        if (roamerWalkableBlockResolved != null) {
            return roamerWalkableBlockResolved;
        }
        Set<Block> set = new HashSet<>();
        if (roamerWalkableBlockSet != null) {
            for (String name : roamerWalkableBlockSet) {
                Block b = Block.getBlockFromName(name);
                if (b != null) {
                    set.add(b);
                }
            }
        }
        return set;
    }

    public static Map<String, Double> getRoadRunnerSpeedBlocks() {
        return roadRunnerSpeedBlocks;
    }

    public static double getRoadRunnerSpeedMultiplier(String registryName) {
        if (roadRunnerSpeedBlocks == null) {
            return 0.0;
        }
        return roadRunnerSpeedBlocks.getOrDefault(registryName, 0.0);
    }

    public static void reloadConfig() {
        if (config != null) {
            config.load();
            loadConfig();
            // Economy authorization is re-read above; tell the API so a mod that has just been
            // authorized is announced rather than staying suppressed by an earlier denial.
            com.micatechnologies.minecraft.sum.economy.apiimpl.EconomyApiRegistry
                .onConfigReloaded();
        }
    }

    public static boolean addRoamerWalkableBlock(String registryName) {
        if (roamerWalkableBlockSet.contains(registryName)) {
            return false;
        }
        roamerWalkableBlockSet.add(registryName);
        roamerWalkableBlockResolved = null;
        saveRoamerWalkableBlocks();
        return true;
    }

    public static boolean removeRoamerWalkableBlock(String registryName) {
        if (!roamerWalkableBlockSet.contains(registryName)) {
            return false;
        }
        roamerWalkableBlockSet.remove(registryName);
        roamerWalkableBlockResolved = null;
        saveRoamerWalkableBlocks();
        return true;
    }

    private static void saveRoamerWalkableBlocks() {
        roamerWalkableBlocks = roamerWalkableBlockSet.toArray(new String[0]);
        config.get(CATEGORY_ROAMER, FIELD_KEY_ROAMER_WALKABLE_BLOCKS,
            FIELD_DEFAULT_ROAMER_WALKABLE_BLOCKS, FIELD_DESCRIPTION_ROAMER_WALKABLE_BLOCKS)
            .set(roamerWalkableBlocks);
        config.save();
    }
}
