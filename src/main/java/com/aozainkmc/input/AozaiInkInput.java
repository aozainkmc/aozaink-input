package com.aozainkmc.input;

import com.aozainkmc.core.AozaiInkCoreApi;
import com.aozainkmc.input.block.AozaiInkBlocks;
import com.aozainkmc.input.command.AozaiInputCommand;
import com.aozainkmc.input.item.AozaiInkItems;
import com.aozainkmc.input.item.AozaiInkInputRecipes;
import com.aozainkmc.input.network.AozaiInkNetworking;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.LinkedHashSet;
import com.aozainkmc.input.api.MoluMenuRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(AozaiInkInput.MOD_ID)
public final class AozaiInkInput {
    public static final String MOD_ID = "aozaink_input";
    public static final String SOURCE_TRAJECTORY = "classic_taiji_traj";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final List<String> TALISMAN_GLYPHS = List.of(
        "一", "二", "三", "四", "五", "六", "七", "八", "九",
        "刻",
        "强", "续", "广", "穿"
    );

    public static List<String> talismanGlyphs() {
        LinkedHashSet<String> glyphs = new LinkedHashSet<>(TALISMAN_GLYPHS);
        MoluMenuRegistry.glyphs().forEach(entry -> glyphs.add(entry.glyph()));
        return List.copyOf(glyphs);
    }

    public AozaiInkInput(IEventBus modBus) {
        AozaiInkCoreApi.registerGlyphs(TALISMAN_GLYPHS);

        AozaiInkBlocks.register(modBus);
        AozaiInkItems.register(modBus);
        AozaiInkInputRecipes.register(modBus);
        modBus.addListener(AozaiInkNetworking::registerPayloads);

        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        AozaiInputCommand.register(event.getDispatcher());
    }
}
