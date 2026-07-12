package com.aozainkmc.input.network;

import com.aozainkmc.core.api.InkTrace;
import com.aozainkmc.input.AozaiInkInput;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> Server: recognize the current paper strokes without casting yet. */
public record PreviewQuickCastPayload(
    InkTrace trace,
    String sourceId,
    float powerMultiplier,
    long ttlTicks,
    long revision
) implements CustomPacketPayload {
    public static final Type<PreviewQuickCastPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(AozaiInkInput.MOD_ID, "preview_quick_cast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PreviewQuickCastPayload> STREAM_CODEC =
        StreamCodec.of(
            (buffer, payload) -> {
                InkTraceCodec.TRACE.encode(buffer, payload.trace());
                buffer.writeUtf(payload.sourceId());
                buffer.writeFloat(payload.powerMultiplier());
                buffer.writeVarLong(payload.ttlTicks());
                buffer.writeVarLong(payload.revision());
            },
            buffer -> new PreviewQuickCastPayload(
                InkTraceCodec.TRACE.decode(buffer), buffer.readUtf(), buffer.readFloat(),
                buffer.readVarLong(), buffer.readVarLong())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
