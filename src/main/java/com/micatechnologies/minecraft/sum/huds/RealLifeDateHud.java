package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.config.annotations.Dropdown;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Real-world date. Modeled on EvergreenHUD's RealLifeDate element. */
public class RealLifeDateHud extends SingleTextHud {

    @Dropdown(name = "Format", options = {
        "ISO (YYYY-MM-DD)", "US (MM/DD/YYYY)", "EU (DD.MM.YYYY)", "Long (MMM D, YYYY)"
    })
    public int format = 0;

    private static final DateTimeFormatter[] FORMATTERS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("MMM d, yyyy"),
    };

    public RealLifeDateHud() {
        super("Date:", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        LocalDate when = example ? LocalDate.of(2026, 7, 4) : LocalDate.now();
        DateTimeFormatter fmt = FORMATTERS[Math.max(0, Math.min(format, FORMATTERS.length - 1))];
        return when.format(fmt);
    }
}
