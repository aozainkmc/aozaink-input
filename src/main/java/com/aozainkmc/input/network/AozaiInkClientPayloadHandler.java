package com.aozainkmc.input.network;

import com.aozainkmc.input.client.BindingRitualCameraTransition;
import com.aozainkmc.input.client.InputBindingRitualRenderer;
import com.aozainkmc.input.client.MoluMenuScreen;
import com.aozainkmc.input.client.QuickCastCandidateClient;
import com.aozainkmc.input.client.TalismanFormationRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class AozaiInkClientPayloadHandler {
    private AozaiInkClientPayloadHandler() {}

    static void handleQuickCastCandidates(QuickCastCandidatesPayload payload) {
        QuickCastCandidateClient.show(payload);
    }

    static void handleTalismanFormation(TalismanFormationPayload payload) {
        TalismanFormationRenderer.add(payload);
    }

    static void handleMenu(MoluMenuPayload payload) {
        Minecraft.getInstance().setScreen(new MoluMenuScreen(payload));
    }

    static void handleBindingRitual(InputBindingRitualPayload payload) {
        InputBindingRitualRenderer.add(payload);
        BindingRitualCameraTransition.start(Minecraft.getInstance(), payload.playerId());
    }
}
