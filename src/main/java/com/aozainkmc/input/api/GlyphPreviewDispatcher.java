package com.aozainkmc.input.api;

import com.aozainkmc.core.api.GlyphDescriber;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The single GlyphDescriber service input installs into core. Routes a slot group to the
 * owning module's preview describer: all written glyphs must share one owner (digits and
 * other unowned glyphs are neutral and follow the owned glyphs); mixed owners are rejected
 * as a cross-module waste talisman.
 */
public final class GlyphPreviewDispatcher implements GlyphDescriber {

    @Override
    public List<String> describe(List<String> slots) {
        List<String> glyphs = new ArrayList<>();
        LinkedHashSet<String> owners = new LinkedHashSet<>();
        for (String slot : slots) {
            String glyph = slot == null || slot.isBlank() ? "" : slot.trim();
            if (glyph.isEmpty()) continue;
            glyphs.add(glyph);
            String owner = TalismanSyntaxRegistry.ownerOf(glyph);
            if (!owner.isEmpty()) {
                owners.add(owner);
            }
        }
        if (glyphs.isEmpty() || owners.isEmpty()) {
            return List.of();
        }
        if (owners.size() > 1) {
            return List.of(header(glyphs) + "废符 · 符法不合");
        }
        return GlyphPreviewRegistry.describerFor(owners.iterator().next())
            .<List<String>>map(describer -> describer.describe(slots))
            .orElse(List.of());
    }

    private static String header(List<String> glyphs) {
        return "「" + String.join("+", glyphs) + "」";
    }
}
