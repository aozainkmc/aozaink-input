package com.aozainkmc.input.network;

import com.aozainkmc.input.AozaiInkInput;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class AozaiInkNetworking {

    private AozaiInkNetworking() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SubmitTalismanPayload.TYPE, SubmitTalismanPayload.STREAM_CODEC, AozaiInkNetworking::handleSubmitTalisman);
        registrar.playToServer(CastPaperPayload.TYPE, CastPaperPayload.STREAM_CODEC, AozaiInkNetworking::handleCastPaper);
    }

    public static void sendSubmitTalisman(SubmitTalismanPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendCastPaper(CastPaperPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private static void handleSubmitTalisman(SubmitTalismanPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            AozaiInkServerHandlers.submitTalisman(player, payload);
        });
    }

    private static void handleCastPaper(CastPaperPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            AozaiInkServerHandlers.castPaper(player, payload);
        });
    }
}
