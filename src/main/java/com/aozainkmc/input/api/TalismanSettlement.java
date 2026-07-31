package com.aozainkmc.input.api;

import com.aozainkmc.core.api.InkRecognitionResult;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

/**
 * Settlement context handed to the owning module when all written glyphs belong to it.
 * glyphs/results are slot-ordered (index 0-2, index 2 is the tail slot); blank slots are "".
 * facing is the placed block's orientation, captured by input before the block is consumed.
 */
public record TalismanSettlement(
    ServerPlayer player,
    BlockPos pos,
    List<String> glyphs,
    List<InkRecognitionResult> results,
    Direction facing
) {}
