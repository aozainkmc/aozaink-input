package com.aozainkmc.input.client;

import com.aozainkmc.input.AozaiInkInput;
import com.aozainkmc.input.block.AozaiInkBlocks;
import com.aozainkmc.input.item.AozaiInkItems;
import com.aozainkmc.input.item.TalismanAssembly;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class TalismanHintRenderer {
    private static final int GAZE_TICKS = 20;
    private static final int FADE_TICKS = 20;
    private static final int ACTION_COOLDOWN_TICKS = 30;
    private static final float TEXT_SCALE = 0.025F;

    private enum Target { NONE, CRAFTING_TABLE, PLACED_TALISMAN }

    private static Target currentTarget = Target.NONE;
    private static Target displayedTarget = Target.NONE;
    private static BlockPos targetPos = null;
    private static int gazeTicks = 0;
    private static float alpha = 0.0F;
    private static int actionCooldown = 0;

    private TalismanHintRenderer() {}

    public static void tick(Minecraft minecraft) {
        if (!AozaiInkClientConfig.hintsEnabled()
                || minecraft.screen != null
                || TalismanCameraTransition.isActive()) {
            setTarget(Target.NONE, null);
            return;
        }

        if (actionCooldown > 0) {
            actionCooldown--;
            setTarget(Target.NONE, null);
            return;
        }

        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            setTarget(Target.NONE, null);
            return;
        }

        HitResult hitResult = minecraft.hitResult;
        if (!(hitResult instanceof BlockHitResult blockHit)) {
            setTarget(Target.NONE, null);
            return;
        }

        BlockPos pos = blockHit.getBlockPos();
        var state = minecraft.level.getBlockState(pos);
        Target target = Target.NONE;
        if (state.is(Blocks.CRAFTING_TABLE)) {
            BlockPos talismanPos = pos.above();
            if (minecraft.level.getBlockState(talismanPos).is(AozaiInkBlocks.YELLOW_TALISMAN.get())) {
                target = Target.PLACED_TALISMAN;
                pos = talismanPos;
            } else {
                ItemStack held = player.getMainHandItem();
                if (held.is(AozaiInkItems.YELLOW_TALISMAN.get()) && !TalismanAssembly.isWritten(held)) {
                    target = Target.CRAFTING_TABLE;
                }
            }
        } else if (state.is(AozaiInkBlocks.YELLOW_TALISMAN.get())) {
            target = Target.PLACED_TALISMAN;
        }

        setTarget(target, pos);
    }

    public static void onPlaced() {
        fadeOut();
    }

    public static void onOpened() {
        fadeOut();
    }

    private static void fadeOut() {
        currentTarget = Target.NONE;
        gazeTicks = 0;
        actionCooldown = ACTION_COOLDOWN_TICKS;
    }

    private static void setTarget(Target target, BlockPos pos) {
        if (currentTarget != target || (pos != null && !pos.equals(targetPos))) {
            currentTarget = target;
            if (target == Target.NONE) {
                gazeTicks = 0;
            } else {
                BlockPos newPos = pos == null ? null : pos.immutable();
                boolean resumingFade = displayedTarget == target
                    && newPos != null
                    && newPos.equals(targetPos)
                    && alpha > 0.001F;
                if (resumingFade) {
                    gazeTicks = GAZE_TICKS;
                } else {
                    displayedTarget = target;
                    targetPos = newPos;
                    gazeTicks = 0;
                    alpha = 0.0F;
                }
            }
        }

        if (currentTarget == Target.NONE) {
            alpha = Math.max(0.0F, alpha - 1.0F / FADE_TICKS);
            if (alpha <= 0.001F) {
                displayedTarget = Target.NONE;
                targetPos = null;
            }
        } else {
            gazeTicks = Math.min(GAZE_TICKS, gazeTicks + 1);
            if (gazeTicks >= GAZE_TICKS) {
                alpha = Math.min(1.0F, alpha + 1.0F / FADE_TICKS);
            }
        }
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (alpha <= 0.001F || targetPos == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;

        String text = switch (displayedTarget) {
            case CRAFTING_TABLE -> "蹲下 + 右键放置黄符";
            case PLACED_TALISMAN -> "右键开始写字";
            default -> "";
        };
        if (text.isEmpty()) return;

        Vec3 pos = new Vec3(targetPos.getX() + 0.5D, targetPos.getY() + 1.25D, targetPos.getZ() + 0.5D);
        Vec3 cameraPos = event.getCamera().getPosition();

        var poseStack = event.getPoseStack();
        poseStack.pushPose();
        Vec3 relative = pos.subtract(cameraPos);
        poseStack.translate(relative.x, relative.y, relative.z);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

        Font font = minecraft.font;
        float x = -font.width(text) * 0.5F;
        float y = -font.lineHeight * 0.5F;
        int color = withAlpha(0xFFFFE8A0, alpha);
        int outline = withAlpha(0xFF2E1C08, alpha * 0.92F);

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        font.drawInBatch8xOutline(Component.literal(text).getVisualOrderText(), x, y, color, outline,
            poseStack.last().pose(), buffers, 0x00F000F0);
        buffers.endBatch();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();

        poseStack.popPose();
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }
}
