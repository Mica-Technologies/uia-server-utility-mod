package com.micatechnologies.minecraft.sum.pauser;

import com.micatechnologies.minecraft.sum.SumConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameRules;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;

public class ServerPauserHandler {

    private static final String RULE_DAYLIGHT = "doDaylightCycle";
    private static final String RULE_WEATHER = "doWeatherCycle";

    public void onServerStarting(FMLServerStartingEvent event) {
        if (!SumConfig.isPauserEnabled()) {
            return;
        }
        applyPaused(event.getServer());
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        if (!SumConfig.isPauserEnabled()) {
            return;
        }
        MinecraftServer server = event.player.getServer();
        if (server == null) {
            return;
        }
        if (server.getPlayerList().getCurrentPlayerCount() == 1) {
            applyActive(server);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (!SumConfig.isPauserEnabled()) {
            return;
        }
        MinecraftServer server = event.player.getServer();
        if (server == null) {
            return;
        }
        if (server.getPlayerList().getCurrentPlayerCount() == 0) {
            applyPaused(server);
        }
    }

    private void applyPaused(MinecraftServer server) {
        GameRules rules = overworldRules(server);
        if (rules == null) {
            return;
        }
        if (SumConfig.isPauserPauseDaylight()) {
            rules.setOrCreateGameRule(RULE_DAYLIGHT, "false");
        }
        if (SumConfig.isPauserPauseWeather()) {
            rules.setOrCreateGameRule(RULE_WEATHER, "false");
        }
    }

    private void applyActive(MinecraftServer server) {
        GameRules rules = overworldRules(server);
        if (rules == null) {
            return;
        }
        if (SumConfig.isPauserPauseDaylight()) {
            rules.setOrCreateGameRule(RULE_DAYLIGHT, "true");
        }
        if (SumConfig.isPauserPauseWeather()) {
            rules.setOrCreateGameRule(RULE_WEATHER, "true");
        }
    }

    private static GameRules overworldRules(MinecraftServer server) {
        WorldServer overworld = server.getWorld(0);
        return overworld == null ? null : overworld.getGameRules();
    }
}
