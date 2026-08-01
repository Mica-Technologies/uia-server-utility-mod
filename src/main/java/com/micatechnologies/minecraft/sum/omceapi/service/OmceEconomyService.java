package com.micatechnologies.minecraft.sum.omceapi.service;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.omceapi.OmceAccount;
import com.micatechnologies.minecraft.sum.omceapi.OmceBalance;
import com.micatechnologies.minecraft.sum.omceapi.OmceError;
import com.micatechnologies.minecraft.sum.omceapi.OmceEventPage;
import com.micatechnologies.minecraft.sum.omceapi.OmceHealth;
import com.micatechnologies.minecraft.sum.omceapi.OmceMoney;
import com.micatechnologies.minecraft.sum.omceapi.OmceParty;
import com.micatechnologies.minecraft.sum.omceapi.OmceProtocol;
import com.micatechnologies.minecraft.sum.omceapi.OmceResult;
import com.micatechnologies.minecraft.sum.omceapi.OmceTransaction;
import com.micatechnologies.minecraft.sum.omceapi.OmceTransactionRequest;
import com.micatechnologies.minecraft.sum.omceapi.OmceTransactionResult;
import com.micatechnologies.minecraft.sum.omceapi.client.OmceClient;
import com.micatechnologies.minecraft.sum.omceapi.client.OmceClientConfig;
import com.micatechnologies.minecraft.sum.omceapi.client.OmceTlsProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSocketFactory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

/**
 * Owns SUM's connection to a remote economy service: lifecycle, threading, caching, and the
 * translation between SUM's synchronous economy calls and the protocol's asynchronous ones.
 *
 * <h2>Threading</h2>
 *
 * Every HTTP call runs on {@link #executor}; nothing here blocks the Minecraft server thread. All
 * in-game reads are served from {@link OmceBalanceCache}, which the executor keeps fresh.
 *
 * <h2>What this owns</h2>
 *
 * Player <b>bank accounts</b> only. Wallets are local to the world save and never reach the
 * network, so shops, plots, job escrow and payments settle synchronously without this class being
 * involved at all. Money crosses between wallet and bank only at an ATM, through
 * {@link com.micatechnologies.minecraft.sum.bank.BankService}.
 *
 * <p>That boundary is why every mutating call here goes through {@link #processTransaction},
 * which reports only once the service says {@code committed}. An ATM is always about to hand the
 * player something irreversible - a bill, or wallet credit - so nothing may be granted on an
 * unconfirmed write.
 *
 */
public final class OmceEconomyService {

    /** Concurrent HTTP calls. Small: the protocol batches reads, and writes follow player actions. */
    private static final int WORKER_THREADS = 2;

    /** Bounded so a service outage cannot grow an unbounded backlog and exhaust the heap. */
    private static final int QUEUE_CAPACITY = 512;

    private static final int EVENT_PAGE_LIMIT = 200;

    private final MinecraftServer server;
    private final OmceClientConfig config;
    private final OmceClient client;
    private final OmceServerEnvironment environment;
    private final OmceBalanceCache cache = new OmceBalanceCache();

    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService scheduler;

    /** Latest successful handshake. Null until the first {@code /health} succeeds. */
    private final AtomicReference<OmceHealth> health = new AtomicReference<>(null);

