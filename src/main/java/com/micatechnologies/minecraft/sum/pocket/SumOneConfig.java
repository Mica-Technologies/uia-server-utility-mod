package com.micatechnologies.minecraft.sum.pocket;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.HUD;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.data.ModType;

/**
 * Root OneConfig entry-point for SUM. Owns all client-side preference fields and
 * registered HUD modules. Loaded once on the client during {@code SumClientProxy.preInit};
 * persists to {@code config/sum/sum.json} via OneConfig's storage backend.
 *
 * <p>Server-side settings (roamer walkable blocks, roadrunner multipliers, world border,
 * etc.) intentionally remain in the Forge {@code Configuration} system — OneConfig is
 * client-only, so storing server settings here would either let any client edit them
 * locally without the server picking up the change, or require a server-config-sync
 * packet that we don't have. The OneConfig page itself is gated to singleplayer-or-op
 * by {@link PocketKeybinds} as a defense-in-depth UX measure.</p>
 *
 * <p>HUD modules are declared as {@code @HUD}-annotated fields; OneConfig auto-discovers
 * them and exposes drag-to-reposition + per-HUD knobs through its standard UI.</p>
 */
public class SumOneConfig extends Config {

    /** Singleton — referenced by {@code SumClientProxy.preInit} so the static-initializer
     *  fires once at startup; subsequent code reads HUD/option fields directly. */
    public static SumOneConfig INSTANCE;

    /**
     * Pocket HUD module — three filtered slots (phone / debit card / bills) painted as a
     * compact icon row. Position, scale, and the standard OneConfig background/border
     * knobs are managed by the inherited {@link cc.polyfrost.oneconfig.hud.BasicHud}.
     */
    @HUD(name = "Pocket HUD",
         category = "Pocket",
         subcategory = "HUD")
    public PocketHud pocketHud = new PocketHud();

    public SumOneConfig() {
        super(new Mod("Server Utility Mod", ModType.UTIL_QOL), "sum.json");
        INSTANCE = this;
    }
}
