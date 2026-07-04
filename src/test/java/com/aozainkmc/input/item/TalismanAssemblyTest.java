package com.aozainkmc.input.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TalismanAssemblyTest {
    @Test
    void inscriptionMarkerCanUseEitherIncantationSlot() {
        TalismanAssembly.Result result = TalismanAssembly.classify("火", "刻", "广");

        assertEquals(TalismanAssembly.Type.INSCRIPTION, result.type());
        assertEquals("火", result.slot1());
        assertEquals("刻", result.slot2());
        assertEquals("广", result.slot3());
    }

    @Test
    void inscriptionRejectsSkillInTailSlot() {
        TalismanAssembly.Result result = TalismanAssembly.classify("刻", "广", "火");

        assertEquals(TalismanAssembly.Type.FAILED, result.type());
    }

    @Test
    void inscriptionRejectsMarkInTailSlot() {
        TalismanAssembly.Result result = TalismanAssembly.classify("火", "广", "刻");

        assertEquals(TalismanAssembly.Type.FAILED, result.type());
    }

    @Test
    void inscriptionRejectsPiercingModifierOperation() {
        TalismanAssembly.Result result = TalismanAssembly.classify("刻", "广", "穿");

        assertEquals(TalismanAssembly.Type.FAILED, result.type());
    }

    @Test
    void inscriptionAllowsTwoModifierOperation() {
        TalismanAssembly.Result result = TalismanAssembly.classify("刻", "强", "续");

        assertEquals(TalismanAssembly.Type.INSCRIPTION, result.type());
    }

    @Test
    void inscriptionAllowsSingleModifierOperation() {
        TalismanAssembly.Result result = TalismanAssembly.classify("刻", "续", "");

        assertEquals(TalismanAssembly.Type.INSCRIPTION, result.type());
    }
}
