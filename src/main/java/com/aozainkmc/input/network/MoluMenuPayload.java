package com.aozainkmc.input.network;

import com.aozainkmc.input.AozaiInkInput;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MoluMenuPayload(List<BindingEntry> bindings, List<GlyphEntry> glyphs, List<TabEntry> tabs)
        implements CustomPacketPayload {
    public static final Type<MoluMenuPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(AozaiInkInput.MOD_ID, "molu_menu"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MoluMenuPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            buffer.writeVarInt(payload.bindings().size());
            for (BindingEntry entry : payload.bindings()) {
                buffer.writeVarInt(entry.slot()); buffer.writeUtf(entry.glyph()); buffer.writeUtf(entry.owner());
            }
            buffer.writeVarInt(payload.glyphs().size());
            for (GlyphEntry entry : payload.glyphs()) {
                buffer.writeUtf(entry.owner()); buffer.writeUtf(entry.glyph());
                buffer.writeUtf(entry.brief()); buffer.writeUtf(entry.detail());
            }
            buffer.writeVarInt(payload.tabs().size());
            for (TabEntry tab : payload.tabs()) {
                buffer.writeUtf(tab.id()); buffer.writeUtf(tab.label()); buffer.writeUtf(tab.title());
                writeStrings(buffer, tab.columns());
                buffer.writeVarInt(tab.rows().size());
                for (List<String> row : tab.rows()) writeStrings(buffer, row);
            }
        },
        buffer -> {
            List<BindingEntry> bindings = new ArrayList<>();
            for (int i = buffer.readVarInt(); i > 0; i--) {
                bindings.add(new BindingEntry(buffer.readVarInt(), buffer.readUtf(), buffer.readUtf()));
            }
            List<GlyphEntry> glyphs = new ArrayList<>();
            for (int i = buffer.readVarInt(); i > 0; i--) {
                glyphs.add(new GlyphEntry(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf()));
            }
            List<TabEntry> tabs = new ArrayList<>();
            for (int i = buffer.readVarInt(); i > 0; i--) {
                String id = buffer.readUtf(); String label = buffer.readUtf(); String title = buffer.readUtf();
                List<String> columns = readStrings(buffer);
                List<List<String>> rows = new ArrayList<>();
                for (int rowCount = buffer.readVarInt(); rowCount > 0; rowCount--) rows.add(readStrings(buffer));
                tabs.add(new TabEntry(id, label, title, columns, rows));
            }
            return new MoluMenuPayload(bindings, glyphs, tabs);
        });

    private static void writeStrings(RegistryFriendlyByteBuf buffer, List<String> values) {
        buffer.writeVarInt(values.size());
        for (String value : values) buffer.writeUtf(value == null ? "" : value);
    }
    private static List<String> readStrings(RegistryFriendlyByteBuf buffer) {
        List<String> values = new ArrayList<>();
        for (int i = buffer.readVarInt(); i > 0; i--) values.add(buffer.readUtf());
        return values;
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public record BindingEntry(int slot, String glyph, String owner) {}
    public record GlyphEntry(String owner, String glyph, String brief, String detail) {}
    public record TabEntry(String id, String label, String title, List<String> columns, List<List<String>> rows) {}
}
