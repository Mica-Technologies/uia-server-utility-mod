package com.micatechnologies.minecraft.sum.mixin;

import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.afk.AfkTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels the entire {@link WorldServer#tick()} when no players are logged in. Halts everything
 * vanilla's tick handles — time, weather, scheduled block updates, TileEntity ticks (so AFK
 * farms genuinely freeze), entity ticks, random ticks, mob spawning. Resumes immediately when
 * the first player logs in.
 *
 * <p>Gated by {@link SumConfig#isPauserEnabled()}; if the feature is disabled in config, the
 * Mixin no-ops.
 */
@Mixin(WorldServer.class)
public abstract class MixinWorldServer {

    @Shadow @Final private MinecraftServer server;

    @Inject(at = @At("HEAD"), method = "tick()V", cancellable = true)
    private void sum$pauseWhenEmpty(CallbackInfo ci) {
        if (!SumConfig.isPauserEnabled()) {
            return;
        }
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        if (server.getPlayerList().getCurrentPlayerCount() <= 0) {
            ci.cancel();
            return;
        }
        // Optional extension: also freeze the world while every online player is AFK.
        if (SumConfig.isAfkPauseWorldWhenAllAfk() && AfkTracker.allOnlinePlayersAfk(server)) {
            ci.cancel();
        }
    }
}
