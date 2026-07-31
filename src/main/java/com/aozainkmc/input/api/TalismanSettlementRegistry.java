package com.aozainkmc.input.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** One settlement handler per glyph owner; dispatch keys on {@link TalismanSyntaxRegistry#ownerOf}. */
public final class TalismanSettlementRegistry {
    private static final Map<String, TalismanSettlementHandler> HANDLERS = new LinkedHashMap<>();

    private TalismanSettlementRegistry() {}

    public static synchronized void register(String owner, TalismanSettlementHandler handler) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(handler, "handler");
        if (HANDLERS.putIfAbsent(owner, handler) != null) {
            throw new IllegalStateException("Talisman settlement already registered: " + owner);
        }
    }

    public static synchronized Optional<TalismanSettlementHandler> handlerFor(String owner) {
        return Optional.ofNullable(HANDLERS.get(owner));
    }
}
