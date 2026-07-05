package com.aozainkmc.input.network;

import com.aozainkmc.core.api.InkTrace;
import com.aozainkmc.input.AozaiInkInput;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server: the player has finished writing on a temporary paper plane.
 * Only the raw ink trace and minimal source metadata are sent; the server performs
 * authoritative recognition and broadcasts the recognized event.
 */
public record CastPaperPayload(
    InkTrace trace,
    String sourceId,
    float powerMultiplier,
    long ttlTicks
) implements CustomPacketPayload {

    public static final Type<CastPaperPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(AozaiInkInput.MOD_ID, "cast_paper"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CastPaperPayload> STREAM_CODEC =
        StreamCodec.of(
            (buffer, payload) -> {
                InkTraceCodec.TRACE.encode(buffer, payload.trace());
                buffer.writeUtf(payload.sourceId());
                buffer.writeFloat(payload.powerMultiplier());
                buffer.writeVarLong(payload.ttlTicks());
            },
            buffer -> new CastPaperPayload(
                InkTraceCodec.TRACE.decode(buffer),
                buffer.readUtf(),
                buffer.readFloat(),
                buffer.readVarLong()
            )
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
