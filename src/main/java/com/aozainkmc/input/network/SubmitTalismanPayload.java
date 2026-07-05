package com.aozainkmc.input.network;

import com.aozainkmc.core.api.InkTrace;
import com.aozainkmc.input.AozaiInkInput;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server: the player has finished writing on a yellow talisman block.
 * Only the raw ink traces are sent; the server performs authoritative recognition,
 * scoring, and item creation.
 */
public record SubmitTalismanPayload(BlockPos pos, List<Slot> slots) implements CustomPacketPayload {

    public record Slot(boolean present, InkTrace trace) {}

    public static final Type<SubmitTalismanPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(AozaiInkInput.MOD_ID, "submit_talisman"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SubmitTalismanPayload> STREAM_CODEC =
        StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos);
                buffer.writeVarInt(payload.slots.size());
                for (Slot slot : payload.slots) {
                    buffer.writeBoolean(slot.present());
                    InkTraceCodec.TRACE.encode(buffer, slot.trace());
                }
            },
            buffer -> {
                BlockPos pos = buffer.readBlockPos();
                int count = buffer.readVarInt();
                List<Slot> slots = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    boolean present = buffer.readBoolean();
                    InkTrace trace = InkTraceCodec.TRACE.decode(buffer);
                    slots.add(new Slot(present, trace));
                }
                return new SubmitTalismanPayload(pos, slots);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
