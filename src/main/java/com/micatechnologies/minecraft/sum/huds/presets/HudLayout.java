package com.micatechnologies.minecraft.sum.huds.presets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable description of a HUD layout — which SUM HUDs are visible, where, and how
 * they stack relative to a column anchor. Coordinates are in 1080p reference space;
 * OneConfig anchors them by thirds at apply time so they scale correctly across
 * resolutions.
 *
 * <p>Layouts are expressed as a list of {@link Column}s. A column declares an anchor
 * point (x, y) and a {@link Direction}; HUDs listed in the column are stacked
 * outwards from the anchor with row pitch computed from the current style's scale +
 * padding (see {@code HudPresets.computeRowPitch}). That responsive row pitch is the
 * whole point of the column abstraction — fixed y coordinates would leave gigantic
 * gaps at small scales and overlap at large scales.</p>
 *
 * <p>For one-shot placements (a single HUD that isn't part of a stacked group), use
 * {@link Builder#place} — it just adds a one-row column.</p>
 *
 * <pre>{@code
 *     HudLayout.builder("Explorer")
 *         .place("coordsHud", 960f, 10f)                                       // solo
 *         .column(10f,   1050f, Direction.UP,  "dayCounterHud", "timeHud",
 *                                              "directionHud", "speedHud")     // stack bottom-up
 *         .column(1920f, 1050f, Direction.UP,  "fpsHud", "ftiHud", "tpsHud",
 *                                              "memoryHud", "playtimeHud")     // stack bottom-up
 *         .build();
 * }</pre>
 *
 * Anything not added via {@code .place} or {@code .column} is implicitly disabled
 * when this layout is applied.
 */
public final class HudLayout {

    /** Which way rows extend from the column anchor. */
    public enum Direction {
        /** Row 0 sits at the anchor, row 1 sits one pitch below it, etc. */
        DOWN,
        /** Row 0 sits at the anchor, row 1 sits one pitch above it, etc. */
        UP
    }

    public final String name;
    public final List<Column> columns;

    private HudLayout(String name, List<Column> columns) {
        this.name = name;
        this.columns = columns;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** A vertical stack of HUDs anchored at (x, anchorY) and extending {@link #direction}. */
    public static final class Column {
        public final float x;
        public final float anchorY;
        public final Direction direction;
        public final List<String> hudNames;

        public Column(float x, float anchorY, Direction direction, List<String> hudNames) {
            this.x = x;
            this.anchorY = anchorY;
            this.direction = direction;
            this.hudNames = hudNames;
        }
    }

    public static final class Builder {
        private final String name;
        private final List<Column> columns = new ArrayList<>();

        private Builder(String name) {
            this.name = name;
        }

        /**
         * Add a single-HUD "column" — sugar for an isolated placement that doesn't
         * stack with anything else. Direction is irrelevant since the column has
         * only one row, so it's left as DOWN by convention.
         */
        public Builder place(String hudFieldName, float x, float y) {
            columns.add(new Column(x, y, Direction.DOWN,
                Collections.singletonList(hudFieldName)));
            return this;
        }

        /**
         * Add a multi-row stacked column anchored at {@code (x, anchorY)}. The first
         * HUD name sits at the anchor; subsequent names extend along
         * {@code direction}. Row pitch is computed at apply time from each HUD's
         * current scale, so the column auto-tightens at small font scales and
         * spreads at large ones.
         */
        public Builder column(float x, float anchorY, Direction direction,
                              String... hudNames) {
            List<String> names = new ArrayList<>(hudNames.length);
            Collections.addAll(names, hudNames);
            columns.add(new Column(x, anchorY, direction, names));
            return this;
        }

        public HudLayout build() {
            return new HudLayout(name, Collections.unmodifiableList(columns));
        }
    }
}
