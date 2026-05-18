package com.micatechnologies.minecraft.sum.pocket;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.HUD;
import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.data.ModType;
import com.micatechnologies.minecraft.sum.huds.BiomeHud;
import com.micatechnologies.minecraft.sum.huds.CoordsHud;
import com.micatechnologies.minecraft.sum.huds.DayCounterHud;
import com.micatechnologies.minecraft.sum.huds.DirectionHud;
import com.micatechnologies.minecraft.sum.huds.FpsHud;
import com.micatechnologies.minecraft.sum.huds.TimeHud;

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

    // Text HUDs, modeled on EvergreenHUD's element catalog. Each is a thin SingleTextHud
    // subclass that exposes its own per-element knobs (color the title, hide an axis,
    // etc.) via its own annotated fields — OneConfig auto-surfaces those under the HUD's
    // settings page so we don't need to wire them here.

    @HUD(name = "Coordinates", category = "HUDs", subcategory = "Information")
    public CoordsHud coordsHud = new CoordsHud();

    @HUD(name = "FPS", category = "HUDs", subcategory = "Performance")
    public FpsHud fpsHud = new FpsHud();

    @HUD(name = "Facing Direction", category = "HUDs", subcategory = "Information")
    public DirectionHud directionHud = new DirectionHud();

    @HUD(name = "Clock", category = "HUDs", subcategory = "Information")
    public TimeHud timeHud = new TimeHud();

    @HUD(name = "Biome", category = "HUDs", subcategory = "Information")
    public BiomeHud biomeHud = new BiomeHud();

    @HUD(name = "Day Counter", category = "HUDs", subcategory = "Information")
    public DayCounterHud dayCounterHud = new DayCounterHud();

    // === Migrated client preferences ===
    // Server-side configuration (roamer walkable blocks, roadrunner multipliers, world
    // border, etc.) intentionally stays in the Forge Configuration file because OneConfig
    // is client-only and we don't have a server→client config-sync system. Only true
    // client-side preferences move here.

    /**
     * Whether to render the small gold star in the upper-right of every favorited slot in
     * the creative inventory. Migrated from SumConfig's favorites.enableStarOverlay knob —
     * the Forge config file is read once on load if the OneConfig file doesn't exist yet,
     * so existing installs keep their setting on the first OneConfig boot.
     */
    @Switch(name = "Favorites Star Overlay", category = "Favorites")
    public static boolean favoritesStarOverlay = true;

    public SumOneConfig() {
        super(new Mod("Server Utility Mod", ModType.UTIL_QOL), "sum.json");
        INSTANCE = this;
    }
}
