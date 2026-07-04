package com.aozainkmc.input.item;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class TalismanCopyRecipe extends CustomRecipe {
    public TalismanCopyRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return resultFor(input).isPresent();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return resultFor(input).orElse(ItemStack.EMPTY);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return NonNullList.withSize(input.width() * input.height(), ItemStack.EMPTY);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return AozaiInkInputRecipes.TALISMAN_COPY.get();
    }

    private static java.util.Optional<ItemStack> resultFor(CraftingInput input) {
        if (input.width() != 3 || input.height() != 3 || input.ingredientCount() != 9) {
            return java.util.Optional.empty();
        }

        ItemStack template = input.getItem(1, 1);
        if (!isWrittenTalisman(template)) {
            return java.util.Optional.empty();
        }

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                ItemStack stack = input.getItem(x, y);
                if (x == 1 && y == 1) {
                    continue;
                }
                if (x == 1 && y == 2) {
                    if (!stack.is(Items.RED_DYE)) {
                        return java.util.Optional.empty();
                    }
                } else if (!isEmptyTalisman(stack)) {
                    return java.util.Optional.empty();
                }
            }
        }

        return java.util.Optional.of(template.copyWithCount(2));
    }

    private static boolean isWrittenTalisman(ItemStack stack) {
        return stack.is(AozaiInkItems.YELLOW_TALISMAN.get()) && TalismanAssembly.isWritten(stack);
    }

    private static boolean isEmptyTalisman(ItemStack stack) {
        return stack.is(AozaiInkItems.YELLOW_TALISMAN.get()) && !TalismanAssembly.isWritten(stack);
    }
}
