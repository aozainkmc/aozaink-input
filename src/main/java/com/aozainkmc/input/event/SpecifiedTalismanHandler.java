package com.aozainkmc.input.event;

import com.aozainkmc.input.AozaiInkInput;
import com.aozainkmc.input.binding.QuickGlyphBinding;
import com.aozainkmc.input.api.QuickBindingChangedEvent;
import com.aozainkmc.input.item.AozaiInkItems;
import com.aozainkmc.input.item.TalismanAssembly;
import com.aozainkmc.input.network.InputBindingRitualPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = AozaiInkInput.MOD_ID)
public final class SpecifiedTalismanHandler {
    private SpecifiedTalismanHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void rightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getHand() == InteractionHand.MAIN_HAND
                && tryBind(player, player.getMainHandItem())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (player.serverLevel().getBlockState(event.getPos()).getMenuProvider(player.serverLevel(), event.getPos()) != null
                && !player.isSecondaryUseActive()) return;
        if (tryBind(player, player.getMainHandItem())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static boolean tryBind(ServerPlayer player, ItemStack stack) {
        if (!stack.is(AozaiInkItems.YELLOW_TALISMAN.get())) return false;
        CompoundTag tag = TalismanAssembly.talismanTag(stack);
        if (!"specified".equals(tag.getString(TalismanAssembly.TAG_TYPE))) return false;
        String digit = tag.getString(TalismanAssembly.TAG_SLOT1);
        String glyph = tag.getString(TalismanAssembly.TAG_SLOT2);
        if (!QuickGlyphBinding.isChineseDigit(digit) || glyph.isBlank()) {
            player.displayClientMessage(Component.literal("指定符内容无效"), true);
            return true;
        }
        QuickGlyphBinding.bind(player, digit, glyph);
        QuickGlyphBinding.Binding binding = QuickGlyphBinding.get(player, digit).orElseThrow();
        NeoForge.EVENT_BUS.post(new QuickBindingChangedEvent(player, binding));
        player.displayClientMessage(Component.literal(digit + " → " + glyph + " 绑定完成").withColor(0xE0B45C), true);
        player.sendSystemMessage(Component.literal("已指定 " + digit + " → " + glyph + " ")
            .append(Component.literal("[点击查看]").withStyle(style -> style
                .withColor(0xE0B45C).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/molu menu"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("打开墨箓菜单"))))));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
            SoundSource.PLAYERS, 0.7f, 1.5f);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
            SoundSource.PLAYERS, 0.3f, 1.3f);
        Vec3 center = player.position();
        InputBindingRitualPayload payload = new InputBindingRitualPayload(
            player.getUUID(), center.x, center.y, center.z, digit, glyph);
        double rangeSqr = 96.0D * 96.0D;
        for (ServerPlayer viewer : player.serverLevel().players()) {
            if (viewer.distanceToSqr(center) <= rangeSqr) PacketDistributor.sendToPlayer(viewer, payload);
        }
        stack.shrink(1);
        return true;
    }
}
