package com.micatechnologies.minecraft.sum.mixin;

import com.micatechnologies.minecraft.sum.SumConfig;
import net.minecraft.network.NetHandlerPlayServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Loosens vanilla's "moved too quickly" thresholds in {@link NetHandlerPlayServer} so that
 * lag-induced rubberbanding doesn't throw players back to their last good position. Vanilla
 * 1.12.2 hardcodes:
 * <ul>
 *   <li>{@code processPlayer}: {@code 100.0F} (normal) and {@code 300.0F} (elytra) —
 *       multiplied by the per-tick packet count.</li>
 *   <li>{@code processVehicleMove}: {@code 100.0D} for vehicles (boats, minecarts, etc.).</li>
 * </ul>
 *
 * <p>Each constant is multiplied by {@link SumConfig#getMovementToleranceMultiplier()} when the
 * feature is enabled. Vanilla compares a <em>squared</em> distance, so allowed travel per server
 * tick grows only with the square root of the multiplier: at 1024 (default) a player may move 320
 * blocks per tick instead of vanilla's 10; with multiplier 1, behavior matches vanilla.
 *
 * <p>Inspired by Moving Quickly / GottaGoFast (server-side coremods that patch the same
 * constants). The 1024 default is deliberate parity with Moving Quickly 1.12.2_1.0.0, whose
 * {@code MovingQuicklyTransformer} rewrote every {@code LDC} of 100.0F/300.0F in
 * {@code processPlayer} as {@code value * 1024.0F}. Two deliberate differences: SUM also relaxes
 * {@code processVehicleMove} (Moving Quickly left boats and minecarts at vanilla), and SUM gates
 * the whole thing behind a per-server config knob.
 */
@Mixin(NetHandlerPlayServer.class)
public abstract class MixinNetHandlerPlayServer {

    @ModifyConstant(method = "processPlayer", constant = @Constant(floatValue = 100.0F))
    private float sum$relaxPlayerThreshold(float original) {
        return SumConfig.isMovementToleranceEnabled()
            ? original * (float) SumConfig.getMovementToleranceMultiplier()
            : original;
    }

    @ModifyConstant(method = "processPlayer", constant = @Constant(floatValue = 300.0F))
    private float sum$relaxElytraThreshold(float original) {
        return SumConfig.isMovementToleranceEnabled()
            ? original * (float) SumConfig.getMovementToleranceMultiplier()
            : original;
    }

    @ModifyConstant(method = "processVehicleMove", constant = @Constant(doubleValue = 100.0D))
    private double sum$relaxVehicleThreshold(double original) {
        return SumConfig.isMovementToleranceEnabled()
            ? original * SumConfig.getMovementToleranceMultiplier()
            : original;
    }
}
