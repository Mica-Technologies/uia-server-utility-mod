package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.hud.SingleTextHud;

/** JVM heap usage. Modeled on EvergreenHUD's memory element. */
public class MemoryHud extends SingleTextHud {

    @Switch(name = "Show as %")
    public boolean asPercent = false;

    public MemoryHud() {
        super("Memory:", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return asPercent ? "37%" : "2.4 / 6.0 GB";
        }
        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory();
        long used = rt.totalMemory() - rt.freeMemory();
        if (asPercent) {
            return Math.round(100.0 * used / max) + "%";
        }
        return String.format("%.1f / %.1f GB", used / 1073741824.0, max / 1073741824.0);
    }
}
