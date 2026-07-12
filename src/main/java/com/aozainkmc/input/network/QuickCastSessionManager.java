package com.aozainkmc.input.network;

import com.aozainkmc.core.AozaiInkCoreApi;
import com.aozainkmc.core.api.InkCandidate;
import com.aozainkmc.core.api.InkRecognitionResult;
import com.aozainkmc.core.api.InkSource;
import com.aozainkmc.input.AozaiInkInput;
import com.aozainkmc.input.api.MoluMenuRegistry;
import com.aozainkmc.input.binding.QuickGlyphBinding;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/** Owns short-lived server-authoritative candidate sessions for white-paper casting. */
final class QuickCastSessionManager {
    static final List<String> DIGITS = List.of("一", "二", "三", "四", "五", "六", "七", "八", "九");
    private static final int MAX_CANDIDATES = 3;
    private static final long SESSION_TTL_TICKS = 20L * 10L;
    private static final AtomicLong NEXT_SESSION_ID = new AtomicLong(1L);
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LATEST_REVISION = new ConcurrentHashMap<>();

    private QuickCastSessionManager() {}

    static List<String> recognitionGlyphs() {
        LinkedHashSet<String> glyphs = new LinkedHashSet<>(DIGITS);
        MoluMenuRegistry.glyphs().forEach(entry -> glyphs.add(entry.glyph()));
        return List.copyOf(glyphs);
    }

    static boolean beginRevision(ServerPlayer player, long revision) {
        UUID id = player.getUUID();
        Long current = LATEST_REVISION.compute(id, (ignored, previous) ->
            previous == null || revision >= previous ? revision : previous);
        return current != null && revision >= current;
    }

    static void offer(ServerPlayer player, InkRecognitionResult result, InkSource source, long revision) {
        if (!isCurrent(player, revision) || result == null) return;
        List<InkCandidate> candidates = result.candidates().stream()
            .filter(candidate -> candidate != null && candidate.word() != null && !candidate.word().isBlank())
            .limit(MAX_CANDIDATES)
            .toList();
        if (candidates.isEmpty()) {
            player.displayClientMessage(Component.literal("[AozaiInk] 未识别到可吟唱字"), true);
            return;
        }

        long sessionId = NEXT_SESSION_ID.getAndIncrement();
        long expiresAt = player.serverLevel().getGameTime() + SESSION_TTL_TICKS;
        SESSIONS.put(player.getUUID(), new Session(sessionId, revision, candidates, result, source, expiresAt));
        List<QuickCastCandidatesPayload.Entry> entries = candidates.stream()
            .map(candidate -> new QuickCastCandidatesPayload.Entry(candidate.word(), candidate.confidence()))
            .toList();
        PacketDistributor.sendToPlayer(player, new QuickCastCandidatesPayload(sessionId, revision, entries));
    }

