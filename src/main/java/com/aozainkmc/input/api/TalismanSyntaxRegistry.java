package com.aozainkmc.input.api;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Per-module talisman grammar: structure glyphs, base glyphs and tail-slot glyphs. */
public final class TalismanSyntaxRegistry {
    private static final Map<String, ModuleSyntax> MODULES = new LinkedHashMap<>();
    private static final Map<String, String> GLYPH_OWNERS = new LinkedHashMap<>();

    private TalismanSyntaxRegistry() {}

    public record ModuleSyntax(
        String owner,
        Set<String> structureGlyphs,
        Set<String> baseGlyphs,
        Set<String> tailGlyphs
    ) {}

    public static synchronized void register(
            String owner, Set<String> structureGlyphs, Set<String> baseGlyphs, Set<String> tailGlyphs) {
        Objects.requireNonNull(owner, "owner");
        if (MODULES.containsKey(owner)) {
            throw new IllegalStateException("Talisman syntax already registered: " + owner);
        }
        ModuleSyntax syntax = new ModuleSyntax(
            owner,
            structureGlyphs == null ? Set.of() : Set.copyOf(structureGlyphs),
            baseGlyphs == null ? Set.of() : Set.copyOf(baseGlyphs),
            tailGlyphs == null ? Set.of() : Set.copyOf(tailGlyphs)
        );
        LinkedHashSet<String> all = new LinkedHashSet<>();
        all.addAll(syntax.structureGlyphs());
        all.addAll(syntax.baseGlyphs());
        all.addAll(syntax.tailGlyphs());
        for (String glyph : all) {
            if (glyph == null || glyph.isBlank()) {
                throw new IllegalArgumentException("glyph cannot be blank");
            }
            String previous = GLYPH_OWNERS.putIfAbsent(glyph, owner);
            if (previous != null && !previous.equals(owner)) {
                throw new IllegalStateException("Glyph '" + glyph + "' is already owned by " + previous);
            }
        }
        MODULES.put(owner, syntax);
    }

    public static synchronized boolean isTailGlyph(String glyph) {
        for (ModuleSyntax syntax : MODULES.values()) {
            if (syntax.tailGlyphs().contains(glyph)) return true;
        }
        return false;
    }

    public static synchronized String ownerOf(String glyph) {
        return GLYPH_OWNERS.getOrDefault(glyph, "");
    }

    public static synchronized Set<String> allGlyphs() {
        LinkedHashSet<String> all = new LinkedHashSet<>();
        for (ModuleSyntax syntax : MODULES.values()) {
            all.addAll(syntax.structureGlyphs());
            all.addAll(syntax.baseGlyphs());
            all.addAll(syntax.tailGlyphs());
        }
        return Set.copyOf(all);
    }

    public static synchronized Set<String> tailGlyphs() {
        LinkedHashSet<String> all = new LinkedHashSet<>();
        for (ModuleSyntax syntax : MODULES.values()) {
            all.addAll(syntax.tailGlyphs());
        }
        return Set.copyOf(all);
    }
}
