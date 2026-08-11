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

    /** Whether OneConfig is installed. Read once — the mod list cannot change after startup. */
    private static final boolean ONECONFIG_PRESENT =
        net.minecraftforge.fml.common.Loader.isModLoaded("oneconfig");

    /** {@code [minTicks, maxTicks]} for knob-backed types when the feature is enabled,
     *  otherwise {@code null} (use vanilla). */
    private static int[] getConfiguredRangeTicks(MusicTicker.MusicType type) {
        // The knobs live in OneConfig, which is an optional (after:) dependency. Everything that
        // touches SumOneConfig is behind this check and inside the holder class below.
        return ONECONFIG_PRESENT ? Knobs.rangeFor(type) : null;
    }

    /**
     * Everything that reads {@link SumOneConfig}, kept in its own class so it is never loaded
     * without OneConfig.
     *
     * <p><b>A null check is not enough here, and that is the whole point of this class.</b>
     * {@code SumOneConfig} extends a OneConfig type, so merely reading {@code SumOneConfig.INSTANCE}
     * makes the JVM resolve that supertype — which throws {@code NoClassDefFoundError} before any
     * check on the value can run. The old code guarded on {@code config == null} and crashed anyway,
     * on the first client that had SUM without OneConfig: the music ticker asks for a delay within
     * seconds of reaching the main menu, so the client died at startup with a stack trace pointing
     * at vanilla's {@code MusicTicker}.
     *
     * <p>Class loading is triggered by first access, so putting the reference in a separate class
     * behind {@link #ONECONFIG_PRESENT} means it genuinely never resolves — rather than relying on
     * a particular JVM's laziness about resolving a field it is about to skip over.
     */
    private static final class Knobs {

        private Knobs() {
        }

        static int[] rangeFor(MusicTicker.MusicType type) {
            SumOneConfig config = SumOneConfig.INSTANCE;
            if (config == null || !config.musicDelaysEnabled) {
                return null;
            }
            switch (type) {
                case MENU:
                    return secondsRange(config.menuMusicMinDelaySeconds,
                        config.menuMusicMaxDelaySeconds);
                case GAME:
                    return secondsRange(config.gameMusicMinDelaySeconds,
                        config.gameMusicMaxDelaySeconds);
                case CREATIVE:
                    return secondsRange(config.creativeMusicMinDelaySeconds,
                        config.creativeMusicMaxDelaySeconds);
                default:
                    return null;
            }
        }
    }

    private static int[] secondsRange(int minSeconds, int maxSeconds) {
        return new int[] { Math.max(0, minSeconds) * TICKS_PER_SECOND,
                           Math.max(0, maxSeconds) * TICKS_PER_SECOND };
    }
}
