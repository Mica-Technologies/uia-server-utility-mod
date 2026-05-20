package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.config.annotations.Text;
import cc.polyfrost.oneconfig.hud.SingleTextHud;

/**
 * User-defined text widget. Each instance owns one editable text field; the user types
 * whatever they want into the OneConfig UI and the HUD paints it. The text supports
 * {@code %placeholder%} tokens via {@link TextPlaceholders} so a single slot can render
 * dynamic strings like {@code "%player% @ %x%, %z% (%biome%)"} without composing
 * multiple HUDs. SUM registers ten of these so a player can run up to ten such strings
 * on screen.
 *
 * <p>Modeled on EvergreenHUD's CustomTexts element — minus the "add more slots" UI,
 * which would require a custom OneConfig option type to surface dynamically-sized
 * lists. With placeholders, 10 fixed slots is plenty.</p>
 */
public class CustomTextHud extends SingleTextHud {

    @Text(name = "Text")
    public String text = "";

    public CustomTextHud() {
        // Empty title so the HUD shows only the user's text (vanilla SingleTextHud
        // separates title and value with a colon — we suppress that for free-form
        // text). Title can be re-added via the per-HUD options if the user wants a
        // prefix.
        super("", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            String src = text == null || text.isEmpty() ? "Your custom text" : text;
            return TextPlaceholders.applyExample(src);
        }
        return TextPlaceholders.apply(text == null ? "" : text);
    }
}
