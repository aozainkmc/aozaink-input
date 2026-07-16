package com.aozainkmc.input.client;

import com.aozainkmc.core.api.InkPoint;
import com.aozainkmc.core.api.InkRecognitionRequest;
import com.aozainkmc.core.api.InkSource;
import com.aozainkmc.core.api.InkTrace;
import com.aozainkmc.input.AozaiInkInput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ClassicSubmissionHelper {
    private static final long DEFAULT_TTL_TICKS = 20L * 60L * 10L;
    private static final float BASE_POWER_MULTIPLIER = 1.0f;
    private static final String BASE_TIER_LABEL = "default";
    private static final float BASE_TIER_RANK = 0.0f;
    private ClassicSubmissionHelper() {}

    public static InkRecognitionRequest buildRequest(
        List<InkStroke> strokes,
        boolean fromBack
    ) {
        InkSource source = new InkSource(
            AozaiInkInput.SOURCE_TRAJECTORY,
            BASE_POWER_MULTIPLIER,
            BASE_TIER_LABEL,
            BASE_TIER_RANK,
            Collections.emptyMap()
        );

        List<List<InkPoint>> normalizedStrokes = new ArrayList<>();
        for (InkStroke stroke : strokes) {
            if (stroke.fromBack() != fromBack) {
                continue;
            }
            List<InkPoint> points = new ArrayList<>();
            for (InkStrokePoint sp : stroke.points()) {
                float u = fromBack ? -sp.u() : sp.u();
                float v = -sp.v();
                points.add(new InkPoint(u, v, sp.timeMs()));
            }
            if (!points.isEmpty()) normalizedStrokes.add(points);
        }

        InkTrace trace = new InkTrace(normalizedStrokes);
        return new InkRecognitionRequest(
            trace,
            null,
            Collections.emptyList(),
            DEFAULT_TTL_TICKS,
            source
        );
    }
}
