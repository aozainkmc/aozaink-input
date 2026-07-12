package com.aozainkmc.input.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aozainkmc.input.api.MoluMenuRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuickCastSessionManagerTest {
    @Test
    void recognitionIncludesDigitsAndEveryGameplayRegisteredGlyph() {
        MoluMenuRegistry.registerGlyph("quick_cast_test", "霆", "", "");
        List<String> glyphs = QuickCastSessionManager.recognitionGlyphs();
        assertTrue(glyphs.containsAll(QuickCastSessionManager.DIGITS));
        assertTrue(glyphs.contains("霆"));
    }

    @Test
    void clientPayloadNeverExposesMoreThanThreeCandidates() {
        QuickCastCandidatesPayload payload = new QuickCastCandidatesPayload(1L, 1L, List.of(
            new QuickCastCandidatesPayload.Entry("火", 1.0F),
            new QuickCastCandidatesPayload.Entry("雷", 0.9F),
            new QuickCastCandidatesPayload.Entry("护", 0.8F),
            new QuickCastCandidatesPayload.Entry("风", 0.7F)));
        assertEquals(3, payload.candidates().size());
    }
}
