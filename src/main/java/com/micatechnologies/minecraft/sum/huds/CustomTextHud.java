package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.config.annotations.Text;
import cc.polyfrost.oneconfig.hud.SingleTextHud;

/**
 * User-defined text widget. Each instance owns one editable text field; the user types
 * whatever they want into the OneConfig UI and the HUD paints it. SUM registers five
 * of these so a player can run up to five custom strings on screen without us porting
 * EvergreenHUD's full dynamic HudList framework. Empty {@link #text} means the HUD
 * draws an empty line (effectively hidden).
 *
 * <p>Modeled on EvergreenHUD's CustomTexts element — minus the "add more slots" UI,
 * which would require a custom OneConfig option type to surface dynamically-sized
 * lists.</p>
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
            return text == null || text.isEmpty() ? "Your custom text" : text;
        }
        return text == null ? "" : text;
    }
}
