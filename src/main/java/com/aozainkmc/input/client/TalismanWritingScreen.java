package com.aozainkmc.input.client;

import com.aozainkmc.core.AozaiInkCoreApi;
import com.aozainkmc.core.api.GlyphDescriber;
import com.aozainkmc.core.api.InkPoint;
import com.aozainkmc.core.api.InkRecognitionMode;
import com.aozainkmc.core.api.InkRecognitionRequest;
import com.aozainkmc.core.api.InkRecognitionResult;
import com.aozainkmc.core.api.InkRecognizedEvent;
import com.aozainkmc.core.api.InkSource;
import com.aozainkmc.core.api.InkTrace;
import com.aozainkmc.input.AozaiInkInput;
import com.aozainkmc.input.block.AozaiInkBlocks;
import com.aozainkmc.input.effect.TailModifierFailureEffect;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;

public final class TalismanWritingScreen extends Screen {
    private static final long AUTO_RECOGNIZE_MS = 700L;
    private static final int SLOT_COUNT = 3;
    private static final int SLOT_SIZE = 118;
    private static final int MODIFIER_SLOT = 2;
    private static final int MODIFIER_SLOT_SIZE = 96;
    private static final int SLOT_GAP = 6;
    private static final int PANEL_PAD = 18;
    private static final int COMBO_PREVIEW_HEIGHT = 24;
    private static final int SLOT_TEXTURE_SIZE = 256;
    private static final int INK_COLOR = 0xFF14110B;
    private static final int BORDER = 0xFF5F3B11;
    private static final int CINNABAR_BORDER = 0xFFB3261E;
    private static final int PAPER = 0xFFF6D05A;
    private static final int PAPER_DARK = 0xFFE0AD35;
    private static final Set<String> TAIL_MODIFIERS = Set.of("强", "续", "广", "穿");
    private static final ResourceLocation ZHOUWEI_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("aozaink_input", "textures/gui/talisman_zhouwei.png");
    private static final ResourceLocation WEIXIU_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("aozaink_input", "textures/gui/talisman_weixiu.png");

    private final BlockPos blockPos;
    private final SlotState[] slots = { new SlotState(), new SlotState(), new SlotState() };
    private int panelX;
    private int panelY;
    private int activeSlot = -1;
    private String status = "";

    public TalismanWritingScreen(BlockPos blockPos) {
        super(Component.literal("黄符"));
        this.blockPos = blockPos.immutable();
    }

