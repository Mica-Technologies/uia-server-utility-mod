package com.micatechnologies.minecraft.sum.mixin;

import com.micatechnologies.minecraft.sum.pauser.ServerPauser;
import net.minecraft.entity.EntityTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips the entity tracker while the server pauser has the worlds frozen. Nothing moves while
 * paused, so there is nothing to send. See {@link MixinWorldServer}.
 */
@Mixin(EntityTracker.class)
public abstract class MixinEntityTracker {

    @Inject(at = @At("HEAD"), method = "tick()V", cancellable = true)
    private void sum$pauseTracker(CallbackInfo ci) {
        if (ServerPauser.isPaused()) {
            ci.cancel();
        }
    }
}
