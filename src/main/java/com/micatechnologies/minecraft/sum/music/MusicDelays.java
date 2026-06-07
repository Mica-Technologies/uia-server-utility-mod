package com.micatechnologies.minecraft.sum.music;

import com.micatechnologies.minecraft.sum.pocket.SumOneConfig;
import net.minecraft.client.audio.MusicTicker;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Resolves the configured background-music delays for {@code MixinMusicTickerMusicType}.
 * Vanilla 1.12.2 hardcodes the gap between tracks per {@link MusicTicker.MusicType}:
 * MENU 20–600 ticks (1–30s), GAME 12000–24000 ticks (10–20 min), CREATIVE/NETHER
 * 1200–3600 ticks (1–3 min). With a large custom soundtrack the 10–20 minute survival
 * gap means most tracks are rarely heard, so SUM exposes the MENU/GAME/CREATIVE delays
 * as client preferences in {@link SumOneConfig} (seconds, converted to ticks here).
 *
 * <p>Types without a knob (NETHER, END, CREDITS, END_BOSS) and disabled-state always
 * fall through to the vanilla value so behavior matches stock Minecraft exactly.</p>
 */
@SideOnly(Side.CLIENT)
public final class MusicDelays {

    private static final int TICKS_PER_SECOND = 20;

    private MusicDelays() {
    }

    /** Configured minimum delay in ticks for {@code type}, or {@code vanillaTicks} when
     *  the feature is off / the type has no knob. */
    public static int getMinDelayTicks(MusicTicker.MusicType type, int vanillaTicks) {
        int[] range = getConfiguredRangeTicks(type);
        return range != null ? range[0] : vanillaTicks;
    }

    /** Configured maximum delay in ticks for {@code type}, or {@code vanillaTicks} when
     *  the feature is off / the type has no knob. Never less than the configured minimum,
     *  so a min &gt; max misconfiguration degrades to a fixed delay instead of crashing
     *  {@code MathHelper.getInt}'s random-range call. */
    public static int getMaxDelayTicks(MusicTicker.MusicType type, int vanillaTicks) {
        int[] range = getConfiguredRangeTicks(type);
        return range != null ? Math.max(range[0], range[1]) : vanillaTicks;
    }

    /** {@code [minTicks, maxTicks]} for knob-backed types when the feature is enabled,
     *  otherwise {@code null} (use vanilla). */
    private static int[] getConfiguredRangeTicks(MusicTicker.MusicType type) {
        SumOneConfig config = SumOneConfig.INSTANCE;
        if (config == null || !config.musicDelaysEnabled) {
            return null;
        }
        switch (type) {
            case MENU:
                return secondsRange(config.menuMusicMinDelaySeconds, config.menuMusicMaxDelaySeconds);
            case GAME:
                return secondsRange(config.gameMusicMinDelaySeconds, config.gameMusicMaxDelaySeconds);
            case CREATIVE:
                return secondsRange(config.creativeMusicMinDelaySeconds, config.creativeMusicMaxDelaySeconds);
            default:
                return null;
        }
    }

    private static int[] secondsRange(int minSeconds, int maxSeconds) {
        return new int[] { Math.max(0, minSeconds) * TICKS_PER_SECOND,
                           Math.max(0, maxSeconds) * TICKS_PER_SECOND };
    }
}
