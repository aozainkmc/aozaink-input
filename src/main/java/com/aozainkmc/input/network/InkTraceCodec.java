package com.aozainkmc.input.network;

import com.aozainkmc.core.api.InkPoint;
import com.aozainkmc.core.api.InkTrace;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Network codec for {@link InkTrace}. The format is:
 * <pre>
 *   strokeCount : varInt
 *   for each stroke:
 *     pointCount : varInt
 *     for each point:
 *       x        : float
 *       y        : float
 *       timeMs   : varLong
 * </pre>
 */
public final class InkTraceCodec {

    public static final StreamCodec<RegistryFriendlyByteBuf, InkTrace> TRACE =
        StreamCodec.of(
            (buffer, trace) -> {
                List<List<InkPoint>> strokes = trace.strokes();
                buffer.writeVarInt(strokes.size());
                for (List<InkPoint> stroke : strokes) {
                    buffer.writeVarInt(stroke.size());
                    for (InkPoint point : stroke) {
                        buffer.writeFloat(point.x());
                        buffer.writeFloat(point.y());
                        buffer.writeVarLong(point.timeMs());
                    }
                }
            },
            buffer -> {
                int strokeCount = buffer.readVarInt();
                List<List<InkPoint>> strokes = new ArrayList<>(strokeCount);
                for (int i = 0; i < strokeCount; i++) {
                    int pointCount = buffer.readVarInt();
                    List<InkPoint> points = new ArrayList<>(pointCount);
                    for (int j = 0; j < pointCount; j++) {
                        float x = buffer.readFloat();
                        float y = buffer.readFloat();
                        long timeMs = buffer.readVarLong();
                        points.add(new InkPoint(x, y, timeMs));
                    }
                    strokes.add(points);
                }
                return new InkTrace(strokes);
            }
        );

    private InkTraceCodec() {}
}
