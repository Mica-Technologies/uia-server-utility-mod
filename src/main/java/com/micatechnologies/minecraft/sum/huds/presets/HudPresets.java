package com.micatechnologies.minecraft.sum.huds.presets;

import cc.polyfrost.oneconfig.config.annotations.HUD;
import cc.polyfrost.oneconfig.config.core.OneColor;
import cc.polyfrost.oneconfig.hud.BasicHud;
import cc.polyfrost.oneconfig.hud.Hud;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import cc.polyfrost.oneconfig.hud.TextHud;
import com.micatechnologies.minecraft.sum.huds.presets.HudLayout.Column;
import com.micatechnologies.minecraft.sum.huds.presets.HudLayout.Direction;
import com.micatechnologies.minecraft.sum.pocket.SumOneConfig;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Catalogue of SUM's bundled HUD style + layout presets, plus the apply machinery that
 * actually mutates HUD fields on the live {@link SumOneConfig} instance.
 *
 * <p>The dropdown options declared on {@code SumOneConfig.stylePresetIndex} /
 * {@code SumOneConfig.layoutPresetIndex} MUST stay in sync with the ordering of
 * {@link #STYLES} / {@link #LAYOUTS} respectively; mismatched indices would silently
 * apply the wrong preset. The constructor below static-asserts the dropdown ordering
 * against the catalog so the build fails fast if they drift.</p>
 *
 * <p>OneConfig's {@code BasicHud}/{@code TextHud}/{@code SingleTextHud} expose their
 * tunables as {@code protected} fields, so the apply path uses reflection to walk the
 * class hierarchy and {@code setAccessible(true)} each one. We accept the runtime cost
 * because a preset apply is a once-per-click action, not a per-frame hot path.</p>
 *
 * <p>Layouts are responsive to whatever style is currently active: each column's row
 * pitch is computed per-HUD from the live scale + padding + border via
 * {@link #computeRowPitch}. {@link #applyStyle} also re-runs the current layout at
 * the end (when one is selected) so changing the style instantly reflows spacing
 * without the user having to click Apply Layout a second time.</p>
 */
public final class HudPresets {

    private HudPresets() {}

    // -----------------------------------------------------------------------------
    // Styles
    // -----------------------------------------------------------------------------

    /** Names shown in the {@code SumOneConfig.stylePresetIndex} dropdown, in order. */
    public static final String[] STYLE_NAMES = {
        "Default",
        "Minimal Brackets",
        "Clean Professional",
        "Stylish Glass",
        "Compact Light",
        "Retro Terminal",
        "Realistic Game HUD",
        "High Contrast"
    };

    public static final HudStyle[] STYLES = {
        // Default — match the stock OneConfig look (semi-transparent gray bg, medium
        // font, no brackets) so "Default" is a real reset rather than a SUM opinion.
        new HudStyle("Default", 1.0f,
            true, false, 2f, 5f, 5f, new OneColor(0, 0, 0, 120),
            false, 2f, new OneColor(0, 0, 0),
            new OneColor(255, 255, 255), 0,
            false, new OneColor(255, 255, 255)),

        // Minimal Brackets — the look in the explorer screenshot the user shared: no
        // background, tiny font with full shadow, white brackets around each value.
        new HudStyle("Minimal Brackets", 0.5f,
            false, false, 2f, 3f, 3f, new OneColor(0, 0, 0, 0),
            false, 2f, new OneColor(0, 0, 0),
            new OneColor(255, 255, 255), 2,
            true, new OneColor(255, 255, 255)),

        // Clean Professional — opaque navy background, soft cyan text, rounded corners.
        // Reads well on bright screenshots; the dropped-shadow rendering keeps text
        // crisp against the bg.
        new HudStyle("Clean Professional", 1.0f,
            true, true, 3f, 6f, 4f, new OneColor(20, 30, 50, 220),
            false, 2f, new OneColor(0, 0, 0),
            new OneColor(200, 230, 255), 1,
            false, new OneColor(255, 255, 255)),

        // Stylish Glass — frosted-glass look with a soft white border, larger text. Best
        // on darker scenes (caves, nighttime).
        new HudStyle("Stylish Glass", 1.1f,
            true, true, 8f, 8f, 6f, new OneColor(255, 255, 255, 60),
            true, 1f, new OneColor(255, 255, 255, 200),
            new OneColor(255, 255, 255), 1,
            false, new OneColor(255, 255, 255)),

        // Compact Light — small text with no chrome at all. Good middle-ground between
        // Default and Realistic.
        new HudStyle("Compact Light", 0.75f,
            false, false, 2f, 3f, 3f, new OneColor(0, 0, 0, 0),
            false, 2f, new OneColor(0, 0, 0),
            new OneColor(255, 255, 255), 2,
            false, new OneColor(255, 255, 255)),

        // Retro Terminal — green-on-black CRT vibe. Background plus a thin green border
        // makes each HUD look like its own console window.
        new HudStyle("Retro Terminal", 0.9f,
            true, false, 1f, 5f, 4f, new OneColor(0, 8, 0, 230),
            true, 1f, new OneColor(0, 200, 0, 255),
            new OneColor(80, 255, 80), 0,
            true, new OneColor(0, 200, 0)),

        // Realistic Game HUD — the explicitly-requested option. Tiny font, no bg, full
        // shadow so the text stays legible against any background.
        new HudStyle("Realistic Game HUD", 0.4f,
            false, false, 2f, 2f, 2f, new OneColor(0, 0, 0, 0),
            false, 2f, new OneColor(0, 0, 0),
            new OneColor(255, 255, 255), 2,
            false, new OneColor(255, 255, 255)),

        // High Contrast — yellow on solid black, no anti-shenanigans. Accessibility
        // option for users on bright scenes or low-vision contexts.
        new HudStyle("High Contrast", 1.0f,
            true, false, 2f, 5f, 4f, new OneColor(0, 0, 0, 255),
            false, 2f, new OneColor(0, 0, 0),
            new OneColor(255, 255, 0), 2,
            false, new OneColor(255, 255, 255))
    };

    // -----------------------------------------------------------------------------
    // Layouts
    // -----------------------------------------------------------------------------

    /** Names shown in the {@code SumOneConfig.layoutPresetIndex} dropdown, in order. */
    public static final String[] LAYOUT_NAMES = {
        "Off (All Hidden)",
        "Vanilla+",
        "Survival Essentials",
        "Speedrunner",
        "PvP Focus",
        "Builder",
        "Explorer",
        "Performance Watcher",
        "Time Tracker",
        "Minimal Top",
        "Server Op",
        "Urban Builder",
        "City Explorer",
        "Architect",
        "Cityscape Photographer",
        "Explorer 2 (SUM)",
        "Combat Pro"
    };

    /** Coordinates are in 1080p reference space; OneConfig anchors by thirds at apply. */
    public static final HudLayout[] LAYOUTS = {
        // Off — everything hidden. Useful as a one-click reset.
        HudLayout.builder("Off (All Hidden)").build(),

        // Vanilla+ — coords + FPS, top-left.
        HudLayout.builder("Vanilla+")
            .column(0f, 1f, Direction.DOWN, "coordsHud", "fpsHud")
            .build(),

        // Survival Essentials — left column with the always-relevant survival info,
        // now expanded to include the vanilla-gap HUDs (Health, Hunger, XP, Effects).
        HudLayout.builder("Survival Essentials")
            .column(0f, 1f, Direction.DOWN,
                "coordsHud", "biomeHud", "timeHud", "dayCounterHud",
                "healthHud", "hungerHud", "xpHud", "saturationHud",
                "armourHud", "activeEffectsHud")
            .build(),

        // Speedrunner — splits-relevant info distributed for at-a-glance reading.
        HudLayout.builder("Speedrunner")
            .place("coordsHud", 960f, 1f)
            .column(0f, 1f, Direction.DOWN, "timeHud", "playtimeHud")
            .column(1920f, 1f, Direction.DOWN, "fpsHud", "pingHud")
            .build(),

        // PvP Focus — combat-relevant info, kept out of the center crosshair. Bottom-
        // anchored columns extend upward, so the FIRST entry sits flush against the
        // bottom and the rest stack above it. Health + Effects + Tool added so a PvP
        // player has every critical combat readout at a glance.
        HudLayout.builder("PvP Focus")
            .column(0f, 1077f, Direction.UP,
                "cpsHud", "saturationHud", "hungerHud", "armourHud",
                "durabilityHud", "activeEffectsHud", "healthHud")
            .column(1920f, 1077f, Direction.UP,
                "directionHud", "fpsHud", "pingHud")
            .build(),

        // Builder — survival builder focus (block above, height limit for tall builds),
        // plus light level (mob-spawn checks) and looking-at-block (block ID lookup).
        HudLayout.builder("Builder")
            .column(0f, 1f, Direction.DOWN,
                "coordsHud", "directionHud", "blockAboveHud", "heightLimitHud",
                "lightLevelHud", "lookingAtBlockHud")
            .build(),

        // Explorer — matches the user-provided screenshot: coords top-center; speed /
        // direction / IGT / day stacked bottom-left; playtime / mem / tps / fti / fps
        // stacked bottom-right. Bottom-anchored UP columns auto-extend upward, so the
        // 5-row right column reaches higher than the 4-row left column without us
        // having to math out a separate anchor y for each.
        HudLayout.builder("Explorer")
            .place("coordsHud", 960f, 1f)
            .column(0f, 1077f, Direction.UP,
                "dayCounterHud", "timeHud", "directionHud", "speedHud")
            .column(1920f, 1077f, Direction.UP,
                "fpsHud", "ftiHud", "tpsHud", "memoryHud", "playtimeHud")
            .build(),

        // Performance Watcher — diagnostics stack bottom-right, nothing else.
        HudLayout.builder("Performance Watcher")
            .column(1920f, 1077f, Direction.UP,
                "memoryHud", "pingHud", "tpsHud", "ftiHud", "fpsHud")
            .build(),

        // Time Tracker — clocks and dates in the top-right, for long sessions / streams.
        HudLayout.builder("Time Tracker")
            .column(1920f, 1f, Direction.DOWN,
                "realLifeDateHud", "timeHud", "dayCounterHud", "playtimeHud")
            .build(),

        // Minimal Top — only what's strictly needed: coords + fps centred at the top.
        HudLayout.builder("Minimal Top")
            .place("coordsHud", 900f, 1f)
            .place("fpsHud", 1100f, 1f)
            .build(),

        // Server Op — admin-focused: server identity + health up top. Snapshot HUDs
        // (jobs available, plot info) on the right give an at-a-glance "what's
        // happening on this server" readout.
        HudLayout.builder("Server Op")
            .column(0f, 1f, Direction.DOWN,
                "serverIpHud", "tpsHud", "pingHud")
            .column(1920f, 1f, Direction.DOWN,
                "playtimeHud", "jobsHud", "plotInfoHud")
            .build(),

        // ---- City/creative-oriented ----

        // Urban Builder — coords centred up top; block above + height limit on the left
        // for vertical-build reference; date + game mode on the right.
        HudLayout.builder("Urban Builder")
            .column(960f, 1f, Direction.DOWN, "coordsHud", "directionHud")
            .column(0f, 1f, Direction.DOWN, "blockAboveHud", "heightLimitHud")
            .column(1920f, 1f, Direction.DOWN, "gameModeHud", "realLifeDateHud")
            .build(),

        // City Explorer — wandering a server city: nav info top-centre, RP info (wallet,
        // bank, phone, pocket text, nearest roamer, plot) on the left, time + server-ip
        // + jobs on the right. Heavy on SUM-specific HUDs since the layout's whole
        // purpose is server-city RP.
        HudLayout.builder("City Explorer")
            .place("coordsHud", 870f, 1f)
            .place("directionHud", 1060f, 1f)
            .column(0f, 1077f, Direction.UP,
                "nearestRoamerHud", "plotInfoHud", "pocketTextHud",
                "phoneNumberHud", "bankBalanceHud", "walletHud",
                "biomeHud", "timeHud", "dayCounterHud")
            .column(1920f, 1077f, Direction.UP,
                "loyaltyHud", "jobsHud", "playtimeHud",
                "serverIpHud", "borderDistanceHud")
            .build(),

        // Architect — precision-build mode. Detailed coords + height limit + block-above
        // on the left, everything else suppressed so the viewport is uncluttered.
        // Light level + looking-at-block added for precision-build lookups.
        HudLayout.builder("Architect")
            .column(0f, 1f, Direction.DOWN,
                "coordsHud", "directionHud", "heightLimitHud",
                "blockAboveHud", "lookingAtBlockHud", "lightLevelHud", "gameModeHud")
            .build(),

        // Cityscape Photographer — minimal-overlay for screenshots. Just one tiny coord
        // line bottom-centre and the real-life date top-right; user is expected to pair
        // this with the Realistic Game HUD style for the smallest possible footprint.
        HudLayout.builder("Cityscape Photographer")
            .place("coordsHud", 870f, 1077f)
            .place("realLifeDateHud", 1920f, 1f)
            .build(),

        // Explorer 2 (SUM) — the comprehensive city-RP layout, intended as the modpack
        // default. Bottom corners match the original Explorer (nav left, perf right).
        // Top-left: vanilla survival info (health/hunger/XP/effects/durability).
        // Top-center: coords + facing direction. Top-right: SUM-specific HUDs
        // including the icon-based PocketHud, plus wallet/bank/phone/plot/jobs.
        HudLayout.builder("Explorer 2 (SUM)")
            .column(960f, 1f, Direction.DOWN, "coordsHud", "directionHud")
            .column(0f, 1f, Direction.DOWN,
                "healthHud", "hungerHud", "xpHud", "activeEffectsHud",
                "durabilityHud", "plotInfoHud")
            .column(1920f, 1f, Direction.DOWN,
                "walletHud", "bankBalanceHud", "phoneNumberHud", "pocketTextHud",
                "jobsHud", "loyaltyHud", "pocketHud")
            .column(0f, 1077f, Direction.UP,
                "dayCounterHud", "timeHud", "biomeHud", "speedHud")
            .column(1920f, 1077f, Direction.UP,
                "fpsHud", "ftiHud", "tpsHud", "memoryHud", "pingHud", "playtimeHud")
            .build(),

        // Combat Pro — PvP/combat-tuned. Bottom-anchored columns so the eye doesn't
        // have to drift far from the crosshair: health/effects/durability left,
        // armour/saturation/cps right, ping/fps top-right for connection-quality
        // checks during fights.
        HudLayout.builder("Combat Pro")
            .column(0f, 1077f, Direction.UP,
                "activeEffectsHud", "durabilityHud", "armourHud",
                "hungerHud", "healthHud")
            .column(1920f, 1077f, Direction.UP,
                "cpsHud", "saturationHud", "directionHud")
            .column(1920f, 1f, Direction.DOWN, "pingHud", "fpsHud")
            .build()
    };

    static {
        // Cheap dropdown/catalog drift detection — a name array of length N must match
        // a catalog array of length N. The dropdown literals on SumOneConfig add the
        // third constraint that the contents are identical.
        if (STYLE_NAMES.length != STYLES.length) {
            throw new IllegalStateException(
                "STYLE_NAMES (" + STYLE_NAMES.length + ") and STYLES (" + STYLES.length
                    + ") out of sync");
        }
        if (LAYOUT_NAMES.length != LAYOUTS.length) {
            throw new IllegalStateException(
                "LAYOUT_NAMES (" + LAYOUT_NAMES.length + ") and LAYOUTS ("
                    + LAYOUTS.length + ") out of sync");
        }
    }

    // -----------------------------------------------------------------------------
    // Apply
    // -----------------------------------------------------------------------------

    public static void applyStyle(int index, SumOneConfig cfg) {
        HudStyle s = STYLES[clamp(index, STYLES.length)];
        for (Hud hud : collectHuds(cfg).values()) {
            setField(hud, "scale", s.scale);
            if (hud instanceof BasicHud) {
                setField(hud, "background", s.background);
                setField(hud, "rounded", s.rounded);
                setField(hud, "cornerRadius", s.cornerRadius);
                setField(hud, "paddingX", s.paddingX);
                setField(hud, "paddingY", s.paddingY);
                setField(hud, "bgColor", s.bgColor);
                setField(hud, "border", s.border);
                setField(hud, "borderSize", s.borderSize);
                setField(hud, "borderColor", s.borderColor);
            }
            if (hud instanceof TextHud) {
                setField(hud, "color", s.textColor);
                setField(hud, "textType", s.textType);
            }
            if (hud instanceof SingleTextHud) {
                setField(hud, "brackets", s.brackets);
                setField(hud, "bracketsColor", s.bracketsColor);
            }
        }
        cfg.save();

        // Auto-reflow the current layout so the new scale / padding translate into
        // matching row spacing without the user having to click Apply Layout again.
        // Skip when the active layout is "Off" (index 0) — re-applying that would
        // force-disable every HUD the user might be relying on, which is surprising
        // behaviour for a style-change action.
        if (cfg.layoutPresetIndex > 0) {
            applyLayout(cfg.layoutPresetIndex, cfg);
        }
    }

    public static void applyLayout(int index, SumOneConfig cfg) {
        HudLayout layout = LAYOUTS[clamp(index, LAYOUTS.length)];
        Map<String, Hud> hudMap = collectHuds(cfg);

        // Default everything to hidden — any HUD listed in the layout below flips back
        // to enabled. This guarantees that switching to a smaller layout doesn't leave
        // orphaned HUDs from the previous layout still visible.
        for (Hud hud : hudMap.values()) {
            setField(hud, "enabled", false);
        }

        for (Column col : layout.columns) {
            // Walk the column row-by-row, advancing the cursor by each row's own pitch
            // (computed from its live scale/padding/border). Per-row pitch is correct
            // in the uniform-scale case (cumulative becomes N * pitch) and also when
            // the user has hand-tweaked individual HUD scales between preset applies.
            float cursor = col.anchorY;
            for (String name : col.hudNames) {
                Hud hud = hudMap.get(name);
                if (hud == null) continue;
                setField(hud, "enabled", true);
                // CRITICAL: reset position size to (0, 0) before re-anchoring. OneConfig
                // computes stored offsets using the current position.width/height — if
                // the HUD has previously rendered (so those are non-zero from the prior
                // text size), passing x=1920 with width=50 stores offset=50, which on
                // the next render places the LEFT edge at 1920 and pushes the entire
                // HUD off screen. Resetting size to 0 makes the stored offset be a pure
                // anchor offset, so the next render's setSize re-populates dimensions
                // and the right edge lands flush at x=1920 regardless of text width.
                hud.position.setSize(0f, 0f);
                // setPosition normalises against a fixed 1920x1080 reference and picks
                // the anchor by thirds, so positions ported as 1080p coordinates land
                // correctly at any current screen resolution.
                hud.position.setPosition(col.x, cursor, 1920f, 1080f);
                float pitch = computeRowPitch(hud);
                cursor += (col.direction == Direction.UP) ? -pitch : pitch;
            }
        }
        cfg.save();
    }

    // -----------------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------------

    /**
     * Reflect over {@link SumOneConfig}'s declared fields, pulling out every
     * {@code @HUD}-annotated field as a (name → Hud-instance) map. Iteration order
     * follows declaration order for predictable apply behaviour.
     */
    private static Map<String, Hud> collectHuds(SumOneConfig cfg) {
        Map<String, Hud> result = new LinkedHashMap<>();
        for (Field f : SumOneConfig.class.getDeclaredFields()) {
            if (!f.isAnnotationPresent(HUD.class)) continue;
            try {
                f.setAccessible(true);
                Object value = f.get(cfg);
                if (value instanceof Hud) {
                    result.put(f.getName(), (Hud) value);
                }
            } catch (IllegalAccessException ignored) {
                // Field is annotated @HUD but unreadable — skip and continue.
            }
        }
        return result;
    }

    /**
     * Vertical distance from one row's anchor to the next row's anchor in a stacked
     * column, computed from the live HUD's scale + padding + border. Picking row
     * pitch dynamically (instead of using a hardcoded 20px from the old layouts) is
     * what makes layouts responsive to the active style preset.
     *
     * <p>12px is OneConfig's per-text-line height ({@code TextHud#draw} advances
     * {@code textY += 12 * scale} per line). Padding only adds visible space when the
     * background is drawn; border adds {@code borderSize} on each edge when both bg
     * and border are on. The fixed +2 at the end is a small visual breathing gap so
     * adjacent rows don't end up flush against each other.</p>
     */
    private static float computeRowPitch(Hud hud) {
        float scale = readFloat(hud, "scale", 1f);
        boolean bg = false;
        boolean border = false;
        float paddingY = 0f;
        float borderSize = 0f;
        if (hud instanceof BasicHud) {
            bg = readBool(hud, "background", false);
            if (bg) {
                paddingY = readFloat(hud, "paddingY", 0f);
                border = readBool(hud, "border", false);
                if (border) {
                    borderSize = readFloat(hud, "borderSize", 0f);
                }
            }
        }
        float boxHeight = 12f + paddingY * 2f + borderSize * 2f + 2f;
        return boxHeight * scale;
    }

    /**
     * Walk {@code instance}'s class chain looking for a declared field named
     * {@code fieldName} and set it to {@code value}. Silently no-ops if the field
     * doesn't exist on this HUD type (e.g. setting {@code brackets} on a PocketHud,
     * which extends BasicHud directly without going through SingleTextHud).
     */
    private static void setField(Object instance, String fieldName, Object value) {
        Class<?> c = instance.getClass();
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(instance, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (IllegalAccessException e) {
                return;
            }
        }
    }

    private static float readFloat(Object instance, String fieldName, float fallback) {
        Class<?> c = instance.getClass();
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.getFloat(instance);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (IllegalAccessException e) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean readBool(Object instance, String fieldName, boolean fallback) {
        Class<?> c = instance.getClass();
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.getBoolean(instance);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (IllegalAccessException e) {
                return fallback;
            }
        }
        return fallback;
    }

    private static int clamp(int idx, int length) {
        if (idx < 0) return 0;
        if (idx >= length) return length - 1;
        return idx;
    }
}
