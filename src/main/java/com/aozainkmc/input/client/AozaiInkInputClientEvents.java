package com.aozainkmc.input.client;

import com.aozainkmc.input.AozaiInkInput;
import com.aozainkmc.input.item.AozaiInkItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@EventBusSubscriber(modid = AozaiInkInput.MOD_ID, value = Dist.CLIENT)
public final class AozaiInkInputClientEvents {

    private AozaiInkInputClientEvents() {}

    @SubscribeEvent
    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(AozaiInkItems.YELLOW_TALISMAN.get());
        }
    }
}
