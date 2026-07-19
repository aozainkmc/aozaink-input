package com.aozainkmc.input.client;

import com.aozainkmc.core.AozaiInkCoreApi;
import com.aozainkmc.core.api.GlyphDescriber;
import com.aozainkmc.input.AozaiInkInput;
import com.aozainkmc.input.network.ClearQuickBindingPayload;
import com.aozainkmc.input.network.MoluMenuPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Shared Molu menu using the original Sigillum scroll-book visual language. */
public final class MoluMenuScreen extends Screen {
    private static final int OVERLAY = 0x66000000;
    private static final int VOID = 0xF21A120B;
    private static final int VOID_DK = 0xFF120E09;
    private static final int VOID_LT = 0xFF241B12;
    private static final int EDGE = 0xFF5B3E22;
    private static final int EDGE_SOFT = 0x885B3E22;
    private static final int GRID = 0x664D3824;
    private static final int PAPER = 0xFF9D7B45;
    private static final int PAPER_DK = 0xFF74623F;
    private static final int PAPER_TX = 0xFFD7C19A;
    private static final int INK = 0xFF1C140C;
    private static final int MUTED_DK = 0xFF6F604C;
    private static final int RED = 0xFF5A2114;
    private static final int RED_LT = 0xFF8F2E1F;
    private static final int RED_TX = 0xFFB43C2B;
    private static final int HOT_TX = 0xFFE2CFAB;
    private static final int SLOT_COUNT = 9;
    private static final float BRIEF_TEXT_SCALE = 1.15f;
    private static final float DETAIL_TEXT_SCALE = 0.82f;
    private static final Set<String> TAIL_MODIFIERS = Set.of("强", "续", "广", "穿");

    private final Map<Integer, MoluMenuPayload.BindingEntry> bindings = new LinkedHashMap<>();
    private final List<MoluMenuPayload.GlyphEntry> glyphs;
    private final List<MoluMenuPayload.TabEntry> extensionTabs;
    private final List<String> tabLabels = new ArrayList<>();
    private final List<String> comboGlyphs;
    private final int[] scrollOffsets;
    private final String[] comboSlots = { "", "", "" };
    private int panelX;
    private int panelY;
    private int activeTab;
    private boolean detailed = true;

    public MoluMenuScreen(MoluMenuPayload payload) {
        super(Component.literal("符咒簿"));
        for (MoluMenuPayload.BindingEntry entry : payload.bindings()) {
            bindings.put(entry.slot(), entry);
        }
        glyphs = List.copyOf(payload.glyphs());
        extensionTabs = List.copyOf(payload.tabs());
        tabLabels.add("快速上手");
        tabLabels.add("快速吟唱");
        tabLabels.add("黄符字典");
        tabLabels.add("技能表");
        for (MoluMenuPayload.TabEntry tab : extensionTabs) tabLabels.add(tab.label());
        tabLabels.add("设置");
        scrollOffsets = new int[tabLabels.size()];
        comboGlyphs = List.copyOf(AozaiInkInput.talismanGlyphs());
    }

