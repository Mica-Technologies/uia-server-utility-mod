package com.micatechnologies.minecraft.sum.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.launchwrapper.Launch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Apple-Silicon (runClient17 / lwjgl3ify) launch fix: restore the {@code org.lwjgl.}
 * classloader exclusion that OneConfig removes.
 *
 * <p>When running the 1.12.2 client on lwjgl3ify (the arm64-native path), LWJGL3 is
 * provided by lwjgl3ify and RetroFuturaBootstrap (RFB) keeps {@code org.lwjgl.} on the
 * LaunchClassLoader's <em>classloader-exclusion</em> list so those classes are defined
 * exactly once, on the RFB-System loader. OneConfig's tweaker
 * ({@code cc.polyfrost.oneconfig.internal.plugin.asm.OneConfigTweaker#injectIntoClassLoader})
 * reflectively <em>removes</em> {@code "org.lwjgl."} from that set (its
 * {@code removeLWJGLException()}), so it can load its own repacked LWJGL3 through the
 * transforming LaunchClassLoader. On a normal LWJGL2 client that's harmless; on lwjgl3ify
 * it lets {@code org.lwjgl.glfw.GLFWVidMode} / {@code org.lwjgl.system.*} be defined a
 * second time by the RFB-Launch loader, and the client dies during GLFW display init with:
 * <pre>java.lang.LinkageError: loader constraint violation: loader 'RFB-Launch' wants to load
 * class org.lwjgl.glfw.GLFWVidMode. A different class with the same name was previously
 * loaded by 'RFB-System'.</pre>
 *
 * <p>{@code Minecraft.run()} is the earliest mod-reachable point that is guaranteed to run
 * after every tweaker's {@code injectIntoClassLoader} (so after OneConfig has removed the
 * exclusion) and before {@code Minecraft.init()} touches LWJGL. Re-adding the exclusion
 * here makes LWJGL3 resolve from RFB-System again. No-op on dedicated-server / non-lwjgl3ify
 * launches where the exclusion is already present and re-adding is idempotent.</p>
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Inject(method = "run", at = @At("HEAD"))
    private void sum$restoreLwjglClassloaderExclusion(CallbackInfo ci) {
        // Only relevant under lwjgl3ify / RetroFuturaBootstrap (the runClient17 arm64 path),
        // where LWJGL3 lives on the RFB-System loader and must stay parent-loaded. On a
        // normal LWJGL2 client (e.g. the x86_64 Rosetta runClient) OneConfig deliberately
        // removes this exclusion to load its own isolated LWJGL3 — re-adding it there would
        // break OneConfig — so scope this to the RFB launch only.
        if (System.getProperty("java.system.class.loader", "").contains("RfbSystemClassLoader")) {
            Launch.classLoader.addClassLoaderExclusion("org.lwjgl.");
        }
    }
}
