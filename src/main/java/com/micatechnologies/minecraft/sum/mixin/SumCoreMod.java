package com.micatechnologies.minecraft.sum.mixin;

import java.security.CodeSource;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.spongepowered.asm.mixin.Mixins;

/**
 * FML loading plugin whose sole job is to register {@code mixins.sum.json} with
 * MixinBooter early enough that the Mixin transformer can apply our patches.
 *
 * <p>Why this exists: MixinBooter only auto-discovers configs declared in a mod
 * jar's {@code META-INF/MANIFEST.MF#MixinConfigs} attribute. In a production
 * jar build that attribute is set by the GTNH buildscript ({@code addon.gradle})
 * and the discovery just works. In a dev environment ({@code runClient} /
 * {@code runServer}) we're running from {@code build/classes/} and {@code build/resources/}
 * directly — no jar, no manifest — so MixinBooter never sees the config and our
 * mixins silently never apply.</p>
 *
 * <p>Setting {@code coreModClass = mixin.SumCoreMod} in {@code buildscript.properties}
 * makes the buildscript do two things for us:
 * <ul>
 *   <li>Production jar: add {@code FMLCorePlugin: com.micatechnologies.minecraft.sum.mixin.SumCoreMod}
 *       to {@code MANIFEST.MF} so FML instantiates this class during coremod scanning.</li>
 *   <li>Dev launch tasks: pass {@code -Dfml.coreMods.load=com.micatechnologies.minecraft.sum.mixin.SumCoreMod}
 *       as a JVM arg so FML picks the coremod up even though it isn't in a jar manifest.</li>
 * </ul>
 * Either way, this class's constructor fires before Mixin's class-transformer
 * pipeline starts, and the {@code Mixins.addConfiguration} call lands on time.</p>
 *
 * <p>This class is intentionally a plain mixin-config registrar — no ASM
 * transformers, no FML setup, no access transformer — so it interacts with
 * exactly one thing (Mixin) and stays understandable.</p>
 */
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.Name("SUM Mixin Loader")
@IFMLLoadingPlugin.SortingIndex(1001)
public class SumCoreMod implements IFMLLoadingPlugin {

    public SumCoreMod() {
        // Only register manually in dev. In a production jar the MANIFEST's
        // `MixinConfigs: mixins.sum.json` attribute is what MixinBooter discovers, and calling
        // Mixins.addConfiguration here would blow up: FML instantiates coremods from
        // CoreModManager.discoverCoreMods, which runs *before* MixinBooter's tweaker has called
        // MixinBootstrap.init(), so Mixin throws "Environment conflict, mismatched versions or
        // you didn't call MixinBootstrap.init()" and the game dies during coremod discovery.
        //
        // This used to be masked: while the jar declared a TweakClass, FML took the cascading-
        // tweaker branch and `continue`d past loadCoreMod entirely, so this constructor never
        // ran here — MixinBooter instantiated it later, once Mixin was ready. Dropping the
        // TweakClass (see addon.gradle for why it had to go) exposed the latent bug.
        if (isDevEnvironment()) {
            Mixins.addConfiguration("mixins.sum.json");
        }
    }

    /**
     * True when running from loose classes ({@code build/classes}) rather than a packaged jar,
     * i.e. a {@code runClient} / {@code runServer} dev launch. Deliberately does not consult
     * {@code Launch.blackboard.get("fml.deobfuscatedEnvironment")}: FML only publishes that key
     * after {@code discoverCoreMods} has already constructed every coremod, so it is still
     * absent at this point.
     */
    private static boolean isDevEnvironment() {
        try {
            CodeSource source = SumCoreMod.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return false;
            }
            return !source.getLocation().getPath().toLowerCase(Locale.ROOT).endsWith(".jar");
        }
        catch (SecurityException e) {
            // Locked-down security manager — assume production and let the manifest do the work.
            return false;
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    @Nullable
    public String getModContainerClass() {
        return null;
    }

    @Override
    @Nullable
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // no-op
    }

    @Override
    @Nullable
    public String getAccessTransformerClass() {
        return null;
    }
}
