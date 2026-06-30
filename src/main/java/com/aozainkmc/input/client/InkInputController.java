package com.aozainkmc.input.client;

import com.aozainkmc.core.AozaiInkCoreApi;
import com.aozainkmc.core.api.EngineType;
import com.aozainkmc.core.api.InkRecognitionRequest;
import com.aozainkmc.core.api.InkRecognizedEvent;
import com.aozainkmc.input.AozaiInkInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class InkInputController {
    private static final float MIN_POINT_DISTANCE = 0.012f;
    private static final int MAX_POINTS_PER_STROKE = 512;
    private static final int MAX_TOTAL_POINTS = 2048;
    private static final float MAX_ACTIVE_DISTANCE_SQR = 10.0f * 10.0f;
    private static final long CLOSING_DURATION_MS = 500L;

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
    private static final EngineType CURRENT_ENGINE_TYPE = EngineType.ONLINE_TRAJECTORY;
    private static long lastPenUpTimeMs;

    private static InkPlane closingPlane;
    private static List<InkStroke> closingStrokes;
    private static long closingStartTimeMs;

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
            if (elapsed >= CLOSING_DURATION_MS) {
                closingPlane = null;
                closingStrokes = null;
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

        if (closingPlane != null && System.currentTimeMillis() - closingStartTimeMs < CLOSING_DURATION_MS) {
            RENDERER.render(event.getPoseStack(), event.getCamera(), closingPlane,
                closingStrokes == null ? List.of() : closingStrokes, null, null, 0.0f);
        }

        if (!active || plane == null) return;
        RENDERER.render(event.getPoseStack(), event.getCamera(), plane, STROKES, currentStroke, currentHit, confirmProgress());
    }

    public static boolean isActive() { return active; }

    public static void resetSession() { close(); }

    public static void togglePaperCasting(Minecraft minecraft, LocalPlayer player) {
        if (active) {
            finishAndClose(minecraft, player);
        } else {
            openPaper(minecraft, player);
        }
    }

    private static void openPaper(Minecraft minecraft, LocalPlayer player) {
        if (minecraft.screen != null) return;
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F).normalize();
        plane = InkPlane.create(eye, look);
        active = true;
        lastHeldItem = player.getMainHandItem().copy();
        clear();
        say(player, "白纸施写已展开：左键写，右键收束识别");
        spawnOpenParticles(plane);
    }

    private static void finishAndClose(Minecraft minecraft, LocalPlayer player) {
        if (!active || plane == null) return;
        finishStroke(true, minecraft);
        InkPlane submittedPlane = plane;
        boolean submittedFromBack = fromBack;

        if (STROKES.isEmpty()) {
            startClosing();
            say(player, "白纸施写已关闭");
            return;
        }

        InkRecognitionRequest request;
        try {
            request = ClassicSubmissionHelper.buildRequest(STROKES, submittedFromBack, CURRENT_ENGINE_TYPE);
        } catch (Exception e) {
            spawnFailParticles(submittedPlane);
            say(player, "识别请求生成失败: " + e.getClass().getSimpleName());
            AozaiInkInput.LOGGER.error("Temporary paper recognition request failed", e);
            startClosing();
            return;
        }

        startClosing();

        if (!minecraft.hasSingleplayerServer()) {
            say(player, "当前只支持单人存档");
            return;
        }

        MinecraftServer server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            try {
                net.minecraft.server.level.ServerPlayer sp = server.getPlayerList().getPlayer(player.getUUID());
                if (sp == null) return;

                InkRecognizedEvent event = AozaiInkCoreApi.recognizer().recognizeAndBroadcast(request, server, sp);

                if (event == null) {
                    minecraft.execute(() -> {
                        spawnFailParticles(submittedPlane);
                        say(player, "临时施法未生效");
                    });
                    return;
                }

                ItemStack mainHand = sp.getMainHandItem();
                if (mainHand.is(Items.PAPER)) {
                    mainHand.shrink(1);
                }

                String msg = "临时施法: " + event.result().topGlyph() +
                    " " + Math.round(event.result().confidence() * 1000f) / 10f + "%";

                minecraft.execute(() -> say(player, msg));
            } catch (Exception e) {
                AozaiInkInput.LOGGER.error("Temporary paper recognition failed", e);
                minecraft.execute(() -> {
                    spawnFailParticles(submittedPlane);
                    say(player, "识别失败: " + e.getClass().getSimpleName());
                });
            }
        });
    }

    private static void startClosing() {
        if (plane != null) {
            closingPlane = plane;
            closingStrokes = STROKES.isEmpty() ? null : new ArrayList<>(STROKES);
            closingStartTimeMs = System.currentTimeMillis();
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
            currentStroke = new InkStroke();
            autoRecognizePending = false;
            recognizedSinceChange = false;
        }

        if (!currentStroke.isEmpty()) {
            InkStrokePoint last = currentStroke.last();
            if (currentStroke.size() >= MAX_POINTS_PER_STROKE) {
                finishStroke(false, null);
                currentStroke = new InkStroke();
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
        }
        currentStroke = null;
    }

    private static float confirmProgress() {
        if (!autoRecognizePending || recognizedSinceChange || STROKES.isEmpty()) return 0.0f;
        return Math.min(1.0f, Math.max(0.0f, (System.currentTimeMillis() - lastPenUpTimeMs) / 700.0f));
    }

    private static void say(LocalPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    private static void spawnOpenParticles(InkPlane plane) {
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
                minecraft.level.addParticle(ParticleTypes.SCRAPE, pos.x, pos.y, pos.z,
                    Math.cos(angle) * 0.02, 0.01, Math.sin(angle) * 0.02);
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