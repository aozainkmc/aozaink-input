package com.aozainkmc.input.client;

import com.aozainkmc.core.api.EngineType;
import com.aozainkmc.core.api.InkPoint;
import com.aozainkmc.core.api.InkRecognitionMode;
import com.aozainkmc.core.api.InkRecognitionRequest;
import com.aozainkmc.core.api.InkSource;
import com.aozainkmc.core.api.InkTrace;
import com.aozainkmc.core.ocr.DebugDump;
import com.aozainkmc.core.ocr.StrokeRasterizer;
import com.aozainkmc.input.AozaiInkInput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ClassicSubmissionHelper {
    private static final long DEFAULT_TTL_TICKS = 20L * 60L * 10L;
    private static final float BASE_POWER_MULTIPLIER = 1.0f;
    private static final String BASE_TIER_LABEL = "default";
    private static final float BASE_TIER_RANK = 0.0f;
    private static final StrokeRasterizer RASTERIZER = new StrokeRasterizer();

    private ClassicSubmissionHelper() {}

    public static InkRecognitionRequest buildRequest(
        List<InkStroke> strokes,
        boolean fromBack,
        EngineType engineType
    ) {
        String sourceId = engineType == EngineType.ONLINE_TRAJECTORY
            ? AozaiInkInput.SOURCE_TRAJECTORY
            : AozaiInkInput.SOURCE_IMAGE;
        InkSource source = new InkSource(
            sourceId,
            BASE_POWER_MULTIPLIER,
            BASE_TIER_LABEL,
            BASE_TIER_RANK,
            Collections.emptyMap()
        );

        List<List<InkPoint>> normalizedStrokes = new ArrayList<>();
        for (InkStroke stroke : strokes) {
            List<InkPoint> points = new ArrayList<>();
            for (InkStrokePoint sp : stroke.points()) {
                float u = fromBack ? -sp.u() : sp.u();
                float v = engineType == EngineType.ONLINE_TRAJECTORY ? -sp.v() : sp.v();
                points.add(new InkPoint(u, v, sp.timeMs()));
            }
            if (!points.isEmpty()) normalizedStrokes.add(points);
        }

        InkTrace trace = new InkTrace(normalizedStrokes);
        float[] imageInput = engineType == EngineType.OFFLINE_IMAGE ? RASTERIZER.rasterize(trace) : null;
        if (imageInput != null) {
            DebugDump.offlineImageInput(imageInput);
        }
        InkRecognitionMode mode = engineType == EngineType.ONLINE_TRAJECTORY
            ? InkRecognitionMode.ONLINE
            : InkRecognitionMode.OFFLINE;

        return new InkRecognitionRequest(
            trace,
            imageInput,
            mode,
            Collections.emptyList(),
            DEFAULT_TTL_TICKS,
            source
        );
    }
}
