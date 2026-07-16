package com.aozainkmc.input.network;

import com.aozainkmc.core.AozaiInkCoreApi;
import com.aozainkmc.core.api.InkCandidate;
import com.aozainkmc.core.api.InkRecognitionRequest;
import com.aozainkmc.core.api.InkRecognitionResult;
import com.aozainkmc.core.api.InkRecognizedEvent;
import com.aozainkmc.core.api.InkSource;
import com.aozainkmc.core.api.InkTrace;
import com.aozainkmc.core.recognizer.AozaiInkRecognitionExecutor;
import com.aozainkmc.input.AozaiInkInput;
import com.aozainkmc.input.block.AozaiInkBlocks;
import com.aozainkmc.input.effect.TailModifierFailureEffect;
import com.aozainkmc.input.effect.TalismanFormationEffect;
import com.aozainkmc.input.item.AozaiInkItems;
import com.aozainkmc.input.item.TalismanAssembly;
import com.aozainkmc.input.scoring.TailModifierStability;
import com.aozainkmc.input.scoring.TalismanGrade;
import com.aozainkmc.input.scoring.TalismanScorer;
import com.aozainkmc.input.signal.InputSignals;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-side handlers for input network packets. All methods are expected to run on the
 * server main thread (they are invoked from {@link AozaiInkNetworking} inside
 * {@code context.enqueueWork(...)}).
 */
public final class AozaiInkServerHandlers {

    private static final double MAX_TALISMAN_DISTANCE_SQR = 8.0 * 8.0;
    private static final double MAX_PAPER_DISTANCE_SQR = 10.0 * 10.0;
    private static final long DEFAULT_TTL_TICKS = 20L * 60L * 10L;
    private static final Set<String> TAIL_MODIFIERS = Set.of("强", "续", "广", "穿");

    private AozaiInkServerHandlers() {}

    public static void submitTalisman(ServerPlayer player, SubmitTalismanPayload payload) {
        BlockPos pos = payload.pos();
        BlockState state = player.serverLevel().getBlockState(pos);
        if (!state.is(AozaiInkBlocks.YELLOW_TALISMAN.get())) {
            player.sendSystemMessage(Component.literal("[AozaiInk] 黄符方块已不存在"));
            return;
        }
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_TALISMAN_DISTANCE_SQR) {
            player.displayClientMessage(Component.literal("[AozaiInk] 距离黄符方块过远"), true);
            return;
        }

        List<SubmitTalismanPayload.Slot> slots = payload.slots();
        if (slots == null || slots.size() != 3) {
            player.displayClientMessage(Component.literal("[AozaiInk] 无效的成符请求"), true);
            return;
        }

        for (SubmitTalismanPayload.Slot slot : slots) {
            if (slot.present() && !AozaiInkRecognitionExecutor.validateTrace(slot.trace())) {
                player.displayClientMessage(Component.literal("[AozaiInk] 笔迹数据过大"), true);
                return;
            }
        }

        if (!AozaiInkRecognitionExecutor.get().tryAcquireCooldown(player)) {
            player.displayClientMessage(Component.literal("[AozaiInk] 识别过快，请稍后再试"), true);
            return;
        }