    @Override
    protected void init() {
        int margin = Math.max(8, Math.min(width, height) / 28);
        panelX = margin;
        panelY = margin;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, OVERLAY);
        drawFrame(g);
        drawTitle(g);
        drawTabs(g, mouseX, mouseY);
        if (activeTab == 0) renderQuickStart(g);
        else if (activeTab == 1) renderBindings(g);
        else if (activeTab == 2) renderCodex(g);
        else if (activeTab == comboTabIndex()) renderComboWiki(g);
        else if (activeTab == settingsTabIndex()) renderSettings(g, mouseX, mouseY);
        else renderExtension(g, extensionTabs.get(activeTab - extensionTabStart()));
        drawBottomButtons(g, mouseX, mouseY);
    }

    private int comboTabIndex() { return 3; }
    private int extensionTabStart() { return 4; }
    private int settingsTabIndex() { return tabLabels.size() - 1; }

    private void renderQuickStart(GuiGraphics g) {
        int x = contentX(), y = contentY(), w = contentW();
        int panelH = contentH();
        int rowH = 18;
        int bottomPadding = 24;
        int totalRows = quickStartTotalRows();
        int visibleRows = panelH / rowH;
        int maxOffset = Math.max(0, totalRows - visibleRows + bottomPadding / rowH);
        int offset = clamp(scrollOffsets[0], 0, maxOffset);
        scrollOffsets[0] = offset;

        g.enableScissor(x, y, x + w, y + panelH);

        int yy = y - offset * rowH + 8;
        int textX = x + 18;
        int maxW = w - 36;

        yy += drawQuickStartLine(g, "核心玩法", textX, yy, maxW, HOT_TX, 1.2f) + 8;
        yy += drawQuickStartLine(g, "① 先合成空白黄符", textX, yy, maxW, PAPER_TX, 1.05f) + 5;
        yy += drawQuickStartWrapped(g, "在工作台无序合成：3 张纸 + 1 份黄色染料，产出 6 张空白黄符。",
            textX + 14, yy, maxW - 14, PAPER_TX, 0.84f) + 10;

        int blankY = yy;
        renderBlankTalismanRecipeAt(g, x + 24, blankY, w - 48);
        yy += recipeSlotSize() * 2 + 44;

        yy += drawQuickStartLine(g, "② 把空白黄符放到工作台上", textX, yy, maxW, PAPER_TX, 1.05f) + 5;
        yy += drawQuickStartWrapped(g, "手持空白黄符，按住潜行键并对工作台按使用键，即可将其平铺在工作台上。",
            textX + 14, yy, maxW - 14, PAPER_TX, 0.84f) + 12;

        yy += drawQuickStartLine(g, "③ 书写并施放", textX, yy, maxW, PAPER_TX, 1.05f) + 5;
        yy += drawQuickStartWrapped(g, "右键已放置的黄符打开书写界面，按提示写出符咒。书写完成后拿起成符，右键即可施放。",
            textX + 14, yy, maxW - 14, PAPER_TX, 0.84f) + 16;

        yy += drawQuickStartLine(g, "拓印配方", textX, yy, maxW, HOT_TX, 1.2f) + 8;
        yy += drawQuickStartWrapped(g, "后期可以把写好的成符复制给更多空白黄符：",
            textX, yy, maxW, PAPER_TX, 0.84f) + 8;
        int copyY = yy;
        renderCopyRecipeAt(g, x + 24, copyY, w - 48);
        yy += recipeSlotSize() * 3 + 40;

        yy += drawQuickStartLine(g, "提示", textX, yy, maxW, HOT_TX, 1.2f) + 8;
        yy += drawQuickStartWrapped(g, "对准工作台或已放置黄符时，会显示悬浮操作提示。可在“设置”中关闭。",
            textX, yy, maxW, PAPER_TX, 0.84f);

        g.disableScissor();

        if (totalRows > visibleRows) {
            drawScrollBar(g, x, y, w, panelH, totalRows + bottomPadding / rowH, visibleRows, offset);
        }
    }

    private int quickStartTotalRows() {
        int rowH = 18;
        int x = contentX(), w = contentW();
        int textX = x + 18;
        int maxW = w - 36;
        int h = 8;
        h += lineHeightScaled(1.2f) + 8;
        h += lineHeightScaled(1.05f) + 5;
        h += measureWrappedString("在工作台无序合成：3 张纸 + 1 份黄色染料，产出 6 张空白黄符。",
            maxW - 14, 4, 0.84f) + 10;
        h += recipeSlotSize() * 2 + 44;
        h += lineHeightScaled(1.05f) + 5;
        h += measureWrappedString("手持空白黄符，按住潜行键并对工作台按使用键，即可将其平铺在工作台上。",
            maxW - 14, 4, 0.84f) + 12;
        h += lineHeightScaled(1.05f) + 5;
        h += measureWrappedString("右键已放置的黄符打开书写界面，按提示写出符咒。书写完成后拿起成符，右键即可施放。",
            maxW - 14, 4, 0.84f) + 16;
        h += lineHeightScaled(1.2f) + 8;
        h += measureWrappedString("后期可以把写好的成符复制给更多空白黄符：",
            maxW, 4, 0.84f) + 8;
        h += recipeSlotSize() * 3 + 40;
        h += lineHeightScaled(1.2f) + 8;
        h += measureWrappedString("对准工作台或已放置黄符时，会显示悬浮操作提示。可在“设置”中关闭。",
            maxW, 4, 0.84f);
        return (h + rowH - 1) / rowH;
    }

    private int lineHeightScaled(float scale) {
        return Math.round(font.lineHeight * scale);
    }

    private int drawQuickStartLine(GuiGraphics g, String text, int x, int y, int maxW, int color, float scale) {
        drawScaledText(g, text, x, y, color, scale);
        return Math.round(font.lineHeight * scale);
    }

    private int drawQuickStartWrapped(GuiGraphics g, String text, int x, int y, int maxW, int color, float scale) {
        return drawWrappedString(g, text, x, y, maxW, 4, color, scale);
    }

    private void renderBlankTalismanRecipeAt(GuiGraphics g, int x, int y, int w) {
        int size = recipeSlotSize();
        drawRecipeSlot(g, x, y, size, "纸", PAPER_TX);
        drawRecipeSlot(g, x + size + 5, y, size, "纸", PAPER_TX);
        drawRecipeSlot(g, x, y + size + 5, size, "纸", PAPER_TX);
        drawRecipeSlot(g, x + size + 5, y + size + 5, size, "黄色 染料", PAPER_TX);
        drawScaledCenteredString(g, ">", x + size * 2 + 32, y + size - 4, PAPER_TX, 1.4f);
        drawRecipeSlot(g, x + size * 2 + 58, y + (size + 5) / 2, size, "黄符x6", PAPER_TX);
        int textX = x + Math.max(170, w * 36 / 100);
        drawInfoLines(g, textX, y + 8, x + w - textX - 10, List.of(
            "无序合成：3纸 + 1黄色染料。",
            "产出6张空白黄符。"));
    }

    private void renderCopyRecipeAt(GuiGraphics g, int x, int y, int w) {
        int size = recipeSlotSize();
        String[][] cells = {{"空白 黄符", "空白 黄符", "空白 黄符"},
                            {"空白 黄符", "成符", "空白 黄符"},
                            {"空白 黄符", "红色 染料", "空白 黄符"}};
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                drawRecipeSlot(g, x + column * (size + 5), y + row * (size + 5),
                    size, cells[row][column], PAPER_TX);
            }
        }
        drawScaledCenteredString(g, ">", x + size * 3 + 30, y + size + 4, PAPER_TX, 1.4f);
        drawRecipeSlot(g, x + size * 3 + 56, y + size + 5, size, "成符x2", PAPER_TX);
        int textX = x + Math.max(170, w * 36 / 100);
        drawInfoLines(g, textX, y + 8, x + w - textX - 10, List.of(
            "有序3x3拓印。",
            "中心放已写黄符，下中放红色染料。",
            "其余7格放空白黄符。"));
    }

    private void renderBindings(GuiGraphics g) {
        int x = contentX(), y = contentY(), w = contentW();
        int headerH = 20;
        int visibleRows = visibleRows(20, headerH, SLOT_COUNT);
        int rowH = rowHeight(visibleRows, headerH, 20, 24);
        int offset = clamp(scrollOffsets[activeTab], 0, SLOT_COUNT - visibleRows);
        scrollOffsets[activeTab] = offset;
        int slotColX = x;
        int slotColW = w * 16 / 100;
        int glyphColX = x + slotColW;
        int glyphColW = w * 32 / 100;
        int effectColX = x + w * 48 / 100;
        int actionColX = x + w - 76;
        int effectColW = Math.max(24, actionColX - effectColX);
        int actionColW = Math.max(44, x + w - 8 - actionColX);

        drawTable(g, x, y, w, headerH + visibleRows * rowH, headerH, rowH, visibleRows);
        drawCenteredInBox(g, "槽位", slotColX, y, slotColW, headerH, INK, 1.0f);
        drawCenteredInBox(g, "绑定字", glyphColX, y, glyphColW, headerH, INK, 1.0f);
        drawCenteredInBox(g, "效果", effectColX, y, effectColW, headerH, INK, 1.0f);
        drawCenteredInBox(g, "操作", actionColX, y, actionColW, headerH, INK, 1.0f);
        drawVLine(g, glyphColX, y, headerH + visibleRows * rowH, GRID);
        drawVLine(g, effectColX, y, headerH + visibleRows * rowH, GRID);
        drawVLine(g, actionColX, y, headerH + visibleRows * rowH, GRID);

        for (int row = 0; row < visibleRows; row++) {
            int slot = offset + row + 1;
            int rowY = y + headerH + row * rowH;
            MoluMenuPayload.BindingEntry binding = bindings.get(slot);
            drawCenteredInBox(g, String.valueOf(slot), slotColX, rowY, slotColW, rowH,
                PAPER_TX, BRIEF_TEXT_SCALE);
            if (binding == null || binding.glyph().isEmpty()) {
                drawCenteredInBox(g, "未绑定", glyphColX, rowY, glyphColW, rowH,
                    MUTED_DK, BRIEF_TEXT_SCALE);
                drawCenteredInBox(g, "未绑定", effectColX, rowY, effectColW, rowH,
                    MUTED_DK, BRIEF_TEXT_SCALE);
                continue;
            }
            drawGlyphTagCentered(g, binding.glyph(), glyphColX, rowY, glyphColW, rowH);
            MoluMenuPayload.GlyphEntry glyph = findGlyph(binding.glyph());
            String effect = glyph == null ? "已注册玩法字" : (detailed ? glyph.detail() : glyph.brief());
            if (detailed) {
                drawWrappedCenteredInBox(g, effect, effectColX + 5, rowY,
                    effectColW - 10, rowH, 2, PAPER_TX, DETAIL_TEXT_SCALE);
            } else {
                drawCenteredInBox(g, effect, effectColX, rowY, effectColW, rowH,
                    PAPER_TX, BRIEF_TEXT_SCALE);
            }
            int[] clear = clearRect(rowY, rowH);
            g.fill(clear[0], clear[1], clear[0] + clear[2], clear[1] + clear[3], 0x66302118);
            drawBorder(g, clear[0], clear[1], clear[2], clear[3], MUTED_DK);
            drawCenteredInBox(g, "清除", clear[0], clear[1], clear[2], clear[3], PAPER_TX, 1.0f);
        }
        drawScrollBar(g, x, y + headerH, w, visibleRows * rowH,
            SLOT_COUNT, visibleRows, offset);
    }

    private void renderCodex(GuiGraphics g) {
        int x = contentX(), y = contentY(), w = contentW();
        int totalRows = Math.max(1, (glyphs.size() + 1) / 2);
        int visibleRows = visibleRows(24, 24, totalRows);
        int rowH = rowHeight(visibleRows, 30, 24, 30);
        int listY = y + 24;
        int offset = clamp(scrollOffsets[activeTab], 0, totalRows - visibleRows);
        scrollOffsets[activeTab] = offset;
        drawDarkPanel(g, x, y, w, 24 + visibleRows * rowH);
        g.fill(x + 1, y + 1, x + w - 1, y + 22, PAPER_DK);
        drawMenuString(g, "玩法字典 · 写符时会实时显示组合效果", x + 16, y + 7, INK);

        int colW = (w - 50) / 2;
        for (int row = 0; row < visibleRows; row++) {
            int sourceRow = offset + row;
            for (int col = 0; col < 2; col++) {
                int index = col * totalRows + sourceRow;
                if (index >= glyphs.size()) continue;
                MoluMenuPayload.GlyphEntry entry = glyphs.get(index);
                int rowX = x + 18 + col * colW;
                int rowY = listY + row * rowH;
                drawScaledText(g, entry.glyph(), rowX, rowY + 1, RED_TX, 1.25f);
                drawWrappedString(g, detailed ? entry.detail() : entry.brief(),
                    rowX + 32, rowY + 4, colW - 42, 2, PAPER_TX, 0.72f);
            }
        }
        drawScrollBar(g, x, listY, w, visibleRows * rowH,
            totalRows, visibleRows, offset);
    }

    private void renderExtension(GuiGraphics g, MoluMenuPayload.TabEntry tab) {
        int x = contentX(), y = contentY(), w = contentW();
        List<String> columns = tab.columns().isEmpty() ? List.of("内容") : tab.columns();
        int totalRows = Math.max(1, tab.rows().size());
        int visibleRows = visibleRows(22, 20, totalRows);
        int rowH = rowHeight(visibleRows, 20, 22, 28);
        int offset = clamp(scrollOffsets[activeTab], 0, totalRows - visibleRows);
        scrollOffsets[activeTab] = offset;
        int tableH = 20 + visibleRows * rowH;
        drawTable(g, x, y, w, tableH, 20, rowH, visibleRows);
        for (int column = 0; column < columns.size(); column++) {
            int left = columnLeft(x, w, columns.size(), column);
            int right = columnLeft(x, w, columns.size(), column + 1);
            drawCenteredInBox(g, columns.get(column), left, y, right - left, 20, INK, 1.0f);
            if (column > 0) drawVLine(g, left, y, tableH, GRID);
        }
        if (tab.rows().isEmpty()) {
            drawCenteredInBox(g, "暂无数据", x, y + 20, w, rowH, MUTED_DK, BRIEF_TEXT_SCALE);
            return;
        }
        for (int row = 0; row < visibleRows && offset + row < tab.rows().size(); row++) {
            List<String> values = tab.rows().get(offset + row);
            int rowY = y + 20 + row * rowH;
            for (int column = 0; column < columns.size(); column++) {
                int left = columnLeft(x, w, columns.size(), column);
                int right = columnLeft(x, w, columns.size(), column + 1);
                String value = column < values.size() ? values.get(column) : "";
                drawWrappedCenteredInBox(g, value, left + 5, rowY, right - left - 10,
                    rowH, 2, PAPER_TX, column == 2 ? 0.9f : 0.76f);
            }
        }
        drawScrollBar(g, x, y + 20, w, visibleRows * rowH,
            totalRows, visibleRows, offset);
    }

    private void renderComboWiki(GuiGraphics g) {
        int x = contentX(), y = contentY(), w = contentW();
        int slotSize = recipeSlotSize();
        int pad = 8;
        int plusW = font.width("+");
        int startX = x;
        int slotY = y + 4;

        for (int i = 0; i < 3; i++) {
            int slotX = startX + i * (slotSize + plusW + pad * 2);
            drawRecipeSlot(g, slotX, slotY, slotSize,
                comboSlots[i].isEmpty() ? "空" : comboSlots[i], PAPER_TX);
            if (i < 2) {
                int plusX = slotX + slotSize + pad;
                drawCenteredInBox(g, "+", plusX, slotY, plusW, slotSize, PAPER_TX, 1.2f);
            }
        }

        int textY = slotY + slotSize + 12;
        int textH = 60;
        List<String> lines = buildComboDescription();
        drawInfoLinesClipped(g, x, textY, w - 20, textH, lines);

        int[] grid = comboGridRect();
        int gx = grid[0], gy = grid[1], cols = grid[2], size = grid[3];
        int visibleRows = grid[4], offset = grid[5];
        for (int i = offset * cols; i < comboGlyphs.size() && i < (offset + visibleRows) * cols; i++) {
            int displayRow = i / cols - offset;
            int col = i % cols;
            drawRecipeSlot(g, gx + col * size, gy + displayRow * size, size - 4, comboGlyphs.get(i), PAPER_TX);
        }
        drawScrollBar(g, x, gy, w, visibleRows * size,
            (comboGlyphs.size() + cols - 1) / cols, visibleRows, offset);
    }

    private List<String> buildComboDescription() {
        boolean invalidTail = !comboSlots[2].isEmpty() && !TAIL_MODIFIERS.contains(comboSlots[2]);
        if (invalidTail) {
            return List.of("尾修槽只接受 强 / 续 / 广 / 穿");
        }
        if (comboSlots[0].isEmpty() && comboSlots[1].isEmpty() && comboSlots[2].isEmpty()) {
            return List.of("点击下方字填入槽位，查看组合效果");
        }
        var describer = AozaiInkCoreApi.getService(GlyphDescriber.class);
        if (describer == null) return List.of("效果服务未加载");
        return describer.describe(List.of(comboSlots[0], comboSlots[1], comboSlots[2]));
    }

    private void renderSettings(GuiGraphics g, int mouseX, int mouseY) {
        int x = contentX() + 16;
        int y = contentY() + 8;
        int labelW = Math.max(menuWidth("上下文提示:"), menuWidth("效果描述:"));

        boolean enabled = AozaiInkClientConfig.hintsEnabled();
        int[] hintsBtn = hintsToggleRect();
        drawMenuString(g, "悬浮提示:", x, y + (buttonH() - font.lineHeight) / 2, PAPER_TX);
        drawButton(g, hintsBtn, enabled ? "开" : "关",
            enabled ? PAPER_DK : RED, mouseX, mouseY);

        int[] detailBtn = detailToggleRect();
        drawMenuString(g, "效果描述:", x, detailBtn[1] + (buttonH() - font.lineHeight) / 2, PAPER_TX);
        drawButton(g, detailBtn, detailed ? "详细" : "简略",
            PAPER_DK, mouseX, mouseY);

        drawInfoLines(g, x, detailBtn[1] + detailBtn[3] + 16, contentW() - 32, List.of(
            "悬浮提示：开启后，对准工作台或已放置黄符时会显示操作提示。",
            "效果描述：切换黄符字典与快速吟唱中的效果描述详细程度。"));
    }

    private void drawFrame(GuiGraphics g) {
        int w = panelW(), h = panelH();
        g.fill(panelX - 2, panelY - 2, panelX + w + 2, panelY + h + 2, EDGE);
        g.fill(panelX, panelY, panelX + w, panelY + h, VOID);
        g.fill(panelX + 4, panelY + 4, panelX + w - 4, panelY + h - 4, VOID_DK);
        g.fill(panelX + 5, panelY + 5, panelX + w - 5, panelY + h - 5, VOID);
        drawHLine(g, panelX + 4, panelY + topH(), w - 8, RED);
        drawHLine(g, panelX + 4, panelY + topH() + 1, w - 8, EDGE_SOFT);
    }

    private void drawTitle(GuiGraphics g) {
        int center = panelX + panelW() / 2;
        int y = panelY + Math.max(10, topH() / 4);
        String title = titleText();
        drawScaledCenteredString(g, title, center, y, HOT_TX, 2.0f);
        int sealX = center + menuWidth(title) + 8;
        int sealY = y - 1;
        g.fill(sealX, sealY, sealX + 14, sealY + 22, 0x882A120C);
        drawBorder(g, sealX, sealY, 14, 22, RED);
        drawMenuString(g, "墨", sealX + 4, sealY + 3, RED_LT);
        drawMenuString(g, "箓", sealX + 4, sealY + 12, RED_LT);
    }

    private String titleText() {
        if (activeTab == 0) return "快速上手";
        if (activeTab == 1) return "快速吟唱设置";
        if (activeTab == 2) return "黄符字典";
        if (activeTab == comboTabIndex()) return "技能表";
        if (activeTab == settingsTabIndex()) return "设置";
        return extensionTabs.get(activeTab - extensionTabStart()).title();
    }

    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        for (int index = 0; index < tabLabels.size(); index++) {
            int[] rect = tabRect(index);
            boolean selected = activeTab == index;
            boolean hover = hit(mouseX, mouseY, rect);
            int bg = selected ? PAPER : (hover ? VOID_LT : 0x66201810);
            int text = selected ? INK : PAPER_TX;
            g.fill(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3], bg);
            if (selected) {
                g.fill(rect[0], rect[1], rect[0] + 5, rect[1] + rect[3], RED);
                drawBorder(g, rect[0], rect[1], rect[2], rect[3], EDGE);
            } else {
                drawBorder(g, rect[0], rect[1], rect[2], rect[3], 0x552F2518);
            }
            drawScaledCenteredString(g, tabLabels.get(index), rect[0] + rect[2] / 2,
                rect[1] + (rect[3] - 9) / 2, text, 1.15f);
        }
    }

    private void drawBottomButtons(GuiGraphics g, int mouseX, int mouseY) {
        if (activeTab == 1) {
            drawButton(g, clearAllRect(), "全部清空", PAPER_DK, mouseX, mouseY);
        }
        drawButton(g, backRect(), "返回", PAPER_DK, mouseX, mouseY);
    }

    private void drawButton(GuiGraphics g, int[] rect, String label, int base, int mouseX, int mouseY) {
        boolean hover = hit(mouseX, mouseY, rect);
        int bg = hover ? (base == RED ? RED_LT : PAPER) : base;
        int text = base == RED ? HOT_TX : INK;
        g.fill(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3], bg);
        drawBorder(g, rect[0], rect[1], rect[2], rect[3], EDGE);
        drawBorder(g, rect[0] + 2, rect[1] + 2, rect[2] - 4, rect[3] - 4, 0x66472F1B);
        drawScaledCenteredString(g, label, rect[0] + rect[2] / 2,
            rect[1] + (rect[3] - 9) / 2, text, 1.15f);
    }

    private void drawScrollBar(GuiGraphics g, int x, int y, int w, int h,
            int totalRows, int visibleRows, int offset) {
        if (visibleRows >= totalRows) return;
        int trackX = x + w - 8, trackY = y + 3, trackH = Math.max(12, h - 6);
        int thumbH = Math.max(10, trackH * visibleRows / totalRows);
        int thumbY = trackY + (trackH - thumbH) * offset / Math.max(1, totalRows - visibleRows);
        g.fill(trackX, trackY, trackX + 3, trackY + trackH, 0x55201810);
        g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, PAPER_DK);
    }

    private void drawTable(GuiGraphics g, int x, int y, int w, int h,
            int headerH, int rowH, int rows) {
        drawDarkPanel(g, x, y, w, h);
        g.fill(x + 1, y + 1, x + w - 1, y + headerH, PAPER_DK);
        drawHLine(g, x, y + headerH, w, GRID);
        for (int row = 1; row <= rows; row++) {
            drawHLine(g, x, y + headerH + row * rowH, w, GRID);
        }
    }

    private void drawDarkPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, EDGE_SOFT);
        g.fill(x, y, x + w, y + h, 0xEE1E1A12);
        drawBorder(g, x, y, w, h, EDGE);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0x66120E09);
    }

    private void drawGlyphTagCentered(GuiGraphics g, String glyph, int x, int rowY, int colW, int rowH) {
        int h = Math.max(15, Math.min(20, rowH - 8));
        int w = Math.max(16, Math.min(21, h + 1));
        int left = x + (colW - w) / 2;
        int top = rowY + (rowH - h) / 2;
        g.fill(left, top, left + w, top + h, PAPER);
        drawBorder(g, left, top, w, h, EDGE);
        int color = "火裂心".contains(glyph) ? RED_TX : INK;
        drawCenteredInBox(g, glyph, left, top, w, h, color, 1.1f);
    }

    private void drawRecipeSlot(GuiGraphics g, int x, int y, int size, String label, int color) {
        g.fill(x, y, x + size, y + size, 0x66302118);
        drawBorder(g, x, y, size, size, EDGE);
        drawBorder(g, x + 2, y + 2, size - 4, size - 4, 0x553B2D1B);
        String[] lines = label.split(" ");
        float scale = 0.9f;
        int lineHeight = Math.max(6, Math.round(font.lineHeight * scale));
        int totalH = lines.length * lineHeight;
        int startY = y + (size - totalH) / 2;
        for (int i = 0; i < lines.length; i++) {
            drawCenteredInBox(g, lines[i], x, startY + i * lineHeight, size, lineHeight, color, scale);
        }
    }

    private void drawInfoLines(GuiGraphics g, int x, int y, int maxWidth, List<String> lines) {
        int yy = y;
        for (String line : lines) {
            yy += drawWrappedString(g, line, x, yy, maxWidth, 3, PAPER_TX, 0.82f) + 6;
        }
    }

    private void drawInfoLinesClipped(GuiGraphics g, int x, int y, int maxWidth, int maxHeight, List<String> lines) {
        if (maxHeight <= 0) return;
        int yy = y;
        float scale = 0.82f;
        int lineHeight = Math.max(6, Math.round(font.lineHeight * scale));
        int gap = 6;
        int available = Math.max(1, (int)(maxWidth / scale));
        int remainingHeight = maxHeight;
        outer:
        for (String line : lines) {
            if (remainingHeight < lineHeight) break;
            String text = line;
            while (!text.isEmpty() && remainingHeight >= lineHeight) {
                boolean lastLine = remainingHeight < lineHeight * 2 + gap;
                String value = font.plainSubstrByWidth(text, available);
                if (value.isEmpty()) break;
                text = text.substring(value.length());
                if (lastLine && !text.isEmpty()) {
                    value = font.plainSubstrByWidth(value,
                        Math.max(1, available - menuWidth("..."))) + "...";
                    text = "";
                }
                drawScaledText(g, value, x, yy, PAPER_TX, scale);
                yy += lineHeight;
                remainingHeight -= lineHeight;
                if (!text.isEmpty()) {
                    if (remainingHeight < lineHeight) break outer;
                } else {
                    yy += gap;
                    remainingHeight -= gap;
                }
            }
        }
    }

    private int drawWrappedString(GuiGraphics g, String text, int x, int y,
            int maxWidth, int maxLines, int color, float scale) {
        if (maxWidth <= 0 || maxLines <= 0 || text == null || text.isEmpty()) return 0;
        int available = Math.max(1, (int)(maxWidth / scale));
        int lineHeight = Math.max(6, Math.round(font.lineHeight * scale));
        String remaining = text;
        int used = 0;
        for (int line = 0; line < maxLines && !remaining.isEmpty(); line++) {
            boolean last = line == maxLines - 1;
            String value = font.plainSubstrByWidth(remaining, available);
            if (value.isEmpty()) break;
            remaining = remaining.substring(value.length());
            if (last && !remaining.isEmpty()) {
                value = font.plainSubstrByWidth(value,
                    Math.max(1, available - menuWidth("..."))) + "...";
                remaining = "";
            }
            drawScaledText(g, value, x, y + used, color, scale);
            used += lineHeight;
        }
        return used;
    }

    private int measureWrappedString(String text, int maxWidth, int maxLines, float scale) {
        if (maxWidth <= 0 || maxLines <= 0 || text == null || text.isEmpty()) return 0;
        int available = Math.max(1, (int) (maxWidth / scale));
        int lineHeight = Math.max(6, Math.round(font.lineHeight * scale));
        String remaining = text;
        int used = 0;
        for (int line = 0; line < maxLines && !remaining.isEmpty(); line++) {
            String value = font.plainSubstrByWidth(remaining, available);
            if (value.isEmpty()) break;
            remaining = remaining.substring(value.length());
            used += lineHeight;
        }
        return used;
    }

    private void drawWrappedCenteredInBox(GuiGraphics g, String text, int x, int y,
            int w, int h, int maxLines, int color, float scale) {
        if (w <= 0 || h <= 0 || maxLines <= 0 || text == null || text.isEmpty()) return;
        int available = Math.max(1, (int)(w / scale));
        int lineHeight = Math.max(6, Math.round(font.lineHeight * scale));
        List<String> lines = new ArrayList<>();
        String remaining = text;
        while (lines.size() < maxLines && !remaining.isEmpty()) {
            boolean last = lines.size() == maxLines - 1;
            String value = font.plainSubstrByWidth(remaining, available);
            if (value.isEmpty()) break;
            remaining = remaining.substring(value.length());
            if (last && !remaining.isEmpty()) {
                value = font.plainSubstrByWidth(value,
                    Math.max(1, available - menuWidth("..."))) + "...";
                remaining = "";
            }
            lines.add(value);
        }
        int textY = y + Math.max(0, (h - lines.size() * lineHeight) / 2);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            int textX = Math.round(x + (w - menuWidth(line) * scale) / 2.0f);
            drawScaledText(g, line, textX, textY + index * lineHeight, color, scale);
        }
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        drawHLine(g, x, y, w, color);
        drawHLine(g, x, y + h - 1, w, color);
        drawVLine(g, x, y, h, color);
        drawVLine(g, x + w - 1, y, h, color);
    }

    private void drawHLine(GuiGraphics g, int x, int y, int w, int color) {
        g.fill(x, y, x + w, y + 1, color);
    }

    private void drawVLine(GuiGraphics g, int x, int y, int h, int color) {
        g.fill(x, y, x + 1, y + h, color);
    }

    private void drawScaledCenteredString(GuiGraphics g, String text, int centerX,
            int y, int color, float scale) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(centerX - menuWidth(text) * scale / 2.0f, y, 0.0f);
        pose.scale(scale, scale, 1.0f);
        drawMenuString(g, text, 0, 0, color);
        pose.popPose();
    }

    private void drawCenteredInBox(GuiGraphics g, String text, int x, int y,
            int w, int h, int color, float scale) {
        int textX = Math.round(x + (w - menuWidth(text) * scale) / 2.0f);
        int textY = Math.round(y + (h - font.lineHeight * scale) / 2.0f);
        drawScaledText(g, text, textX, textY, color, scale);
    }

    private void drawScaledText(GuiGraphics g, String text, int x, int y, int color, float scale) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0f);
        pose.scale(scale, scale, 1.0f);
        drawMenuString(g, text, 0, 0, color);
        pose.popPose();
    }

    private void drawMenuString(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    private int menuWidth(String text) {
        return font.width(text);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        for (int index = 0; index < tabLabels.size(); index++) {
            if (hit(mouseX, mouseY, tabRect(index))) {
                activeTab = index;
                return true;
            }
        }
        if (hit(mouseX, mouseY, backRect())) {
            onClose();
            return true;
        }
        if (hit(mouseX, mouseY, clearAllRect())) {
            if (activeTab == 1) {
                for (int slot = 1; slot <= SLOT_COUNT; slot++) {
                    if (bindings.containsKey(slot)) {
                        PacketDistributor.sendToServer(new ClearQuickBindingPayload(slot));
                    }
                }
                bindings.clear();
                return true;
            }
        }
        if (activeTab == 1) {
            int visibleRows = visibleRows(20, 20, SLOT_COUNT);
            int rowH = rowHeight(visibleRows, 20, 20, 24);
            int offset = clamp(scrollOffsets[1], 0, SLOT_COUNT - visibleRows);
            for (int row = 0; row < visibleRows; row++) {
                int slot = offset + row + 1;
                int rowY = contentY() + 20 + row * rowH;
                if (bindings.containsKey(slot) && hit(mouseX, mouseY, clearRect(rowY, rowH))) {
                    PacketDistributor.sendToServer(new ClearQuickBindingPayload(slot));
                    bindings.remove(slot);
                    return true;
                }
            }
        }
        if (activeTab == 0 || activeTab == settingsTabIndex()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (activeTab == comboTabIndex()) {
            for (int i = 0; i < 3; i++) {
                if (!comboSlots[i].isEmpty() && hit(mouseX, mouseY, comboSlotRect(i))) {
                    comboSlots[i] = "";
                    return true;
                }
            }
            int[] grid = comboGridRect();
            int gx = grid[0], gy = grid[1], cols = grid[2], size = grid[3];
            int visibleRows = grid[4], offset = grid[5];
            for (int i = offset * cols; i < comboGlyphs.size() && i < (offset + visibleRows) * cols; i++) {
                int displayRow = i / cols - offset;
                int col = i % cols;
                int rx = gx + col * size;
                int ry = gy + displayRow * size;
                if (hit(mouseX, mouseY, new int[] {rx, ry, size - 4, size - 4})) {
                    fillNextSlot(comboGlyphs.get(i));
                    return true;
                }
            }
        }
        if (activeTab == settingsTabIndex()) {
            if (hit(mouseX, mouseY, hintsToggleRect())) {
                AozaiInkClientConfig.toggleHintsEnabled();
                return true;
            }
            if (hit(mouseX, mouseY, detailToggleRect())) {
                detailed = !detailed;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!hit(mouseX, mouseY, new int[] {contentX(), contentY(), contentW(), contentH()})) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int totalRows;
        int visibleRows;
        if (activeTab == 0) {
            totalRows = quickStartTotalRows();
            visibleRows = visibleRows(20, 20, totalRows);
            if (totalRows <= visibleRows) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        } else if (activeTab == 1) {
            totalRows = SLOT_COUNT;
            visibleRows = visibleRows(20, 20, totalRows);
        } else if (activeTab == 2) {
            totalRows = Math.max(1, (glyphs.size() + 1) / 2);
            visibleRows = visibleRows(24, 24, totalRows);
        } else if (activeTab == settingsTabIndex()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        } else if (activeTab == comboTabIndex()) {
            int[] grid = comboGridRect();
            int cols = grid[2];
            visibleRows = grid[4];
            int rows = (comboGlyphs.size() + cols - 1) / cols;
            int next = clamp(scrollOffsets[activeTab] + (scrollY < 0.0 ? 1 : -1),
                0, Math.max(0, rows - visibleRows));
            if (next == scrollOffsets[activeTab]) return false;
            scrollOffsets[activeTab] = next;
            return true;
        } else {
            totalRows = Math.max(1, extensionTabs.get(activeTab - extensionTabStart()).rows().size());
            visibleRows = visibleRows(22, 20, totalRows);
        }
        int next = clamp(scrollOffsets[activeTab] + (scrollY < 0.0 ? 1 : -1),
            0, Math.max(0, totalRows - visibleRows));
        if (next == scrollOffsets[activeTab]) return false;
        scrollOffsets[activeTab] = next;
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, OVERLAY);
    }

    @Override protected void renderBlurredBackground(float partialTick) {}
    @Override public boolean isPauseScreen() { return false; }

    private int panelW() { return width - panelX * 2; }
    private int panelH() { return height - panelY * 2; }
    private int topH() { return Math.max(42, panelH() / 8); }
    private int sidebarW() { return Math.max(84, panelW() / 6); }
    private int contentX() { return panelX + sidebarW() + Math.max(16, panelW() / 38); }
    private int contentY() { return panelY + topH() + Math.max(12, panelH() / 38); }
    private int contentW() { return panelX + panelW() - contentX() - Math.max(14, panelW() / 42); }
    private int contentH() { return bottomY() - contentY() - Math.max(14, panelH() / 40); }
    private int bottomY() { return panelY + panelH() - Math.max(34, panelH() / 12); }
    private int tabH() { return Math.max(24, Math.min(42, panelH() / Math.max(10, tabLabels.size() + 9))); }
    private int buttonW() { return Math.max(64, Math.min(112, panelW() / 7)); }
    private int buttonH() { return Math.max(20, Math.min(34, panelH() / 17)); }
    private int recipeSlotSize() { return Math.max(24, Math.min(34, panelH() / 14)); }

    private int[] tabRect(int index) {
        int x = panelX + Math.max(12, panelW() / 60);
        int y = contentY() + index * (tabH() + 5);
        return new int[] {x, y, sidebarW() - Math.max(24, panelW() / 30), tabH()};
    }

    private int[] clearAllRect() { return new int[] {contentX(), bottomY(), buttonW() + 18, buttonH()}; }
    private int[] toggleDetailRect() {
        int w = Math.max(70, buttonW() + 4);
        return new int[] {contentX() + contentW() / 2 - w / 2, bottomY(), w, buttonH()};
    }
    private int[] backRect() { return new int[] {contentX() + contentW() - buttonW(), bottomY(), buttonW(), buttonH()}; }
    private int[] clearRect(int rowY, int rowH) {
        int h = Math.max(12, Math.min(18, rowH - 8));
        return new int[] {contentX() + contentW() - 56, rowY + (rowH - h) / 2, 44, h};
    }

    private int[] comboSlotRect(int slot) {
        int slotSize = recipeSlotSize();
        int pad = 8;
        int plusW = font.width("+");
        int slotX = contentX() + slot * (slotSize + plusW + pad * 2);
        return new int[] {slotX, contentY() + 4, slotSize, slotSize};
    }

    private int[] comboGridRect() {
        int x = contentX(), y = contentY(), w = contentW();
        int slotSize = recipeSlotSize();
        int cols = Math.max(4, w / (slotSize + 8));
        int slotY = y + 4;
        int textY = slotY + slotSize + 12;
        int textH = 60;
        int rows = (comboGlyphs.size() + cols - 1) / cols;
        int neededRows = Math.min(rows, 3);
        int gridTop = Math.min(textY + textH + 12,
            bottomY() - 14 - neededRows * slotSize);
        int gridBottom = bottomY() - 14;
        int gridH = Math.max(neededRows * slotSize, gridBottom - gridTop);
        int visibleRows = Math.max(neededRows, gridH / slotSize);
        visibleRows = Math.min(visibleRows, rows);
        int offset = clamp(scrollOffsets[comboTabIndex()], 0, Math.max(0, rows - visibleRows));
        scrollOffsets[comboTabIndex()] = offset;
        return new int[] {x, gridTop, cols, slotSize, visibleRows, offset};
    }

    private void fillNextSlot(String glyph) {
        for (int i = 0; i < comboSlots.length; i++) {
            if (comboSlots[i].isEmpty()) {
                comboSlots[i] = glyph;
                return;
            }
        }
    }

    private int[] hintsToggleRect() {
        int x = contentX() + 16;
        int y = contentY() + 8;
        int labelW = Math.max(menuWidth("悬浮提示:"), menuWidth("效果描述:"));
        int btnW = Math.max(44, menuWidth("简略") + 18);
        return new int[] {x + labelW + 12, y, btnW, buttonH()};
    }

    private int[] detailToggleRect() {
        int x = contentX() + 16;
        int y = contentY() + 8;
        int labelW = Math.max(menuWidth("悬浮提示:"), menuWidth("效果描述:"));
        int btnW = Math.max(44, menuWidth("简略") + 18);
        return new int[] {x + labelW + 12, y + buttonH() + 10, btnW, buttonH()};
    }

    private int visibleRows(int minimumRowHeight, int reserved, int totalRows) {
        int available = Math.max(minimumRowHeight, contentH() - reserved);
        return Math.max(1, Math.min(totalRows, available / minimumRowHeight));
    }
    private int rowHeight(int visibleRows, int reserved, int minimum, int maximum) {
        int available = Math.max(minimum, contentH() - reserved);
        return Math.max(minimum, Math.min(maximum, available / Math.max(1, visibleRows)));
    }

    private MoluMenuPayload.GlyphEntry findGlyph(String glyph) {
        for (MoluMenuPayload.GlyphEntry entry : glyphs) {
            if (entry.glyph().equals(glyph)) return entry;
        }
        return null;
    }

    private static int columnLeft(int x, int w, int count, int column) {
        if (count == 4) {
            int[] percentages = {0, 32, 52, 68, 100};
            return x + w * percentages[column] / 100;
        }
        return x + w * column / Math.max(1, count);
    }
    private static int clamp(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }
    private static boolean hit(double x, double y, int[] rect) {
        return x >= rect[0] && x < rect[0] + rect[2]
            && y >= rect[1] && y < rect[1] + rect[3];
    }
}
