package com.aozainkmc.input.item;

import com.aozainkmc.input.AozaiInkInput;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AozaiInkInputRecipes {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, AozaiInkInput.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<TalismanCopyRecipe>> TALISMAN_COPY =
        SERIALIZERS.register("talisman_copy", () -> new SimpleCraftingRecipeSerializer<>(TalismanCopyRecipe::new));

    private AozaiInkInputRecipes() {}

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}
