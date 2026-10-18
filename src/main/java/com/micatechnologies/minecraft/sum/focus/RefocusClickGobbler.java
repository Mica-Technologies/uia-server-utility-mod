package com.micatechnologies.minecraft.sum.focus;

import com.micatechnologies.minecraft.sum.pocket.SumOneConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * Discards the mouse click that brings the game window back into focus.
 *
 * <p>Vanilla only half-handles this. {@code Minecraft#setIngameFocus} sets
 * {@code leftClickCounter = 10000}, which stops that click attacking, but the same click has
 * already been fed to {@code KeyBinding.onTick}, so the use-item and pick-block bindings still
 * fire: clicking back into the window places a block. Worse, with pause-on-lost-focus off
 * (F3+P) alt-tabbing never clears {@code inGameHasFocus}, so {@code setIngameFocus} never runs
 * and the refocusing click is treated as an ordinary one, attack included.
 *
 * <p>So this works from the window, not from {@code inGameHasFocus}. Each client tick samples
 * {@link Display#isActive()}; when it goes from inactive to active, the first mouse press within
 * {@link #ARM_WINDOW_MS} is cancelled. LWJGL queues the activating click in the same
 * {@code Display.update} that reports the window active, so it is always the first press the
 * next tick sees. The short window is what keeps a keyboard alt-tab from eating a deliberate
 * click made seconds later.
 *
 * <p>In game the press is cancelled through Forge's {@link MouseEvent}, which
 * {@code runTickMouse} checks before it touches any key binding. On a screen (the pause menu,
 * an inventory) it is cancelled through {@link GuiScreenEvent.MouseInputEvent.Pre}, so the
 * refocusing click cannot land on "Disconnect". Only the press is dropped; letting its release
 * through is harmless, because every binding is already up.
 */
public class RefocusClickGobbler {

    static final long ARM_WINDOW_MS = 250L;

    private boolean wasActive = true;
    /** {@link Minecraft#getSystemTime()} until which the next press is discarded; 0 when unarmed. */
    private long armedUntil;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        boolean active = Display.isActive();
        if (active && !wasActive && isEnabled()) {
            armedUntil = Minecraft.getSystemTime() + ARM_WINDOW_MS;
        }
        wasActive = active;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(MouseEvent event) {
        if (event.getButton() < 0 || !event.isButtonstate() || !consumeArmedPress()) {
            return;
        }
        event.setCanceled(true);
        // Cancelling also skips runTickMouse's own "click to regain focus" call, so make it here.
        // A no-op in the common case, where inGameHasFocus never went false.
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen == null && !mc.inGameHasFocus) {
            mc.setIngameFocus();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onGuiMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (Mouse.getEventButton() < 0 || !Mouse.getEventButtonState() || !consumeArmedPress()) {
            return;
        }
        event.setCanceled(true);
    }

    /** True, and disarms, when a press arriving now is the one to discard. */
    private boolean consumeArmedPress() {
        if (armedUntil == 0L) {
            return false;
        }
        boolean inWindow = Minecraft.getSystemTime() <= armedUntil;
        armedUntil = 0L;
        return inWindow;
    }

    private static boolean isEnabled() {
        if (!Loader.isModLoaded("oneconfig")) {
            return true;
        }
        return OneConfigSetting.read();
    }

    /** Kept in its own class so a client without OneConfig never resolves {@link SumOneConfig}. */
    private static final class OneConfigSetting {
        static boolean read() {
            SumOneConfig config = SumOneConfig.INSTANCE;
            return config == null || config.refocusClickGobble;
        }
    }
}
