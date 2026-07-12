package com.aozainkmc.input.network;

import com.aozainkmc.input.AozaiInkInput;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TalismanFormationPayload(BlockPos pos, UUID ownerId, boolean chaos) implements CustomPacketPayload {
    public static final Type<TalismanFormationPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(AozaiInkInput.MOD_ID, "talisman_formation")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TalismanFormationPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            buffer.writeBlockPos(payload.pos);
            buffer.writeUUID(payload.ownerId);
            buffer.writeBoolean(payload.chaos);
        },
        buffer -> new TalismanFormationPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
