package com.aozainkmc.input.client;

import com.aozainkmc.core.AozaiInkCoreApi;
import com.aozainkmc.core.api.InkPoint;
import com.aozainkmc.core.api.InkRecognitionMode;
import com.aozainkmc.core.api.InkRecognitionRequest;
import com.aozainkmc.core.api.InkRecognitionResult;
import com.aozainkmc.core.api.InkRecognizedEvent;
import com.aozainkmc.core.api.InkSource;
import com.aozainkmc.core.api.InkTrace;
import com.aozainkmc.input.AozaiInkInput;
import com.aozainkmc.input.block.AozaiInkBlocks;
import com.aozainkmc.input.item.TalismanAssembly;
import com.aozainkmc.input.scoring.TalismanGrade;
import com.aozainkmc.input.scoring.TalismanScorer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;

public final class TalismanWritingScreen extends Screen {
    private static final long AUTO_RECOGNIZE_MS = 700L;
    private static final int SLOT_COUNT = 3;
    private static final int SLOT_SIZE = 118;
    private static final int SLOT_GAP = 6;
    private static final int PANEL_PAD = 18;
    private static final int INK_COLOR = 0xFF14110B;
    private static final int BORDER = 0xFF5F3B11;
    private static final int PAPER = 0xFFF6D05A;
    private static final int PAPER_DARK = 0xFFE0AD35;

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
        int panelWidth = SLOT_COUNT * SLOT_SIZE + (SLOT_COUNT - 1) * SLOT_GAP + PANEL_PAD * 2;
        int panelHeight = SLOT_SIZE + PANEL_PAD * 2 + 44;
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        int buttonY = panelY + PANEL_PAD + SLOT_SIZE + 16;
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
        int panelWidth = SLOT_COUNT * SLOT_SIZE + (SLOT_COUNT - 1) * SLOT_GAP + PANEL_PAD * 2;
        int panelHeight = SLOT_SIZE + PANEL_PAD * 2 + 44;
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xDD2E1C08);
        graphics.drawString(font, title, panelX + PANEL_PAD, panelY + 6, 0xFFFFE8A0, false);

        for (int i = 0; i < SLOT_COUNT; i++) {
            int x = slotX(i);
            int y = slotY();
            int color = i == activeSlot ? 0xFFFFF0A8 : PAPER;
            graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, color);
            graphics.fill(x, y, x + SLOT_SIZE, y + 2, BORDER);
            graphics.fill(x, y + SLOT_SIZE - 2, x + SLOT_SIZE, y + SLOT_SIZE, BORDER);
            graphics.fill(x, y, x + 2, y + SLOT_SIZE, BORDER);
            graphics.fill(x + SLOT_SIZE - 2, y, x + SLOT_SIZE, y + SLOT_SIZE, BORDER);
            drawSlotInk(graphics, slots[i], x, y);
            String label = switch (i) {
                case 0 -> "左";
                case 1 -> "中";
                default -> "右";
            };
            graphics.drawString(font, label, x + 6, y + 6, 0xFF553400, false);
            String recognized = slots[i].recognizedGlyph == null ? "" : slots[i].recognizedGlyph;
            String text = slots[i].recognizing ? "识别中" : (recognized.isEmpty() ? "" : "识别: " + recognized);
            if (!text.isEmpty()) {
                graphics.fill(x + 4, y + SLOT_SIZE - 18, x + SLOT_SIZE - 4, y + SLOT_SIZE - 4, 0xAAFFF1A6);
                graphics.drawString(font, text, x + 8, y + SLOT_SIZE - 15, 0xFF2E1C08, false);
            }
        }

        if (!status.isEmpty()) {
            graphics.drawString(font, status, panelX + PANEL_PAD + 82, panelY + PANEL_PAD + SLOT_SIZE + 22, 0xFFFFE8A0, false);
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
                slots[slot].startStroke(normX(slot, mouseX), normY(mouseY));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && activeSlot >= 0) {
            slots[activeSlot].addPoint(normX(activeSlot, mouseX), normY(mouseY));
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
        submit(glyphs[0], glyphs[1], glyphs[2]);
    }

    private void submit(String slot1, String slot2, String slot3) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            status = "当前版本黄符成符只支持单人/局域网房主测试";
            return;
        }
        TalismanAssembly.Result result = TalismanAssembly.classify(slot1, slot2, slot3);
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(minecraft.player.getUUID());
            if (player == null) return;
            if (!player.serverLevel().getBlockState(blockPos).is(AozaiInkBlocks.YELLOW_TALISMAN.get())) {
                player.sendSystemMessage(Component.literal("[AozaiInk] 黄符方块已不存在"));
                return;
            }
            ItemStack stack = TalismanAssembly.createStack(result);
            player.serverLevel().setBlock(blockPos, Blocks.AIR.defaultBlockState(), 3);
            broadcastTalismanRecognitions(player, server, result, stack);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            player.sendSystemMessage(Component.literal("[AozaiInk] 成符: " + result.type().displayName()
                + " [" + result.slot1() + ", " + result.slot2() + ", " + result.slot3() + "]"));
        });
        minecraft.setScreen(null);
    }

    private void broadcastTalismanRecognitions(ServerPlayer player, MinecraftServer server, TalismanAssembly.Result result, ItemStack stack) {
        String type = result.type().name().toLowerCase();
        Map<Integer, String> slots = Map.of(1, result.slot1(), 2, result.slot2(), 3, result.slot3());
        for (int i = 1; i <= 3; i++) {
            String glyph = slots.get(i);
            if (glyph == null || glyph.isEmpty()) continue;
            if (result.type() == TalismanAssembly.Type.SPECIFIED && i != 2) continue;
            if (result.type() == TalismanAssembly.Type.INSCRIPTION && i != 2) continue;

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

    private void drawSlotInk(GuiGraphics graphics, SlotState slot, int x, int y) {
        for (List<DrawPoint> stroke : slot.strokes) {
            DrawPoint previous = null;
            for (DrawPoint point : stroke) {
                int px = x + Math.round(point.x * (SLOT_SIZE - 1));
                int py = y + Math.round(point.y * (SLOT_SIZE - 1));
                if (previous != null) {
                    int qx = x + Math.round(previous.x * (SLOT_SIZE - 1));
                    int qy = y + Math.round(previous.y * (SLOT_SIZE - 1));
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
            int y = slotY();
            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                return i;
            }
        }
        return -1;
    }

    private float normX(int slot, double mouseX) {
        return clamp((float) ((mouseX - slotX(slot)) / (SLOT_SIZE - 1)));
    }

    private float normY(double mouseY) {
        return clamp((float) ((mouseY - slotY()) / (SLOT_SIZE - 1)));
    }

    private float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private int slotX(int slot) {
        return panelX + PANEL_PAD + slot * (SLOT_SIZE + SLOT_GAP);
    }

    private int slotY() {
        return panelY + PANEL_PAD;
    }

    private static final class SlotState {
        private final List<List<DrawPoint>> strokes = new ArrayList<>();
        private List<DrawPoint> currentStroke;
        private boolean dirty;
        private boolean recognizing;
        private String recognizedGlyph;
        private float confidence;
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
            lastInputMs = 0L;
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



