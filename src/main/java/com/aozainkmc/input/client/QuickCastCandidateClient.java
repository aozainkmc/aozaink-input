package com.aozainkmc.input.client;

import com.aozainkmc.input.network.AozaiInkNetworking;
import com.aozainkmc.input.network.QuickCastCandidatesPayload;
import com.aozainkmc.input.network.SelectQuickCastCandidatePayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client-side display and number-key selection for the current Top-3 result. */
public final class QuickCastCandidateClient {
    private static final long DISPLAY_TTL_MS = 10_000L;
    private static long sessionId = -1L;
    private static long expectedRevision = -1L;
    private static long expiresAtMs;
    private static long nextReminderMs;
    private static int paperHotbarSlot = -1;
    private static int pendingRestoreSlot = -1;
    private static List<QuickCastCandidatesPayload.Entry> candidates = List.of();

    private QuickCastCandidateClient() {}

    public static void expectRevision(long revision) {
        expectedRevision = Math.max(expectedRevision, revision);
    }

    public static void show(QuickCastCandidatesPayload payload) {
        if (payload.revision() < expectedRevision) return;
        expectedRevision = payload.revision();
        if (payload.candidates().isEmpty()) {
            clear();
            return;
        }
        sessionId = payload.sessionId();
        candidates = payload.candidates();
        expiresAtMs = System.currentTimeMillis() + DISPLAY_TTL_MS;
        nextReminderMs = 0L;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) paperHotbarSlot = minecraft.player.getInventory().selected;
        announce();
    }

    public static void tick(Minecraft minecraft) {
        if (pendingRestoreSlot >= 0 && minecraft.player != null) {
            minecraft.player.getInventory().selected = pendingRestoreSlot;
            pendingRestoreSlot = -1;
        }
        if (!isActive()) return;
        long now = System.currentTimeMillis();
        if (now >= expiresAtMs) {
            clear();
            return;
        }
        if (minecraft.screen == null && now >= nextReminderMs) {
            announce();
            nextReminderMs = now + 1_500L;
        }
    }

    public static boolean select(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isActive() || minecraft.screen != null || index < 0 || index >= candidates.size()) return false;
        QuickCastCandidatesPayload.Entry selected = candidates.get(index);
        AozaiInkNetworking.sendQuickCastSelection(new SelectQuickCastCandidatePayload(sessionId, index));
        pendingRestoreSlot = paperHotbarSlot;
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("已选择候选 " + (index + 1) + " · " + selected.glyph()), true);
        }
        InkInputController.completeCandidateSelection();
        clear();
        return true;
    }

    public static void reset() {
        expectedRevision = -1L;
        clear();
        pendingRestoreSlot = -1;
    }

    private static boolean isActive() {
        return sessionId >= 0L && !candidates.isEmpty();
    }

    private static void announce() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || candidates.isEmpty()) return;
        StringBuilder text = new StringBuilder("候选：");
        for (int i = 0; i < candidates.size(); i++) {
            if (i > 0) text.append("   ");
            text.append(i + 1).append('.').append(candidates.get(i).glyph());
        }
        text.append("  ·  按 1 / 2 / 3 施法");
        minecraft.player.displayClientMessage(Component.literal(text.toString()), true);
    }

    private static void clear() {
        sessionId = -1L;
        candidates = List.of();
        expiresAtMs = 0L;
        nextReminderMs = 0L;
        paperHotbarSlot = -1;
    }
}
