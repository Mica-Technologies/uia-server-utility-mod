package com.micatechnologies.minecraft.sum.pocket;

import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import com.micatechnologies.minecraft.sum.favorites.FavoritesClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Client-side keybinds for the pocket subsystem. Registered from {@code SumClientProxy
 * .preInit}; the {@link #onClientTick} handler converts key presses into actions (network
 * packet to open the GUI, screen swap to enter the layout editor).
 *
 * <p>Bindings live under SUM's existing keybind category ({@link FavoritesClientHandler
 * #CATEGORY}) so the player sees one consolidated SUM section in the Controls screen
 * instead of two near-empty ones.
 */
public class PocketKeybinds {

    /** Default key: 'P' — easy to reach and not bound by vanilla. Players can rebind in
     *  Options ▸ Controls. */
    public static final KeyBinding OPEN_POCKET = new KeyBinding(
        "key.sum.pocket.open", Keyboard.KEY_P, FavoritesClientHandler.CATEGORY);

    /** Default key: 'L' for "layout" — also unbound by vanilla. */
    public static final KeyBinding EDIT_HUD = new KeyBinding(
        "key.sum.pocket.edit_hud", Keyboard.KEY_NONE, FavoritesClientHandler.CATEGORY);

    public static void register() {
        ClientRegistry.registerKeyBinding(OPEN_POCKET);
        ClientRegistry.registerKeyBinding(EDIT_HUD);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        // Only fire when the player is actively in the world (no other GUI open). Without
        // this, pressing P while the inventory is open would queue an open-pocket request
        // for after the player closes the current screen, which is jarring.
        if (mc.currentScreen != null || mc.player == null) {
            return;
        }
        if (OPEN_POCKET.isPressed()) {
            SumNetwork.CHANNEL.sendToServer(new PacketOpenPocketGui());
        }
        if (EDIT_HUD.isPressed()) {
            mc.displayGuiScreen(new GuiPocketHudEditor());
        }
    }
}
