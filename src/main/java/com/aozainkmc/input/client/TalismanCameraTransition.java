package com.aozainkmc.input.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Client-only camera transition used before opening the yellow talisman editor. */
public final class TalismanCameraTransition {
    private static final double CAMERA_HEIGHT = 2.65D;
    private static final double SPEED_BLOCKS_PER_SECOND = 6.0D;
    private static final double MIN_DURATION_SECONDS = 0.50D;
    private static final double MAX_DURATION_SECONDS = 1.15D;
    private static final float OPEN_SCREEN_PROGRESS = 0.80F;
    /** Keeps the north/south axis vertical on screen when looking straight down. */
    private static final float FINAL_TOP_DOWN_YAW = 180.0F;

    private static Transition active;

    private TalismanCameraTransition() {}

    public static void start(Minecraft minecraft, BlockPos blockPos) {
        if (active != null || minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;

        Vec3 startPosition = minecraft.player.getEyePosition(1.0F);
        Vec3 target = Vec3.atCenterOf(blockPos).add(0.0D, 0.04D, 0.0D);
        Vec3 endPosition = target.add(0.0D, CAMERA_HEIGHT, 0.0D);
        double distance = startPosition.distanceTo(endPosition);
        double durationSeconds = Mth.clamp(
            distance / SPEED_BLOCKS_PER_SECOND,
            MIN_DURATION_SECONDS,
            MAX_DURATION_SECONDS
        );

        active = new Transition(
            blockPos.immutable(),
            startPosition,
            endPosition,
            target,
            minecraft.player.getYRot(),
            minecraft.player.getXRot(),
            System.nanoTime(),
            (long) (durationSeconds * 1_000_000_000L),
            false,
            false,
            0L,
            false
        );
    }

    public static void tick(Minecraft minecraft) {
        Transition transition = active;
        if (transition == null) return;
        if (minecraft.player == null || minecraft.level == null) {
            reset();
            return;
        }

        if (!transition.returning()
                && !minecraft.level.getBlockState(transition.blockPos()).is(com.aozainkmc.input.block.AozaiInkBlocks.YELLOW_TALISMAN.get())) {
            if (minecraft.screen instanceof TalismanWritingScreen) minecraft.setScreen(null);
            reset();
            return;
        }

        long now = System.nanoTime();
        if (!transition.screenOpened() && transition.progress(now) >= OPEN_SCREEN_PROGRESS) {
            active = transition.withScreenOpened();
            minecraft.setScreen(new TalismanWritingScreen(transition.blockPos()));
            return;
        }

        if (transition.returning() && transition.returnProgress(now) >= 1.0F) {
            active = null;
            if (transition.focusTalismanOnReturn() && minecraft.player != null) {
                float[] rotation = lookAt(minecraft.player.getEyePosition(1.0F), transition.target());
                minecraft.player.setYRot(rotation[0]);
                minecraft.player.setXRot(rotation[1]);
            }
            minecraft.setScreen(null);
            return;
        }

        if (transition.screenOpened() && !transition.returning()
                && !(minecraft.screen instanceof TalismanWritingScreen)) {
            reset();
        }
    }

    public static void beginClosing(boolean focusTalismanOnReturn) {
        Transition transition = active;
        if (transition == null || transition.returning()) return;
        active = transition.withReturnStarted(System.nanoTime(), focusTalismanOnReturn);
    }

    public static CameraPose cameraPose(long nowNanos) {
        Transition transition = active;
        if (transition == null) return null;

        float rawProgress = transition.returning()
            ? 1.0F - transition.returnProgress(nowNanos)
            : transition.progress(nowNanos);
        float positionProgress = smoothStep(rawProgress);
        Vec3 position = curvedPosition(transition, positionProgress);

        float[] targetRotation = lookAt(position, transition.target());
        float rotationProgress = smoothStep(Mth.clamp(rawProgress * 1.18F, 0.0F, 1.0F));
        float alignmentProgress = smoothStep(Mth.clamp((rawProgress - 0.42F) / 0.58F, 0.0F, 1.0F));
        float alignedTargetYaw = Mth.rotLerp(alignmentProgress, targetRotation[0], FINAL_TOP_DOWN_YAW);
        float yaw = Mth.rotLerp(rotationProgress, transition.startYaw(), alignedTargetYaw);
        float pitch = Mth.lerp(rotationProgress, transition.startPitch(), targetRotation[1]);
        return new CameraPose(position, yaw, pitch);
    }

    public static void reset() {
        active = null;
    }

    public static boolean isActive() {
        return active != null;
    }

    private static Vec3 curvedPosition(Transition transition, float progress) {
        Vec3 start = transition.startPosition();
        Vec3 end = transition.endPosition();
        double lift = Math.max(0.75D, Math.min(2.0D, start.distanceTo(end) * 0.28D));
        Vec3 control = start.lerp(end, 0.48D).add(0.0D, lift, 0.0D);
        double inverse = 1.0D - progress;
        return start.scale(inverse * inverse)
            .add(control.scale(2.0D * inverse * progress))
            .add(end.scale(progress * progress));
    }

    private static float[] lookAt(Vec3 position, Vec3 target) {
        Vec3 delta = target.subtract(position);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        return new float[] {yaw, pitch};
    }

    private static float smoothStep(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    public record CameraPose(Vec3 position, float yaw, float pitch) {}

    private record Transition(
        BlockPos blockPos,
        Vec3 startPosition,
        Vec3 endPosition,
        Vec3 target,
        float startYaw,
        float startPitch,
        long startNanos,
        long durationNanos,
        boolean screenOpened,
        boolean returning,
        long returnStartNanos,
        boolean focusTalismanOnReturn
    ) {
        float progress(long nowNanos) {
            return Mth.clamp((nowNanos - startNanos) / (float) durationNanos, 0.0F, 1.0F);
        }

        Transition withScreenOpened() {
            return new Transition(blockPos, startPosition, endPosition, target, startYaw, startPitch,
                startNanos, durationNanos, true, false, 0L, false);
        }

        float returnProgress(long nowNanos) {
            if (!returning) return 0.0F;
            long returnDuration = Math.max(1L, durationNanos / 2L);
            return Mth.clamp((nowNanos - returnStartNanos) / (float) returnDuration, 0.0F, 1.0F);
        }

        Transition withReturnStarted(long nowNanos, boolean focusTalisman) {
            return new Transition(blockPos, startPosition, endPosition, target, startYaw, startPitch,
                startNanos, durationNanos, true, true, nowNanos, focusTalisman);
        }
    }
}
