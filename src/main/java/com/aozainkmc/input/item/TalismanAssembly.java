package com.aozainkmc.input.item;

import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class TalismanAssembly {
    public static final String TAG_TYPE = "aozaink:talisman_type";
    public static final String TAG_SLOT1 = "aozaink:slot1";
    public static final String TAG_SLOT2 = "aozaink:slot2";
    public static final String TAG_SLOT3 = "aozaink:slot3";

    public enum Type {
        SPECIFIED("指定符"),
        INSCRIPTION("刻印符"),
        COMBO("组合符"),
        FAILED("废符");

        private final String displayName;

        Type(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public record Result(Type type, String slot1, String slot2, String slot3) {}

    private static final Set<String> SKILL_GLYPHS = Set.of(
        "镇", "封", "退", "引", "火", "雷", "护", "净"
    );

    private TalismanAssembly() {}

    public static Result classify(String slot1, String slot2, String slot3) {
        slot1 = normalize(slot1);
        slot2 = normalize(slot2);
        slot3 = normalize(slot3);

        if (isDigit(slot1) && isSkill(slot2) && slot3.isEmpty()) {
            return new Result(Type.SPECIFIED, slot1, slot2, slot3);
        }
        if ("刻".equals(slot1)) {
            return classifyInscription(slot2, slot3);
        }
        if (isCombo(slot1, slot2, slot3)) {
            return new Result(Type.COMBO, slot1, slot2, slot3);
        }
        return new Result(Type.FAILED, slot1, slot2, slot3);
    }

    private static Result classifyInscription(String slot2, String slot3) {
        if (isSkill(slot2) || isModifier(slot2)) {
            if (slot3.isEmpty() || isSkill(slot3) || isModifier(slot3)) {
                return new Result(Type.INSCRIPTION, "刻", slot2, slot3);
            }
        }
        return new Result(Type.FAILED, "刻", slot2, slot3);
    }

    public static ItemStack createStack(Result result) {
        ItemStack stack = new ItemStack(AozaiInkItems.YELLOW_TALISMAN.get());
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_TYPE, result.type().name().toLowerCase());
        tag.putString(TAG_SLOT1, result.slot1());
        tag.putString(TAG_SLOT2, result.slot2());
        tag.putString(TAG_SLOT3, result.slot3());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static CompoundTag talismanTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    public static boolean isWritten(ItemStack stack) {
        CompoundTag tag = talismanTag(stack);
        return hasText(tag, TAG_TYPE) || hasText(tag, TAG_SLOT1) || hasText(tag, TAG_SLOT2) || hasText(tag, TAG_SLOT3);
    }

    public static String displayType(String raw) {
        return switch (raw) {
            case "specified" -> Type.SPECIFIED.displayName();
            case "inscription" -> Type.INSCRIPTION.displayName();
            case "combo" -> Type.COMBO.displayName();
            case "failed" -> Type.FAILED.displayName();
            default -> "未成符";
        };
    }

    private static boolean isCombo(String slot1, String slot2, String slot3) {
        String[] slots = { slot1, slot2, slot3 };
        int count = 0;
        for (String slot : slots) {
            if (slot.isEmpty()) continue;
            if (!isSkill(slot) && !isModifier(slot)) return false;
            count++;
        }
        return count >= 1;
    }

    private static boolean isSkill(String glyph) {
        return SKILL_GLYPHS.contains(glyph);
    }

    private static boolean isModifier(String glyph) {
        return "强".equals(glyph) || "续".equals(glyph) || "疾".equals(glyph) || "广".equals(glyph);
    }

    private static boolean isDigit(String glyph) {
        return switch (glyph) {
            case "1", "2", "3", "4", "5", "6", "7", "8", "9",
                 "一", "二", "三", "四", "五", "六", "七", "八", "九" -> true;
            default -> false;
        };
    }

    private static String normalize(String glyph) {
        return glyph == null || glyph.isBlank() ? "" : glyph.trim();
    }

    private static boolean hasText(CompoundTag tag, String key) {
        String value = tag.getString(key);
        return value != null && !value.isBlank();
    }
}
