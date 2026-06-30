package com.aozainkmc.input;

import com.aozainkmc.core.AozaiInkCoreApi;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = AozaiInkInput.MOD_ID)
public final class AozaiInkServerEvents {

    private AozaiInkServerEvents() {}

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        AozaiInkCoreApi.markStore().clearAll();
    }
}
