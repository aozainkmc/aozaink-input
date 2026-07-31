package com.aozainkmc.input.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GlyphPreviewDispatcherTest {

    @BeforeAll
    static void registerFixtures() {
        TalismanSyntaxRegistry.register("test_alpha", Set.of(), Set.of("甲"), Set.of("乙"));
        TalismanSyntaxRegistry.register("test_beta", Set.of(), Set.of("丙"), Set.of());
        GlyphPreviewRegistry.register("test_alpha", slots -> List.of("alpha-preview"));
        GlyphPreviewRegistry.register("test_beta", slots -> List.of("beta-preview"));
    }

    @Test
    void routesToSoleOwner() {
        GlyphPreviewDispatcher dispatcher = new GlyphPreviewDispatcher();
        assertEquals(List.of("alpha-preview"), dispatcher.describe(List.of("甲", "乙", "")));
        assertEquals(List.of("beta-preview"), dispatcher.describe(List.of("丙", "", "")));
    }

    @Test
    void unownedGlyphsFollowTheOwner() {
        GlyphPreviewDispatcher dispatcher = new GlyphPreviewDispatcher();
        assertEquals(List.of("alpha-preview"), dispatcher.describe(List.of("一", "甲", "")));
    }

    @Test
    void mixedOwnersAreRejected() {
        GlyphPreviewDispatcher dispatcher = new GlyphPreviewDispatcher();
        List<String> lines = dispatcher.describe(List.of("甲", "丙", ""));
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("符法不合"));
    }

    @Test
    void digitsOnlyStayQuiet() {
        GlyphPreviewDispatcher dispatcher = new GlyphPreviewDispatcher();
        assertTrue(dispatcher.describe(List.of("一", "二", "")).isEmpty());
        assertTrue(dispatcher.describe(List.of("", "", "")).isEmpty());
    }

    @Test
    void ownerWithoutDescriberStaysQuiet() {
        TalismanSyntaxRegistry.register("test_silent", Set.of(), Set.of("丁"), Set.of());
        GlyphPreviewDispatcher dispatcher = new GlyphPreviewDispatcher();
        assertTrue(dispatcher.describe(List.of("丁", "", "")).isEmpty());
    }
}
