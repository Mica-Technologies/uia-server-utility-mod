package com.micatechnologies.minecraft.sum.mixin;

import java.io.File;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

/**
 * Side-aware shim that bootstraps OneConfig on clients only.
 *
 * <p><b>Why this exists.</b> SUM embeds the OneConfig stage0 wrapper and its published
 * {@code MANIFEST.MF} used to name OneConfig's tweaker directly:
 * {@code TweakClass: cc.polyfrost.oneconfig.loader.stage0.LaunchWrapperTweaker}. LaunchWrapper
 * honours that on <em>both</em> physical sides, so a dedicated server would download and load
 * the real OneConfig binary — which is a client-only mod referencing
 * {@code net.minecraft.server.integrated.IntegratedServer}. Forge's {@code SideTransformer}
 * then aborts the load with "Attempted to load class chd for invalid side SERVER" and the
 * server dies in the tick loop before finishing mod construction.</p>
 *
 * <p>SUM itself never needs OneConfig server-side: {@code SumOneConfig} is instantiated only
 * from {@code SumClientProxy}, and every HUD is a client concern. So the fix is simply to not
 * bootstrap it on a dedicated server.</p>
 *
 * <p><b>How it works.</b> The manifest now names <em>this</em> class. On a client we append
 * OneConfig's tweaker to LaunchWrapper's pending {@code TweakClasses} list, which LaunchWrapper
 * drains in its cascading-tweaker loop — exactly the mechanism FML uses to chain its own
 * tweakers, so OneConfig still loads at the same point in startup it always did. On a dedicated
 * server we do nothing at all and OneConfig is never touched.</p>
 *
 * <p><b>Side detection.</b> We probe for {@code net.minecraft.client.main.Main}, which is absent
 * from a dedicated-server distribution. This deliberately avoids
 * {@code FMLLaunchHandler.side()}: that would couple us to FML's tweaker having already run, and
 * our {@code TweakOrder} is 0 (the same as FML's), so the ordering is not guaranteed. A plain
 * class-presence probe has no such dependency.</p>
 */
public class SumOneConfigTweaker implements ITweaker {

    /** OneConfig's real stage0 bootstrap, embedded in this jar but only wired up on clients. */
    private static final String ONECONFIG_TWEAKER =
        "cc.polyfrost.oneconfig.loader.stage0.LaunchWrapperTweaker";

    /** Present on a client distribution, absent on a dedicated server. */
    private static final String CLIENT_MARKER = "net.minecraft.client.main.Main";

    /** LaunchWrapper's blackboard key holding tweak classes still waiting to be constructed. */
    private static final String TWEAK_CLASSES_KEY = "TweakClasses";

    @Override
    public void acceptOptions(List<String> args, @Nullable File gameDir, @Nullable File assetsDir,
                              @Nullable String profile) {
        // Nothing to consume — OneConfig's own tweaker parses whatever it needs once cascaded.
    }

    @Override
    @SuppressWarnings("unchecked")
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        if (!isClientSide(classLoader)) {
            // Dedicated server: OneConfig is client-only and would crash the SideTransformer.
            return;
        }
        Object pending = Launch.blackboard.get(TWEAK_CLASSES_KEY);
        if (!(pending instanceof List)) {
            // No cascading list to append to — nothing sensible to do but leave OneConfig out
            // rather than risk loading it on the wrong side.
            return;
        }
        List<String> tweakClasses = (List<String>) pending;
        if (!tweakClasses.contains(ONECONFIG_TWEAKER)) {
            tweakClasses.add(ONECONFIG_TWEAKER);
        }
    }

    /**
     * True when running on a physical client. Uses a non-initialising {@link Class#forName}
     * so the probe itself can never trigger client static setup on a server.
     */
    private static boolean isClientSide(LaunchClassLoader classLoader) {
        try {
            Class.forName(CLIENT_MARKER, false, classLoader);
            return true;
        }
        catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    @Override
    public String getLaunchTarget() {
        // Never the launch target — FML's tweaker owns that. LaunchWrapper only consults the
        // primary tweaker, so returning null here is correct for a cascaded shim.
        return null;
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }
}
