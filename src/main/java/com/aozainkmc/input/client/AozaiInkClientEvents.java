package com.aozainkmc.input.client;

import com.aozainkmc.core.AozaiInkCoreApi;
import com.aozainkmc.core.api.client.TalismanPlacedHook;
import com.aozainkmc.input.AozaiInkInput;
import com.aozainkmc.input.block.AozaiInkBlocks;
import com.aozainkmc.input.item.AozaiInkItems;
import com.aozainkmc.input.item.TalismanAssembly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = AozaiInkInput.MOD_ID, value = Dist.CLIENT)
public final class AozaiInkClientEvents {

    private AozaiInkClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        InkInputController.tick(minecraft);
        QuickCastCandidateClient.tick(minecraft);
        BindingRitualCameraTransition.tick(minecraft);
        TalismanCameraTransition.tick(minecraft);
        TalismanHintRenderer.tick(minecraft);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        InkInputController.render(event);
        TalismanFormationRenderer.render(event);
        TalismanHintRenderer.render(event);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getSide() != LogicalSide.CLIENT || event.getHand() != InteractionHand.MAIN_HAND) return;
        var level = event.getLevel();
        var pos = event.getPos();
        var state = level.getBlockState(pos);
        ItemStack stack = event.getItemStack();
        if (state.is(AozaiInkBlocks.YELLOW_TALISMAN.get())) {
            TalismanHintRenderer.onOpened();
            TalismanCameraTransition.start(Minecraft.getInstance(), pos);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        if (state.is(Blocks.CRAFTING_TABLE)
                && stack.is(AozaiInkItems.YELLOW_TALISMAN.get())
                && !TalismanAssembly.isWritten(stack)) {
            TalismanHintRenderer.onPlaced();
            TalismanPlacedHook hook = AozaiInkCoreApi.getService(TalismanPlacedHook.class);
            if (hook != null) hook.onTalismanPlaced();
        }
        if (togglePaperCasting(stack)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getSide() != LogicalSide.CLIENT || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (togglePaperCasting(event.getItemStack())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (InkInputController.isActive() && (event.isAttack() || event.isPickBlock())) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (BindingRitualCameraTransition.isActive() || TalismanCameraTransition.isActive()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        int index = switch (event.getKey()) {
            case GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_KP_1 -> 0;
            case GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_KP_2 -> 1;
            case GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_KP_3 -> 2;
            default -> -1;
        };
        if (index >= 0) QuickCastCandidateClient.select(index);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        InkInputController.resetSession();
        QuickCastCandidateClient.reset();
        BindingRitualCameraTransition.reset();
        TalismanCameraTransition.reset();
    }

    private static boolean togglePaperCasting(ItemStack stack) {
        if (!stack.is(Items.PAPER)) return false;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return false;
        InkInputController.togglePaperCasting(minecraft, player);
        return true;
    }
}
