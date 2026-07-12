package com.aozainkmc.input.item;

import com.aozainkmc.input.api.MoluMenuRegistry;
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
        "镇", "封", "退", "引", "火", "雷", "护", "净", "斩", "明", "吸", "魄"
    );

    private TalismanAssembly() {}

    public static Result classify(String slot1, String slot2, String slot3) {
        slot1 = normalize(slot1);
        slot2 = normalize(slot2);
        slot3 = normalize(slot3);

        if (isDigit(slot1) && isBindableGlyph(slot2) && slot3.isEmpty()) {
            return new Result(Type.SPECIFIED, slot1, slot2, slot3);
        }
        if ("刻".equals(slot1) || "刻".equals(slot2) || "刻".equals(slot3)) {
            return classifyInscription(slot1, slot2, slot3);
        }
        if (isCombo(slot1, slot2, slot3)) {
            return new Result(Type.COMBO, slot1, slot2, slot3);
        }
        return new Result(Type.FAILED, slot1, slot2, slot3);
    }

    private static Result classifyInscription(String slot1, String slot2, String slot3) {
        if ("刻".equals(slot3)) {
            return new Result(Type.FAILED, slot1, slot2, slot3);
        }
        if (!slot3.isEmpty() && !isModifier(slot3)) {
            return new Result(Type.FAILED, slot1, slot2, slot3);
        }
        if (!"刻".equals(slot1) && !"刻".equals(slot2)) {
            return new Result(Type.FAILED, slot1, slot2, slot3);
        }

        String[] slots = { slot1, slot2, slot3 };
        int markCount = 0;
        int skillCount = 0;
        int modifierCount = 0;
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (String slot : slots) {
            if (slot.isEmpty()) continue;
            if (!"刻".equals(slot) && !seen.add(slot)) {
                return new Result(Type.FAILED, slot1, slot2, slot3);
            }
            if ("刻".equals(slot)) {
                markCount++;
                continue;
            } else if (isSkill(slot)) {
                skillCount++;
            } else if (isModifier(slot)) {
                if ("穿".equals(slot)) {
                    return new Result(Type.FAILED, slot1, slot2, slot3);
                }
                modifierCount++;
            } else {
                return new Result(Type.FAILED, slot1, slot2, slot3);
            }
        }
        if (markCount == 1 && skillCount == 1 && modifierCount <= 1) {
            return new Result(Type.INSCRIPTION, slot1, slot2, slot3);
        }
        if (markCount == 1 && skillCount == 0 && modifierCount >= 1 && modifierCount <= 2) {
            return new Result(Type.INSCRIPTION, slot1, slot2, slot3);
        }
        return new Result(Type.FAILED, slot1, slot2, slot3);
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

    private static boolean isBindableGlyph(String glyph) {
        return isSkill(glyph) || !MoluMenuRegistry.ownerOf(glyph).isBlank();
    }

    private static boolean isModifier(String glyph) {
        return "强".equals(glyph) || "续".equals(glyph) || "广".equals(glyph) || "穿".equals(glyph);
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
