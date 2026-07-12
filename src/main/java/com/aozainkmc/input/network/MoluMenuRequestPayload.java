package com.aozainkmc.input.network;

import com.aozainkmc.input.AozaiInkInput;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MoluMenuRequestPayload() implements CustomPacketPayload {
    public static final Type<MoluMenuRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(AozaiInkInput.MOD_ID, "molu_menu_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MoluMenuRequestPayload> STREAM_CODEC =
        StreamCodec.of((buffer, payload) -> {}, buffer -> new MoluMenuRequestPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
