package com.micatechnologies.minecraft.sum.economy.apiimpl;

import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.api.EconomyScope;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Decides what an integrating mod is allowed to do, from the server's
 * {@code economy_integration.allowedMods} config.
 *
 * <p>Deny by default: a mod that is not listed gets an empty scope set and no handle. That is the
 * whole rule — the interesting part is explaining a denial well enough that an operator can fix it
 * without reading source, which is what {@link #describeDenial} is for.
 *
 * <p><b>This is an operator control, not a security boundary.</b> Mod ids are self-declared and
 * taken at face value; nothing here stops a mod calling SUM's internals directly, and nothing
 * inside a shared JVM could. What it buys is revocability without a code change, a loud failure
 * for a mod that never asked, and an attributable mod id on every transaction.
 */
public final class EconomyAuthorizer {

    private EconomyAuthorizer() {}

    /** Normalises a mod id the same way the config parser does, so lookups agree. */
    public static String normalise(@Nullable String modId) {
        return modId == null ? "" : modId.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Scopes granted to a mod. Empty for anything unlisted, so callers can treat the result as
     * the authoritative answer without a separate authorization test.
     */
    public static Set<EconomyScope> scopesFor(@Nullable String modId) {
        return SumConfig.getEconomyIntegrationScopes(normalise(modId));
    }

    /** True when the mod holds at least one scope. */
    public static boolean isAuthorized(@Nullable String modId) {
        return !scopesFor(modId).isEmpty();
    }

    /**
     * Why this mod would be refused a handle, phrased for a server log.
     *
     * <p>Includes the exact config line to add, because the alternative is an operator reading
     * "not authorized" and having to go find out what the syntax is and which scopes exist.
     *
     * @return null when the mod is authorized.
     */
    @Nullable
    public static String describeDenial(@Nullable String modId) {
        String normalised = normalise(modId);
        if (normalised.isEmpty()) {
            return "No mod id was given.";
        }
        if (isAuthorized(normalised)) {
            return null;
        }
        return "'" + normalised + "' is not listed in economy_integration.allowedMods, so it "
            + "cannot use SUM's economy. To allow it, add a line such as \"" + normalised
            + "=wallet_read,wallet_write\" to that list in SUM's config and restart, or run "
            + "'/sum econ api reload' after editing. Scopes: wallet_read, wallet_write, bank_read, "
            + "bank_write, escrow, or * for all.";
    }
}
