package com.micatechnologies.minecraft.sum;

import com.micatechnologies.minecraft.sum.favorites.CreativeTabFavorites;
import com.micatechnologies.minecraft.sum.favorites.FavoritesClientHandler;
import com.micatechnologies.minecraft.sum.favorites.FavoritesStore;
import com.micatechnologies.minecraft.sum.roamer.EntityRoamer;
import com.micatechnologies.minecraft.sum.roamer.RenderRoamer;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
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
        com.micatechnologies.minecraft.sum.Sum.LOGGER.info("[favorites] SumClientProxy.preInit start");
        MinecraftForge.EVENT_BUS.register(this);
        RenderingRegistry.registerEntityRenderingHandler(EntityRoamer.class, RenderRoamer::new);
        FavoritesStore.setStorageFile(event.getModConfigurationDirectory());
        CreativeTabFavorites.INSTANCE = new CreativeTabFavorites();
        com.micatechnologies.minecraft.sum.Sum.LOGGER.info("[favorites] tab constructed; registering keybinds and handler");
        FavoritesClientHandler.registerKeybinds();
        MinecraftForge.EVENT_BUS.register(new FavoritesClientHandler());
        com.micatechnologies.minecraft.sum.Sum.LOGGER.info(
            "[favorites] proxy wiring complete; toggleKey={} jumpKey={}",
            FavoritesClientHandler.TOGGLE.getKeyCode(),
            FavoritesClientHandler.JUMP.getKeyCode());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        FavoritesStore.load();
        com.micatechnologies.minecraft.sum.Sum.LOGGER.info(
            "[favorites] store loaded; size={}", FavoritesStore.size());
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
}
