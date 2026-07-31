package com.aozainkmc.input.api;

import com.aozainkmc.core.api.GlyphDescriber;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** One glyph preview describer per owner; dispatched by {@link GlyphPreviewDispatcher}. */
public final class GlyphPreviewRegistry {
    private static final Map<String, GlyphDescriber> DESCRIBERS = new LinkedHashMap<>();

    private GlyphPreviewRegistry() {}

    public static synchronized void register(String owner, GlyphDescriber describer) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(describer, "describer");
        if (DESCRIBERS.putIfAbsent(owner, describer) != null) {
            throw new IllegalStateException("Glyph preview already registered: " + owner);
        }
    }

    public static synchronized Optional<GlyphDescriber> describerFor(String owner) {
        return Optional.ofNullable(DESCRIBERS.get(owner));
    }
}
