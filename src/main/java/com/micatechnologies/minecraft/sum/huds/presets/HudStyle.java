package com.micatechnologies.minecraft.sum.huds.presets;

import cc.polyfrost.oneconfig.config.core.OneColor;

/**
 * Immutable description of a visual style that can be applied across every SUM HUD —
 * font scale, background, borders, brackets, text rendering. Applied via
 * {@link HudPresets#applyStyle} which uses reflection to reach into the protected
 * fields of OneConfig's {@code Hud} / {@code BasicHud} / {@code TextHud} /
 * {@code SingleTextHud} hierarchy.
 *
 * <p>Field semantics mirror the upstream OneConfig fields one-to-one. {@link #textType}
 * follows OneConfig's dropdown: {@code 0 = No Shadow}, {@code 1 = Shadow},
 * {@code 2 = Full Shadow}.</p>
 */
public final class HudStyle {

    public final String name;
    public final float scale;

    // BasicHud
    public final boolean background;
    public final boolean rounded;
    public final float cornerRadius;
    public final float paddingX;
    public final float paddingY;
    public final OneColor bgColor;
    public final boolean border;
    public final float borderSize;
    public final OneColor borderColor;

    // TextHud (text colour + shadow type)
    public final OneColor textColor;
    public final int textType;

    // SingleTextHud (square-brackets affordance around each value)
    public final boolean brackets;
    public final OneColor bracketsColor;

    public HudStyle(String name, float scale,
                    boolean background, boolean rounded, float cornerRadius,
                    float paddingX, float paddingY, OneColor bgColor,
                    boolean border, float borderSize, OneColor borderColor,
                    OneColor textColor, int textType,
                    boolean brackets, OneColor bracketsColor) {
        this.name = name;
        this.scale = scale;
        this.background = background;
        this.rounded = rounded;
        this.cornerRadius = cornerRadius;
        this.paddingX = paddingX;
        this.paddingY = paddingY;
        this.bgColor = bgColor;
        this.border = border;
        this.borderSize = borderSize;
        this.borderColor = borderColor;
        this.textColor = textColor;
        this.textType = textType;
        this.brackets = brackets;
        this.bracketsColor = bracketsColor;
    }
}
