package com.micatechnologies.minecraft.sum.economy;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.omceapi.OmceParty;
import com.micatechnologies.minecraft.sum.omceapi.OmceProtocol;
import com.micatechnologies.minecraft.sum.omceapi.service.OmceEconomyService;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Loader;

/**
 * Unified facade for SUM's balance operations. Internally routes between three backends, in
 * priority order:
 *
 * <ol>
 *   <li><b>Open MCEconomic API</b> (preferred when configured and connected) - a remote service
 *       owns balances and the ledger; SUM reads a cache it keeps fresh. See
 *       {@link OmceEconomyService}.</li>
 *   <li><b>EconomyInc</b> (when loaded) - reflection-only access to the mod's {@code IMoney}
 *       capability.</li>
 *   <li><b>SUM</b> (fallback) - SUM's own {@link ISumMoney} capability attached to every
 *       player.</li>
 * </ol>
 *
 * <p>Callers always go through {@link #getBalance}, {@link #adjustBalance}, and
 * {@link #isAvailable}; they never need to know which backend is active.
 *
 * <p><b>A note on the remote backend.</b> The local backends mutate a balance synchronously and
 * cannot fail after the fact. The remote one cannot: HTTP must not run on the server thread, so
 * {@link #adjustBalance} applies the change to a local cache and settles it in the background.
 * The boolean it returns therefore means "accepted, and the service will be asked", not
 * "committed". Call sites that grant an irreversible in-world effect — handing over bills,
 * transferring items, assigning ownership — should use
 * {@link OmceEconomyService#processTransaction}, whose callback fires only once the service has
 * confirmed. {@link #adjustBalance} remains correct for reversible effects.
 *
 * <p>EconomyInc 1.6.2 signatures locked via javap on the production jar:
 * <ul>
 *   <li>{@code IMoney.getMoney()} returns {@code double}</li>
 *   <li>{@code IMoney.setMoney(double)}</li>
 *   <li>{@code IMoney.sync(EntityPlayer)} pushes server changes to the client</li>
 *   <li>{@code CapabilityLoading.getMoneyHandler(Entity)} static helper wraps the
 *       capability dance with {@code EnumFacing.DOWN}.</li>
 * </ul>
 */
public final class EconomyBridge {

    public static final String MOD_ID = "economy";

    private static final String CAPABILITY_LOADING_CLASS = "fr.fifou.economy.capability.CapabilityLoading";
    private static final String IMONEY_CLASS = "fr.fifou.economy.capability.IMoney";

    private static final boolean MOD_PRESENT;
    private static final Method GET_MONEY_HANDLER;
    private static final Method GET_MONEY;
    private static final Method SET_MONEY;
    private static final Method SYNC;

    static {
        boolean present = Loader.isModLoaded(MOD_ID);
        Method getHandler = null;
        Method get = null;
        Method set = null;
        Method sync = null;
        if (present) {
            try {
                Class<?> capabilityLoading = Class.forName(CAPABILITY_LOADING_CLASS);
                Class<?> entityClass = Class.forName("net.minecraft.entity.Entity");
                Class<?> imoney = Class.forName(IMONEY_CLASS);
                Class<?> playerClass = Class.forName("net.minecraft.entity.player.EntityPlayer");
                getHandler = capabilityLoading.getMethod("getMoneyHandler", entityClass);
                get = imoney.getMethod("getMoney");
                set = imoney.getMethod("setMoney", double.class);
                sync = imoney.getMethod("sync", playerClass);
                Sum.LOGGER.info("[economy] EconomyInc bridge bound; SUM money capability will stay inert.");
            } catch (Throwable t) {
                Sum.LOGGER.warn("[economy] EconomyInc is loaded but the bridge could not bind; "
                    + "falling back to SUM's own money capability.", t);
                getHandler = null;
                get = null;
                set = null;
                sync = null;
            }
        } else {
            Sum.LOGGER.info("[economy] EconomyInc is not loaded; SUM's own money capability will own the balance.");
        }
        MOD_PRESENT = present;
        GET_MONEY_HANDLER = getHandler;
        GET_MONEY = get;
        SET_MONEY = set;
        SYNC = sync;
    }

