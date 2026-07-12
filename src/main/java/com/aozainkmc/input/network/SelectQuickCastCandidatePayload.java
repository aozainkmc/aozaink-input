package com.aozainkmc.input.network;

import com.aozainkmc.input.AozaiInkInput;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> Server: choose candidate 0, 1, or 2 from the current session. */
public record SelectQuickCastCandidatePayload(long sessionId, int index) implements CustomPacketPayload {
    public static final Type<SelectQuickCastCandidatePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(AozaiInkInput.MOD_ID, "select_quick_cast_candidate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectQuickCastCandidatePayload> STREAM_CODEC =
        StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarLong(payload.sessionId());
                buffer.writeVarInt(payload.index());
            },
            buffer -> new SelectQuickCastCandidatePayload(buffer.readVarLong(), buffer.readVarInt())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
