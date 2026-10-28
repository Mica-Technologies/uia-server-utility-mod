package com.micatechnologies.minecraft.sum.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.SumConfig;
import com.micatechnologies.minecraft.sum.pauser.ServerPauser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server pauser, deep level: while nobody is online, suspends everything in the server tick except
 * what a login needs.
 *
 * <p>Freezing {@code WorldServer.tick()} and {@code updateEntities()} ({@link MixinWorldServer})
 * is not enough on its own. Plenty of mods tick from {@code ServerTickEvent} or
 * {@code WorldTickEvent} rather than from an entity or TileEntity: Immersive Vehicles runs every
 * vehicle's physics from a world tick handler, IC2 its energy net, FAWE and BuildCraft their
 * queues. Those kept running on an empty server, and freezing {@code WorldServer.tick()} also
 * stopped chunk unloading, so the chunks around the last player to leave stayed loaded with
 * everything in them still ticking.
 *
 * <p>While {@link ServerPauser#isDeepPaused()}:
 * <ul>
 *   <li>the {@code ServerTickEvent} START and END posts are skipped;</li>
 *   <li>the per-dimension loop in {@code updateTimeLightAndEntities()} sees no dimensions, which
 *       skips {@code WorldTickEvent}, the world tick, entities, TileEntities and the tracker;</li>
 *   <li>autosaves are skipped. The world is saved once as the pause begins, and nothing changes
 *       after that.</li>
 * </ul>
 * Scheduled tasks, chunk I/O callbacks, networking (logins, status pings), the player list and
 * {@code ITickable}s registered on the server still run, so the first player to connect gets in
 * and the next tick resumes normally.
 *
 * <p>Uses MixinExtras' chainable injectors rather than {@code @Redirect} or cancelling
 * {@code tick()}, so other mods' injections into the same methods still apply.
 *
 * <p>Gated by {@link SumConfig#isPauserEnabled()}.
 */
@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {

    @Inject(at = @At("HEAD"), method = "tick()V")
    private void sum$decidePause(CallbackInfo ci) {
        if (ServerPauser.beginTick((MinecraftServer) (Object) this)) {
            MinecraftServer server = (MinecraftServer) (Object) this;
            Sum.LOGGER.info("Pauser: saving before suspending ticks");
            server.getPlayerList().saveAllPlayerData();
            server.saveAllWorlds(true);
        }
    }

    @WrapWithCondition(method = "tick()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraftforge/fml/common/FMLCommonHandler;onPreServerTick()V", remap = false))
    private boolean sum$skipPreServerTick(FMLCommonHandler handler) {
        return !ServerPauser.isDeepPaused();
    }

    @WrapWithCondition(method = "tick()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraftforge/fml/common/FMLCommonHandler;onPostServerTick()V", remap = false))
    private boolean sum$skipPostServerTick(FMLCommonHandler handler) {
        return !ServerPauser.isDeepPaused();
    }

    @WrapWithCondition(method = "tick()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/management/PlayerList;saveAllPlayerData()V"))
    private boolean sum$skipAutosavePlayers(PlayerList playerList) {
        return !ServerPauser.isDeepPaused();
    }

    @WrapWithCondition(method = "tick()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/MinecraftServer;saveAllWorlds(Z)V"))
    private boolean sum$skipAutosaveWorlds(MinecraftServer server, boolean isSilent) {
        return !ServerPauser.isDeepPaused();
    }

    @ModifyExpressionValue(method = "updateTimeLightAndEntities()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraftforge/common/DimensionManager;getIDs(Z)[Ljava/lang/Integer;", remap = false))
    private Integer[] sum$skipWorldLoop(Integer[] ids) {
        return ServerPauser.isDeepPaused() ? new Integer[0] : ids;
    }
}
