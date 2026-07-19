package com.aozainkmc.input.item;

import com.aozainkmc.input.scoring.TalismanGrade;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class YellowTalismanItem extends BlockItem {
    public YellowTalismanItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (TalismanAssembly.isWritten(stack)) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Player player = context.getPlayer();
        if (!level.getBlockState(clickedPos).is(Blocks.CRAFTING_TABLE)) {
            if (player != null && level.isClientSide) {
                player.displayClientMessage(Component.literal("黄符只能放在工作台上"), true);
            }
            return InteractionResult.FAIL;
        }
        if (player != null && player.isCrouching()) {
            BlockPlaceContext placeContext = BlockPlaceContext.at(
                new BlockPlaceContext(context), clickedPos, Direction.UP);
            return this.place(placeContext);
        }
        return super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = TalismanAssembly.talismanTag(stack);
        String type = tag.getString(TalismanAssembly.TAG_TYPE);
        if (type == null || type.isBlank()) {
            tooltip.add(Component.literal("未书写").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("只能放在工作台上").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.literal("类型: " + TalismanAssembly.displayType(type)).withStyle(ChatFormatting.GOLD));
        TalismanGrade grade = overallGrade(tag);
        if (grade != null) {
            tooltip.add(Component.literal("品质: " + grade.display()).withStyle(gradeColor(grade)));
        }
        tooltip.add(Component.literal("字: "
            + slot(tag, TalismanAssembly.TAG_SLOT1) + " / "
            + slot(tag, TalismanAssembly.TAG_SLOT2) + " / "
            + slot(tag, TalismanAssembly.TAG_SLOT3)).withStyle(ChatFormatting.GRAY));
    }

    private static TalismanGrade overallGrade(CompoundTag tag) {
        TalismanGrade worst = null;
        for (int i = 1; i <= 3; i++) {
            TalismanGrade grade = TalismanGrade.byName(tag.getString("aozaink:grade" + i));
            if (grade == null) continue;
            if (worst == null || grade.ordinal() > worst.ordinal()) {
                worst = grade;
            }
        }
        return worst;
    }

    private static ChatFormatting gradeColor(TalismanGrade grade) {
        return switch (grade) {
            case EXQUISITE -> ChatFormatting.AQUA;
            case FINE -> ChatFormatting.GREEN;
            case INFERIOR -> ChatFormatting.YELLOW;
            case WASTE -> ChatFormatting.RED;
        };
    }

    private static String slot(CompoundTag tag, String key) {
        String value = tag.getString(key);
        return value == null || value.isBlank() ? "空" : value;
    }
}
