package com.aozainkmc.input;

import com.aozainkmc.core.AozaiInkCoreApi;
import com.aozainkmc.core.api.EngineType;
import com.aozainkmc.input.block.AozaiInkBlocks;
import com.aozainkmc.input.command.AozaiInputCommand;
import com.aozainkmc.input.item.AozaiInkItems;
import com.aozainkmc.input.item.AozaiInkInputRecipes;
import com.aozainkmc.input.network.AozaiInkNetworking;
import com.mojang.logging.LogUtils;
import java.util.List;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(AozaiInkInput.MOD_ID)
public final class AozaiInkInput {
    public static final String MOD_ID = "aozaink_input";
    public static final String SOURCE_IMAGE = "classic_taiji_image";
    public static final String SOURCE_TRAJECTORY = "classic_taiji_traj";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final List<String> TALISMAN_GLYPHS = List.of(
        "一", "二", "三", "四", "五", "六", "七", "八", "九",
        "刻",
        "镇", "封", "退", "引", "火", "雷", "护", "净", "斩", "明", "吸", "魄",
        "强", "续", "广", "穿"
    );

    public AozaiInkInput(IEventBus modBus) {
        AozaiInkCoreApi.registerInput(SOURCE_TRAJECTORY, EngineType.ONLINE_TRAJECTORY);
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