    @Override
    protected void init() {
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        int buttonY = panelY + PANEL_PAD + SLOT_SIZE + COMBO_PREVIEW_HEIGHT + 12;
        addRenderableWidget(Button.builder(Component.literal("清空"), button -> clearAll())
            .bounds(panelX + PANEL_PAD, buttonY, 70, 20)
            .build());
        addRenderableWidget(Button.builder(Component.literal("成符"), button -> finish())
            .bounds(panelX + panelWidth - PANEL_PAD - 70, buttonY, 70, 20)
            .build());
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        for (SlotState slot : slots) {
            if (slot.dirty && !slot.recognizing && slot.hasInk() && now - slot.lastInputMs >= AUTO_RECOGNIZE_MS) {
                recognize(slot);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x66000000);
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xDD2E1C08);
        graphics.drawString(font, title, panelX + PANEL_PAD, panelY + 6, 0xFFFFE8A0, false);

        for (int i = 0; i < SLOT_COUNT; i++) {
            int x = slotX(i);
            int y = slotY(i);
            int size = slotSize(i);
            int color = i == activeSlot ? 0xFFFFF0A8 : PAPER;
            graphics.fill(x, y, x + size, y + size, color);
            drawSlotTexture(graphics, i, x, y, size);
            drawSlotBorder(graphics, x, y, size, BORDER, 2);
            if (i == MODIFIER_SLOT) {
                drawSlotBorder(graphics, x - 2, y - 2, size + 4, CINNABAR_BORDER, 1);
            }
            drawSlotInk(graphics, slots[i], x, y, size);
            String label = switch (i) {
                case 0, 1 -> "咒位";
                default -> "尾修";
            };
            graphics.drawString(font, label, x + 6, y + 6, 0xFF553400, false);
            drawSlotRecognition(graphics, i, slots[i], x, y, size);
        }

        drawComboPreview(graphics);

        if (!status.isEmpty()) {
            graphics.drawString(font, status, panelX + PANEL_PAD, panelY + panelHeight - 16, 0xFFFFE8A0, false);
        }
        for (var renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0) {
                activeSlot = slot;
                slots[slot].startStroke(normX(slot, mouseX), normY(slot, mouseY));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && activeSlot >= 0) {
            slots[activeSlot].addPoint(normX(activeSlot, mouseX), normY(activeSlot, mouseY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && activeSlot >= 0) {
            slots[activeSlot].endStroke();
            activeSlot = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x66000000);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void finish() {
        String[] glyphs = new String[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            SlotState slot = slots[i];
            if (!slot.hasInk()) {
                glyphs[i] = "";
            } else {
                if (slot.dirty || slot.recognizedGlyph == null) {
                    recognize(slot);
                }
                glyphs[i] = slot.recognizedGlyph == null ? "" : slot.recognizedGlyph;
            }
        }
        if (invalidTailGlyph(glyphs[MODIFIER_SLOT])) {
            status = "尾修槽只接受 强 / 续 / 广 / 穿";
            return;
        }
        TailModifierStability.Result tailStability = null;
        String tailGlyph = normalizeGlyph(glyphs[MODIFIER_SLOT]);
        if (!tailGlyph.isEmpty()) {
            SlotState tailSlot = slots[MODIFIER_SLOT];
            int strokeCount = tailSlot.simplifiedStrokeCount > 0 ? tailSlot.simplifiedStrokeCount : tailSlot.strokeCount();
            tailStability = TailModifierStability.evaluate(tailGlyph, strokeCount).orElse(null);
        }
        submit(glyphs[0], glyphs[1], glyphs[2], tailStability);
    }

    private void submit(String slot1, String slot2, String slot3, TailModifierStability.Result tailStability) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            status = "当前版本黄符成符只支持单人/局域网房主测试";
            return;
        }
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(minecraft.player.getUUID());
            if (player == null) return;
            if (!player.serverLevel().getBlockState(blockPos).is(AozaiInkBlocks.YELLOW_TALISMAN.get())) {
                player.sendSystemMessage(Component.literal("[AozaiInk] 黄符方块已不存在"));
                return;
            }
            if (tailStability != null && player.getRandom().nextDouble() >= tailStability.successChance()) {
                player.serverLevel().setBlock(blockPos, Blocks.AIR.defaultBlockState(), 3);
                player.displayClientMessage(Component.literal("尾修失败，符力暴乱"), true);
                InputSignals.tailModifierChaos(player, false, false, false);
                TailModifierFailureEffect.start(player, blockPos);
                return;
            }
            TalismanAssembly.Result result = TalismanAssembly.classify(slot1, slot2, slot3);
            ItemStack stack = TalismanAssembly.createStack(result);
            player.serverLevel().setBlock(blockPos, Blocks.AIR.defaultBlockState(), 3);
            broadcastTalismanRecognitions(player, server, result, stack);
            triggerTalismanCreated(player, result, stack);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            player.sendSystemMessage(Component.literal("[AozaiInk] 成符: " + result.type().displayName()
                + " [" + result.slot1() + ", " + result.slot2() + ", " + result.slot3() + "]"));
        });
        minecraft.setScreen(null);
    }

    private static void triggerTalismanCreated(ServerPlayer player, TalismanAssembly.Result result, ItemStack stack) {
        String type = result.type().name().toLowerCase();
        String grade = overallGrade(stack, new String[] {result.slot1(), result.slot2(), result.slot3()}).name().toLowerCase();
        boolean tail = !normalizeGlyph(result.slot3()).isEmpty();
        InputSignals.talismanCreated(player, type, grade, tail);
    }

    private static TalismanGrade overallGrade(ItemStack stack, String[] slots) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
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

    private void broadcastTalismanRecognitions(ServerPlayer player, MinecraftServer server, TalismanAssembly.Result result, ItemStack stack) {
        String type = result.type().name().toLowerCase();
        Map<Integer, String> slots = Map.of(1, result.slot1(), 2, result.slot2(), 3, result.slot3());
        for (int i = 1; i <= 3; i++) {
            String glyph = slots.get(i);
            if (glyph == null || glyph.isEmpty()) continue;
            if (result.type() == TalismanAssembly.Type.SPECIFIED && i != 2) continue;
            if (result.type() == TalismanAssembly.Type.INSCRIPTION && "刻".equals(glyph)) continue;

            Map<String, Object> extra = new HashMap<>();
            extra.put("talisman_type", type);
            extra.put("slot", String.valueOf(i));
            if (result.type() == TalismanAssembly.Type.SPECIFIED) {
                extra.put("specified_number", result.slot1());
                extra.put("specified_glyph", result.slot2());
            }

            InkTrace trace = slotTrace(i - 1);
            if (trace == null || trace.isEmpty()) continue;
            InkRecognitionRequest request = new InkRecognitionRequest(
                trace,
                null,
                InkRecognitionMode.ONLINE,
                AozaiInkInput.TALISMAN_GLYPHS,
                20L * 60L * 10L,
                new InkSource(AozaiInkInput.SOURCE_TRAJECTORY, 1.0f, "yellow_talisman", 0, extra)
            );
            try {
                InkRecognizedEvent event = AozaiInkCoreApi.recognizer().recognizeAndBroadcast(request, server, player);
                embedScore(stack, i, event);
            } catch (Exception e) {
                AozaiInkInput.LOGGER.warn("Talisman recognition broadcast failed for slot {}: {}", i, e.getMessage());
            }
        }
    }

    private static void embedScore(ItemStack stack, int slot, InkRecognizedEvent event) {
        if (event == null) return;
        InkRecognitionResult r = event.result();
        Optional<TalismanScorer.Score> score = TalismanScorer.score(
            r.topGlyph(), r.simplifiedStrokeCount(), r.simplifiedPointCount(), r.writingDurationMs(), r.confidence());
        if (score.isEmpty()) return;
        TalismanGrade grade = TalismanGrade.of(score.get().composite());
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        tag.putString("aozaink:grade" + slot, grade.name());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private InkTrace slotTrace(int index) {
        SlotState slot = slots[index];
        if (slot == null || slot.strokes.isEmpty()) return null;
        List<List<InkPoint>> strokes = new ArrayList<>();
        for (List<DrawPoint> stroke : slot.strokes) {
            List<InkPoint> points = new ArrayList<>();
            for (DrawPoint point : stroke) {
                points.add(new InkPoint(point.x, point.y, point.timeMs));
            }
            if (!points.isEmpty()) strokes.add(points);
        }
        return strokes.isEmpty() ? null : new InkTrace(strokes);
    }

    private void recognize(SlotState slot) {
        if (!slot.hasInk()) {
            slot.recognizedGlyph = "";
            slot.confidence = 0.0f;
            slot.simplifiedStrokeCount = 0;
            slot.dirty = false;
            return;
        }
        try {
            if (AozaiInkCoreApi.recognizer() == null) {
                status = "core recognizer 不可用";
                return;
            }
            slot.recognizing = true;
            InkRecognitionResult result = AozaiInkCoreApi.recognizer().recognize(buildRequest(slot));
            slot.recognizedGlyph = result.topGlyph();
            slot.confidence = result.confidence();
            slot.simplifiedStrokeCount = result.simplifiedStrokeCount();
            slot.dirty = false;
            status = slot.recognizedGlyph.isEmpty()
                ? "未识别"
                : "识别 " + slot.recognizedGlyph + " " + Math.round(slot.confidence * 100.0f) + "%";
        } catch (Exception e) {
            AozaiInkInput.LOGGER.warn("Yellow talisman slot recognition failed", e);
            status = "识别异常: " + e.getClass().getSimpleName();
        } finally {
            slot.recognizing = false;
        }
    }

    private InkRecognitionRequest buildRequest(SlotState slot) {
        List<List<InkPoint>> strokes = new ArrayList<>();
        for (List<DrawPoint> stroke : slot.strokes) {
            List<InkPoint> points = new ArrayList<>();
            for (DrawPoint point : stroke) {
                points.add(new InkPoint(point.x, point.y, point.timeMs));
            }
            if (!points.isEmpty()) strokes.add(points);
        }
        return new InkRecognitionRequest(
            new InkTrace(strokes),
            null,
            InkRecognitionMode.ONLINE,
            AozaiInkInput.TALISMAN_GLYPHS,
            20L * 60L * 10L,
            new InkSource(AozaiInkInput.SOURCE_TRAJECTORY, 1.0f, "yellow_talisman", 0, Collections.emptyMap())
        );
    }

    private void clearAll() {
        for (SlotState slot : slots) {
            slot.clear();
        }
        activeSlot = -1;
        status = "";
    }

    private void drawSlotInk(GuiGraphics graphics, SlotState slot, int x, int y, int size) {
        for (List<DrawPoint> stroke : slot.strokes) {
            DrawPoint previous = null;
            for (DrawPoint point : stroke) {
                int px = x + Math.round(point.x * (size - 1));
                int py = y + Math.round(point.y * (size - 1));
                if (previous != null) {
                    int qx = x + Math.round(previous.x * (size - 1));
                    int qy = y + Math.round(previous.y * (size - 1));
                    drawLine(graphics, qx, qy, px, py);
                }
                graphics.fill(px - 1, py - 1, px + 2, py + 2, INK_COLOR);
                previous = point;
            }
        }
    }

    private void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) {
            graphics.fill(x1 - 1, y1 - 1, x1 + 2, y1 + 2, INK_COLOR);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + Math.round(dx * (i / (float) steps));
            int y = y1 + Math.round(dy * (i / (float) steps));
            graphics.fill(x - 1, y - 1, x + 2, y + 2, INK_COLOR);
        }
    }

    private int slotAt(double mouseX, double mouseY) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            int x = slotX(i);
            int y = slotY(i);
            int size = slotSize(i);
            if (mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size) {
                return i;
            }
        }
        return -1;
    }

    private float normX(int slot, double mouseX) {
        return clamp((float) ((mouseX - slotX(slot)) / (slotSize(slot) - 1)));
    }

    private float normY(int slot, double mouseY) {
        return clamp((float) ((mouseY - slotY(slot)) / (slotSize(slot) - 1)));
    }

    private float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private void drawSlotRecognition(GuiGraphics graphics, int index, SlotState slot, int x, int y, int size) {
        String recognized = slot.recognizedGlyph == null ? "" : slot.recognizedGlyph;
        String recText = slot.recognizing ? "识别中" : (recognized.isEmpty() ? "" : "识别: " + recognized);
        String descLine = "";
        boolean invalidTail = index == MODIFIER_SLOT && invalidTailGlyph(recognized);
        if (invalidTail) {
            descLine = "尾修仅强/续/广/穿";
        } else if (!recognized.isEmpty()) {
            GlyphDescriber describer = AozaiInkCoreApi.getService(GlyphDescriber.class);
            if (describer != null) {
                var desc = describer.describe(List.of(recognized));
                if (!desc.isEmpty()) descLine = desc.get(0);
            }
        }
        boolean hasRec = !recText.isEmpty();
        boolean hasDesc = !descLine.isEmpty();
        if (!hasRec && !hasDesc) return;
        int bandTop = y + size - 4 - (hasDesc ? 28 : 14);
        graphics.fill(x + 4, bandTop, x + size - 4, y + size - 4, invalidTail ? 0xAADF5A45 : 0xAAFFF1A6);
        if (hasRec) {
            graphics.drawString(font, recText, x + 8, bandTop + 3, 0xFF2E1C08, false);
        }
        if (hasDesc) {
            graphics.drawString(font, descLine, x + 8, bandTop + 15, 0xFF553400, false);
        }
    }

    private void drawComboPreview(GuiGraphics graphics) {
        List<String> lines = buildComboLines();
        if (lines.isEmpty()) return;
        int y = slotBaseY() + SLOT_SIZE + 4;
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), panelX + PANEL_PAD, y + i * 10, 0xFFD7C17A, false);
        }
    }

    private List<String> buildComboLines() {
        String s0 = slotGlyph(0);
        String s1 = slotGlyph(1);
        String s2 = slotGlyph(2);
        if (s0.isEmpty() && s1.isEmpty() && s2.isEmpty()) return List.of();
        if (invalidTailGlyph(s2)) return List.of("尾修槽只接受 强 / 续 / 广 / 穿");
        GlyphDescriber describer = AozaiInkCoreApi.getService(GlyphDescriber.class);
        if (describer == null) return List.of();
        return describer.describe(List.of(s0, s1, s2));
    }

    private String slotGlyph(int index) {
        if (index < 0 || index >= SLOT_COUNT) return "";
        SlotState slot = slots[index];
        if (slot.recognizedGlyph == null) return "";
        return slot.recognizedGlyph;
    }

    private int slotX(int slot) {
        int x = panelX + PANEL_PAD;
        for (int i = 0; i < slot; i++) {
            x += slotSize(i) + SLOT_GAP;
        }
        return x;
    }

    private int slotY(int slot) {
        return slotBaseY() + (SLOT_SIZE - slotSize(slot)) / 2;
    }

    private int slotBaseY() {
        return panelY + PANEL_PAD;
    }

    private int slotSize(int slot) {
        return slot == MODIFIER_SLOT ? MODIFIER_SLOT_SIZE : SLOT_SIZE;
    }

    private int panelWidth() {
        int slotsWidth = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            slotsWidth += slotSize(i);
        }
        return slotsWidth + (SLOT_COUNT - 1) * SLOT_GAP + PANEL_PAD * 2;
    }

    private int panelHeight() {
        return SLOT_SIZE + PANEL_PAD * 2 + 44 + COMBO_PREVIEW_HEIGHT;
    }

    private void drawSlotTexture(GuiGraphics graphics, int slot, int x, int y, int size) {
        ResourceLocation texture = slot == MODIFIER_SLOT ? WEIXIU_TEXTURE : ZHOUWEI_TEXTURE;
        int inset = slot == MODIFIER_SLOT ? 18 : 20;
        int textureSize = size - inset * 2;
        graphics.blit(texture, x + inset, y + inset, textureSize, textureSize, 0.0f, 0.0f,
            SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE);
    }

    private void drawSlotBorder(GuiGraphics graphics, int x, int y, int size, int color, int thickness) {
        graphics.fill(x, y, x + size, y + thickness, color);
        graphics.fill(x, y + size - thickness, x + size, y + size, color);
        graphics.fill(x, y, x + thickness, y + size, color);
        graphics.fill(x + size - thickness, y, x + size, y + size, color);
    }

    private static boolean invalidTailGlyph(String glyph) {
        String normalized = normalizeGlyph(glyph);
        return !normalized.isEmpty() && !TAIL_MODIFIERS.contains(normalized);
    }

    private static String normalizeGlyph(String glyph) {
        return glyph == null || glyph.isBlank() ? "" : glyph.trim();
    }

    private static final class SlotState {
        private final List<List<DrawPoint>> strokes = new ArrayList<>();
        private List<DrawPoint> currentStroke;
        private boolean dirty;
        private boolean recognizing;
        private String recognizedGlyph;
        private float confidence;
        private int simplifiedStrokeCount;
        private long lastInputMs;

        private boolean hasInk() {
            for (List<DrawPoint> stroke : strokes) {
                if (!stroke.isEmpty()) return true;
            }
            return false;
        }

        private void startStroke(float x, float y) {
            currentStroke = new ArrayList<>();
            strokes.add(currentStroke);
            addPoint(x, y);
        }

        private void addPoint(float x, float y) {
            if (currentStroke == null) startStroke(x, y);
            DrawPoint next = new DrawPoint(x, y, System.currentTimeMillis());
            if (currentStroke.isEmpty() || currentStroke.get(currentStroke.size() - 1).distanceSquared(next) > 0.00001f) {
                currentStroke.add(next);
                dirty = true;
                recognizedGlyph = null;
                confidence = 0.0f;
                simplifiedStrokeCount = 0;
                lastInputMs = next.timeMs;
            }
        }

        private void endStroke() {
            currentStroke = null;
            lastInputMs = System.currentTimeMillis();
        }

        private void clear() {
            strokes.clear();
            currentStroke = null;
            dirty = false;
            recognizing = false;
            recognizedGlyph = null;
            confidence = 0.0f;
            simplifiedStrokeCount = 0;
            lastInputMs = 0L;
        }

        private int strokeCount() {
            int count = 0;
            for (List<DrawPoint> stroke : strokes) {
                if (!stroke.isEmpty()) count++;
            }
            return count;
        }
    }

    private record DrawPoint(float x, float y, long timeMs) {
        private float distanceSquared(DrawPoint other) {
            float dx = x - other.x;
            float dy = y - other.y;
            return dx * dx + dy * dy;
        }
    }
}
