package com.micatechnologies.minecraft.sum.mixin;

import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.pauser.ServerPauser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server pauser: freezes each world while {@link ServerPauser#isPaused} says so (nobody online,
 * or optionally everyone AFK). Resumes on the first tick after a player logs in or comes back.
 *
 * <p>Two methods are cancelled, because a world's tick is split between them:
 * <ul>
 *   <li>{@link WorldServer#tick()}: time, weather, scheduled block updates, random ticks, mob
 *       spawning, chunk unloading.</li>
 *   <li>{@link WorldServer#updateEntities()}: every entity and every {@code ITickable}
 *       TileEntity. Vanilla stops this 300 ticks after a world empties, but Forge skips that
 *       shutdown while any chunk is force-loaded, so chunk-loaded machines and farms would
 *       otherwise run forever on an empty server.</li>
 * </ul>
 * {@code MixinEntityTracker} skips the entity tracker on the same decision.
 *
 * <p>Mods' own {@code ServerTickEvent} / {@code WorldTickEvent} handlers still run.
 *
 * <p>Gated by {@link SumConfig#isPauserEnabled()}.
 */
@Mixin(WorldServer.class)
public abstract class MixinWorldServer {

    @Shadow @Final private MinecraftServer server;

    @Inject(at = @At("HEAD"), method = "tick()V", cancellable = true)
    private void sum$pauseTick(CallbackInfo ci) {
        if (ServerPauser.isPaused(server)) {
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "updateEntities()V", cancellable = true)
    private void sum$pauseEntities(CallbackInfo ci) {
        if (ServerPauser.isPaused(server)) {
            ci.cancel();
        }
    }
}
