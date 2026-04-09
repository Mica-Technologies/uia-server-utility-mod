package com.micatechnologies.minecraft.sum;

import com.micatechnologies.minecraft.sum.roamer.EntityRoamer;
import com.micatechnologies.minecraft.sum.roamer.ItemRoamerSpawnEgg;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
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

    @Mod.Instance(SumConstants.MOD_NAMESPACE)
    public static Sum instance;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        SumConfig.init(event.getSuggestedConfigurationFile());
        LOGGER.info("I am " + SumConstants.MOD_NAME + " at version " + SumConstants.MOD_VERSION);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
    }

    @Mod.EventBusSubscriber(modid = SumConstants.MOD_NAMESPACE)
    public static class RegistrationHandler {

        private static int entityId = 0;

        @SubscribeEvent
        public static void registerEntities(RegistryEvent.Register<EntityEntry> event) {
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
        public static void registerItems(RegistryEvent.Register<Item> event) {
            event.getRegistry().register(new ItemRoamerSpawnEgg());
        }

        @SubscribeEvent
        public static void registerBlocks(RegistryEvent.Register<Block> event) {
        }

        @SubscribeEvent
        public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        }
    }
}