    private EconomyBridge() {}

    /**
     * The remote economy client, or null when the API is disabled or this side does not own the
     * connection. Volatile because it is set on the server thread at start-up and read from
     * everywhere.
     */
    private static volatile OmceEconomyService remote;

    /** Installs the remote backend. Called from {@code Sum.serverStarting}. */
    public static void setRemoteService(@Nullable OmceEconomyService service) {
        remote = service;
        if (service != null) {
            Sum.LOGGER.info("[economy] Open MCEconomic API backend active; it takes priority over "
                + "EconomyInc and SUM's local capability.");
        }
    }

    /** Removes the remote backend, e.g. on server stop. */
    public static void clearRemoteService() {
        remote = null;
    }

    /** True when a connected remote economy service owns balances. */
    public static boolean isRemoteBackend() {
        OmceEconomyService service = remote;
        return service != null && service.isRunning();
    }

    /** The remote client, or null when it is not the active backend. */
    @Nullable
    public static OmceEconomyService getRemoteService() {
        return isRemoteBackend() ? remote : null;
    }

    /** True if the EconomyInc reflection bridge bound successfully and the mod is loaded. */
    public static boolean isEconomyIncBackend() {
        return MOD_PRESENT && GET_MONEY_HANDLER != null
            && GET_MONEY != null && SET_MONEY != null;
    }

    /** True if SUM's own money capability has been registered. Becomes true after
     *  {@code Sum.preInit} completes. */
    public static boolean isSumBackend() {
        return CapabilitySumMoney.CAPABILITY != null;
    }

    /** True if any backend can answer balance queries. Used by features that need to
     *  decide whether to show "Economy mod required" or proceed with the operation. */
    public static boolean isAvailable() {
        return isRemoteBackend() || isEconomyIncBackend() || isSumBackend();
    }

    @Nullable
    private static Object resolveEconomyIncHandler(EntityPlayer player) {
        if (!isEconomyIncBackend() || player == null) {
            return null;
        }
        try {
            return GET_MONEY_HANDLER.invoke(null, player);
        } catch (Throwable t) {
            Sum.LOGGER.warn("[economy] Failed to resolve IMoney handler for {}", player.getName(), t);
            return null;
        }
    }

    /**
     * @return the player's current balance in dollars, or {@link Double#NaN} if no backend can
     *     answer (mod absent and SUM capability missing, or both reflection paths failed).
     */
    public static double getBalance(EntityPlayer player) {
        if (player == null) return Double.NaN;
        OmceEconomyService service = getRemoteService();
        if (service != null) {
            // Cache read only — never network. This runs on the server thread and, on a physical
            // client, on the render thread from GUIs.
            return service.getCachedBalanceDollars(player.getUniqueID());
        }
        if (isEconomyIncBackend()) {
            Object handler = resolveEconomyIncHandler(player);
            if (handler != null) {
                try {
                    Object value = GET_MONEY.invoke(handler);
                    return value instanceof Number ? ((Number) value).doubleValue() : Double.NaN;
                } catch (Throwable t) {
                    Sum.LOGGER.warn("[economy] EconomyInc getBalance failed for {}", player.getName(), t);
                }
            }
            return Double.NaN;
        }
        if (isSumBackend()) {
            ISumMoney money = player.getCapability(CapabilitySumMoney.CAPABILITY, null);
            if (money != null) {
                return money.getBalance();
            }
        }
        return Double.NaN;
    }

