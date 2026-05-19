package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import java.util.Collection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.PotionEffect;

/**
 * Comma-separated list of active potion effects with their amplifier (roman
 * numerals) and, optionally, remaining duration. First three shown by name; any
 * beyond that condensed to "+N more" so the line stays a reasonable width on a
 * crowded PvP loadout.
 */
public class ActiveEffectsHud extends SingleTextHud {

    @Switch(name = "Show duration")
    public boolean showDuration = false;

    public ActiveEffectsHud() {
        super("Effects", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return showDuration ? "Speed II (1:23), Resistance" : "Speed II, Resistance";
        }
        EntityPlayer p = Minecraft.getMinecraft().player;
        if (p == null) return "—";
        Collection<PotionEffect> effects = p.getActivePotionEffects();
        if (effects.isEmpty()) return "none";

        StringBuilder sb = new StringBuilder();
        int i = 0;
        int total = effects.size();
        for (PotionEffect e : effects) {
            if (i >= 3) {
                sb.append(", +").append(total - 3);
                break;
            }
            if (i > 0) sb.append(", ");
            sb.append(I18n.format(e.getEffectName()));
            if (e.getAmplifier() > 0) {
                sb.append(' ').append(roman(e.getAmplifier() + 1));
            }
            if (showDuration) {
                int totalSec = e.getDuration() / 20;
                sb.append(" (").append(totalSec / 60).append(':');
                int s = totalSec % 60;
                if (s < 10) sb.append('0');
                sb.append(s).append(')');
            }
            i++;
        }
        return sb.toString();
    }

    private static String roman(int n) {
        switch (n) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            case 6: return "VI";
            case 7: return "VII";
            case 8: return "VIII";
            case 9: return "IX";
            case 10: return "X";
            default: return Integer.toString(n);
        }
    }
}
