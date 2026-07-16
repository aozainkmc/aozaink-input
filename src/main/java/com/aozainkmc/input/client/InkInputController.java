package com.aozainkmc.input.client;

import com.aozainkmc.core.api.InkRecognitionRequest;
import com.aozainkmc.input.AozaiInkInput;
import com.aozainkmc.input.network.AozaiInkNetworking;
import com.aozainkmc.input.network.CastPaperPayload;
import com.aozainkmc.input.network.PreviewQuickCastPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class InkInputController {
    private static final float MIN_POINT_DISTANCE = 0.012f;
    private static final int MAX_POINTS_PER_STROKE = 512;
    private static final int MAX_TOTAL_POINTS = 2048;
    private static final float MAX_ACTIVE_DISTANCE_SQR = 10.0f * 10.0f;
    private static final long EMPTY_CLOSING_DURATION_MS = 260L;
    private static final long WRITTEN_CLOSING_DURATION_MS = 220L;
    private static final long OPENING_DURATION_MS = 260L;

    private static final List<InkStroke> STROKES = new ArrayList<>();
    private static final InkCircleRenderer RENDERER = new InkCircleRenderer();

    private static boolean active;
    private static InkPlane plane;
    private static InkStroke currentStroke;
    private static PlaneHit currentHit;
    private static boolean lastMouseDown;
    private static int totalPoints;
    private static boolean fromBack;
    private static ItemStack lastHeldItem;
    private static boolean autoRecognizePending;
    private static boolean recognizedSinceChange;
    private static long lastPenUpTimeMs;
    private static long revisionCounter = System.currentTimeMillis();

    private static InkPlane closingPlane;
    private static List<InkStroke> closingStrokes;
    private static long closingStartTimeMs;
    private static long closingDurationMs;
    private static boolean closingWritten;
    private static boolean closingFromBack;
    private static Vec3 closingReturnCenter;
    private static boolean closingParticlesSpawned;
    private static long openingStartTimeMs;

    private InkInputController() {}

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            close();
            return;
        }

        if (active && lastHeldItem != null) {
            ItemStack mainHand = player.getMainHandItem();
            if (!ItemStack.isSameItemSameComponents(mainHand, lastHeldItem)) {
                finishStroke(true, minecraft);
                startClosing();
                say(player, "切换物品，临时施写已关闭");
                return;
            }
        }

        if (active && plane != null && player.position().distanceToSqr(plane.center()) > MAX_ACTIVE_DISTANCE_SQR) {
            startClosing();
            say(player, "距离过远，临时施写已中断");
            return;
        }

        if (active && plane != null) {
            Vec3 eye = player.getEyePosition(1.0F);
            Vec3 toEye = eye.subtract(plane.center());
            fromBack = toEye.dot(plane.normal()) > 0;
        }

        if (closingPlane != null) {
            long elapsed = System.currentTimeMillis() - closingStartTimeMs;
            float progress = closingDurationMs <= 0L ? 1.0F : Math.min(1.0F, elapsed / (float) closingDurationMs);
            if (closingWritten && !closingParticlesSpawned && progress >= 0.45F) {
                closingParticlesSpawned = true;
                spawnBurnParticles(closingPlane);
            }
            if (elapsed >= closingDurationMs) {
                closingPlane = null;
                closingStrokes = null;
                closingReturnCenter = null;
            }
        }

        if (!active || minecraft.screen != null) {
            finishStroke(false, minecraft);
            lastMouseDown = false;
            return;
        }

        sampleMouseStroke(minecraft, player);
    }

    public static void render(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_WEATHER) return;

        if (closingPlane != null && System.currentTimeMillis() - closingStartTimeMs < closingDurationMs) {
            float progress = (System.currentTimeMillis() - closingStartTimeMs) / (float) closingDurationMs;
            RENDERER.renderClosing(event.getPoseStack(), event.getCamera(), closingPlane,
                closingStrokes == null ? List.of() : closingStrokes, progress, closingWritten,
                closingFromBack, closingReturnCenter);
        }

        if (!active || plane == null) return;
        RENDERER.render(event.getPoseStack(), event.getCamera(), plane, STROKES, currentStroke, currentHit,
            confirmProgress(), openingProgress(), fromBack);
    }

    public static boolean isActive() { return active; }

    public static void resetSession() { close(); }

    public static void completeCandidateSelection() {
        if (active) startClosing();
    }

    public static void togglePaperCasting(Minecraft minecraft, LocalPlayer player) {
        if (active) {
            finishAndClose(minecraft, player);
        } else {
            openPaper(minecraft, player);
        }
    }

    private static void openPaper(Minecraft minecraft, LocalPlayer player) {
        if (minecraft.screen != null) return;
        Vec3 paperAnchor = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F).normalize();
        plane = InkPlane.create(paperAnchor, look);
        active = true;
        openingStartTimeMs = System.currentTimeMillis();
        lastHeldItem = player.getMainHandItem().copy();
        QuickCastCandidateClient.reset();
        clear();
        say(player, "白纸施写已展开：左键写，右键收束识别");
    }

    private static void finishAndClose(Minecraft minecraft, LocalPlayer player) {
        if (!active || plane == null) return;
        // Final submission already performs authoritative recognition. Suppress the
        // duplicate pen-up preview for this same last stroke while closing.
        finishStroke(true, null);
        InkPlane submittedPlane = plane;
        boolean submittedFromBack = fromBack;
        List<InkStroke> submittedStrokes = strokesForSide(submittedFromBack);

        if (submittedStrokes.isEmpty()) {
            startClosing();
            say(player, "白纸施写已关闭");
            return;
        }

        InkRecognitionRequest request;
        try {
            request = ClassicSubmissionHelper.buildRequest(submittedStrokes, submittedFromBack);
        } catch (Exception e) {
            spawnFailParticles(submittedPlane);
            say(player, "识别请求生成失败: " + e.getClass().getSimpleName());
            AozaiInkInput.LOGGER.error("Temporary paper recognition request failed", e);
            startClosing();
            return;
        }

        long finalRevision = ++revisionCounter;
        QuickCastCandidateClient.expectRevision(finalRevision);
        startClosing();

        AozaiInkNetworking.sendCastPaper(new CastPaperPayload(
            request.trace(),
            request.source().sourceId(),
            request.source().powerMultiplier(),
            request.ttlTicks(),
            finalRevision
        ));
        say(player, "临时施写已提交");
    }

    private static void startClosing() {
        if (plane != null) {
            List<InkStroke> visibleStrokes = strokesForSide(fromBack);
            boolean hasStrokes = !visibleStrokes.isEmpty();
            closingPlane = plane;
            closingStrokes = hasStrokes ? new ArrayList<>(visibleStrokes) : null;
            closingStartTimeMs = System.currentTimeMillis();
            closingDurationMs = hasStrokes ? WRITTEN_CLOSING_DURATION_MS : EMPTY_CLOSING_DURATION_MS;
            closingWritten = hasStrokes;
            closingFromBack = fromBack;
            closingReturnCenter = playerChestCenter();
            closingParticlesSpawned = false;
        }
        close();
    }

    private static void close() {
        active = false;
        plane = null;
        currentStroke = null;
        lastMouseDown = false;
        fromBack = false;
        lastHeldItem = null;
        openingStartTimeMs = 0L;
        clear();
    }

    private static void clear() {
        STROKES.clear();
        currentStroke = null;
        currentHit = null;
        totalPoints = 0;
        autoRecognizePending = false;
        recognizedSinceChange = false;
        lastPenUpTimeMs = 0L;
    }

    private static void sampleMouseStroke(Minecraft minecraft, LocalPlayer player) {
        boolean mouseDown = GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (!mouseDown) {
            finishStroke(lastMouseDown, minecraft);
            lastMouseDown = false;
            return;
        }

        if (plane == null) {
            lastMouseDown = true;
            return;
        }

        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F).normalize();
        Optional<PlaneHit> hit = plane.raycast(eye, look);
        currentHit = hit.orElse(null);
        hit.ifPresent(InkInputController::addPoint);
        lastMouseDown = true;
    }

    private static void addPoint(PlaneHit hit) {
        if (totalPoints >= MAX_TOTAL_POINTS) return;

        long now = System.currentTimeMillis();
        if (currentStroke == null || !lastMouseDown) {
            currentStroke = new InkStroke(fromBack);
            autoRecognizePending = false;
            recognizedSinceChange = false;
        }

        if (!currentStroke.isEmpty()) {
            InkStrokePoint last = currentStroke.last();
            if (currentStroke.size() >= MAX_POINTS_PER_STROKE) {
                finishStroke(false, null);
                currentStroke = new InkStroke(fromBack);
            } else if (last.distanceTo(hit.u(), hit.v()) < MIN_POINT_DISTANCE) {
                return;
            }
        }

        currentStroke.add(new InkStrokePoint(hit.u(), hit.v(), now));
        autoRecognizePending = false;
        recognizedSinceChange = false;
        totalPoints++;
    }

    private static void finishStroke(boolean commit, Minecraft minecraft) {
        if (currentStroke != null && !currentStroke.isEmpty() && commit) {
            STROKES.add(currentStroke);
            lastPenUpTimeMs = System.currentTimeMillis();
            autoRecognizePending = true;
            recognizedSinceChange = false;
            requestCandidates(minecraft);
        }
        currentStroke = null;
    }

    private static void requestCandidates(Minecraft minecraft) {
        if (minecraft == null || !active || plane == null || STROKES.isEmpty()) return;
        List<InkStroke> submittedStrokes = strokesForSide(fromBack);
        if (submittedStrokes.isEmpty()) return;
        try {
            InkRecognitionRequest request = ClassicSubmissionHelper.buildRequest(
                submittedStrokes, fromBack);
            long revision = ++revisionCounter;
            QuickCastCandidateClient.expectRevision(revision);
            AozaiInkNetworking.sendPreviewQuickCast(new PreviewQuickCastPayload(
                request.trace(), request.source().sourceId(), request.source().powerMultiplier(),
                request.ttlTicks(), revision));
            recognizedSinceChange = true;
        } catch (Exception exception) {
            AozaiInkInput.LOGGER.debug("Quick-cast preview request failed", exception);
        }
    }

    private static float confirmProgress() {
        if (!autoRecognizePending || recognizedSinceChange || STROKES.isEmpty()) return 0.0f;
        return Math.min(1.0f, Math.max(0.0f, (System.currentTimeMillis() - lastPenUpTimeMs) / 700.0f));
    }

    private static float openingProgress() {
        if (openingStartTimeMs <= 0L) return 1.0f;
        return Math.min(1.0f, Math.max(0.0f,
            (System.currentTimeMillis() - openingStartTimeMs) / (float) OPENING_DURATION_MS));
    }

    private static List<InkStroke> strokesForSide(boolean sideFromBack) {
        List<InkStroke> visible = new ArrayList<>();
        for (InkStroke stroke : STROKES) {
            if (stroke.fromBack() == sideFromBack && !stroke.isEmpty()) {
                visible.add(stroke);
            }
        }
        return visible;
    }

    private static Vec3 playerChestCenter() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        return player.getEyePosition(1.0F).subtract(0.0D, 0.62D, 0.0D);
    }

    private static void say(LocalPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    private static void spawnBurnParticles(InkPlane plane) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) return;
            Vec3 center = plane.center();
            Vec3 right = plane.right();
            Vec3 up = plane.up();
            Vec3 normal = plane.normal();
            float radius = plane.radius();
            for (int i = 0; i < 24; i++) {
                double side = (minecraft.level.random.nextDouble() - 0.5D) * radius * 1.35D;
                double rise = (minecraft.level.random.nextDouble() - 0.5D) * radius * 0.18D;
                Vec3 pos = center.add(right.scale(side)).add(up.scale(rise)).add(normal.scale(-0.04D));
                minecraft.level.addParticle(ParticleTypes.FLAME, pos.x, pos.y, pos.z,
                    normal.x * 0.025D, 0.045D + minecraft.level.random.nextDouble() * 0.035D, normal.z * 0.025D);
                if (i % 3 == 0) {
                    minecraft.level.addParticle(ParticleTypes.SMOKE, pos.x, pos.y, pos.z,
                        normal.x * 0.015D, 0.025D, normal.z * 0.015D);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void spawnFailParticles(InkPlane plane) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) return;
            Vec3 center = plane.center();
            Vec3 right = plane.right();
            Vec3 up = plane.up();
            float radius = plane.radius() * 1.05f;
            for (int i = 0; i < 48; i++) {
                double angle = Math.PI * 2.0 * i / 48.0;
                Vec3 offset = right.scale(Math.cos(angle) * radius).add(up.scale(Math.sin(angle) * radius));
                Vec3 pos = center.add(offset);
                minecraft.level.addParticle(ParticleTypes.SMOKE, pos.x, pos.y, pos.z,
                    Math.cos(angle) * 0.03, 0.02, Math.sin(angle) * 0.03);
            }
        } catch (Exception ignored) {}
    }
}
