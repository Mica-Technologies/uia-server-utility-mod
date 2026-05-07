package com.micatechnologies.minecraft.sum.favorites;

import com.micatechnologies.minecraft.sum.Sum;
import java.lang.reflect.Method;
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
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;

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

    public FavoritesClientHandler() {
        Sum.LOGGER.info("[favorites] FavoritesClientHandler instance constructed");
    }

    private boolean tickEventSeen = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!tickEventSeen) {
            tickEventSeen = true;
            Sum.LOGGER.info("[favorites] ClientTickEvent reached handler (event bus dispatch is working)");
        }
    }

    private Map<ResourceLocation, Set<Integer>> favoritesByItem = Collections.emptyMap();
    private int favoritesCacheVersion = -1;

    public static void registerKeybinds() {
        ClientRegistry.registerKeyBinding(TOGGLE);
        ClientRegistry.registerKeyBinding(JUMP);
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onKeyboardInput(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        int eventKey = Keyboard.getEventKey();
        boolean keyDown = Keyboard.getEventKeyState();

        // Unconditional diagnostic: log every B/Z event (down or up) reaching this handler,
        // regardless of which GUI is open. Helps disambiguate "event not delivered" from
        // "event delivered but bailed because GUI wasn't GuiContainerCreative".
        if (eventKey == Keyboard.KEY_B || eventKey == Keyboard.KEY_Z) {
            String guiName = event.getGui() != null ? event.getGui().getClass().getName() : "null";
            Sum.LOGGER.info("[favorites] keyboard event reached handler: key={} state={} gui={} canceled={}",
                eventKey, keyDown, guiName, event.isCanceled());
        }

        if (!(event.getGui() instanceof GuiContainerCreative)) {
            return;
        }
        if (!keyDown) {
            return;
        }
        if (eventKey == 0) {
            return;
        }

        GuiContainerCreative gui = (GuiContainerCreative) event.getGui();

        if (eventKey == TOGGLE.getKeyCode() || eventKey == JUMP.getKeyCode()) {
            Sum.LOGGER.info(
                "[favorites] keypress eventKey={} toggleKey={} jumpKey={} tab={} canceled={}",
                eventKey, TOGGLE.getKeyCode(), JUMP.getKeyCode(),
                gui.getSelectedTabIndex(), event.isCanceled());
        }

        if (eventKey == JUMP.getKeyCode()) {
            handleJump(gui, event);
            return;
        }
        if (eventKey == TOGGLE.getKeyCode()) {
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

    private void handleToggle(GuiContainerCreative gui, GuiScreenEvent.KeyboardInputEvent.Pre event) {
        // Defer to the search field's own input handling when the search tab is active.
        if (gui.getSelectedTabIndex() == CreativeTabs.SEARCH.getIndex()) {
            Sum.LOGGER.info("[favorites] toggle bailed: on SEARCH tab");
            return;
        }
        Slot slot = gui.getSlotUnderMouse();
        if (slot == null) {
            Sum.LOGGER.info("[favorites] toggle bailed: no slot under mouse");
            return;
        }
        ItemStack stack = slot.getStack();
        if (stack.isEmpty()) {
            Sum.LOGGER.info("[favorites] toggle bailed: slot {} empty", slot.slotNumber);
            return;
        }
        FavoriteKey key = FavoriteKey.of(stack);
        if (key == null) {
            Sum.LOGGER.info("[favorites] toggle bailed: stack has no registry name");
            return;
        }
        boolean nowPresent = FavoritesStore.toggle(key);
        FavoritesStore.save();
        Sum.LOGGER.info("[favorites] toggled {} -> {}", key, nowPresent ? "added" : "removed");
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
