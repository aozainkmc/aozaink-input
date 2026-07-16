package com.aozainkmc.input.network;

import com.aozainkmc.input.AozaiInkInput;
import com.aozainkmc.input.api.MoluMenuRegistry;
import com.aozainkmc.input.binding.QuickGlyphBinding;
import java.util.ArrayList;
import java.util.List;
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
        registrar.playToServer(PreviewQuickCastPayload.TYPE, PreviewQuickCastPayload.STREAM_CODEC,
            AozaiInkNetworking::handlePreviewQuickCast);
        registrar.playToServer(SelectQuickCastCandidatePayload.TYPE, SelectQuickCastCandidatePayload.STREAM_CODEC,
            AozaiInkNetworking::handleQuickCastSelection);
        registrar.playToClient(QuickCastCandidatesPayload.TYPE, QuickCastCandidatesPayload.STREAM_CODEC,
            AozaiInkNetworking::handleQuickCastCandidates);
        registrar.playToClient(TalismanFormationPayload.TYPE, TalismanFormationPayload.STREAM_CODEC,
            AozaiInkNetworking::handleTalismanFormation);
        registrar.playToServer(MoluMenuRequestPayload.TYPE, MoluMenuRequestPayload.STREAM_CODEC,
            AozaiInkNetworking::handleMenuRequest);
        registrar.playToServer(ClearQuickBindingPayload.TYPE, ClearQuickBindingPayload.STREAM_CODEC,
            AozaiInkNetworking::handleClearBinding);
        registrar.playToClient(MoluMenuPayload.TYPE, MoluMenuPayload.STREAM_CODEC, AozaiInkNetworking::handleMenu);
        registrar.playToClient(InputBindingRitualPayload.TYPE, InputBindingRitualPayload.STREAM_CODEC,
            AozaiInkNetworking::handleBindingRitual);
    }

    public static void sendSubmitTalisman(SubmitTalismanPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendCastPaper(CastPaperPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendPreviewQuickCast(PreviewQuickCastPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendQuickCastSelection(SelectQuickCastCandidatePayload payload) {
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

    private static void handlePreviewQuickCast(PreviewQuickCastPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                AozaiInkServerHandlers.previewQuickCast(player, payload);
            }
        });
    }

    private static void handleQuickCastSelection(SelectQuickCastCandidatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                AozaiInkServerHandlers.selectQuickCastCandidate(player, payload);
            }
        });
    }

    private static void handleQuickCastCandidates(QuickCastCandidatesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> AozaiInkClientPayloadHandler.handleQuickCastCandidates(payload));
    }

    private static void handleTalismanFormation(TalismanFormationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> AozaiInkClientPayloadHandler.handleTalismanFormation(payload));
    }

    private static void handleMenuRequest(MoluMenuRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) sendMenu(player);
        });
    }

    private static void handleClearBinding(ClearQuickBindingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                QuickGlyphBinding.bind(player, QuickGlyphBinding.toChineseDigit(payload.slot()), "");
            }
        });
    }

    private static void handleMenu(MoluMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> AozaiInkClientPayloadHandler.handleMenu(payload));
    }

    private static void handleBindingRitual(InputBindingRitualPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> AozaiInkClientPayloadHandler.handleBindingRitual(payload));
    }

    public static void sendMenu(ServerPlayer player) {
        List<MoluMenuPayload.BindingEntry> bindings = new ArrayList<>();
        for (int slot = 1; slot <= 9; slot++) {
            final int bindingSlot = slot;
            QuickGlyphBinding.get(player, QuickGlyphBinding.toChineseDigit(bindingSlot)).ifPresent(binding ->
                bindings.add(new MoluMenuPayload.BindingEntry(bindingSlot, binding.glyph(), binding.owner())));
        }
        List<MoluMenuPayload.GlyphEntry> glyphs = MoluMenuRegistry.glyphs().stream()
            .map(entry -> new MoluMenuPayload.GlyphEntry(entry.owner(), entry.glyph(), entry.brief(), entry.detail()))
            .toList();
        List<MoluMenuPayload.TabEntry> tabs = MoluMenuRegistry.tabsFor(player).stream()
            .map(tab -> new MoluMenuPayload.TabEntry(tab.id(), tab.label(), tab.title(), tab.columns(), tab.rows()))
            .toList();
        PacketDistributor.sendToPlayer(player, new MoluMenuPayload(bindings, glyphs, tabs));
    }
}
