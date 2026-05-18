package com.micatechnologies.minecraft.sum.pocket;

import cc.polyfrost.oneconfig.gui.OneConfigGui;
import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import com.micatechnologies.minecraft.sum.favorites.FavoritesClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Client-side keybinds for the pocket subsystem. Registered from {@code SumClientProxy
 * .preInit}; the {@link #onClientTick} handler converts key presses into actions.
 *
 * <p>Bindings live under SUM's existing keybind category ({@link FavoritesClientHandler
 * #CATEGORY}) so the player sees one consolidated SUM section in the Controls screen
 * instead of several near-empty ones.</p>
 */
public class PocketKeybinds {

    /** Default key: 'P' — easy to reach and not bound by vanilla. Players can rebind in
     *  Options ▸ Controls. */
    public static final KeyBinding OPEN_POCKET = new KeyBinding(
        "key.sum.pocket.open", Keyboard.KEY_P, FavoritesClientHandler.CATEGORY);

    /** Default unbound — pressing R-SHIFT (OneConfig's universal binding) also opens the
     *  page. Provided for users who want a SUM-specific shortcut. */
    public static final KeyBinding OPEN_SUM_SETTINGS = new KeyBinding(
        "key.sum.settings.open", Keyboard.KEY_NONE, FavoritesClientHandler.CATEGORY);

    public static void register() {
        ClientRegistry.registerKeyBinding(OPEN_POCKET);
        ClientRegistry.registerKeyBinding(OPEN_SUM_SETTINGS);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        // Only fire when the player is actively in the world (no other GUI open). Without
        // this, pressing a SUM key while the inventory is open would queue an action for
        // after the player closes the current screen, which is jarring.
        if (mc.currentScreen != null || mc.player == null) {
            return;
        }
        if (OPEN_POCKET.isPressed()) {
            SumNetwork.CHANNEL.sendToServer(new PacketOpenPocketGui());
        }
        if (OPEN_SUM_SETTINGS.isPressed()) {
            openSumSettingsIfAllowed(mc);
        }
    }

    /**
     * Opens the SUM OneConfig page when the player is allowed to. The settings UI is
     * server-side-affecting in spirit (even though OneConfig itself is client-only,
     * server admins may not want random players reconfiguring HUDs on shared screens or
     * tweaking knobs that could later become networked), so we gate it to singleplayer
     * sessions and to operators on multiplayer servers.
     */
    private static void openSumSettingsIfAllowed(Minecraft mc) {
        if (!isAllowed(mc)) {
            mc.player.sendStatusMessage(new TextComponentString(
                TextFormatting.YELLOW + "SUM settings are op-only on this server."),
                true);
            return;
        }
        try {
            mc.displayGuiScreen(OneConfigGui.create());
        } catch (NoClassDefFoundError missingOneConfig) {
            // Defensive: if OneConfig isn't on the classpath we don't want to crash.
            // Shouldn't happen since OneConfig is a hard runtime dep, but if a user
            // strips it out the keybind just no-ops with a chat message.
            mc.player.sendStatusMessage(new TextComponentString(
                TextFormatting.RED + "SUM settings require OneConfig (not installed)."),
                true);
        }
    }

    /**
     * Singleplayer always allowed. On multiplayer, the SUM settings page is operator-
     * only — gated by checking whether the local player has the standard op permission
     * level (>= 2, matching vanilla's "/op" default).
     */
    private static boolean isAllowed(Minecraft mc) {
        if (mc.isSingleplayer()) {
            return true;
        }
        EntityPlayerSP player = mc.player;
        if (player == null) {
            return false;
        }
        // EntityPlayerSP's permissionLevel reflects the server's view of the player's op
        // level (0 = no op, 2+ = standard op). Cheats-on-LAN bumps this for the host;
        // dedicated servers populate it from ops.json.
        return mc.player.canUseCommand(2, "");
    }
}
