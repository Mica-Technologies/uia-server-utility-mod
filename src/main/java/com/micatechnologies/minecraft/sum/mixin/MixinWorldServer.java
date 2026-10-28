package com.micatechnologies.minecraft.sum.mixin;

import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.pauser.ServerPauser;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server pauser: freezes each world while {@link ServerPauser#isPaused} says so. This is the
 * soft pause, used when every online player is AFK; with nobody online
 * {@code MixinMinecraftServer} skips the world loop altogether, so these never run.
 *
 * <p>Two methods are cancelled, because a world's tick is split between them:
 * <ul>
 *   <li>{@link WorldServer#tick()}: time, weather, scheduled block updates, random ticks, mob
 *       spawning, chunk unloading.</li>
 *   <li>{@link WorldServer#updateEntities()}: every entity and every {@code ITickable}
 *       TileEntity. Vanilla stops this 300 ticks after a world empties, but Forge skips that
 *       shutdown while any chunk is force-loaded, so chunk-loaded machines and farms would
 *       otherwise run forever.</li>
 * </ul>
 * {@code MixinEntityTracker} skips the entity tracker on the same decision.
 *
 * <p>Mods' own {@code ServerTickEvent} / {@code WorldTickEvent} handlers still run during a soft
 * pause.
 *
 * <p>Gated by {@link SumConfig#isPauserEnabled()}.
 */
@Mixin(WorldServer.class)
public abstract class MixinWorldServer {

    @Inject(at = @At("HEAD"), method = "tick()V", cancellable = true)
    private void sum$pauseTick(CallbackInfo ci) {
        if (ServerPauser.isPaused()) {
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "updateEntities()V", cancellable = true)
    private void sum$pauseEntities(CallbackInfo ci) {
        if (ServerPauser.isPaused()) {
            ci.cancel();
        }
    }
}
