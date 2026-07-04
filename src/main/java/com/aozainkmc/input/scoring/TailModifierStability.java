package com.aozainkmc.input.scoring;

import java.util.Optional;

public final class TailModifierStability {
    public static final double BASE_SUCCESS_CHANCE = 0.80;
    public static final double STROKE_PENALTY_PER_DIFF = 0.05;

    private TailModifierStability() {}

    public record Result(String glyph, int actualStrokes, int expectedStrokes, double successChance) {
        public boolean hasTail() {
            return glyph != null && !glyph.isBlank();
        }
    }

    public static Optional<Result> evaluate(String glyph, int actualStrokes) {
        if (glyph == null || glyph.isBlank()) {
            return Optional.empty();
        }
        return TalismanScorer.baseline(glyph).map(baseline -> {
            int expected = (int) Math.round(baseline.strokeCount());
            int diff = Math.abs(actualStrokes - expected);
            double chance = Math.max(0.0, BASE_SUCCESS_CHANCE - diff * STROKE_PENALTY_PER_DIFF);
            return new Result(glyph, actualStrokes, expected, chance);
        });
    }
}