    /**
     * Adjusts the player's balance by {@code delta}. Refuses to overdraft (returns false).
     * Triggers a balance sync to the player's client on success so the in-game GUIs see the
     * new value on the next frame.
     *
     * @return true on success; false if no backend is available or the result would go negative.
     */
    public static boolean adjustBalance(EntityPlayer player, double delta) {
        // No transaction context supplied, so the remote ledger gets a generic classification.
        // Prefer the overload below wherever the feature is known; a ledger of "adjustment"
        // entries is technically correct and practically useless to an operator auditing it.
        return adjustBalance(player, delta, null, null, null);
    }

    /**
     * Adjusts the player's balance, telling the remote ledger what the money was for.
     *
     * <p>The extra arguments are ignored by the local backends, which have no ledger — they exist
     * so a remote service records a shop purchase as a shop purchase rather than an unexplained
     * adjustment.
     *
     * @param transactionType an {@code OmceProtocol.TX_*} constant, or null for a generic entry.
     * @param counterparty the other side of the movement (the shop, job, plot, cash, or system
     *     sink), or null to use an unclassified system counterparty.
     * @param reason short human-readable description for the audit log; may be null.
     * @return true on success. With the remote backend this means "accepted and dispatched" —
     *     see this class's javadoc.
     */
    public static boolean adjustBalance(EntityPlayer player, double delta,
        @Nullable String transactionType, @Nullable OmceParty counterparty,
        @Nullable String reason) {
        return adjustBalance(player, delta, transactionType, counterparty, reason, null);
    }

    /**
     * As above, with a callback for the one failure mode only the remote backend has.
     *
     * <p>Under the remote backend this method returns before the service has answered, so a
     * caller that granted something on the strength of that {@code true} needs a way to undo it if
     * the service later refuses. {@code onRejected} runs on the server thread in that case.
     *
     * <p>It is only useful where the grant is server-side state the caller can actually revert —
     * plot ownership, a job listing. Where the grant is an item in a player's hands there is
     * nothing reliable to undo (they may have dropped, stashed, or consumed it), so those call
     * sites must instead wait for confirmation via
     * {@link OmceEconomyService#processTransaction}. The local backends cannot fail after
     * returning true, so they ignore this argument.
     *
     * @param onRejected undo action, or null if the caller has nothing to revert.
     */
    public static boolean adjustBalance(EntityPlayer player, double delta,
        @Nullable String transactionType, @Nullable OmceParty counterparty,
        @Nullable String reason, @Nullable Runnable onRejected) {
        if (player == null) return false;
        OmceEconomyService service = getRemoteService();
        if (service != null) {
            boolean credit = delta >= 0.0;
            String type = transactionType != null ? transactionType
                : (credit ? OmceProtocol.TX_ADMIN_CREDIT : OmceProtocol.TX_ADMIN_DEBIT);
            OmceParty other = counterparty != null ? counterparty
                : OmceParty.system(credit ? "faucet.sum" : "sink.sum");
            return service.adjustBalanceOptimistic(player, delta, type, other, reason, onRejected);
        }
        return adjustLocalBalance(player, delta);
    }

    /** The pre-existing EconomyInc / SUM-capability path, unchanged. */
    private static boolean adjustLocalBalance(EntityPlayer player, double delta) {
        if (isEconomyIncBackend()) {
            Object handler = resolveEconomyIncHandler(player);
            if (handler == null) return false;
            try {
                double current = ((Number) GET_MONEY.invoke(handler)).doubleValue();
                double next = current + delta;
                if (next < 0.0) return false;
                SET_MONEY.invoke(handler, next);
                if (player instanceof EntityPlayerMP && SYNC != null) {
                    SYNC.invoke(handler, player);
                }
                return true;
            } catch (Throwable t) {
                Sum.LOGGER.warn("[economy] EconomyInc adjustBalance failed for {}", player.getName(), t);
                return false;
            }
        }
        if (isSumBackend()) {
            ISumMoney money = player.getCapability(CapabilitySumMoney.CAPABILITY, null);
            if (money != null && money.adjust(delta)) {
                money.sync(player);
                return true;
            }
        }
        return false;
    }
}
