package com.aozainkmc.input;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aozainkmc.input.api.TalismanSyntaxRegistry;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AozaiInkInputTest {
    @Test
    void talismanRecognitionCandidatesIncludeGameplayRegisteredGlyphs() {
        TalismanSyntaxRegistry.register("test_fixture", Set.of(), Set.of("火"), Set.of());
        assertTrue(AozaiInkInput.talismanGlyphs().contains("火"));
        assertTrue(AozaiInkInput.talismanGlyphs().contains("一"));
    }
}
