package com.aozainkmc.input.signal;

import com.aozainkmc.core.api.InkModuleSignalEvent;
import com.aozainkmc.input.AozaiInkInput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

public final class InputSignals {
    public static final ResourceLocation TALISMAN_CREATED =
        ResourceLocation.fromNamespaceAndPath(AozaiInkInput.MOD_ID, "talisman_created");
    public static final ResourceLocation TAIL_MODIFIER_CHAOS =
        ResourceLocation.fromNamespaceAndPath(AozaiInkInput.MOD_ID, "tail_modifier_chaos");

    private InputSignals() {}

    public static void talismanCreated(ServerPlayer player, String type, String grade, boolean tail) {
        CompoundTag payload = new CompoundTag();
        payload.putString("type", type);
        payload.putString("grade", grade);
        payload.putBoolean("tail", tail);
        emit(player, TALISMAN_CREATED, payload);
    }

    public static void tailModifierChaos(ServerPlayer player, boolean escaped, boolean hitPlayer, boolean killedEntity) {
        CompoundTag payload = new CompoundTag();
        payload.putBoolean("escaped", escaped);
        payload.putBoolean("hit_player", hitPlayer);
        payload.putBoolean("killed_entity", killedEntity);
        emit(player, TAIL_MODIFIER_CHAOS, payload);
    }

    private static void emit(ServerPlayer player, ResourceLocation signalId, CompoundTag payload) {
        if (player != null) {
            NeoForge.EVENT_BUS.post(new InkModuleSignalEvent(player, signalId, payload));
        }
    }
}
