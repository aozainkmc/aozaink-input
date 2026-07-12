package com.aozainkmc.input.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.aozainkmc.input.network.InputBindingRitualPayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class InputBindingRitualRenderer {
    private static final int DURATION_TICKS = 110;
    private static final int GLYPH_COUNT = 5;
    private static final List<Ritual> RITUALS = new ArrayList<>();

    private InputBindingRitualRenderer() {}

    public static void add(InputBindingRitualPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || payload.digit().isBlank() || payload.glyph().isBlank()) return;
        RITUALS.add(new Ritual(payload.playerId(), new Vec3(payload.x(), payload.y(), payload.z()),
            payload.digit(), payload.glyph(), minecraft.level.getGameTime()));
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || RITUALS.isEmpty()) return;
        long now = minecraft.level.getGameTime();
        Iterator<Ritual> iterator = RITUALS.iterator();
        while (iterator.hasNext()) {
            Ritual ritual = iterator.next();
            if (now - ritual.startTick() >= DURATION_TICKS) iterator.remove();
        }
        if (RITUALS.isEmpty()) return;

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        Vector3f cameraLeftVector = camera.getLeftVector();
        Vector3f cameraUpVector = camera.getUpVector();
        Vec3 cameraRight = new Vec3(-cameraLeftVector.x(), -cameraLeftVector.y(), -cameraLeftVector.z());
        Vec3 cameraUp = new Vec3(cameraUpVector.x(), cameraUpVector.y(), cameraUpVector.z());
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        List<GlyphDraw> glyphs = new ArrayList<>();
        List<LineSegment> trails = new ArrayList<>();

        for (Ritual ritual : RITUALS) {
            float progress = Mth.clamp((now - ritual.startTick() + partialTick) / DURATION_TICKS, 0.0F, 1.0F);
            Player player = minecraft.level.getPlayerByUUID(ritual.playerId());
            Vec3 feet = player == null ? ritual.fallback() : interpolatedPosition(player, partialTick);
            float height = player == null ? 1.8F : player.getBbHeight();
            float yaw = player == null ? 0.0F : Mth.lerp(partialTick, player.yRotO, player.getYRot());
            Vec3 chest = feet.add(0.0D, height * 0.58D, 0.0D);
            Vec3 forward = horizontalForward(yaw);
            Vec3 playerRight = new Vec3(-forward.z, 0.0D, forward.x);
            Vec3 digitRest = chest.add(forward.scale(4.50D)).add(0.0D, 2.20D, 0.0D);
            Vec3 digitPosition = digitPosition(chest, digitRest, progress);
            float absorption = absorbed(progress);
            float pulse = absorptionPulse(progress);
            if (progress >= 0.22F && progress < 0.88F) {
                float motion = smooth(Mth.clamp((progress - 0.22F) / 0.08F, 0.0F, 1.0F));
                digitPosition = digitPosition.add(playerRight.scale(Math.sin(progress * Math.PI * 9.0D) * 0.16D * motion))
                    .add(0.0D, Math.sin(progress * Math.PI * 12.0D) * 0.22D * motion, 0.0D);
            }
            float digitScale = digitScale(progress, absorption)
                * (1.0F + pulse * 0.18F + (progress >= 0.24F && progress < 0.86F
                    ? (float) Math.sin(progress * Math.PI * 10.0D) * 0.035F : 0.0F));
            float digitAlpha = fade(progress);
            if (digitScale > 0.0F && digitAlpha > 0.0F) {
                int digitColor = blendColor(0xFF8A2119, 0xFFFFD86A, absorption);
                int digitOutline = blendColor(0xFF42100D, 0xFFFFF0AD, absorption);
                glyphs.add(new GlyphDraw(ritual.digit(), digitPosition, digitScale,
                    digitColor, digitOutline, digitAlpha));
                addDigitSeal(trails, digitPosition, cameraRight, cameraUp, progress, absorption, pulse, digitAlpha);
            }

            addSpiralBands(trails, chest, progress);

            for (int index = 0; index < GLYPH_COUNT; index++) {
                float release = 0.46F + index * 0.045F;
                float arrival = release + 0.15F;
                Vec3 position = skillPosition(chest, digitRest, ritual.playerId(), index, progress, release, arrival);
                float alpha = fadeInOut(progress, 0.08F + index * 0.018F, arrival + 0.025F);
                if (alpha > 0.0F) {
                    glyphs.add(new GlyphDraw(ritual.glyph(), position, 0.130F,
                        0xFFFFD86A, 0xFFFFF1A8, alpha));
                    addTrail(trails, chest, digitRest, ritual.playerId(), index, progress, release, arrival, alpha);
                }
            }
        }

        renderTrails(event.getPoseStack(), camera, trails);
        renderGlyphs(event.getPoseStack(), cameraPos, minecraft, glyphs);
    }

    private static Vec3 digitPosition(Vec3 chest, Vec3 rest, float progress) {
        if (progress < 0.08F) return chest;
        if (progress < 0.24F) return chest.lerp(rest, smooth((progress - 0.08F) / 0.16F));
        if (progress < 0.86F) return rest;
        if (progress < 0.94F) return rest.lerp(chest, smooth((progress - 0.86F) / 0.08F));
        return chest;
    }

    private static float digitScale(float progress, float absorption) {
        float chestScale = 0.018F;
        float floatingScale = 0.180F + absorption * 0.250F;
        if (progress < 0.08F) return chestScale;
        if (progress < 0.24F) {
            return Mth.lerp(smooth((progress - 0.08F) / 0.16F), chestScale, floatingScale);
        }
        if (progress < 0.86F) return floatingScale;
        if (progress < 0.94F) {
            return Mth.lerp(smooth((progress - 0.86F) / 0.08F), floatingScale, chestScale);
        }
        return chestScale;
    }

    private static Vec3 skillPosition(Vec3 center, Vec3 target, UUID playerId, int index,
            float progress, float release, float arrival) {
        Vec3 orbit = orbitPosition(center, playerId, index, Math.min(progress, release));
        if (progress <= release) return orbit;
        float flight = smooth(Mth.clamp((progress - release) / (arrival - release), 0.0F, 1.0F));
        Vec3 control = orbit.lerp(target, 0.52D).add(0.0D, 0.72D + index * 0.055D, 0.0D);
        return quadratic(orbit, control, target, flight);
    }

    private static Vec3 orbitPosition(Vec3 center, UUID playerId, int index, float progress) {
        long seed = playerId.getMostSignificantBits() ^ playerId.getLeastSignificantBits() ^ (index * 0x9E3779B97F4A7C15L);
        double phase = unit(seed) * Math.PI * 2.0D;
        double spin = (index % 2 == 0 ? 1.0D : -1.0D) * Math.PI * 2.0D * 2.15D;
        double angle = phase + progress * spin;
        double radius = 2.80D + unit(seed >>> 17) * 1.40D;
        double tilt = -0.58D + unit(seed >>> 31) * 1.16D;
        double rotation = unit(seed >>> 47) * Math.PI * 2.0D;
        double flatX = Math.cos(angle) * radius;
        double flatZ = Math.sin(angle) * radius * Math.cos(tilt);
        double y = 0.72D + index * 0.24D + Math.sin(angle) * radius * Math.sin(tilt);
        double x = flatX * Math.cos(rotation) - flatZ * Math.sin(rotation);
        double z = flatX * Math.sin(rotation) + flatZ * Math.cos(rotation);
        return center.add(x, y, z);
    }

    private static void addTrail(List<LineSegment> trails, Vec3 center, Vec3 target, UUID playerId, int index,
            float progress, float release, float arrival, float alpha) {
        Vec3 previous = skillPosition(center, target, playerId, index, progress, release, arrival);
        for (int step = 1; step <= 22; step++) {
            float earlier = Math.max(0.0F, progress - step * 0.0085F);
            Vec3 point = skillPosition(center, target, playerId, index, earlier, release, arrival);
            float trailAlpha = alpha * (1.0F - step / 23.0F) * 0.94F;
            trails.add(new LineSegment(point, previous, 0.036D + (22 - step) * 0.0028D, trailAlpha));
            previous = point;
        }
    }

    private static void addSpiralBands(List<LineSegment> trails, Vec3 center, float progress) {
        float appear = smooth(Mth.clamp(progress / 0.14F, 0.0F, 1.0F));
        float vanish = 1.0F - smooth(Mth.clamp((progress - 0.76F) / 0.12F, 0.0F, 1.0F));
        float alpha = appear * vanish * 0.48F;
        if (alpha <= 0.0F) return;
        for (int band = 0; band < 3; band++) {
            Vec3 previous = spiralPoint(center, band, 0.0D, progress);
            for (int segment = 1; segment <= 64; segment++) {
                double t = segment / 64.0D;
                Vec3 point = spiralPoint(center, band, t, progress);
                double taper = Math.sin(Math.PI * t);
                trails.add(new LineSegment(previous, point, 0.018D + taper * 0.020D,
                    alpha * (0.48F + (float) taper * 0.52F)));
                previous = point;
            }
        }
    }

    private static Vec3 spiralPoint(Vec3 center, int band, double t, float progress) {
        double direction = band % 2 == 0 ? 1.0D : -1.0D;
        double phase = band * Math.PI * 0.68D + progress * Math.PI * 3.2D * direction;
        double angle = phase + t * Math.PI * 2.0D * direction;
        double radius = 2.20D + band * 0.48D + Math.sin(t * Math.PI * 2.0D + phase) * 0.18D;
        double y = -0.48D + t * 2.70D + band * 0.18D;
        return center.add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
    }

    private static void addDigitSeal(List<LineSegment> trails, Vec3 center, Vec3 right, Vec3 up,
            float progress, float absorption, float pulse, float alpha) {
        float appear = smooth(Mth.clamp((progress - 0.17F) / 0.10F, 0.0F, 1.0F));
        float vanish = 1.0F - smooth(Mth.clamp((progress - 0.88F) / 0.08F, 0.0F, 1.0F));
        float sealAlpha = appear * vanish * alpha;
        if (sealAlpha <= 0.0F) return;
        double baseRadius = 1.25D + absorption * 1.30D + pulse * 0.30D;
        for (int ring = 0; ring < 3; ring++) {
            double radius = baseRadius * (0.72D + ring * 0.24D);
            double rotation = progress * Math.PI * (ring % 2 == 0 ? 1.4D : -1.0D) + ring * 0.42D;
            Vec3 previous = sealPoint(center, right, up, radius, rotation);
            for (int segment = 1; segment <= 48; segment++) {
                double angle = rotation + segment / 48.0D * Math.PI * 2.0D;
                Vec3 point = sealPoint(center, right, up, radius, angle);
                trails.add(new LineSegment(previous, point, 0.026D + ring * 0.006D,
                    sealAlpha * (0.72F - ring * 0.14F)));
                previous = point;
            }
        }
        double spokeRadius = baseRadius * 1.16D;
        for (int spoke = 0; spoke < 8; spoke++) {
            double angle = progress * Math.PI * 0.8D + spoke * Math.PI / 4.0D;
            Vec3 inner = sealPoint(center, right, up, baseRadius * 0.82D, angle);
            Vec3 outer = sealPoint(center, right, up, spokeRadius, angle);
            trails.add(new LineSegment(inner, outer, 0.032D, sealAlpha * 0.72F));
        }
    }

    private static Vec3 sealPoint(Vec3 center, Vec3 right, Vec3 up, double radius, double angle) {
        return center.add(right.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
    }

    private static void renderTrails(PoseStack poseStack, Camera camera, List<LineSegment> trails) {
        if (trails.isEmpty()) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix = poseStack.last().pose();
        Vector3f leftVector = camera.getLeftVector();
        Vector3f upVector = camera.getUpVector();
        Vec3 right = new Vec3(-leftVector.x(), -leftVector.y(), -leftVector.z());
        Vec3 up = new Vec3(upVector.x(), upVector.y(), upVector.z());
        Vec3 cameraPos = camera.getPosition();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (LineSegment trail : trails) {
            line(builder, matrix, trail.from().subtract(cameraPos), trail.to().subtract(cameraPos), right, up,
                trail.width() * 3.8D, alphaColor(0x58FFD05C, trail.alpha()));
            line(builder, matrix, trail.from().subtract(cameraPos), trail.to().subtract(cameraPos), right, up,
                trail.width() * 1.65D, alphaColor(0xD8FFD86A, trail.alpha()));
            line(builder, matrix, trail.from().subtract(cameraPos), trail.to().subtract(cameraPos), right, up,
                trail.width() * 0.52D, alphaColor(0xF4FFF4C4, trail.alpha()));
        }
        MeshData mesh = builder.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderGlyphs(PoseStack poseStack, Vec3 cameraPos, Minecraft minecraft, List<GlyphDraw> glyphs) {
        if (glyphs.isEmpty()) return;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Font font = minecraft.font;
        for (GlyphDraw glyph : glyphs) {
            poseStack.pushPose();
            Vec3 relative = glyph.position().subtract(cameraPos);
            poseStack.translate(relative.x, relative.y, relative.z);
            poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
            poseStack.scale(glyph.scale(), -glyph.scale(), glyph.scale());
            Matrix4f matrix = poseStack.last().pose();
            float x = -font.width(glyph.text()) * 0.5F;
            float y = -font.lineHeight * 0.5F;
            int color = alphaColor(glyph.color(), glyph.alpha());
            int outline = alphaColor(glyph.outlineColor(), glyph.alpha() * 0.92F);
            font.drawInBatch8xOutline(Component.literal(glyph.text()).getVisualOrderText(), x, y,
                color, outline, matrix, buffers, 0x00F000F0);
            poseStack.popPose();
        }
        buffers.endBatch();
    }

    private static Vec3 interpolatedPosition(Player player, float partialTick) {
        return new Vec3(Mth.lerp(partialTick, player.xo, player.getX()),
            Mth.lerp(partialTick, player.yo, player.getY()), Mth.lerp(partialTick, player.zo, player.getZ()));
    }

    private static Vec3 horizontalForward(float yawDegrees) {
        double yaw = yawDegrees * Mth.DEG_TO_RAD;
        return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
    }

    private static float absorbed(float progress) {
        float value = 0.0F;
        for (int index = 0; index < GLYPH_COUNT; index++) {
            float arrival = 0.46F + index * 0.045F + 0.15F;
            value += smooth(Mth.clamp((progress - arrival + 0.025F) / 0.055F, 0.0F, 1.0F));
        }
        return value / GLYPH_COUNT;
    }

    private static float absorptionPulse(float progress) {
        float pulse = 0.0F;
        for (int index = 0; index < GLYPH_COUNT; index++) {
            float arrival = 0.46F + index * 0.045F + 0.15F;
            float distance = Math.abs(progress - arrival) / 0.045F;
            pulse = Math.max(pulse, smooth(Mth.clamp(1.0F - distance, 0.0F, 1.0F)));
        }
        return pulse;
    }

    private static float fade(float progress) {
        if (progress < 0.06F) return smooth(progress / 0.06F);
        if (progress > 0.96F) return 1.0F - smooth((progress - 0.96F) / 0.04F);
        return 1.0F;
    }

    private static float fadeInOut(float progress, float start, float end) {
        float in = smooth(Mth.clamp((progress - start) / 0.07F, 0.0F, 1.0F));
        float out = 1.0F - smooth(Mth.clamp((progress - end) / 0.035F, 0.0F, 1.0F));
        return in * out;
    }

    private static int blendColor(int from, int to, float amount) {
        float t = Mth.clamp(amount, 0.0F, 1.0F);
        int red = Math.round(Mth.lerp(t, from >> 16 & 255, to >> 16 & 255));
        int green = Math.round(Mth.lerp(t, from >> 8 & 255, to >> 8 & 255));
        int blue = Math.round(Mth.lerp(t, from & 255, to & 255));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static double unit(long value) {
        long mixed = value ^ value >>> 33;
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        return (mixed & 0xFFFFFFL) / (double) 0x1000000L;
    }

    private static Vec3 quadratic(Vec3 start, Vec3 control, Vec3 end, double t) {
        double inverse = 1.0D - t;
        return start.scale(inverse * inverse).add(control.scale(2.0D * inverse * t)).add(end.scale(t * t));
    }

    private static void line(BufferBuilder builder, Matrix4f matrix, Vec3 from, Vec3 to,
            Vec3 right, Vec3 up, double width, int color) {
        Vec3 value = to.subtract(from);
        double x = value.dot(right), y = value.dot(up);
        Vec3 normal = right.scale(-y).add(up.scale(x));
        normal = normal.lengthSqr() < 0.0001D ? up : normal.normalize();
        Vec3 half = normal.scale(width * 0.5D);
        quad(builder, matrix, from.subtract(half), to.subtract(half), to.add(half), from.add(half), color);
    }

    private static void quad(BufferBuilder builder, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        int alpha = color >>> 24 & 255, red = color >>> 16 & 255, green = color >>> 8 & 255, blue = color & 255;
        builder.addVertex(matrix, (float) a.x, (float) a.y, (float) a.z).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, (float) b.x, (float) b.y, (float) b.z).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, (float) c.x, (float) c.y, (float) c.z).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, (float) d.x, (float) d.y, (float) d.z).setColor(red, green, blue, alpha);
    }

    private static int alphaColor(int color, float alpha) {
        return Math.round((color >>> 24 & 255) * Mth.clamp(alpha, 0.0F, 1.0F)) << 24 | color & 0x00FFFFFF;
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private record Ritual(UUID playerId, Vec3 fallback, String digit, String glyph, long startTick) {}
    private record GlyphDraw(String text, Vec3 position, float scale, int color, int outlineColor, float alpha) {}
    private record LineSegment(Vec3 from, Vec3 to, double width, float alpha) {}
}
