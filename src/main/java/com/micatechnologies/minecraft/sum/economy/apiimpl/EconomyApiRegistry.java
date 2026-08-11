package com.micatechnologies.minecraft.sum.economy.apiimpl;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.api.EconomyHandle;
import com.micatechnologies.minecraft.sum.api.EconomyScope;
import com.micatechnologies.minecraft.sum.api.EconomyStatus;
import com.micatechnologies.minecraft.sum.api.SumEconomy;
import com.micatechnologies.minecraft.sum.bank.BankService;
import com.micatechnologies.minecraft.sum.economy.EconomyBridge;
import com.micatechnologies.minecraft.sum.omceapi.service.OmceEconomyService;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/**
 * Serves {@link SumEconomy#acquire} — the implementation behind the public API's entry point.
 *
 * <p>Installed when the server starts and removed when it stops, for the same reason the remote
 * economy client is: the economy backend is chosen from the live server, and a single-player
 * client that opens a second world must not inherit the first world's handles.
 *
 * <p>Handles are cached per mod id and never expire. That is safe because a handle carries no
 * authority of its own — {@link EconomyHandleImpl} re-reads its scopes from config on every call —
 * so a cached handle whose mod has since been revoked simply fails every operation.
 */
public final class EconomyApiRegistry implements SumEconomy.Provider {

    private static final EconomyApiRegistry INSTANCE = new EconomyApiRegistry();

    private final Map<String, EconomyHandleImpl> handles = new ConcurrentHashMap<>();

    /** Mod ids already warned about, so a mod retrying acquire every tick cannot flood the log. */
    private final Set<String> denialsLogged = ConcurrentHashMap.newKeySet();

    private EconomyApiRegistry() {}

    /** Makes the economy API live. Called on server start. */
    public static void install() {
        INSTANCE.handles.clear();
        INSTANCE.denialsLogged.clear();
        SumEconomy.installProvider(INSTANCE);
        EscrowService.onServerStart();
        Map<String, Set<EconomyScope>> allowed =
            com.micatechnologies.minecraft.sum.SumConfig.getEconomyIntegrationAllowedMods();
        if (allowed.isEmpty()) {
            Sum.LOGGER.info("[economy-api] ready. No mods are authorized; add entries to "
                + "economy_integration.allowedMods to let other mods use SUM's economy.");
        } else {
            Sum.LOGGER.info("[economy-api] ready. {} authorized mod(s): {}", allowed.size(),
                allowed.keySet());
        }
    }

    /** Takes the economy API offline and drops cached handles. Called on server stop. */
    public static void uninstall() {
        SumEconomy.installProvider(null);
        EscrowService.onServerStop();
        INSTANCE.handles.clear();
        INSTANCE.denialsLogged.clear();
    }

    /**
     * Clears the remembered denials after a config reload, so a mod that has just been authorized
     * is announced rather than staying silently suppressed.
     *
     * <p>Cached handles are deliberately kept: their scopes are read live, so an existing handle
     * picks up the new grants immediately and a mod need not re-acquire.
     */
    public static void onConfigReloaded() {
        INSTANCE.denialsLogged.clear();
        // A mod may have just been revoked, which orphans anything it was still holding.
        EscrowService.onConfigReloaded();
        Sum.LOGGER.info("[economy-api] authorization reloaded; {} authorized mod(s): {}",
            com.micatechnologies.minecraft.sum.SumConfig.getEconomyIntegrationAllowedMods().size(),
            com.micatechnologies.minecraft.sum.SumConfig.getEconomyIntegrationAllowedMods()
                .keySet());
    }

    /** Every mod that has taken a handle this session, for {@code /sum econ api mods}. */
    public static Set<String> getAcquiredModIds() {
        return java.util.Collections.unmodifiableSet(INSTANCE.handles.keySet());
    }

    @Override
    public Optional<EconomyHandle> acquire(String modId) {
        String normalised = EconomyAuthorizer.normalise(modId);
        if (!EconomyAuthorizer.isAuthorized(normalised)) {
            if (denialsLogged.add(normalised)) {
                Sum.LOGGER.warn("[economy-api] denied economy access to '{}'. {}", normalised,
                    EconomyAuthorizer.describeDenial(normalised));
            }
            return Optional.empty();
        }
        EconomyHandleImpl handle = handles.computeIfAbsent(normalised, id -> {
            Sum.LOGGER.info("[economy-api] granted economy access to '{}' with {}", id,
                EconomyAuthorizer.scopesFor(id));
            return new EconomyHandleImpl(id);
        });
        return Optional.of(handle);
    }

    @Override
    @Nullable
    public String describeDenial(String modId) {
        return EconomyAuthorizer.describeDenial(modId);
    }

    @Override
    public EconomyStatus getStatus() {
        return currentStatus();
    }

    /**
     * A snapshot of the live economy.
     *
     * <p>Availability tracks the <b>wallet</b> backend, because that is what every purchase spends
     * and what an integration is most likely to want. The bank is described separately: it is
     * always present in some form — a remote service when one is configured, the world save
     * otherwise — so the useful facts about it are whether it is remote and whether it is healthy.
     */
    static EconomyStatus currentStatus() {
        if (!EconomyBridge.isAvailable()) {
            return EconomyStatus.unavailable();
        }
        OmceEconomyService remote = EconomyBridge.getRemoteService();
        boolean degraded = remote != null && remote.isDegraded();
        String symbol = remote != null ? remote.getCurrencySymbol() : "$";
        return EconomyStatus.available(BankService.isRemote(), degraded, symbol,
            BankService.getMinorUnitDigits());
    }
}
