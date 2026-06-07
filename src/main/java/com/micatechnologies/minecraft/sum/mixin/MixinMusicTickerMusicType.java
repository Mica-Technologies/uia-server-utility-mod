package com.micatechnologies.minecraft.sum.mixin;

import com.micatechnologies.minecraft.sum.music.MusicDelays;
import net.minecraft.client.audio.MusicTicker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the per-{@link MusicTicker.MusicType} delays between background music tracks
 * configurable. Vanilla hardcodes them in the enum constructor (GAME waits 10–20 minutes
 * between tracks); {@code MusicTicker.update()} only ever reads them through
 * {@code getMinDelay()}/{@code getMaxDelay()}, so overriding the two getters covers every
 * call site — including the mid-wait {@code Math.min(timeUntilNextMusic, maxDelay)} clamp,
 * which means lowering the max in the config takes effect immediately rather than after
 * the current wait expires.
 *
 * <p>RETURN-injection (rather than HEAD) hands {@link MusicDelays} the vanilla value, so
 * the disabled/unknown-type path returns it untouched.</p>
 */
@Mixin(MusicTicker.MusicType.class)
public abstract class MixinMusicTickerMusicType {

    @Inject(method = "getMinDelay", at = @At("RETURN"), cancellable = true)
    private void sum$overrideMinDelay(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(MusicDelays.getMinDelayTicks(
            (MusicTicker.MusicType) (Object) this, cir.getReturnValueI()));
    }

    @Inject(method = "getMaxDelay", at = @At("RETURN"), cancellable = true)
    private void sum$overrideMaxDelay(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(MusicDelays.getMaxDelayTicks(
            (MusicTicker.MusicType) (Object) this, cir.getReturnValueI()));
    }
}