    /** Cursor for the change feed; null starts from "now" on the next poll. */
    private final AtomicReference<String> eventCursor = new AtomicReference<>(null);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean degraded = new AtomicBoolean(true);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    private OmceEconomyService(MinecraftServer server, OmceClientConfig config,
        OmceServerEnvironment environment, @Nullable SSLSocketFactory socketFactory) {
        this.server = server;
        this.config = config;
        this.environment = environment;
        this.client = new OmceClient(config, environment, socketFactory);

        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "SUM-OMCE-" + System.identityHashCode(r));
            // Daemon so a hung request can never keep the JVM alive after the server stops.
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        };
        this.executor = new ThreadPoolExecutor(WORKER_THREADS, WORKER_THREADS, 30L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY), factory,
            // Never block the caller (which may be the server thread) when the queue is full;
            // reject the work and let the submitting code report a failure instead.
            new ThreadPoolExecutor.AbortPolicy());
        this.executor.allowCoreThreadTimeOut(true);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SUM-OMCE-Scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    // ---------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------

    /**
     * Builds and starts the service if the operator has enabled and correctly configured it.
     *
     * <p>Returns null — leaving SUM on its local economy backends — when the feature is off, the
     * configuration is unusable, or this is an integrated server without
     * {@code allowIntegratedServer}. Every rejection is logged with a specific reason, because a
     * silently inert economy backend is far worse to diagnose than a loud one.
     */
    @Nullable
    public static OmceEconomyService startIfEnabled(MinecraftServer server) {
        if (server == null || !SumConfig.isEconomyApiEnabled()) {
            return null;
        }
        String problem = SumConfig.validateEconomyApiConfig();
        if (problem != null) {
            Sum.LOGGER.error("[omce] Economy API is enabled but not usable: {}", problem);
            return null;
        }
        if (!SumConfig.isEconomyApiActiveOn(server)) {
            Sum.LOGGER.warn("[omce] Economy API is configured, but this is an integrated "
                + "(single-player or LAN) server and economy_api.allowIntegratedServer is false. "
                + "Using SUM's local economy for this world instead. This is the safe default — "
                + "it stops a local save from spending real balances.");
            return null;
        }

        OmceClientConfig config;
        try {
            config = OmceClientConfig.builder()
                .baseUrl(SumConfig.getEconomyApiBaseUrl())
                .authToken(SumConfig.getEconomyApiAuthToken())
                .instanceId(SumConfig.getEconomyApiInstanceId())
                .hmacSecret(SumConfig.getEconomyApiHmacSecret())
                .certificate(SumConfig.getEconomyApiCertificateFile(),
                    SumConfig.getEconomyApiCertificatePassword())
                .httpAllowed(SumConfig.isEconomyApiHttpEnabled())
                .timeouts(SumConfig.getEconomyApiConnectTimeoutMs(),
                    SumConfig.getEconomyApiReadTimeoutMs())
                .maxRetries(SumConfig.getEconomyApiMaxRetries())
                .sendIntegrityHeaders(SumConfig.isEconomyApiSendIntegrityHeaders())
                .verboseLogging(SumConfig.isEconomyApiVerboseLogging())
                .userAgent("SUM/" + SumConstants.MOD_VERSION + " OpenMCEconomicAPI/"
                    + OmceProtocol.VERSION)
                .build();
        } catch (RuntimeException e) {
            Sum.LOGGER.error("[omce] Economy API configuration is invalid: {}", e.getMessage());
            return null;
        }

        SSLSocketFactory socketFactory;
        try {
            socketFactory = OmceTlsProvider.createSocketFactory(
                SumConfig.getEconomyApiCertificateFile(),
                SumConfig.getEconomyApiCertificatePassword());
        } catch (OmceTlsProvider.OmceTlsException e) {
            // Fatal by design: falling back to an unverified connection would defeat the point of
            // configuring a certificate at all.
            Sum.LOGGER.error("[omce] Economy API disabled — {}", e.getMessage());
            return null;
        }

        if (config.isPlaintext()) {
            Sum.LOGGER.warn("[omce] ******************************************************");
            Sum.LOGGER.warn("[omce] economy_api.enableHttp is TRUE — the economy auth token");
            Sum.LOGGER.warn("[omce] and every player balance are travelling in PLAINTEXT.");
            Sum.LOGGER.warn("[omce] Use https:// in production. For a self-signed certificate,");
            Sum.LOGGER.warn("[omce] set economy_api.certificatePath instead of enabling this.");
            Sum.LOGGER.warn("[omce] ******************************************************");
        }

        OmceWorldState worldState = null;
        try {
            if (server.getWorld(0) != null) {
                worldState = OmceWorldState.get(server.getWorld(0));
            }
        } catch (RuntimeException e) {
            Sum.LOGGER.warn("[omce] Could not load per-world integrity state; rollback detection "
                + "headers will be omitted.", e);
        }

        OmceEconomyService service = new OmceEconomyService(server, config,
            new OmceServerEnvironment(server, worldState), socketFactory);
        service.start();
        return service;
    }

    private void start() {
        running.set(true);
        Sum.LOGGER.info("[omce] Starting economy client: {}", config);
        executor.execute(this::handshake);

        int healthSeconds = Math.max(5, SumConfig.getEconomyApiHealthPollSeconds());
        scheduler.scheduleWithFixedDelay(safely(this::handshake), healthSeconds, healthSeconds,
            TimeUnit.SECONDS);

        int eventSeconds = SumConfig.getEconomyApiEventPollSeconds();
        if (eventSeconds > 0) {
            scheduler.scheduleWithFixedDelay(safely(this::pollEvents), eventSeconds,
                eventSeconds, TimeUnit.SECONDS);
        }

        int refreshSeconds = Math.max(5, SumConfig.getEconomyApiBalanceCacheTtlSeconds());
        scheduler.scheduleWithFixedDelay(safely(this::refreshStaleBalances), refreshSeconds,
            refreshSeconds, TimeUnit.SECONDS);
    }

    /** Wraps a periodic task so a thrown exception cannot silently cancel the schedule. */
    private Runnable safely(Runnable task) {
        return () -> {
            if (!running.get()) {
                return;
            }
            try {
                task.run();
            } catch (Throwable t) {
                Sum.LOGGER.warn("[omce] Scheduled economy task failed.", t);
            }
        };
    }

    /** Stops all background work. Safe to call more than once. */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        Sum.LOGGER.info("[omce] Stopping economy client.");
        scheduler.shutdownNow();
        executor.shutdown();
        try {
            // Give in-flight transactions a brief chance to land so the ledger stays complete.
            if (!executor.awaitTermination(3L, TimeUnit.SECONDS)) {
                List<Runnable> dropped = executor.shutdownNow();
                if (!dropped.isEmpty()) {
                    Sum.LOGGER.warn("[omce] {} economy request(s) were still queued at shutdown and "
                        + "were dropped. They were never sent, so no money moved.", dropped.size());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        cache.clear();
    }

    public boolean isRunning() {
        return running.get();
    }

    // ---------------------------------------------------------------------------------------
    // Handshake and health
    // ---------------------------------------------------------------------------------------

    private void handshake() {
        OmceResult<OmceHealth> result = client.health();
        if (result.isFailure()) {
            onFailure(result.getError(), "/health");
            return;
        }
        OmceHealth fresh = result.get();
        OmceHealth previous = health.getAndSet(fresh);
        onSuccess();

        if (previous == null) {
            OmceHealth.Currency currency = fresh.getCurrency();
            Sum.LOGGER.info("[omce] Connected to {} — currency {} ({}, {} decimal place(s)), "
                    + "status {}.", fresh.getImplementationName(), currency.getCode(),
                currency.getSymbol(), currency.getMinorUnitDigits(), fresh.getStatus());
            Sum.LOGGER.info("[omce] Capabilities: {}",
                fresh.getCapabilities().all().isEmpty() ? "(none declared)"
                    : String.join(", ", fresh.getCapabilities().all()));
            warnAboutFallbacks(fresh);
            // Seed the cache for anyone already online (a /reload or a late start).
            executor.execute(() -> resolveAndLoad(onlinePlayerUuids()));
        }
    }

    /** Logs, once per connection, every capability the service lacks that changes SUM's behaviour. */
    private void warnAboutFallbacks(OmceHealth h) {
        OmceHealth.Capabilities caps = h.getCapabilities();
        if (!caps.canVoid()) {
            Sum.LOGGER.info("[omce] Service has no 'void' capability — rollbacks will be sent as "
                + "compensating reverse transactions instead.");
        }
        if (!caps.hasEvents()) {
            Sum.LOGGER.info("[omce] Service has no 'events' capability — balance changes made "
                + "outside Minecraft will only appear after the periodic refresh.");
        }
        if (!caps.hasFees()) {
            Sum.LOGGER.info("[omce] Service has no 'fees' capability — /pay fees will be sent as a "
                + "second transaction.");
        }
        if (caps.isStrictTransactionTypes()) {
            Sum.LOGGER.warn("[omce] Service declares 'strictTransactionTypes'. It will reject "
                + "transaction types it does not know, so new SUM economy features may fail until "
                + "the service is updated.");
        }
        if (h.getCurrency().getMinorUnitDigits() < 2) {
            Sum.LOGGER.warn("[omce] Service currency has {} decimal place(s); SUM prices with finer "
                    + "precision will be rounded UP to the next whole unit when charging.",
                h.getCurrency().getMinorUnitDigits());
        }
        if (!h.getRequiredHeaders().isEmpty()) {
            Sum.LOGGER.info("[omce] Service requires headers: {}",
                String.join(", ", h.getRequiredHeaders()));
        }
    }

    private void onSuccess() {
        consecutiveFailures.set(0);
        if (degraded.compareAndSet(true, false)) {
            Sum.LOGGER.info("[omce] Economy service is reachable; leaving degraded mode.");
        }
    }

    private void onFailure(@Nullable OmceError error, String what) {
        int failures = consecutiveFailures.incrementAndGet();
        if (degraded.compareAndSet(false, true)) {
            Sum.LOGGER.warn("[omce] Economy service unreachable ({} on {}); entering degraded mode "
                + "with policy '{}'.", error, what, SumConfig.getEconomyApiUnavailablePolicy());
        } else if (failures == 1 || failures % 20 == 0) {
            // Log the first failure and then only occasionally, so an extended outage does not
            // fill the log with one line per poll.
            Sum.LOGGER.warn("[omce] Economy service still unreachable after {} attempt(s): {}",
                failures, error);
        }
    }

    /** True while the service is unreachable or in maintenance. */
    public boolean isDegraded() {
        OmceHealth h = health.get();
        return degraded.get() || h == null || !h.isWritable();
    }

    @Nullable
    public OmceHealth getHealth() {
        return health.get();
    }

    /** Currency scale, defaulting to cents before the first handshake completes. */
    public int getMinorUnitDigits() {
        OmceHealth h = health.get();
        return h == null ? OmceMoney.DEFAULT_MINOR_UNIT_DIGITS : h.getCurrency().getMinorUnitDigits();
    }

    public String getCurrencySymbol() {
        OmceHealth h = health.get();
        return h == null ? "$" : h.getCurrency().getSymbol();
    }

    private String getCurrencyCode() {
        OmceHealth h = health.get();
        return h == null ? "SUM" : h.getCurrency().getCode();
    }

    // ---------------------------------------------------------------------------------------
    // Player lifecycle
    // ---------------------------------------------------------------------------------------

    /** Resolves and loads a player's balance on login. Never blocks the caller. */
    public void onPlayerLogin(EntityPlayerMP player) {
        if (player == null || !running.get()) {
            return;
        }
        UUID uuid = player.getUniqueID();
        String name = player.getName();
        submit(() -> {
            Map<UUID, String> names = new HashMap<>();
            names.put(uuid, name);
            resolveAndLoad(Collections.singletonList(uuid), names);
        }, "login:" + name);
    }

    public void onPlayerLogout(EntityPlayerMP player) {
        if (player != null) {
            // Keep the entry briefly rather than dropping it: a relog within the refresh window
            // then shows the right balance immediately instead of a zero.
            cache.remove(player.getUniqueID());
        }
    }

    private void resolveAndLoad(Collection<UUID> uuids) {
        resolveAndLoad(uuids, Collections.emptyMap());
    }

    private void resolveAndLoad(Collection<UUID> uuids, Map<UUID, String> names) {
        if (uuids.isEmpty()) {
            return;
        }
        OmceHealth h = health.get();
        boolean create = h != null && h.getCapabilities().autoProvisionsAccounts();
        OmceResult<List<OmceAccount>> resolved = client.resolveAccounts(uuids, names, create);
        if (resolved.isFailure()) {
            onFailure(resolved.getError(), "/resolveAccounts");
            return;
        }
        onSuccess();
        List<UUID> usable = new ArrayList<>();
        for (OmceAccount account : resolved.get()) {
            if (account.isUsable()) {
                cache.putAccountId(account.getPlayerUuid(), account.getAccountId(), account.getStatus());
                usable.add(account.getPlayerUuid());
            } else if (config.isVerboseLogging()) {
                Sum.LOGGER.info("[omce] {} has no usable economy account ({}).",
                    account.getPlayerUuid(), account.getReason());
            }
        }
        loadBalances(usable);
    }

    private void loadBalances(Collection<UUID> uuids) {
        if (uuids.isEmpty()) {
            return;
        }
        for (List<UUID> batch : partition(uuids, batchLimit())) {
            OmceResult<List<OmceBalance>> balances = client.getBalances(batch);
            if (balances.isFailure()) {
                onFailure(balances.getError(), "/getBalances");
                return;
            }
            onSuccess();
            for (OmceBalance balance : balances.get()) {
                cache.applyAuthoritative(balance);
            }
        }
    }

    private int batchLimit() {
        OmceHealth h = health.get();
        return h == null ? 50 : h.getLimits().getMaxBatchAccounts();
    }

    private void refreshStaleBalances() {
        if (isDegraded()) {
            // Still try the health check (scheduled separately); no point sweeping balances while
            // the service is down.
            return;
        }
        long ttl = TimeUnit.SECONDS.toMillis(
            Math.max(1, SumConfig.getEconomyApiBalanceCacheTtlSeconds()));
        List<UUID> stale = cache.findStale(onlinePlayerUuids(), ttl);
        if (!stale.isEmpty()) {
            loadBalances(stale);
        }
    }

    private List<UUID> onlinePlayerUuids() {
        List<UUID> uuids = new ArrayList<>();
        if (server.getPlayerList() != null) {
            for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                uuids.add(player.getUniqueID());
            }
        }
        return uuids;
    }

    // ---------------------------------------------------------------------------------------
    // Change feed
    // ---------------------------------------------------------------------------------------

    private void pollEvents() {
        OmceHealth h = health.get();
        if (h == null || !h.getCapabilities().hasEvents() || isDegraded()) {
            return;
        }
        OmceResult<OmceEventPage> result = client.getEvents(eventCursor.get(), EVENT_PAGE_LIMIT);
        if (result.isFailure()) {
            OmceError error = result.getError();
            if (error != null && OmceProtocol.ERR_CURSOR_EXPIRED.equals(error.getCode())) {
                // Our cursor is too old to serve. The cache can no longer be trusted, so drop it
                // and rebuild from scratch rather than serving balances that may have moved.
                Sum.LOGGER.warn("[omce] Event cursor expired; discarding cached balances and "
                    + "re-reading every online player.");
                eventCursor.set(null);
                cache.clear();
                executor.execute(() -> resolveAndLoad(onlinePlayerUuids()));
                return;
            }
            onFailure(error, "/getEvents");
            return;
        }
        onSuccess();
        OmceEventPage page = result.get();
        for (OmceEventPage.Event event : page.getEvents()) {
            if (event.isBalanceChange()) {
                OmceBalance balance = event.getBalance();
                if (cache.applyAuthoritative(balance) && event.getReason() != null) {
                    notifyPlayer(event.getPlayerUuid(), TextFormatting.GREEN + event.getReason());
                }
            }
        }
        if (page.getNextCursor() != null) {
            eventCursor.set(page.getNextCursor());
        }
        if (page.hasMore()) {
            // Drain promptly rather than waiting a whole poll interval per page.
            executor.execute(safely(this::pollEvents));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Reads (game thread, cache only)
    // ---------------------------------------------------------------------------------------

    /**
     * The player's balance in dollars, from cache. Safe on the server thread; never hits the
     * network.
     *
     * @return the balance, or {@link Double#NaN} if nothing is cached for this player yet.
     */
    public double getCachedBalanceDollars(UUID playerUuid) {
        OmceBalanceCache.Entry entry = cache.get(playerUuid);
        if (entry == null || entry.getAccountId() == null) {
            return Double.NaN;
        }
        return OmceMoney.toDollars(entry.getEffectiveBalance(), getMinorUnitDigits());
    }

    /** True when a usable account is cached for this player. */
    public boolean hasAccount(UUID playerUuid) {
        OmceBalanceCache.Entry entry = cache.get(playerUuid);
        return entry != null && entry.getAccountId() != null;
    }

    public OmceBalanceCache getCache() {
        return cache;
    }

    // ---------------------------------------------------------------------------------------
    // Writes
    // ---------------------------------------------------------------------------------------

    /**
     * Dispatches a transaction and invokes {@code callback} with the outcome on the <b>server
     * thread</b>.
     *
     * <p>This is the path irreversible effects must use: the callback fires only after the service
     * has answered, so a caller can hand over items strictly on {@code committed}.
     *
     * @param request built once; its idempotency key is reused across every internal retry.
     * @param callback receives the result; always invoked exactly once.
     */
    public void processTransaction(OmceTransactionRequest request,
        Consumer<OmceResult<OmceTransactionResult>> callback) {
        if (!running.get()) {
            complete(callback, OmceResult.fail(OmceProtocol.ERR_NOT_CONNECTED,
                "The economy client is not running.", false));
            return;
        }
        boolean accepted = submit(() -> {
            OmceResult<OmceTransactionResult> result = dispatch(request);
            applyResultToCache(result);
            complete(callback, result);
        }, "tx:" + request.getType());
        if (!accepted) {
            complete(callback, OmceResult.fail(OmceProtocol.ERR_SERVICE_UNAVAILABLE,
                "The economy request queue is full.", true));
        }
    }

    /**
     * Sends a transaction, resolving accounts and recovering an ambiguous outcome.
     *
     * <p>The recovery step is the important one: a timeout does not mean the transaction failed,
     * only that we did not hear the answer. Asking {@code /getTransaction} for our idempotency key
     * is the only way to tell "never applied" from "applied but the reply was lost", and treating
     * the latter as the former is how players get charged twice.
     */
    private OmceResult<OmceTransactionResult> dispatch(OmceTransactionRequest request) {
        OmceTransactionRequest resolved = attachAccountIds(request);
        environment.advanceSequence();
        OmceResult<OmceTransactionResult> result =
            client.processTransaction(resolved, getCurrencyCode(), null);

        if (result.isFailure()) {
            OmceError error = result.getError();
            boolean ambiguous = error != null
                && (OmceProtocol.ERR_TRANSPORT.equals(error.getCode())
                    || OmceProtocol.ERR_BAD_RESPONSE.equals(error.getCode()));
            if (ambiguous) {
                OmceResult<OmceTransactionResult> recovered =
                    client.getTransactionByKey(request.getIdempotencyKey());
                if (recovered.isOk()) {
                    Sum.LOGGER.warn("[omce] Transaction {} was applied despite a transport failure; "
                        + "recovered via /getTransaction.", request.getIdempotencyKey());
                    return recovered;
                }
                OmceError recoveryError = recovered.getError();
                if (recoveryError != null
                    && OmceProtocol.ERR_TRANSACTION_NOT_FOUND.equals(recoveryError.getCode())) {
                    // Definitive: never applied. Report the original failure to the caller.
                    return result;
                }
                // Genuinely unknown. Say so rather than implying the money did not move.
                Sum.LOGGER.error("[omce] Transaction {} has an UNKNOWN outcome — the service could "
                    + "not be reached and its status could not be confirmed. Reconcile manually.",
                    request.getIdempotencyKey());
            }
            onFailure(error, "/processTransaction");
            return result;
        }
        onSuccess();
        return result;
    }

    /** Attaches cached account ids to whichever parties are players, without changing the key. */
    private OmceTransactionRequest attachAccountIds(OmceTransactionRequest request) {
        String sourceId = accountIdOf(request.getSource().getPlayerUuid());
        String destId = accountIdOf(request.getDestination().getPlayerUuid());
        return (sourceId == null && destId == null)
            ? request
            : request.withResolvedAccounts(sourceId, destId);
    }

    @Nullable
    private String accountIdOf(@Nullable UUID uuid) {
        OmceBalanceCache.Entry entry = cache.get(uuid);
        return entry == null ? null : entry.getAccountId();
    }

    private void applyResultToCache(OmceResult<OmceTransactionResult> result) {
        if (result.isOk()) {
            for (OmceBalance balance : result.get().getBalances()) {
                cache.applyAuthoritative(balance);
            }
            return;
        }
        // A rejection may still carry authoritative balances (the spec recommends it on
        // INSUFFICIENT_FUNDS), which is exactly what a stale cache needs.
        OmceError error = result.getError();
        if (error != null && error.getDetails() != null) {
            long balance = error.detailLong("balance", Long.MIN_VALUE);
            if (balance != Long.MIN_VALUE && config.isVerboseLogging()) {
                Sum.LOGGER.info("[omce] Rejection reported balance {}; a refresh will follow.", balance);
            }
        }
    }

    /**
     * Reverses a committed transaction, for when an in-world step fails after the money moved.
     *
     * <p>Prefers {@code /voidTransaction} so the ledger records an explicit reversal linked to the
     * original. Services that do not offer it get a compensating transaction with the parties
     * swapped instead — the money ends up in the right place either way, the audit trail is just
     * less explicit.
     *
     * @param original the committed transaction to reverse.
     * @param player the player to make whole; used for the compensating path and messaging.
     */
    public void refund(OmceTransaction original, EntityPlayer player, String reason) {
        if (original == null || !running.get()) {
            return;
        }
        OmceHealth h = health.get();
        boolean canVoid = h != null && h.getCapabilities().canVoid();
        UUID uuid = player == null ? null : player.getUniqueID();
        String name = player == null ? "" : player.getName();

        submit(() -> {
            OmceResult<OmceTransactionResult> result;
            if (canVoid) {
                result = client.voidTransaction(original.getTransactionId(), reason,
                    UUID.randomUUID().toString());
            } else if (uuid != null) {
                OmceTransactionRequest compensating = OmceTransactionRequest
                    .builder(original.getType(), original.getAmount(),
                        OmceParty.system("reversal.sum"),
                        OmceParty.player(uuid, name,
                            accountIdOf(uuid)))
                    .reason(reason)
                    .meta("reverses", original.getTransactionId())
                    .build();
                result = dispatch(compensating);
            } else {
                return;
            }
            if (result.isFailure()) {
                // Nothing further we can do automatically; make sure an operator can find it.
                Sum.LOGGER.error("[omce] FAILED to reverse transaction {} for {} ({}): {}. The "
                    + "player was charged but not served — refund manually.",
                    original.getTransactionId(), name, reason, result.getError());
                notifyPlayer(uuid, TextFormatting.RED + "A refund could not be processed "
                    + "automatically. Please contact an administrator.");
                return;
            }
            applyResultToCache(result);
            Sum.LOGGER.info("[omce] Reversed transaction {} for {} ({}).",
                original.getTransactionId(), name, reason);
        }, "refund:" + original.getTransactionId());
    }

    /** Sends a chat line to a player, hopping to the server thread first. */
    private void notifyPlayer(@Nullable UUID uuid, String message) {
        if (uuid == null) {
            return;
        }
        server.addScheduledTask(() -> {
            if (server.getPlayerList() == null) {
                return;
            }
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(uuid);
            if (player != null) {
                player.sendMessage(new TextComponentString(message));
            }
        });
    }

    /** Runs {@code callback} on the server thread so callers never touch game state off-thread. */
    private <T> void complete(Consumer<T> callback, T value) {
        if (callback == null) {
            return;
        }
        server.addScheduledTask(() -> {
            try {
                callback.accept(value);
            } catch (Throwable t) {
                Sum.LOGGER.error("[omce] An economy callback threw.", t);
            }
        });
    }

    /** @return false when the queue is full and the work was not accepted. */
    private boolean submit(Runnable task, String label) {
        if (!running.get()) {
            return false;
        }
        try {
            executor.execute(safely(task));
            return true;
        } catch (java.util.concurrent.RejectedExecutionException e) {
            Sum.LOGGER.warn("[omce] Economy request queue is full; dropped '{}'.", label);
            return false;
        }
    }

    private static <T> List<List<T>> partition(Collection<T> items, int size) {
        List<List<T>> batches = new ArrayList<>();
        List<T> current = new ArrayList<>(size);
        for (T item : items) {
            current.add(item);
            if (current.size() >= size) {
                batches.add(current);
                current = new ArrayList<>(size);
            }
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }

    /** Party types this service settles, for diagnostics. */
    public Set<String> getSettledPartyTypes() {
        OmceHealth h = health.get();
        return h == null ? new LinkedHashSet<>() : h.getSettledPartyTypes();
    }
}
