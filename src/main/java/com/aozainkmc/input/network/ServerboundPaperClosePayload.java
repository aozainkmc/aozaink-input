package com.aozainkmc.input.network;

import com.aozainkmc.input.AozaiInkInput;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server: the local player has closed the temporary paper plane.
 */
public record ServerboundPaperClosePayload() implements CustomPacketPayload {

    public static final Type<ServerboundPaperClosePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(AozaiInkInput.MOD_ID, "paper_close"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundPaperClosePayload> STREAM_CODEC =
        StreamCodec.of(
            (buffer, payload) -> {},
            buffer -> new ServerboundPaperClosePayload()
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