    static void select(ServerPlayer player, SelectQuickCastCandidatePayload payload) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.id() != payload.sessionId()) {
            player.displayClientMessage(Component.literal("[AozaiInk] 候选已失效，请重新落笔"), true);
            return;
        }
        if (player.serverLevel().getGameTime() > session.expiresAt()) {
            SESSIONS.remove(player.getUUID(), session);
            player.displayClientMessage(Component.literal("[AozaiInk] 候选已超时，请重新落笔"), true);
            return;
        }
        if (payload.index() < 0 || payload.index() >= session.candidates().size()) {
            return;
        }

        // Claim the session before resolving or casting. Every confirmation path is one-shot,
        // so duplicate packets can never consume paper or cast a second time.
        if (!SESSIONS.remove(player.getUUID(), session)) {
            player.displayClientMessage(Component.literal("[AozaiInk] 候选已被使用"), true);
            return;
        }

        InkCandidate selected = session.candidates().get(payload.index());
        DispatchTarget target = resolve(player, selected.word());
        if (target == null) return;
        if (!consumePaper(player)) {
            player.displayClientMessage(Component.literal("[AozaiInk] 需要消耗一张纸"), true);
            return;
        }
        dispatch(player, session, selected, target, payload.index());
    }

    static void castLegacyDigit(
        ServerPlayer player, String digit, InkRecognitionResult result, InkSource source, long revision
    ) {
        LATEST_REVISION.merge(player.getUUID(), revision, Math::max);
        // Right-click/Enter legacy confirmation and numbered candidate selection share one
        // server-side session. Whichever succeeds first invalidates the other route.
        SESSIONS.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player,
            new QuickCastCandidatesPayload(0L, revision, List.of()));
        QuickGlyphBinding.Binding binding = QuickGlyphBinding.get(player, digit).orElse(null);
        if (binding == null) {
            player.displayClientMessage(Component.literal("数字 " + digit + " 未指定"), true);
            return;
        }
        if (!consumePaper(player)) {
            player.displayClientMessage(Component.literal("[AozaiInk] 需要消耗一张纸"), true);
            return;
        }
        InkCandidate selected = new InkCandidate(digit, result.confidence());
        Session session = new Session(0L, revision, List.of(selected), result, source, 0L);
        dispatch(player, session, selected,
            new DispatchTarget(binding.glyph(), binding.owner(), digit + " → " + binding.glyph(), "binding"), 0);
    }

    private static boolean isCurrent(ServerPlayer player, long revision) {
        return beginRevision(player, revision);
    }

    private static DispatchTarget resolve(ServerPlayer player, String selectedGlyph) {
        if (DIGITS.contains(selectedGlyph)) {
            QuickGlyphBinding.Binding binding = QuickGlyphBinding.get(player, selectedGlyph).orElse(null);
            if (binding == null) {
                player.displayClientMessage(Component.literal("数字 " + selectedGlyph + " 未指定"), true);
                return null;
            }
            return new DispatchTarget(binding.glyph(), binding.owner(),
                selectedGlyph + " → " + binding.glyph(), "binding");
        }
        String owner = MoluMenuRegistry.ownerOf(selectedGlyph);
        if (owner.isBlank()) {
            player.displayClientMessage(Component.literal("字 " + selectedGlyph + " 尚未接入快速吟唱"), true);
            return null;
        }
        return new DispatchTarget(selectedGlyph, owner, selectedGlyph, "candidate");
    }

    private static void dispatch(
        ServerPlayer player, Session session, InkCandidate selected, DispatchTarget target, int candidateIndex
    ) {
        InkRecognitionResult original = session.result();
        InkRecognitionResult resolved = new InkRecognitionResult(
            target.glyph(), selected.confidence(), List.of(new InkCandidate(target.glyph(), selected.confidence())),
            original.simplifiedStrokeCount(), original.simplifiedPointCount(), original.writingDurationMs());
        Map<String, Object> extra = new HashMap<>(session.source().extra());
        extra.put("quick_owner", target.owner());
        extra.put("quick_display", target.display());
        extra.put("quick_mode", target.mode());
        extra.put("quick_candidate_index", candidateIndex + 1);
        if (DIGITS.contains(selected.word())) extra.put("quick_digit", selected.word());
        InkSource source = new InkSource(
            session.source().sourceId(), session.source().powerMultiplier(), session.source().tierLabel(),
            session.source().tierRank(), extra);
        try {
            AozaiInkCoreApi.recognizer().broadcast(resolved, source, player);
        } catch (Exception exception) {
            AozaiInkInput.LOGGER.warn("Quick cast broadcast failed", exception);
            player.displayClientMessage(Component.literal("[AozaiInk] 施法失败"), true);
        }
    }

    private static boolean consumePaper(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(Items.PAPER)) {
            mainHand.shrink(1);
            return true;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(Items.PAPER)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private record Session(
        long id, long revision, List<InkCandidate> candidates, InkRecognitionResult result,
        InkSource source, long expiresAt
    ) {}

    private record DispatchTarget(String glyph, String owner, String display, String mode) {}
}
