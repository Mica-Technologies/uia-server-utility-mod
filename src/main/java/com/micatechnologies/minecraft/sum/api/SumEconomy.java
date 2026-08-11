package com.micatechnologies.minecraft.sum.api;

import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Entry point to SUM's economy for other mods.
 *
 * <p>Ask for a handle once, keep it, and use it for everything:
 *
 * <pre>{@code
 * Optional<EconomyHandle> maybe = SumEconomy.acquire("mycasino");
 * if (!maybe.isPresent()) {
 *     LOGGER.warn("SUM economy unavailable: {}", SumEconomy.describeDenial("mycasino"));
 *     return;
 * }
 * EconomyHandle economy = maybe.get();
 * }</pre>
 *
 * <p>Acquire during or after {@code FMLServerStartingEvent} — the economy backend is selected when
 * the server starts, so a handle taken in {@code preInit} would be asking too early.
 *
 * <h2>Authorization</h2>
 *
 * <p>A server operator must list your mod in {@code economy_integration.allowedMods} and grant it
 * scopes, e.g. {@code mycasino=wallet_read,wallet_write,escrow}. The list is empty by default, so
 * <b>a fresh server denies every integration until someone opts it in</b>. A denial is logged with
 * the exact config line needed to fix it.
 *
 * <p><b>This is an operator control, not a security boundary.</b> It exists so an operator can
 * revoke one integration without a code change, so a mod that forgets to ask fails loudly instead
 * of half-working, and so every transaction carries an attributable mod id. It is a backstop, and
 * it does not and cannot stop a mod that is determined to reach into SUM's internals directly —
 * nothing running inside the same JVM could. Do not present it to operators as a sandbox.
 *
 * <h2>Depending on SUM</h2>
 *
 * <p>Compile against the {@code -api} jar and declare a hard dependency, so your mod is not loaded
 * into a world without an economy to talk to:
 *
 * <pre>{@code @Mod(modid = "mycasino", dependencies = "required-after:sum")}</pre>
 *
 * <h2>Versioning</h2>
 *
 * <p>{@link #API_VERSION} increases when this package changes in a way existing consumers would
 * notice. Additive changes do not bump it. Check it with {@link #isCompatible(int)} if you want to
 * fail loudly on an unexpectedly old SUM rather than hit a {@link NoSuchMethodError} later.
 */
public final class SumEconomy {

    /** The API revision this SUM build serves. See the versioning note in the class docs. */
    public static final int API_VERSION = 1;

    /** Set once by SUM during server start. Volatile because it is read from the server thread
     * after being written during startup. */
    private static volatile Provider provider;

    private SumEconomy() {}

    /**
     * Requests economy access for a mod.
     *
     * @param modId your own mod id, exactly as it appears in {@code @Mod}. Taken at face value —
     *     see the authorization note in the class docs.
     * @return a handle, or empty when the mod is not authorized or SUM's economy is not running.
     *     {@link #describeDenial} explains which.
     */
    public static Optional<EconomyHandle> acquire(String modId) {
        Provider current = provider;
        if (current == null || modId == null || modId.trim().isEmpty()) {
            return Optional.empty();
        }
        return current.acquire(modId.trim());
    }

    /**
     * Why {@link #acquire} would fail for this mod, as a sentence suitable for a log line.
     *
     * @return null when the mod would be granted a handle.
     */
    @Nullable
    public static String describeDenial(String modId) {
        Provider current = provider;
        if (current == null) {
            return "SUM's economy API is not running. It starts with the server, so this is normal "
                    + "before FMLServerStartingEvent, and otherwise means SUM has no economy "
                    + "backend attached.";
        }
        if (modId == null || modId.trim().isEmpty()) {
            return "No mod id was given.";
        }
        return current.describeDenial(modId.trim());
    }

    /** True when an economy backend is attached and mutations can succeed. */
    public static boolean isEconomyAvailable() {
        Provider current = provider;
        return current != null && current.getStatus().isAvailable();
    }

    /**
     * What the economy can currently do. Safe to call at any time; reports
     * {@link EconomyStatus#unavailable()} before the server has started.
     */
    public static EconomyStatus getStatus() {
        Provider current = provider;
        return current != null ? current.getStatus() : EconomyStatus.unavailable();
    }

    /** True when SUM serves at least the API version your mod was written against. */
    public static boolean isCompatible(int requiredVersion) {
        return API_VERSION >= requiredVersion;
    }

    /**
     * SUM's own hook for supplying the implementation.
     *
     * <p><b>Not for consumers.</b> It is public only because the implementation lives outside this
     * package, so that the {@code -api} jar stays free of implementation classes.
     */
    public interface Provider {

        Optional<EconomyHandle> acquire(String modId);

        @Nullable
        String describeDenial(String modId);

        EconomyStatus getStatus();
    }

    /**
     * Installs the implementation. Called by SUM on server start.
     *
     * <p>Single-shot per install: replacing a live provider would silently redirect every existing
     * handle, so a second call with a different provider is refused. Passing null clears it, which
     * is what server shutdown does.
     *
     * @throws IllegalStateException if a different provider is already installed.
     */
    public static void installProvider(@Nullable Provider newProvider) {
        Provider current = provider;
        if (newProvider != null && current != null && current != newProvider) {
            throw new IllegalStateException(
                    "A SUM economy provider is already installed; clear it before installing "
                            + "another. This usually means two mods are shading SUM's API package.");
        }
        provider = newProvider;
    }
}
