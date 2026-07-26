package com.micatechnologies.minecraft.sum.mixin;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;

/**
 * FML loading plugin whose sole job is to register {@code mixins.sum.json} with
 * MixinBooter early enough that the Mixin transformer can apply our patches.
 *
 * <p>Registration goes through {@link IEarlyMixinLoader}, which is MixinBooter's documented
 * hook for mixins that target vanilla/Forge classes. MixinBooter walks FML's {@code coremodList}
 * in {@code MixinBooterPlugin.injectData} and calls {@code Mixins.addConfiguration} for every
 * config returned here. That happens well after {@code MixinBootstrap.init()} (MixinBooter runs
 * it from its own constructor, and it sorts first with {@code SortingIndex = MIN_VALUE + 1}), so
 * this path cannot reproduce the "Environment conflict, mismatched versions or you didn't call
 * MixinBootstrap.init()" crash that registering from our constructor caused.</p>
 *
 * <p>Do NOT go back to relying on the jar's {@code META-INF/MANIFEST.MF#MixinConfigs} attribute.
 * Nothing in this stack reads it for us:
 * <ul>
 *   <li>MixinBooter never inspects manifests — it only collects {@link IEarlyMixinLoader} /
 *       {@code ILateMixinLoader} coremods.</li>
 *   <li>{@code MixinConfigs} is handled by {@code MixinPlatformAgentDefault}, which only runs for
 *       jars Mixin registered as <em>containers</em>. {@code MixinServiceLaunchWrapper
 *       .getContainersFromClassPath} registers a jar only when its manifest declares
 *       {@code TweakClass: org.spongepowered.asm.launch.MixinTweaker}, and SUM declares no
 *       TweakClass at all (see addon.gradle for why it can't). {@code MixinPlatformAgentFMLLegacy
 *       .getMixinContainers()} returns null, so there is no mods-folder scan either.</li>
 * </ul>
 * Between 2026.07.19+2 and 2026.07.24 that assumption left every SUM mixin — movement tolerance,
 * world pauser, music delay — silently inert in production while dev launches looked fine.</p>
 *
 * <p>The manifest attribute is still emitted by {@code addon.gradle} (and asserted by the build
 * guard) as metadata for third-party tooling, but it is not a load path.</p>
 *
 * <p>{@code coreModClass = mixin.SumCoreMod} in {@code buildscript.properties} is what gets this
 * class instantiated: it adds {@code FMLCorePlugin} to the published MANIFEST, and passes
 * {@code -Dfml.coreMods.load=...} to the dev launch tasks. Both routes put us in the coremod list
 * MixinBooter walks, so dev and production now share one code path.</p>
 *
 * <p>This class is intentionally a plain mixin-config registrar — no ASM transformers, no FML
 * setup, no access transformer — so it interacts with exactly one thing (Mixin) and stays
 * understandable.</p>
 */
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.Name("SUM Mixin Loader")
@IFMLLoadingPlugin.SortingIndex(1001)
public class SumCoreMod implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.sum.json");
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