        recognizeSlots(player, slots, results -> finalizeTalisman(player, pos, slots, results));
    }

    private static void recognizeSlots(
        ServerPlayer player,
        List<SubmitTalismanPayload.Slot> slots,
        SlotRecognitionConsumer onComplete
    ) {
        List<InkRecognitionResult> results = new ArrayList<>(Collections.nCopies(3, InkRecognitionResult.empty()));
        AtomicInteger pending = new AtomicInteger(3);

        for (int i = 0; i < 3; i++) {
            final int index = i;
            SubmitTalismanPayload.Slot slot = slots.get(i);
            if (!slot.present()) {
                if (pending.decrementAndGet() == 0) {
                    onComplete.accept(results);
                }
                continue;
            }

            InkRecognitionRequest request = new InkRecognitionRequest(
                slot.trace(),
                null,
                AozaiInkInput.talismanGlyphs(),
                DEFAULT_TTL_TICKS,
                InkSource.simple(AozaiInkInput.SOURCE_TRAJECTORY)
            );

            AozaiInkRecognitionExecutor.get().submitWithoutCooldown(
                player,
                request,
                result -> {
                    results.set(index, result == null ? InkRecognitionResult.empty() : result);
                    if (pending.decrementAndGet() == 0) {
                        onComplete.accept(results);
                    }
                },
                reason -> {
                    results.set(index, InkRecognitionResult.empty());
                    if (pending.decrementAndGet() == 0) {
                        onComplete.accept(results);
                    }
                }
            );
        }
    }

    private static void finalizeTalisman(
        ServerPlayer player,
        BlockPos pos,
        List<SubmitTalismanPayload.Slot> slots,
        List<InkRecognitionResult> results
    ) {
        if (player.isRemoved() || player.connection == null) {
            return;
        }

        // The block may have been destroyed while recognition was running.
        if (!player.serverLevel().getBlockState(pos).is(AozaiInkBlocks.YELLOW_TALISMAN.get())) {
            return;
        }

        String[] glyphs = new String[3];
        for (int i = 0; i < 3; i++) {
            InkRecognitionResult r = results.get(i);
            glyphs[i] = (r == null || r.topGlyph() == null || r.topGlyph().isBlank()) ? "" : r.topGlyph();
        }

        String tailGlyph = normalize(glyphs[2]);
        if (!tailGlyph.isEmpty() && !TAIL_MODIFIERS.contains(tailGlyph)) {
            player.displayClientMessage(Component.literal("[AozaiInk] 尾修槽只接受 强 / 续 / 广 / 穿"), true);
            return;
        }

        // Tail modifier stability check.
        InkRecognitionResult tailResult = results.get(2);
        if (!tailGlyph.isEmpty() && tailResult != null) {
            Optional<TailModifierStability.Result> stability = TailModifierStability.evaluate(
                tailGlyph, tailResult.simplifiedStrokeCount()
            );
            if (stability.isPresent() && player.getRandom().nextDouble() >= stability.get().successChance()) {
                player.serverLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                player.displayClientMessage(Component.literal("尾修失败，符力暴乱"), true);
                InputSignals.tailModifierChaos(player, false, false, false);
                TalismanFormationEffect.startChaos(player, pos);
                TailModifierFailureEffect.start(player, pos);
                return;
            }
        }

        TalismanAssembly.Result result = TalismanAssembly.classify(glyphs[0], glyphs[1], glyphs[2]);
        ItemStack stack = TalismanAssembly.createStack(result);

        for (int i = 0; i < 3; i++) {
            if (glyphs[i].isBlank()) continue;
            InkRecognitionResult r = results.get(i);
            if (r == null) continue;
            embedScore(stack, i + 1, r);
            broadcastTalismanRecognition(player, result, i + 1, r);
        }

        triggerTalismanCreated(player, result, stack);

        player.serverLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        TalismanFormationEffect.startSuccess(player, pos, stack);
        player.displayClientMessage(Component.literal("成符: " + formatGlyphs(result.slot1(), result.slot2(), result.slot3())), true);
    }

    private static String formatGlyphs(String... glyphs) {
        StringBuilder builder = new StringBuilder();
        for (String glyph : glyphs) {
            String normalized = normalize(glyph);
            if (normalized.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(normalized);
        }
        return builder.length() == 0 ? "空符" : builder.toString();
    }

    private static void embedScore(ItemStack stack, int slot, InkRecognitionResult result) {
        Optional<TalismanScorer.Score> score = TalismanScorer.score(
            result.topGlyph(),
            result.simplifiedStrokeCount(),
            result.simplifiedPointCount(),
            result.writingDurationMs(),
            result.confidence()
        );
        if (score.isEmpty()) return;
        TalismanGrade grade = TalismanGrade.of(score.get().composite());
        CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        tag.putString("aozaink:grade" + slot, grade.name());
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void broadcastTalismanRecognition(
        ServerPlayer player,
        TalismanAssembly.Result result,
        int slotIndex,
        InkRecognitionResult recognitionResult
    ) {
        String glyph = switch (slotIndex) {
            case 1 -> result.slot1();
            case 2 -> result.slot2();
            case 3 -> result.slot3();
            default -> "";
        };
        if (glyph.isBlank()) return;
        if (result.type() == TalismanAssembly.Type.SPECIFIED && slotIndex != 2) return;
        if (result.type() == TalismanAssembly.Type.INSCRIPTION && "刻".equals(glyph)) return;

        Map<String, Object> extra = new HashMap<>();
        extra.put("talisman_type", result.type().name().toLowerCase());
        extra.put("slot", String.valueOf(slotIndex));
        if (result.type() == TalismanAssembly.Type.SPECIFIED) {
            extra.put("specified_number", result.slot1());
            extra.put("specified_glyph", result.slot2());
        }

        InkSource source = new InkSource(
            AozaiInkInput.SOURCE_TRAJECTORY,
            1.0f,
            "yellow_talisman",
            0.0f,
            extra
        );

        try {
            AozaiInkCoreApi.recognizer().broadcast(recognitionResult, source, player);
        } catch (Exception e) {
            AozaiInkInput.LOGGER.warn("Talisman recognition broadcast failed for slot {}: {}", slotIndex, e.getMessage());
        }
    }

    private static void triggerTalismanCreated(ServerPlayer player, TalismanAssembly.Result result, ItemStack stack) {
        String type = result.type().name().toLowerCase();
        String grade = overallGrade(stack, new String[] { result.slot1(), result.slot2(), result.slot3() }).name().toLowerCase();
        boolean tail = !normalize(result.slot3()).isEmpty();
        InputSignals.talismanCreated(player, type, grade, tail);
    }

    private static TalismanGrade overallGrade(ItemStack stack, String[] slots) {
        CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        TalismanGrade worst = null;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null || slots[i].isBlank()) continue;
            TalismanGrade grade = TalismanGrade.byName(tag.getString("aozaink:grade" + (i + 1)));
            if (grade == null) continue;
            if (worst == null || grade.ordinal() > worst.ordinal()) {
                worst = grade;
            }
        }
        if (resultType(stack) == TalismanAssembly.Type.FAILED) {
            return TalismanGrade.WASTE;
        }
        return worst == null ? TalismanGrade.FINE : worst;
    }

    private static TalismanAssembly.Type resultType(ItemStack stack) {
        CompoundTag tag = TalismanAssembly.talismanTag(stack);
        try {
            return TalismanAssembly.Type.valueOf(tag.getString(TalismanAssembly.TAG_TYPE).toUpperCase());
        } catch (IllegalArgumentException exception) {
            return TalismanAssembly.Type.FAILED;
        }
    }

    public static void castPaper(ServerPlayer player, CastPaperPayload payload) {
        if (!player.getMainHandItem().is(Items.PAPER)) {
            player.displayClientMessage(Component.literal("[AozaiInk] 主手需持纸"), true);
            return;
        }
        InkTrace trace = payload.trace();
        if (!AozaiInkRecognitionExecutor.validateTrace(trace)) {
            player.displayClientMessage(Component.literal("[AozaiInk] 笔迹数据过大"), true);
            return;
        }
        QuickCastSessionManager.beginRevision(player, payload.revision());
        AozaiInkRecognitionExecutor.get().supersedeLatest(player, payload.revision());

        InkSource source = new InkSource(
            payload.sourceId(),
            payload.powerMultiplier(),
            "default",
            0.0f,
            Map.of()
        );
        InkRecognitionRequest request = new InkRecognitionRequest(
            trace,
            null,
            QuickCastSessionManager.recognitionGlyphs(),
            payload.ttlTicks() > 0 ? payload.ttlTicks() : DEFAULT_TTL_TICKS,
            source
        );

        AozaiInkRecognitionExecutor.get().submit(
            player,
            request,
            result -> finalizePaperCast(player, result, source, payload.revision()),
            reason -> player.displayClientMessage(Component.literal("[AozaiInk] 识别失败: " + reason), true)
        );
    }

    public static void previewQuickCast(ServerPlayer player, PreviewQuickCastPayload payload) {
        if (!player.getMainHandItem().is(Items.PAPER)) return;
        if (!AozaiInkRecognitionExecutor.validateTrace(payload.trace())) return;
        if (!QuickCastSessionManager.beginRevision(player, payload.revision())) return;
        InkSource source = new InkSource(
            payload.sourceId(), payload.powerMultiplier(), "default", 0.0f, Map.of());
        InkRecognitionRequest request = new InkRecognitionRequest(
            payload.trace(), null,
            QuickCastSessionManager.recognitionGlyphs(),
            payload.ttlTicks() > 0 ? payload.ttlTicks() : DEFAULT_TTL_TICKS,
            source);
        AozaiInkRecognitionExecutor.get().submitLatest(
            player,
            request,
            payload.revision(),
            result -> QuickCastSessionManager.offer(player, result, source, payload.revision()),
            reason -> { }
        );
    }

    public static void selectQuickCastCandidate(ServerPlayer player, SelectQuickCastCandidatePayload payload) {
        QuickCastSessionManager.select(player, payload);
    }

    private static void finalizePaperCast(
        ServerPlayer player, InkRecognitionResult result, InkSource source, long revision
    ) {
        if (player.isRemoved() || player.connection == null) {
            return;
        }
        if (result == null || result.candidates().isEmpty()) {
            player.displayClientMessage(Component.literal("[AozaiInk] 临时施法未生效"), true);
            return;
        }
        if (!player.getMainHandItem().is(Items.PAPER)) {
            player.displayClientMessage(Component.literal("[AozaiInk] 主手需持纸"), true);
            return;
        }
        if (QuickCastSessionManager.DIGITS.contains(result.topGlyph())) {
            QuickCastSessionManager.castLegacyDigit(player, result.topGlyph(), result, source, revision);
        } else {
            QuickCastSessionManager.offer(player, result, source, revision);
        }
    }

    private static String normalize(String glyph) {
        return glyph == null || glyph.isBlank() ? "" : glyph.trim();
    }

    @FunctionalInterface
    private interface SlotRecognitionConsumer {
        void accept(List<InkRecognitionResult> results);
    }
}
