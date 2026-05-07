package com.micatechnologies.minecraft.sum.favorites;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.SumConfig;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
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
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class FavoritesClientHandler {

    public static final String CATEGORY = "key.sum.category";

    public static final KeyBinding TOGGLE = new KeyBinding(
        "key.sum.favorites.toggle", Keyboard.KEY_B, CATEGORY);

    public static final KeyBinding JUMP = new KeyBinding(
        "key.sum.favorites.jump", Keyboard.KEY_Z, CATEGORY);

    private static final ResourceLocation STAR_TEX =
        new ResourceLocation("sum", "textures/gui/favorite_star.png");

    private static final Method SET_CURRENT_CREATIVE_TAB = resolveSetTabMethod();

    private static Method resolveSetTabMethod() {
        try {
            return ReflectionHelper.findMethod(GuiContainerCreative.class,
                "setCurrentCreativeTab", "func_147050_b", CreativeTabs.class);
        } catch (Exception e) {
            Sum.LOGGER.error("Could not resolve GuiContainerCreative#setCurrentCreativeTab; "
                + "jump-to-favorites will be disabled.", e);
            return null;
        }
    }

    private Map<ResourceLocation, Set<Integer>> favoritesByItem = Collections.emptyMap();
    private int favoritesCacheVersion = -1;

    public static void registerKeybinds() {
        ClientRegistry.registerKeyBinding(TOGGLE);
        ClientRegistry.registerKeyBinding(JUMP);
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
        if (eventKey == JUMP.getKeyCode()) {
            handleJump(gui, event);
        } else if (eventKey == TOGGLE.getKeyCode()) {
            handleToggle(gui, event);
        }
    }

    private void handleJump(GuiContainerCreative gui, GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (CreativeTabFavorites.INSTANCE == null || SET_CURRENT_CREATIVE_TAB == null) {
            return;
        }
        try {
            SET_CURRENT_CREATIVE_TAB.invoke(gui, CreativeTabFavorites.INSTANCE);
            event.setCanceled(true);
        } catch (Exception e) {
            Sum.LOGGER.error("Failed to invoke setCurrentCreativeTab", e);
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.getGui() instanceof GuiContainerCreative)) {
            return;
        }
        int dWheel = Mouse.getEventDWheel();
        if (dWheel == 0) {
            return;
        }
        if (!GuiScreen.isShiftKeyDown()) {
            return;
        }
        GuiContainerCreative gui = (GuiContainerCreative) event.getGui();
        if (CreativeTabFavorites.INSTANCE == null
            || gui.getSelectedTabIndex() != CreativeTabFavorites.INSTANCE.getIndex()) {
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
        int index = FavoritesStore.indexOf(key);
        if (index < 0) {
            return;
        }
        int target = dWheel > 0 ? index - 1 : index + 1;
        if (FavoritesStore.swap(index, target)) {
            FavoritesStore.save();
            // ContainerCreative.itemList is a snapshot from when the tab was selected, so the
            // visible slot stacks don't update on their own when the underlying store changes.
            // Re-running setCurrentCreativeTab clears that snapshot and repopulates it from
            // CreativeTabFavorites.displayAllRelevantItems, which now reads the new order.
            refreshFavoritesTab(gui);
            event.setCanceled(true);
        }
    }

    private void refreshFavoritesTab(GuiContainerCreative gui) {
        if (SET_CURRENT_CREATIVE_TAB == null || CreativeTabFavorites.INSTANCE == null) {
            return;
        }
        try {
            SET_CURRENT_CREATIVE_TAB.invoke(gui, CreativeTabFavorites.INSTANCE);
        } catch (Exception e) {
            Sum.LOGGER.error("Failed to refresh favorites tab after reorder", e);
        }
    }

    private void handleToggle(GuiContainerCreative gui, GuiScreenEvent.KeyboardInputEvent.Pre event) {
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
        // If the favorites tab is currently displayed, refresh its snapshot so the add/remove
        // shows up immediately (e.g. toggling on a hotbar slot from inside the favorites tab).
        if (CreativeTabFavorites.INSTANCE != null
            && gui.getSelectedTabIndex() == CreativeTabFavorites.INSTANCE.getIndex()) {
            refreshFavoritesTab(gui);
        }
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
        if (!SumConfig.isFavoritesStarOverlayEnabled()) {
            return;
        }
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
