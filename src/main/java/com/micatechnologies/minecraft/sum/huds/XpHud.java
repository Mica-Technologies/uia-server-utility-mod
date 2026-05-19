package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Experience level + (optionally) progress to next level. Vanilla shows the level
 * number above the XP bar but no percent-to-next progress; this exposes both so
 * you can tell at a glance how close you are to the next enchant.
 */
public class XpHud extends SingleTextHud {

    /** Show "L30 (45%)" instead of just "L30". Default on — the progress percent is
     *  the value-add over the vanilla XP bar number. */
    @Switch(name = "Show progress %")
    public boolean showProgress = true;

    public XpHud() {
        super("XP", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) return showProgress ? "L30 (45%)" : "L30";
        EntityPlayer p = Minecraft.getMinecraft().player;
        if (p == null) return "—";
        if (!showProgress) return "L" + p.experienceLevel;
        int pct = (int) Math.round(p.experience * 100f);
        return "L" + p.experienceLevel + " (" + pct + "%)";
    }
}
