package com.aozainkmc.input.api;

/**
 * Full talisman settlement for one owning module: validation, gameplay resolution, block
 * consumption and player feedback all belong to the handler. Invoked on the server thread
 * after recognition, tail-glyph acceptance and tail stability checks have passed.
 */
@FunctionalInterface
public interface TalismanSettlementHandler {
    void settle(TalismanSettlement context);
}
