package com.micatechnologies.minecraft.sum;

import com.micatechnologies.minecraft.sum.atm.GuiSumAtm;
import com.micatechnologies.minecraft.sum.phone.GuiSumPhone;
import com.micatechnologies.minecraft.sum.economy.TESRBillsDisplay;
import com.micatechnologies.minecraft.sum.economy.TileEntityBillsDisplay;
import com.micatechnologies.minecraft.sum.favorites.CreativeTabFavorites;
import com.micatechnologies.minecraft.sum.favorites.FavoritesClientHandler;
import com.micatechnologies.minecraft.sum.favorites.FavoritesStore;
import com.micatechnologies.minecraft.sum.huds.HudStateTracker;
import com.micatechnologies.minecraft.sum.music.NowPlayingTracker;
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

        // OneConfig drives every SUM HUD and the pocket UI, and it's an optional (after:)
        // dependency — so all of the OneConfig-touching registrations are gated on it actually
        // being installed. When it isn't, SUM simply runs without its HUDs/pocket rather than
        // crashing at class-load; the economy, blocks, items and TESRs above don't need it.
        if (net.minecraftforge.fml.common.Loader.isModLoaded("oneconfig")) {
            // `new SumOneConfig()` fires its constructor, which registers the SUM mod with
            // OneConfig and exposes its @HUD fields. OneConfig owns HUD rendering, persistence
            // and drag-to-reposition, so SUM registers no RenderGameOverlayEvent handler itself.
            new SumOneConfig();
            // The stateful counter HUDs (CPS, click count, blocks placed, session playtime)
            // need a real event subscriber to keep their counters fresh — HUD modules are
            // managed by OneConfig and can't subscribe to events themselves.
            MinecraftForge.EVENT_BUS.register(new HudStateTracker());
            // Feeds the Now Playing HUD — captures music/record sounds via PlaySoundEvent and
            // polls SoundHandler to notice when they finish (no stop event exists).
            MinecraftForge.EVENT_BUS.register(new NowPlayingTracker());
            PocketKeybinds.register();
            MinecraftForge.EVENT_BUS.register(new PocketKeybinds());
        } else {
            Sum.LOGGER.warn("OneConfig is not installed; SUM's HUDs and pocket UI are disabled "
                + "for this session (everything else, including the economy, still works).");
        }
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
