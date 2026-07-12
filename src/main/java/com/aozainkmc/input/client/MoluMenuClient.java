package com.aozainkmc.input.client;

import com.aozainkmc.input.AozaiInkInput;
import com.aozainkmc.input.network.MoluMenuRequestPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = AozaiInkInput.MOD_ID, value = Dist.CLIENT)
public final class MoluMenuClient {
    public static final KeyMapping OPEN_MENU = new KeyMapping(
        "key.aozaink_input.open_molu_menu", GLFW.GLFW_KEY_M, "key.categories.aozaink_input");
    private MoluMenuClient() {}
    @SubscribeEvent public static void registerKeys(RegisterKeyMappingsEvent event) { event.register(OPEN_MENU); }
    @SubscribeEvent public static void clientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_MENU.consumeClick()) {
            if (mc.player != null && mc.screen == null) PacketDistributor.sendToServer(new MoluMenuRequestPayload());
        }
    }
    @SubscribeEvent public static void renderLevel(RenderLevelStageEvent event) {
        InputBindingRitualRenderer.render(event);
    }
}
