package com.micatechnologies.minecraft.sum;

import com.micatechnologies.minecraft.sum.atm.GuiSumAtm;
import com.micatechnologies.minecraft.sum.phone.GuiSumPhone;
import com.micatechnologies.minecraft.sum.economy.TESRBillsDisplay;
import com.micatechnologies.minecraft.sum.economy.TileEntityBillsDisplay;
import com.micatechnologies.minecraft.sum.favorites.CreativeTabFavorites;
import com.micatechnologies.minecraft.sum.favorites.FavoritesClientHandler;
import com.micatechnologies.minecraft.sum.favorites.FavoritesStore;
import com.micatechnologies.minecraft.sum.huds.HudStateTracker;
import com.micatechnologies.minecraft.sum.pocket.PocketKeybinds;
import com.micatechnologies.minecraft.sum.pocket.SumOneConfig;
import com.micatechnologies.minecraft.sum.roamer.EntityRoamer;
import com.micatechnologies.minecraft.sum.roamer.RenderRoamer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class SumClientProxy implements SumProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        RenderingRegistry.registerEntityRenderingHandler(EntityRoamer.class, RenderRoamer::new);
        net.minecraftforge.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
            TileEntityBillsDisplay.class, new TESRBillsDisplay());
        FavoritesStore.setStorageFile(event.getModConfigurationDirectory());
        CreativeTabFavorites.INSTANCE = new CreativeTabFavorites();
        FavoritesClientHandler.registerKeybinds();
        MinecraftForge.EVENT_BUS.register(new FavoritesClientHandler());

        // SUM OneConfig — the simple `new SumOneConfig()` fires its constructor which
        // registers the SUM mod with OneConfig and exposes any @HUD fields. OneConfig
        // drives all HUD rendering, persistence, and drag-to-reposition UX, so SUM
        // doesn't register its own RenderGameOverlayEvent handler for the HUD.
        new SumOneConfig();
        // The stateful counter HUDs (CPS, click count, blocks placed, session playtime)
        // need a real event subscriber to keep their counters fresh — HUD modules are
        // managed by OneConfig and can't subscribe to events themselves.
        MinecraftForge.EVENT_BUS.register(new HudStateTracker());
        PocketKeybinds.register();
        MinecraftForge.EVENT_BUS.register(new PocketKeybinds());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        FavoritesStore.load();
    }

    @SubscribeEvent
    public void registerModels(ModelRegistryEvent event) {
        SumRegistry.getItems().forEach(item -> {
            setCustomModelResourceLocation(item, 0, "inventory");
        });
    }

    @Override
    public void setCustomModelResourceLocation(Item item, int meta, String id) {
        if (item != null && item.getRegistryName() != null) {
            ModelLoader.setCustomModelResourceLocation(item, meta,
                new ModelResourceLocation(item.getRegistryName(), id));
        }
    }

    @Override
    public void openAccountAccessGui(EntityPlayer player) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiSumAtm(player));
    }

    @Override
    public void openPhoneGui(EntityPlayer player, boolean enableBanking) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiSumPhone(player, enableBanking));
    }
}
