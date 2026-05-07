package com.micatechnologies.minecraft.sum.favorites;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

public class FavoritesClientHandler {

    public static final String CATEGORY = "key.sum.category";

    public static final KeyBinding TOGGLE = new KeyBinding(
        "key.sum.favorites.toggle", Keyboard.KEY_B, CATEGORY);

    private static final ResourceLocation STAR_TEX =
        new ResourceLocation("sum", "textures/gui/favorite_star.png");

    private Map<ResourceLocation, Set<Integer>> favoritesByItem = Collections.emptyMap();
    private int favoritesCacheVersion = -1;

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

    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.getGui() instanceof GuiContainerCreative)) {
            return;
        }
        refreshCacheIfStale();
        if (favoritesByItem.isEmpty()) {
            return;
        }

        GuiContainerCreative gui = (GuiContainerCreative) event.getGui();
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(STAR_TEX);

        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.translate(0.0F, 0.0F, 300.0F);

        int guiLeft = gui.getGuiLeft();
        int guiTop = gui.getGuiTop();

        for (Slot slot : gui.inventorySlots.inventorySlots) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || !isFavoritedStack(stack)) {
                continue;
            }
            int x = guiLeft + slot.xPos + 9;
            int y = guiTop + slot.yPos - 1;
            Gui.drawScaledCustomSizeModalRect(x, y, 0, 0, 16, 16, 8, 8, 16.0F, 16.0F);
        }

        GlStateManager.popMatrix();
        GlStateManager.disableBlend();
    }

    private boolean isFavoritedStack(ItemStack stack) {
        ResourceLocation name = stack.getItem().getRegistryName();
        if (name == null) {
            return false;
        }
        Set<Integer> metas = favoritesByItem.get(name);
        return metas != null && metas.contains(stack.getMetadata());
    }

    private void refreshCacheIfStale() {
        int currentVersion = FavoritesStore.getVersion();
        if (currentVersion == favoritesCacheVersion) {
            return;
        }
        Map<ResourceLocation, Set<Integer>> idx = new HashMap<>();
        for (FavoriteKey key : FavoritesStore.snapshot()) {
            idx.computeIfAbsent(key.getRegistryName(), k -> new HashSet<>())
                .add(key.getMeta());
        }
        favoritesByItem = idx;
        favoritesCacheVersion = currentVersion;
    }
}
