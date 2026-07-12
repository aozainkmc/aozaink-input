package com.aozainkmc.input.network;

import com.aozainkmc.input.AozaiInkInput;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClearQuickBindingPayload(int slot) implements CustomPacketPayload {
    public static final Type<ClearQuickBindingPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(AozaiInkInput.MOD_ID, "clear_quick_binding"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClearQuickBindingPayload> STREAM_CODEC =
        StreamCodec.of((buffer, payload) -> buffer.writeVarInt(payload.slot()),
            buffer -> new ClearQuickBindingPayload(buffer.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
