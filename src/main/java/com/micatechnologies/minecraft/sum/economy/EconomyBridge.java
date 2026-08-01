package com.micatechnologies.minecraft.sum.economy;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.omceapi.service.OmceEconomyService;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Loader;

/**
 * Facade for the <b>wallet</b> - the money a player carries and spends in-game. Routes between
 * two backends:
 *
 * <ol>
 *   <li><b>EconomyInc</b> (preferred when loaded) - reflection-only access to the mod's
 *       {@code IMoney} capability.</li>
 *   <li><b>SUM</b> (fallback when EconomyInc is absent) - SUM's own {@link ISumMoney}
 *       capability attached to every player.</li>
 * </ol>
 *
 * <p>Callers always go through {@link #getBalance}, {@link #adjustBalance}, and
 * {@link #isAvailable}; they never need to know which backend is active.
 *
 * <p><b>Both backends are local and synchronous.</b> A wallet lives in the world save, so a
 * purchase settles immediately and cannot be refused after the fact. That is deliberate: shops,
 * plots, job escrow and payments all spend the wallet, and none of them should depend on a
 * network round trip.
 *
 * <p>This class deliberately does <i>not</i> route to a remote economy service. When one is
 * configured it owns the player's <b>bank account</b>, not their wallet - see
 * {@link com.micatechnologies.minecraft.sum.bank.BankService}. Money crosses between the two only
 * at an ATM. {@link #getRemoteService()} is exposed here purely because this class owns that
 * client's lifecycle.
 *
 * <p>This class handles the invisible balance only. For the wallet total including carried bills,
 * use {@link WalletService}.
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
            Sum.LOGGER.info("[economy] Open MCEconomic API active; it owns bank accounts. "
                + "Wallets stay local, so in-game purchases never touch the network.");
        }
    }

    /** Removes the remote backend, e.g. on server stop. */
    public static void clearRemoteService() {
        remote = null;
    }

    /** True when a connected remote economy service owns players' <b>bank accounts</b>.
     *  Wallets are always local, so this never affects {@link #getBalance}. */
    public static boolean isRemoteBackend() {
        OmceEconomyService service = remote;
        return service != null && service.isRunning();
    }

    /** The remote economy client, or null when the bank is backed by the world save. */
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
        return isEconomyIncBackend() || isSumBackend();
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
        if (player == null) return false;
        return adjustLocalBalance(player, delta);
    }

    /** The EconomyInc / SUM-capability path. */
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
