package com.aozainkmc.input.network;

import com.aozainkmc.input.AozaiInkInput;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Client -> Server: the local player has opened a temporary paper plane.
 */
public record ServerboundPaperOpenPayload(
    Vec3 center,
    Vec3 normal,
    Vec3 right,
    Vec3 up,
    float radius
) implements CustomPacketPayload {

    public static final Type<ServerboundPaperOpenPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(AozaiInkInput.MOD_ID, "paper_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundPaperOpenPayload> STREAM_CODEC =
        StreamCodec.of(
            (buffer, payload) -> {
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
            buffer -> new ServerboundPaperOpenPayload(
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
