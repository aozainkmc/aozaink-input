package com.aozainkmc.input.item;

import com.aozainkmc.input.AozaiInkInput;
import com.aozainkmc.input.block.AozaiInkBlocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AozaiInkItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AozaiInkInput.MOD_ID);

    public static final DeferredItem<YellowTalismanItem> YELLOW_TALISMAN = ITEMS.register("yellow_talisman",
        () -> new YellowTalismanItem(AozaiInkBlocks.YELLOW_TALISMAN.get(), new net.minecraft.world.item.Item.Properties().stacksTo(64)));

    private AozaiInkItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
