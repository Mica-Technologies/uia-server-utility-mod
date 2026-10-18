package com.micatechnologies.minecraft.sum.picture;

import com.micatechnologies.minecraft.sum.pocket.SumOneConfig;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * This client's own picture frame preferences, from the SUM OneConfig page, with the same
 * defaults when OneConfig is not installed.
 */
@SideOnly(Side.CLIENT)
public final class PictureClientSettings {

    private PictureClientSettings() {}

    public static boolean enabled() {
        SumOneConfig c = config();
        return c == null || c.pictureFramesEnabled;
    }

    public static int maxRenderDistance() {
        SumOneConfig c = config();
        return c == null ? 128 : c.pictureMaxRenderDistance;
    }

    public static long diskCacheBytes() {
        SumOneConfig c = config();
        return (c == null ? 512L : Math.max(16, c.pictureCacheSizeMb)) * 1024L * 1024L;
    }

    public static long diskCacheMaxAgeMillis() {
        SumOneConfig c = config();
        return (c == null ? 7L : Math.max(1, c.pictureCacheDays)) * 86_400_000L;
    }

    private static SumOneConfig config() {
        return Loader.isModLoaded("oneconfig") ? Holder.get() : null;
    }

    /** Separate so a client without OneConfig never resolves {@link SumOneConfig}'s supertypes. */
    private static final class Holder {
        static SumOneConfig get() {
            return SumOneConfig.INSTANCE;
        }
    }
}
