package com.micatechnologies.minecraft.sum.economy;

import com.micatechnologies.minecraft.sum.Sum;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Loader;

/**
 * Reflection-only soft bridge to the EconomyInc mod's per-player balance capability. SUM never
 * compiles against EconomyInc; if EconomyInc is absent at runtime, every method here is a no-op
 * and {@link #isAvailable()} returns false so callers can show a graceful fallback.
 *
 * <p>Signatures locked from EconomyInc 1.6.2 (as bundled in the Alto pack):
 * <ul>
 *   <li>{@code fr.fifou.economy.capability.IMoney#getMoney()} returns {@code double}</li>
 *   <li>{@code fr.fifou.economy.capability.IMoney#setMoney(double)}</li>
 *   <li>{@code fr.fifou.economy.capability.IMoney#sync(EntityPlayer)} pushes server changes
 *       back to the client GUI</li>
 *   <li>{@code fr.fifou.economy.capability.CapabilityLoading#getMoneyHandler(Entity)} is a public
 *       static helper that wraps the {@code hasCapability}/{@code getCapability} dance using
 *       {@code EnumFacing.DOWN}; we call it instead of binding the {@code @CapabilityInject} field
 *       directly.</li>
 * </ul>
 */
public final class EconomyBridge {

    public static final String MOD_ID = "economy";

    private static final String CAPABILITY_LOADING_CLASS = "fr.fifou.economy.capability.CapabilityLoading";
    private static final String IMONEY_CLASS = "fr.fifou.economy.capability.IMoney";

    private static final boolean MOD_PRESENT;
    private static final Method GET_MONEY_HANDLER;
    private static final Method GET_MONEY;
    private static final Method SET_MONEY;
    private static final Method SYNC;

    static {
        boolean present = Loader.isModLoaded(MOD_ID);
        Method getHandler = null;
        Method get = null;
        Method set = null;
        Method sync = null;
        if (present) {
            try {
                Class<?> capabilityLoading = Class.forName(CAPABILITY_LOADING_CLASS);
                Class<?> entityClass = Class.forName("net.minecraft.entity.Entity");
                Class<?> imoney = Class.forName(IMONEY_CLASS);
                Class<?> playerClass = Class.forName("net.minecraft.entity.player.EntityPlayer");
                getHandler = capabilityLoading.getMethod("getMoneyHandler", entityClass);
                get = imoney.getMethod("getMoney");
                set = imoney.getMethod("setMoney", double.class);
                sync = imoney.getMethod("sync", playerClass);
                Sum.LOGGER.info("[economy] EconomyInc bridge bound (IMoney + CapabilityLoading.getMoneyHandler).");
            } catch (Throwable t) {
                Sum.LOGGER.warn("[economy] EconomyInc is loaded but the bridge could not bind; "
                    + "balance reads/writes will be no-ops.", t);
                getHandler = null;
                get = null;
                set = null;
                sync = null;
            }
        } else {
            Sum.LOGGER.info("[economy] EconomyInc is not loaded; SUM bank features will be inert.");
        }
        MOD_PRESENT = present;
        GET_MONEY_HANDLER = getHandler;
        GET_MONEY = get;
        SET_MONEY = set;
        SYNC = sync;
    }

    private EconomyBridge() {}

    public static boolean isAvailable() {
        return MOD_PRESENT && GET_MONEY_HANDLER != null
            && GET_MONEY != null && SET_MONEY != null;
    }

    @Nullable
    private static Object resolveHandler(EntityPlayer player) {
        if (!isAvailable() || player == null) {
            return null;
        }
        try {
            return GET_MONEY_HANDLER.invoke(null, player);
        } catch (Throwable t) {
            Sum.LOGGER.warn("[economy] Failed to resolve IMoney handler for {}", player.getName(), t);
            return null;
        }
    }

    /**
     * @return the player's current balance in dollars, or {@link Double#NaN} if EconomyInc isn't
     *     bound to this player (mod absent, capability missing, reflection failed).
     */
    public static double getBalance(EntityPlayer player) {
        Object handler = resolveHandler(player);
        if (handler == null) return Double.NaN;
        try {
            Object value = GET_MONEY.invoke(handler);
            return value instanceof Number ? ((Number) value).doubleValue() : Double.NaN;
        } catch (Throwable t) {
            Sum.LOGGER.warn("[economy] getBalance failed for {}", player.getName(), t);
            return Double.NaN;
        }
    }

    /**
     * Adjusts the player's balance by {@code delta}. Refuses to overdraft (returns false).
     * Calls {@code IMoney.sync(EntityPlayer)} on success so the client-side balance display
     * stays in sync with the server.
     *
     * @return true on success; false if EconomyInc isn't bound, the player has no money handler,
     *     or the resulting balance would be negative.
     */
    public static boolean adjustBalance(EntityPlayer player, double delta) {
        Object handler = resolveHandler(player);
        if (handler == null) return false;
        try {
            double current = ((Number) GET_MONEY.invoke(handler)).doubleValue();
            double next = current + delta;
            if (next < 0.0) return false;
            SET_MONEY.invoke(handler, next);
            if (player instanceof EntityPlayerMP && SYNC != null) {
                SYNC.invoke(handler, player);
            }
            return true;
        } catch (Throwable t) {
            Sum.LOGGER.warn("[economy] adjustBalance failed for {}", player.getName(), t);
            return false;
        }
    }
}
