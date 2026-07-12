package com.aozainkmc.input.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;

/** Registry owned by Input for the shared Molu menu and quick-invocation routing. */
public final class MoluMenuRegistry {
    private static final Map<String, GlyphEntry> GLYPHS = new LinkedHashMap<>();
    private static final Map<String, TabDefinition> TABS = new LinkedHashMap<>();

    private MoluMenuRegistry() {}

    public static synchronized void registerGlyph(
            String owner, String glyph, String brief, String detail) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(glyph, "glyph");
        if (glyph.isBlank()) throw new IllegalArgumentException("glyph cannot be blank");
        GlyphEntry previous = GLYPHS.putIfAbsent(glyph,
            new GlyphEntry(owner, glyph, safe(brief), safe(detail)));
        if (previous != null && !previous.owner().equals(owner)) {
            throw new IllegalStateException("Glyph '" + glyph + "' is already owned by " + previous.owner());
        }
    }

    public static synchronized void registerTab(
            String owner, String id, String label, String title,
            List<String> columns, TabDataProvider provider) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(id, "id");
        String key = owner + ":" + id;
        if (TABS.containsKey(key)) throw new IllegalStateException("Menu tab already registered: " + key);
        TABS.put(key, new TabDefinition(key, safe(label), safe(title), List.copyOf(columns), provider));
    }

    public static synchronized List<GlyphEntry> glyphs() {
        return List.copyOf(GLYPHS.values());
    }

    public static synchronized String ownerOf(String glyph) {
        GlyphEntry entry = GLYPHS.get(glyph);
        return entry == null ? "" : entry.owner();
    }

    public static synchronized List<TabSnapshot> tabsFor(ServerPlayer player) {
        List<TabSnapshot> snapshots = new ArrayList<>();
        for (TabDefinition tab : TABS.values()) {
            List<List<String>> rows;
            try {
                rows = tab.provider().rows(player);
            } catch (RuntimeException exception) {
                rows = List.of(List.of("栏目数据读取失败"));
            }
            snapshots.add(new TabSnapshot(tab.id(), tab.label(), tab.title(), tab.columns(), copyRows(rows)));
        }
        return snapshots;
    }

    private static List<List<String>> copyRows(List<List<String>> rows) {
        if (rows == null) return List.of();
        return rows.stream().map(List::copyOf).toList();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record GlyphEntry(String owner, String glyph, String brief, String detail) {}
    public record TabSnapshot(String id, String label, String title, List<String> columns, List<List<String>> rows) {}
    private record TabDefinition(String id, String label, String title, List<String> columns, TabDataProvider provider) {}

    @FunctionalInterface
    public interface TabDataProvider {
        List<List<String>> rows(ServerPlayer player);
    }
}
