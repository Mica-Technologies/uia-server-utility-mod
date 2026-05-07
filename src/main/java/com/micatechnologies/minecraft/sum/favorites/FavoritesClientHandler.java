package com.micatechnologies.minecraft.sum.favorites;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

public class FavoritesClientHandler {

    public static final String CATEGORY = "key.sum.category";

    public static final KeyBinding TOGGLE = new KeyBinding(
        "key.sum.favorites.toggle", Keyboard.KEY_B, CATEGORY);

    public static void registerKeybinds() {
        ClientRegistry.registerKeyBinding(TOGGLE);
    }

    @SubscribeEvent
    public void onKeyboardInput(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!(event.getGui() instanceof GuiContainerCreative)) {
            return;
        }
        if (!Keyboard.getEventKeyState()) {
            return;
        }
        int eventKey = Keyboard.getEventKey();
        if (eventKey == 0) {
            return;
        }

        GuiContainerCreative gui = (GuiContainerCreative) event.getGui();

        if (eventKey == TOGGLE.getKeyCode()) {
            handleToggle(gui, event);
        }
    }

    private void handleToggle(GuiContainerCreative gui, GuiScreenEvent.KeyboardInputEvent.Pre event) {
        // Defer to the search field's own input handling when the search tab is active.
        if (gui.getSelectedTabIndex() == CreativeTabs.SEARCH.getIndex()) {
            return;
        }
        Slot slot = gui.getSlotUnderMouse();
        if (slot == null) {
            return;
        }
        ItemStack stack = slot.getStack();
        if (stack.isEmpty()) {
            return;
        }
        FavoriteKey key = FavoriteKey.of(stack);
        if (key == null) {
            return;
        }
        boolean nowPresent = FavoritesStore.toggle(key);
        FavoritesStore.save();
        playFeedback(nowPresent);
        event.setCanceled(true);
    }

    private void playFeedback(boolean added) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return;
        }
        float pitch = added ? 1.5F : 0.7F;
        mc.player.playSound(SoundEvents.BLOCK_NOTE_PLING, 0.4F, pitch);
    }
}
