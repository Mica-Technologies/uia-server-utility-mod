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
import com.micatechnologies.minecraft.sum.huds.ActiveEffectsHud;
import com.micatechnologies.minecraft.sum.huds.ArmourHud;
import com.micatechnologies.minecraft.sum.huds.BankBalanceHud;
import com.micatechnologies.minecraft.sum.huds.BiomeHud;
import com.micatechnologies.minecraft.sum.huds.BlockAboveHud;
import com.micatechnologies.minecraft.sum.huds.BorderDistanceHud;
import com.micatechnologies.minecraft.sum.huds.ClickCounterHud;
import com.micatechnologies.minecraft.sum.huds.CoordsHud;
import com.micatechnologies.minecraft.sum.huds.CpsHud;
import com.micatechnologies.minecraft.sum.huds.CustomTextHud;
import com.micatechnologies.minecraft.sum.huds.DayCounterHud;
import com.micatechnologies.minecraft.sum.huds.DirectionHud;
import com.micatechnologies.minecraft.sum.huds.DurabilityHud;
import com.micatechnologies.minecraft.sum.huds.FpsHud;
import com.micatechnologies.minecraft.sum.huds.FtiHud;
import com.micatechnologies.minecraft.sum.huds.GameModeHud;
import com.micatechnologies.minecraft.sum.huds.HealthHud;
import com.micatechnologies.minecraft.sum.huds.HeightLimitHud;
import com.micatechnologies.minecraft.sum.huds.HungerHud;
import com.micatechnologies.minecraft.sum.huds.JobsHud;
import com.micatechnologies.minecraft.sum.huds.LightLevelHud;
import com.micatechnologies.minecraft.sum.huds.LookingAtBlockHud;
import com.micatechnologies.minecraft.sum.huds.LoyaltyHud;
import com.micatechnologies.minecraft.sum.huds.MemoryHud;
import com.micatechnologies.minecraft.sum.huds.NearestRoamerHud;
import com.micatechnologies.minecraft.sum.huds.PhoneNumberHud;
import com.micatechnologies.minecraft.sum.huds.PingHud;
import com.micatechnologies.minecraft.sum.huds.PitchHud;
import com.micatechnologies.minecraft.sum.huds.PlaceCountHud;
import com.micatechnologies.minecraft.sum.huds.PlaytimeHud;
import com.micatechnologies.minecraft.sum.huds.PlotInfoHud;
import com.micatechnologies.minecraft.sum.huds.PocketTextHud;
import com.micatechnologies.minecraft.sum.huds.RealLifeDateHud;
import com.micatechnologies.minecraft.sum.huds.ResourcePackHud;
import com.micatechnologies.minecraft.sum.huds.SaturationHud;
import com.micatechnologies.minecraft.sum.huds.ServerIpHud;
import com.micatechnologies.minecraft.sum.huds.SpeedHud;
import com.micatechnologies.minecraft.sum.huds.TimeHud;
import com.micatechnologies.minecraft.sum.huds.TpsHud;
import com.micatechnologies.minecraft.sum.huds.WalletHud;
import com.micatechnologies.minecraft.sum.huds.XpHud;
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
     * Favorites preview HUD — top three SUM favorites painted as a compact icon row
     * with the favorites-tab keybind letter in the upper-right corner. Position,
     * scale, and the standard OneConfig background/border knobs are managed by the
     * inherited {@link cc.polyfrost.oneconfig.hud.BasicHud}.
     *
     * <p>Field name is historical — this used to be a pocket-inventory preview.
     * Kept as {@code pocketHud} so user-saved positions migrate transparently.</p>
     */
    @HUD(name = "Favorites HUD",
         category = "Favorites",
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

    @HUD(name = "Looking-At Block", category = "HUDs", subcategory = "Information")
    public LookingAtBlockHud lookingAtBlockHud = new LookingAtBlockHud();

    @HUD(name = "Light Level", category = "HUDs", subcategory = "Information")
    public LightLevelHud lightLevelHud = new LightLevelHud();

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

    @HUD(name = "Health", category = "HUDs", subcategory = "Player")
    public HealthHud healthHud = new HealthHud();

    @HUD(name = "Hunger", category = "HUDs", subcategory = "Player")
    public HungerHud hungerHud = new HungerHud();

    @HUD(name = "Experience", category = "HUDs", subcategory = "Player")
    public XpHud xpHud = new XpHud();

    @HUD(name = "Active Effects", category = "HUDs", subcategory = "Player")
    public ActiveEffectsHud activeEffectsHud = new ActiveEffectsHud();

    @HUD(name = "Tool Durability", category = "HUDs", subcategory = "Player")
    public DurabilityHud durabilityHud = new DurabilityHud();

    // Performance HUDs

    @HUD(name = "Memory", category = "HUDs", subcategory = "Performance")
    public MemoryHud memoryHud = new MemoryHud();

    @HUD(name = "Ping", category = "HUDs", subcategory = "Performance")
    public PingHud pingHud = new PingHud();

    @HUD(name = "TPS", category = "HUDs", subcategory = "Performance")
    public TpsHud tpsHud = new TpsHud();

    @HUD(name = "Frame Time", category = "HUDs", subcategory = "Performance")
    public FtiHud ftiHud = new FtiHud();

    // SUM-specific HUDs — read from SUM's own subsystems (money capability, phone
    // cloud, pocket inventory, roamer entities, world border). These are the HUDs
    // no other mod could produce because the source data only exists inside SUM.

    @HUD(name = "Wallet", category = "HUDs", subcategory = "SUM")
    public WalletHud walletHud = new WalletHud();

    @HUD(name = "Phone Number", category = "HUDs", subcategory = "SUM")
    public PhoneNumberHud phoneNumberHud = new PhoneNumberHud();

    @HUD(name = "Pocket (text)", category = "HUDs", subcategory = "SUM")
    public PocketTextHud pocketTextHud = new PocketTextHud();

    @HUD(name = "Nearest Roamer", category = "HUDs", subcategory = "SUM")
    public NearestRoamerHud nearestRoamerHud = new NearestRoamerHud();

    @HUD(name = "Border Distance", category = "HUDs", subcategory = "SUM")
    public BorderDistanceHud borderDistanceHud = new BorderDistanceHud();

    // Snapshot-driven SUM HUDs — read from PlayerStatusTracker.latest, which is
    // populated every 2 s by an S→C PacketSyncPlayerStatus (channel id 14). These
    // are the HUDs whose source data lives in server-only saved-data so they need
    // the snapshot bridge to be visible client-side at all.

    @HUD(name = "Bank Balance", category = "HUDs", subcategory = "SUM")
    public BankBalanceHud bankBalanceHud = new BankBalanceHud();

    @HUD(name = "Plot Info", category = "HUDs", subcategory = "SUM")
    public PlotInfoHud plotInfoHud = new PlotInfoHud();

    @HUD(name = "Jobs Available", category = "HUDs", subcategory = "SUM")
    public JobsHud jobsHud = new JobsHud();

    @HUD(name = "Loyalty", category = "HUDs", subcategory = "SUM")
    public LoyaltyHud loyaltyHud = new LoyaltyHud();

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

    // Ten fixed custom-text slots. Each is independently positioned, scaled, and
    // shown/hidden via OneConfig's standard HUD UI; the @Text option on each lets the
    // user paint whatever string they want — including %placeholder% tokens like
    // %player%, %x%, %y%, %z%, %dim%, %biome%, %server%, %time%, %realtime%, %date%,
    // %fps%, %direction%. See TextPlaceholders for the full catalog.
    //
    // A fixed-N pattern (rather than the upstream dynamic HudList) keeps the OneConfig
    // wire-up simple — no custom option type for dynamic-sized lists. With placeholder
    // substitution, ten slots cover ~all realistic use cases since one slot can render
    // arbitrarily many live values.

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

    @HUD(name = "Custom Text 6", category = "HUDs", subcategory = "Custom Text")
    public CustomTextHud customText6 = new CustomTextHud();

    @HUD(name = "Custom Text 7", category = "HUDs", subcategory = "Custom Text")
    public CustomTextHud customText7 = new CustomTextHud();

    @HUD(name = "Custom Text 8", category = "HUDs", subcategory = "Custom Text")
    public CustomTextHud customText8 = new CustomTextHud();

    @HUD(name = "Custom Text 9", category = "HUDs", subcategory = "Custom Text")
    public CustomTextHud customText9 = new CustomTextHud();

    @HUD(name = "Custom Text 10", category = "HUDs", subcategory = "Custom Text")
    public CustomTextHud customText10 = new CustomTextHud();

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

    // === Server Config Viewer ===
    // Read-only mirror of the server's sum.cfg state. The server pushes a snapshot on
    // PlayerLoggedInEvent (see ServerConfigBridge); the client caches it in
    // ServerConfigMirror; this button opens GuiServerConfigViewer to display it.
    //
    // Why a Button + GuiScreen instead of @Info fields: OneConfig's @Info text is
    // resolved at compile time (annotation literal), so we can't surface dynamic
    // server-side values through it. Custom GuiScreen avoids the limitation while
    // keeping the entry point inside OneConfig where admins look first.

    @Button(name = "Open Server Config Viewer", text = "Open",
        category = "Server Config", subcategory = "Mirror")
    public void openServerConfigViewer() {
        net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
            new com.micatechnologies.minecraft.sum.serverconfig.GuiServerConfigViewer());
    }

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
            "Cityscape Photographer", "Explorer 2 (SUM)", "Combat Pro"
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
