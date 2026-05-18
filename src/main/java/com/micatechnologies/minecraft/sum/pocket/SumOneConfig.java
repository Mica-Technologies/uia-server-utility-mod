package com.micatechnologies.minecraft.sum.pocket;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.Button;
import cc.polyfrost.oneconfig.config.annotations.Dropdown;
import cc.polyfrost.oneconfig.config.annotations.HUD;
import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.data.ModType;
import com.google.gson.GsonBuilder;
import com.micatechnologies.minecraft.sum.huds.presets.HudPresets;
import java.lang.reflect.Modifier;
import com.micatechnologies.minecraft.sum.huds.ArmourHud;
import com.micatechnologies.minecraft.sum.huds.BiomeHud;
import com.micatechnologies.minecraft.sum.huds.BlockAboveHud;
import com.micatechnologies.minecraft.sum.huds.ClickCounterHud;
import com.micatechnologies.minecraft.sum.huds.CoordsHud;
import com.micatechnologies.minecraft.sum.huds.CpsHud;
import com.micatechnologies.minecraft.sum.huds.CustomTextHud;
import com.micatechnologies.minecraft.sum.huds.DayCounterHud;
import com.micatechnologies.minecraft.sum.huds.DirectionHud;
import com.micatechnologies.minecraft.sum.huds.FpsHud;
import com.micatechnologies.minecraft.sum.huds.FtiHud;
import com.micatechnologies.minecraft.sum.huds.GameModeHud;
import com.micatechnologies.minecraft.sum.huds.HeightLimitHud;
import com.micatechnologies.minecraft.sum.huds.MemoryHud;
import com.micatechnologies.minecraft.sum.huds.PingHud;
import com.micatechnologies.minecraft.sum.huds.PitchHud;
import com.micatechnologies.minecraft.sum.huds.PlaceCountHud;
import com.micatechnologies.minecraft.sum.huds.PlaytimeHud;
import com.micatechnologies.minecraft.sum.huds.RealLifeDateHud;
import com.micatechnologies.minecraft.sum.huds.ResourcePackHud;
import com.micatechnologies.minecraft.sum.huds.SaturationHud;
import com.micatechnologies.minecraft.sum.huds.ServerIpHud;
import com.micatechnologies.minecraft.sum.huds.SpeedHud;
import com.micatechnologies.minecraft.sum.huds.TimeHud;
import com.micatechnologies.minecraft.sum.huds.TpsHud;
import com.micatechnologies.minecraft.sum.huds.YawHud;

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
     *  fires once at startup; subsequent code reads HUD/option fields directly.
     *  {@code transient} is REQUIRED: OneConfig's Gson exclusion strategy only filters
     *  {@code TRANSIENT}, not {@code STATIC}, so without this the self-reference causes
     *  infinite recursion in {@code Config#save()} → StackOverflowError on first launch. */
    public static transient SumOneConfig INSTANCE;

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

    @HUD(name = "Real-Life Date", category = "HUDs", subcategory = "Information")
    public RealLifeDateHud realLifeDateHud = new RealLifeDateHud();

    @HUD(name = "Server IP", category = "HUDs", subcategory = "Information")
    public ServerIpHud serverIpHud = new ServerIpHud();

    @HUD(name = "Resource Pack", category = "HUDs", subcategory = "Information")
    public ResourcePackHud resourcePackHud = new ResourcePackHud();

    @HUD(name = "Game Mode", category = "HUDs", subcategory = "Information")
    public GameModeHud gameModeHud = new GameModeHud();

    @HUD(name = "Block Above", category = "HUDs", subcategory = "Information")
    public BlockAboveHud blockAboveHud = new BlockAboveHud();

    @HUD(name = "Height Limit", category = "HUDs", subcategory = "Information")
    public HeightLimitHud heightLimitHud = new HeightLimitHud();

    // Player-state HUDs

    @HUD(name = "Pitch", category = "HUDs", subcategory = "Player")
    public PitchHud pitchHud = new PitchHud();

    @HUD(name = "Yaw", category = "HUDs", subcategory = "Player")
    public YawHud yawHud = new YawHud();

    @HUD(name = "Speed", category = "HUDs", subcategory = "Player")
    public SpeedHud speedHud = new SpeedHud();

    @HUD(name = "Saturation", category = "HUDs", subcategory = "Player")
    public SaturationHud saturationHud = new SaturationHud();

    @HUD(name = "Armor Durability", category = "HUDs", subcategory = "Player")
    public ArmourHud armourHud = new ArmourHud();

    // Performance HUDs

    @HUD(name = "Memory", category = "HUDs", subcategory = "Performance")
    public MemoryHud memoryHud = new MemoryHud();

    @HUD(name = "Ping", category = "HUDs", subcategory = "Performance")
    public PingHud pingHud = new PingHud();

    @HUD(name = "TPS", category = "HUDs", subcategory = "Performance")
    public TpsHud tpsHud = new TpsHud();

    @HUD(name = "Frame Time", category = "HUDs", subcategory = "Performance")
    public FtiHud ftiHud = new FtiHud();

    // Counter HUDs — backed by HudStateTracker (registered separately in
    // SumClientProxy).

    @HUD(name = "CPS", category = "HUDs", subcategory = "Counters")
    public CpsHud cpsHud = new CpsHud();

    @HUD(name = "Click Counter", category = "HUDs", subcategory = "Counters")
    public ClickCounterHud clickCounterHud = new ClickCounterHud();

    @HUD(name = "Blocks Placed", category = "HUDs", subcategory = "Counters")
    public PlaceCountHud placeCountHud = new PlaceCountHud();

    @HUD(name = "Session Playtime", category = "HUDs", subcategory = "Counters")
    public PlaytimeHud playtimeHud = new PlaytimeHud();

    // Five fixed custom-text slots. Each is independently positioned, scaled, and
    // shown/hidden via OneConfig's standard HUD UI; the @Text option on each lets the
    // user paint whatever string they want. A fixed-N pattern (rather than the upstream
    // dynamic HudList) keeps the OneConfig wire-up simple without porting a custom
    // option type.

    @HUD(name = "Custom Text 1", category = "HUDs", subcategory = "Custom Text")
    public CustomTextHud customText1 = new CustomTextHud();

    @HUD(name = "Custom Text 2", category = "HUDs", subcategory = "Custom Text")
    public CustomTextHud customText2 = new CustomTextHud();

    @HUD(name = "Custom Text 3", category = "HUDs", subcategory = "Custom Text")
    public CustomTextHud customText3 = new CustomTextHud();

    @HUD(name = "Custom Text 4", category = "HUDs", subcategory = "Custom Text")
    public CustomTextHud customText4 = new CustomTextHud();

    @HUD(name = "Custom Text 5", category = "HUDs", subcategory = "Custom Text")
    public CustomTextHud customText5 = new CustomTextHud();

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
    public boolean favoritesStarOverlay = true;

    // === HUD Presets ===
    // Two one-click systems: a "Style Preset" controls how each HUD looks (font scale,
    // background, brackets, etc.), and a "Layout Preset" controls which HUDs are on
    // and where they sit on screen. They're orthogonal — choosing a style does not
    // disturb the layout, and choosing a layout does not disturb the style. After
    // applying, the user can still drag individual HUDs / tweak their per-HUD options
    // freely; another preset apply is what overwrites those tweaks.
    //
    // CRITICAL: the option literals in the @Dropdown(options=...) below MUST stay
    // identical in order to HudPresets.STYLE_NAMES / LAYOUT_NAMES. HudPresets's
    // static initialiser asserts the lengths match; the per-string ordering you have
    // to keep aligned by hand.

    @Dropdown(name = "Style Preset", category = "Presets", subcategory = "Style",
        options = {
            "Default", "Minimal Brackets", "Clean Professional", "Stylish Glass",
            "Compact Light", "Retro Terminal", "Realistic Game HUD", "High Contrast"
        })
    public int stylePresetIndex = 0;

    @Button(name = "Apply Style", text = "Apply", category = "Presets",
        subcategory = "Style")
    public void applyStylePreset() {
        HudPresets.applyStyle(stylePresetIndex, this);
    }

    @Dropdown(name = "Layout Preset", category = "Presets", subcategory = "Layout",
        options = {
            "Off (All Hidden)", "Vanilla+", "Survival Essentials", "Speedrunner",
            "PvP Focus", "Builder", "Explorer", "Performance Watcher", "Time Tracker",
            "Minimal Top", "Server Op", "Urban Builder", "City Explorer", "Architect",
            "Cityscape Photographer"
        })
    public int layoutPresetIndex = 0;

    @Button(name = "Apply Layout", text = "Apply", category = "Presets",
        subcategory = "Layout")
    public void applyLayoutPreset() {
        HudPresets.applyLayout(layoutPresetIndex, this);
    }

    public SumOneConfig() {
        super(new Mod("Server Utility Mod", ModType.UTIL_QOL), "sum.json");
        INSTANCE = this;
        // OneConfig's Config base does NOT auto-initialize; without this call, the @HUD
        // fields below are never scanned, the mod card never appears in the OneConfig
        // GUI, and the HUD editor has nothing to drag. See Config#preload() docs.
        initialize();
    }

    /**
     * Restore Gson's default {@code STATIC | TRANSIENT} field-exclusion mask. OneConfig's
     * base {@link Config#addGsonOptions} drops the {@code STATIC} half, which causes Gson
     * to walk into static helper constants of HUD subclasses ({@code DateTimeFormatter},
     * lookup arrays, etc.) during {@link Config#save()} — some of which contain internal
     * back-references that trigger an infinite recursion / StackOverflowError. Excluding
     * static fields again here is the targeted fix.
     */
    @Override
    protected GsonBuilder addGsonOptions(GsonBuilder builder) {
        return super.addGsonOptions(builder)
            .excludeFieldsWithModifiers(Modifier.STATIC, Modifier.TRANSIENT);
    }
}
