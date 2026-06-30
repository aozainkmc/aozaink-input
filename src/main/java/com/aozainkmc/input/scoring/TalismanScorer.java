package com.aozainkmc.input.scoring;

import com.aozainkmc.input.AozaiInkInput;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class TalismanScorer {

    private static final double CONF_EXPONENT = 3.0;
    private static final double W_STROKE = 0.70;
    private static final double W_POINT = 0.30;
    private static final double STROKE_PENALTY_PER = 0.10;
    private static final double POINT_PENALTY_PER = 0.03;
    private static final double MS_PER_TICK = 50.0;
    private static final double TIME_PER_TICK = 0.005;
    private static final double TIME_BONUS_CAP = 0.30;
    private static final double TIME_TARGET_FALLBACK_MS = 2500.0;

    private static final Map<String, Double> TIME_TARGET_MS_BY_CHAR = Map.ofEntries(
        Map.entry("引", 2000.0),
        Map.entry("广", 2200.0),
        Map.entry("火", 2300.0),
        Map.entry("护", 2800.0),
        Map.entry("净", 2900.0),
        Map.entry("退", 2900.0),
        Map.entry("封", 3100.0),
        Map.entry("疾", 3200.0),
        Map.entry("强", 3200.0),
        Map.entry("续", 3400.0),
        Map.entry("雷", 3700.0),
        Map.entry("镇", 4000.0)
    );

    private static final String REFERENCE_PATH = "/assets/aozaink_input/scoring/reference.json";
    private static final Map<String, Baseline> BASELINES = loadBaselines();

    private TalismanScorer() {}

    public record Baseline(double strokeCount, double pointCount) {}

    public record Score(
        String glyph,
        double confScore,
        double strokeScore,
        double pointScore,
        double timeFactor,
        double composite,
        double baselineStrokes,
        double baselinePoints,
        double targetMs,
        int strokeCount,
        int pointCount,
        long durationMs,
        float confidence
    ) {}

    public static boolean hasBaseline(String glyph) {
        return BASELINES.containsKey(glyph);
    }

    public static Optional<Score> score(String glyph, int strokeCount, int pointCount,
                                        long durationMs, float confidence) {
        Baseline base = BASELINES.get(glyph);
        if (base == null) {
            return Optional.empty();
        }

        double confScore = Math.pow(clamp01(confidence), CONF_EXPONENT);
        double strokeScore = clamp01(1.0 - STROKE_PENALTY_PER * Math.abs(strokeCount - base.strokeCount()));
        double pointScore = clamp01(1.0 - POINT_PENALTY_PER * Math.abs(pointCount - base.pointCount()));
        double shape = W_STROKE * strokeScore + W_POINT * pointScore;

        double targetMs = TIME_TARGET_MS_BY_CHAR.getOrDefault(glyph, TIME_TARGET_FALLBACK_MS);
        double ticksDelta = (durationMs - targetMs) / MS_PER_TICK;
        double timeFactor = clamp(1.0 - TIME_PER_TICK * ticksDelta, 0.0, 1.0 + TIME_BONUS_CAP);

        double composite = shape * timeFactor * confScore;

        return Optional.of(new Score(
            glyph, confScore, strokeScore, pointScore, timeFactor, composite,
            base.strokeCount(), base.pointCount(), targetMs,
            strokeCount, pointCount, durationMs, confidence
        ));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double clamp(double value, double lo, double hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    private static Map<String, Baseline> loadBaselines() {
        Map<String, Baseline> out = new HashMap<>();
        try (InputStream stream = TalismanScorer.class.getResourceAsStream(REFERENCE_PATH)) {
            if (stream == null) {
                AozaiInkInput.LOGGER.warn("Talisman scoring baselines missing: {}", REFERENCE_PATH);
                return out;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                for (String glyph : root.keySet()) {
                    JsonObject entry = root.getAsJsonObject(glyph);
                    out.put(glyph, new Baseline(
                        entry.get("stroke_count").getAsDouble(),
                        entry.get("point_count").getAsDouble()
                    ));
                }
            }
        } catch (Exception e) {
            AozaiInkInput.LOGGER.warn("Failed to load talisman scoring baselines: {}", e.getMessage());
        }
        return out;
    }
}
