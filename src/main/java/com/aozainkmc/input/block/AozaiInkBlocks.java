package com.aozainkmc.input.block;

import com.aozainkmc.input.AozaiInkInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AozaiInkBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AozaiInkInput.MOD_ID);

    public static final DeferredBlock<YellowTalismanBlock> YELLOW_TALISMAN = BLOCKS.register(
        "yellow_talisman",
        () -> new YellowTalismanBlock(BlockBehaviour.Properties.of()
            .strength(0.5f)
            .sound(SoundType.WOOL)
            .noOcclusion()
            .noLootTable())
    );

    private AozaiInkBlocks() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}


