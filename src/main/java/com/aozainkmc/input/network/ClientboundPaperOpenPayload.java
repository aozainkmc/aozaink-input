package com.aozainkmc.input.network;

import com.aozainkmc.input.AozaiInkInput;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Server -> Client: another player has opened a temporary paper plane.
 */
public record ClientboundPaperOpenPayload(
    UUID playerId,
    Vec3 center,
    Vec3 normal,
    Vec3 right,
    Vec3 up,
    float radius
) implements CustomPacketPayload {

    public static final Type<ClientboundPaperOpenPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(AozaiInkInput.MOD_ID, "paper_opened"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundPaperOpenPayload> STREAM_CODEC =
        StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.playerId());
                buffer.writeDouble(payload.center().x);
                buffer.writeDouble(payload.center().y);
                buffer.writeDouble(payload.center().z);
                buffer.writeDouble(payload.normal().x);
                buffer.writeDouble(payload.normal().y);
                buffer.writeDouble(payload.normal().z);
                buffer.writeDouble(payload.right().x);
                buffer.writeDouble(payload.right().y);
                buffer.writeDouble(payload.right().z);
                buffer.writeDouble(payload.up().x);
                buffer.writeDouble(payload.up().y);
                buffer.writeDouble(payload.up().z);
                buffer.writeFloat(payload.radius());
            },
            buffer -> new ClientboundPaperOpenPayload(
                buffer.readUUID(),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readFloat()
            )
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
