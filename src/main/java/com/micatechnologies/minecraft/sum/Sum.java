package com.micatechnologies.minecraft.sum;

import com.micatechnologies.minecraft.sum.roadrunner.RoadRunnerHandler;
import com.micatechnologies.minecraft.sum.roamer.EntityRoamer;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = SumConstants.MOD_NAMESPACE, version = SumConstants.MOD_VERSION, name = SumConstants.MOD_NAME, acceptedMinecraftVersions = "[1.12.2]")
public class Sum {

    public static final Logger LOGGER = LogManager.getLogger(SumConstants.MOD_NAMESPACE);

    @SidedProxy(clientSide = "com.micatechnologies.minecraft.sum.SumClientProxy",
                serverSide = "com.micatechnologies.minecraft.sum.SumCommonProxy")
    public static SumProxy proxy;

    @Mod.Instance(SumConstants.MOD_NAMESPACE)
    public static Sum instance;

    private static int entityId = 0;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        SumConfig.init(event.getSuggestedConfigurationFile());
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new RoadRunnerHandler());
        proxy.preInit(event);
        SumTab.initTabElements();
        LOGGER.info("I am " + SumConstants.MOD_NAME + " at version " + SumConstants.MOD_VERSION);
    }

    @SubscribeEvent
    public void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        event.getRegistry().register(
            EntityEntryBuilder.create()
                .entity(EntityRoamer.class)
                .id(new ResourceLocation(SumConstants.MOD_NAMESPACE, "roamer"), entityId++)
                .name("roamer")
                .tracker(80, 3, true)
                .build()
        );
    }

    @SubscribeEvent
    public void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(SumRegistry.getItems().toArray(new Item[0]));
    }

    @SubscribeEvent
    public void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().registerAll(SumRegistry.getBlocks().toArray(new Block[0]));
    }

    @SubscribeEvent
    public void registerRecipes(RegistryEvent.Register<IRecipe> event) {
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
    }
}
