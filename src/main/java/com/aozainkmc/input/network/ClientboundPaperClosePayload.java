package com.aozainkmc.input.network;

import com.aozainkmc.input.AozaiInkInput;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> Client: another player has closed the temporary paper plane.
 */
public record ClientboundPaperClosePayload(UUID playerId) implements CustomPacketPayload {

    public static final Type<ClientboundPaperClosePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(AozaiInkInput.MOD_ID, "paper_closed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundPaperClosePayload> STREAM_CODEC =
        StreamCodec.of(
            (buffer, payload) -> buffer.writeUUID(payload.playerId()),
            buffer -> new ClientboundPaperClosePayload(buffer.readUUID())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
