package com.aozainkmc.input.binding;

import com.aozainkmc.input.api.MoluMenuRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Input-owned, gameplay-neutral digit-to-glyph bindings. */
public final class QuickGlyphBinding {
    private static final String TAG_ROOT = "aozaink_input";
    private static final String LEGACY_ROOT = "aozaink_sigillum";
    private static final String TAG_BINDINGS = "quick_bindings";
    private static final String LEGACY_BINDINGS = "bindings";
    private static final String TAG_GLYPH = "glyph";
    private static final String TAG_OWNER = "owner";
    private static final Map<String, Integer> CHINESE_TO_ARABIC = new HashMap<>();
    private static final Map<Integer, String> ARABIC_TO_CHINESE = new HashMap<>();

    static {
        String[] chinese = {"一", "二", "三", "四", "五", "六", "七", "八", "九"};
        for (int i = 0; i < chinese.length; i++) {
            CHINESE_TO_ARABIC.put(chinese[i], i + 1);
            ARABIC_TO_CHINESE.put(i + 1, chinese[i]);
        }
    }

    private QuickGlyphBinding() {}

    public static void bind(ServerPlayer player, String digit, String glyph) {
        if (!isChineseDigit(digit)) return;
        CompoundTag root = player.getPersistentData().getCompound(TAG_ROOT);
        CompoundTag bindings = root.getCompound(TAG_BINDINGS);
        if (glyph == null || glyph.isBlank()) {
            bindings.remove(digit);
        } else {
            CompoundTag value = new CompoundTag();
            value.putString(TAG_GLYPH, glyph);
            value.putString(TAG_OWNER, MoluMenuRegistry.ownerOf(glyph));
            bindings.put(digit, value);
        }
        root.put(TAG_BINDINGS, bindings);
        player.getPersistentData().put(TAG_ROOT, root);
        CompoundTag legacyRoot = player.getPersistentData().getCompound(LEGACY_ROOT);
        CompoundTag legacyBindings = legacyRoot.getCompound(LEGACY_BINDINGS);
        legacyBindings.remove(digit);
        legacyRoot.put(LEGACY_BINDINGS, legacyBindings);
        player.getPersistentData().put(LEGACY_ROOT, legacyRoot);
    }

    public static Optional<Binding> get(Player player, String digit) {
        if (!isChineseDigit(digit)) return Optional.empty();
        CompoundTag bindings = player.getPersistentData().getCompound(TAG_ROOT).getCompound(TAG_BINDINGS);
        if (bindings.contains(digit)) {
            CompoundTag value = bindings.getCompound(digit);
            String glyph = value.getString(TAG_GLYPH);
            if (!glyph.isBlank()) {
                String owner = value.getString(TAG_OWNER);
                if (owner.isBlank()) owner = MoluMenuRegistry.ownerOf(glyph);
                return Optional.of(new Binding(digit, glyph, owner));
            }
        }
        // Transparent migration path for alpha.1 worlds.
        CompoundTag legacy = player.getPersistentData().getCompound(LEGACY_ROOT).getCompound(LEGACY_BINDINGS);
        String glyph = legacy.getString(digit);
        if (glyph.isBlank()) return Optional.empty();
        return Optional.of(new Binding(digit, glyph, MoluMenuRegistry.ownerOf(glyph)));
    }

    public static boolean isChineseDigit(String glyph) { return CHINESE_TO_ARABIC.containsKey(glyph); }
    public static String toChineseDigit(int value) { return ARABIC_TO_CHINESE.getOrDefault(value, ""); }
    public static int toArabic(String value) { return CHINESE_TO_ARABIC.getOrDefault(value, 0); }

    public record Binding(String digit, String glyph, String owner) {}
}
