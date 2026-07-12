package com.aozainkmc.input.network;

import com.aozainkmc.input.AozaiInkInput;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> Client: up to three authoritative quick-cast candidates. */
public record QuickCastCandidatesPayload(long sessionId, long revision, List<Entry> candidates)
    implements CustomPacketPayload {

    public static final Type<QuickCastCandidatesPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(AozaiInkInput.MOD_ID, "quick_cast_candidates"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuickCastCandidatesPayload> STREAM_CODEC =
        StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarLong(payload.sessionId());
                buffer.writeVarLong(payload.revision());
                buffer.writeVarInt(payload.candidates().size());
                for (Entry entry : payload.candidates()) {
                    buffer.writeUtf(entry.glyph());
                    buffer.writeFloat(entry.confidence());
                }
            },
            buffer -> {
                long sessionId = buffer.readVarLong();
                long revision = buffer.readVarLong();
                int size = Math.min(3, Math.max(0, buffer.readVarInt()));
                List<Entry> entries = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    entries.add(new Entry(buffer.readUtf(), buffer.readFloat()));
                }
                return new QuickCastCandidatesPayload(sessionId, revision, List.copyOf(entries));
            }
        );

    public QuickCastCandidatesPayload {
        candidates = candidates == null ? List.of() : List.copyOf(candidates.subList(0, Math.min(3, candidates.size())));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(String glyph, float confidence) {}
}
