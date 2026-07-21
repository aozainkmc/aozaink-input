package com.aozainkmc.input;

import com.aozainkmc.core.AozaiInkCoreApi;
import com.aozainkmc.input.network.PaperCastStateManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = AozaiInkInput.MOD_ID)
public final class AozaiInkServerEvents {

    private AozaiInkServerEvents() {}

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        AozaiInkCoreApi.markStore().clearAll();
        PaperCastStateManager.clear();
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PaperCastStateManager.onPlayerLoggedIn(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PaperCastStateManager.onPlayerLoggedOut(player);
        }
    }
}
