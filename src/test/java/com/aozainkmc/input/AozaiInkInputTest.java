package com.aozainkmc.input;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aozainkmc.input.api.MoluMenuRegistry;
import org.junit.jupiter.api.Test;

class AozaiInkInputTest {
    @Test
    void talismanRecognitionCandidatesIncludeGameplayRegisteredGlyphs() {
        MoluMenuRegistry.registerGlyph("test_fixture", "火", "", "");
        assertTrue(AozaiInkInput.talismanGlyphs().contains("火"));
        assertTrue(AozaiInkInput.talismanGlyphs().contains("一"));
    }
}
