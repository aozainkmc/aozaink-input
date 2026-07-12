package com.aozainkmc.input.network;

import com.aozainkmc.input.AozaiInkInput;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record InputBindingRitualPayload(UUID playerId, double x, double y, double z, String digit, String glyph)
        implements CustomPacketPayload {
    public static final Type<InputBindingRitualPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(AozaiInkInput.MOD_ID, "binding_ritual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InputBindingRitualPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            buffer.writeUUID(payload.playerId());
            buffer.writeDouble(payload.x()); buffer.writeDouble(payload.y()); buffer.writeDouble(payload.z());
            buffer.writeUtf(payload.digit(), 8); buffer.writeUtf(payload.glyph(), 8);
        }, buffer -> new InputBindingRitualPayload(buffer.readUUID(), buffer.readDouble(), buffer.readDouble(),
            buffer.readDouble(), buffer.readUtf(8), buffer.readUtf(8)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
