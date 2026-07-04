package com.aozainkmc.input.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TailModifierStabilityTest {
    @Test
    void emptyTailHasNoStabilityRoll() {
        assertTrue(TailModifierStability.evaluate("", 0).isEmpty());
    }

    @Test
    void exactStrokeCountUsesBaseChance() {
        TalismanScorer.Baseline baseline = TalismanScorer.baseline("强").orElseThrow();
        int expected = (int) Math.round(baseline.strokeCount());

        TailModifierStability.Result result = TailModifierStability.evaluate("强", expected).orElseThrow();

        assertEquals(expected, result.expectedStrokes());
        assertEquals(TailModifierStability.BASE_SUCCESS_CHANCE, result.successChance(), 0.0001);
    }

    @Test
    void strokeDiffReducesChanceByFivePercentEach() {
        TalismanScorer.Baseline baseline = TalismanScorer.baseline("续").orElseThrow();
        int expected = (int) Math.round(baseline.strokeCount());

        TailModifierStability.Result result = TailModifierStability.evaluate("续", expected + 3).orElseThrow();

        assertEquals(0.65, result.successChance(), 0.0001);
    }
}
