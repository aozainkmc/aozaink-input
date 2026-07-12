package com.aozainkmc.input.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

public final class BindingRitualCameraTransition {
    private static final long DURATION_NANOS = 5_500_000_000L;
    private static final float OUT_END = 0.18F;
    private static final float RETURN_START = 0.92F;
    private static final double CAMERA_RADIUS = 11.50D;
    private static final double CAMERA_LIFT = 4.00D;
    private static final double ANGLE_RADIANS = Math.toRadians(38.0D);
    private static Shot active;

    private BindingRitualCameraTransition() {}

    public static void start(Minecraft minecraft, UUID playerId) {
        if (minecraft.player == null || !minecraft.player.getUUID().equals(playerId)
                || TalismanCameraTransition.isActive()) return;
        Vec3 forward = minecraft.player.getViewVector(1.0F).multiply(1.0D, 0.0D, 1.0D);
        if (forward.lengthSqr() < 1.0E-4D) forward = new Vec3(0.0D, 0.0D, 1.0D);
        forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 radial = forward.scale(-Math.cos(ANGLE_RADIANS))
            .add(right.scale(Math.sin(ANGLE_RADIANS))).normalize();
        active = new Shot(System.nanoTime(), playerId, minecraft.player.getEyePosition(1.0F), radial,
            minecraft.player.getYRot(), minecraft.player.getXRot());
    }

    public static void tick(Minecraft minecraft) {
        if (active == null) return;
        if (minecraft.player == null || minecraft.level == null
                || System.nanoTime() - active.startNanos() >= DURATION_NANOS) active = null;
    }

    public static CameraPose cameraPose(long nowNanos) {
        Minecraft minecraft = Minecraft.getInstance();
        Shot shot = active;
        if (shot == null || minecraft.player == null || minecraft.level == null) return null;
        Player player = minecraft.level.getPlayerByUUID(shot.playerId());
        if (player == null) return null;
        float progress = Mth.clamp((nowNanos - shot.startNanos()) / (float) DURATION_NANOS, 0.0F, 1.0F);
        double yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 focus = player.position().add(forward.scale(1.55D))
            .add(0.0D, player.getBbHeight() * 1.05D, 0.0D);
        Vec3 cinematic = focus.add(shot.radial().scale(CAMERA_RADIUS)).add(0.0D, CAMERA_LIFT, 0.0D);
        Vec3 playerEye = minecraft.player.getEyePosition(1.0F);
        float blend;
        Vec3 origin;
        if (progress < OUT_END) {
            blend = smoothStep(progress / OUT_END);
            origin = shot.startPosition();
        } else if (progress < RETURN_START) {
            blend = 1.0F;
            origin = shot.startPosition();
        } else {
            blend = 1.0F - smoothStep((progress - RETURN_START) / (1.0F - RETURN_START));
            origin = playerEye;
        }
        Vec3 position = origin.lerp(cinematic, blend);
        float[] target = lookAt(position, focus);
        return new CameraPose(
            position,
            Mth.rotLerp(blend, shot.startYaw(), target[0]),
            Mth.lerp(blend, shot.startPitch(), target[1])
        );
    }

    public static boolean isActive() { return active != null; }
    public static void reset() { active = null; }

    private static float[] lookAt(Vec3 position, Vec3 target) {
        Vec3 delta = target.subtract(position);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        return new float[] {
            (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F,
            (float) -(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG)
        };
    }

    private static float smoothStep(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    public record CameraPose(Vec3 position, float yaw, float pitch) {}
    private record Shot(long startNanos, UUID playerId, Vec3 startPosition, Vec3 radial,
                        float startYaw, float startPitch) {}
}
