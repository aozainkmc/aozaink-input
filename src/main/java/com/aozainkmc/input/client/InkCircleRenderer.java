package com.aozainkmc.input.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class InkCircleRenderer {
    private static final ResourceLocation PAPER_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/paper.png");

    private static final double PAPER_LAYER_OFFSET = -0.010D;
    private static final double STROKE_LAYER_OFFSET = -0.026D;
    private static final float STROKE_OUTLINE_WIDTH = 0.040F;
    private static final float STROKE_CORE_WIDTH = 0.026F;
    private static final float CURSOR_SIZE = 0.045F;

    public void render(PoseStack poseStack, Camera camera, InkPlane plane,
                       List<InkStroke> strokes, InkStroke currentStroke, PlaneHit currentHit,
                       float confirmProgress) {
        Vec3 camPos = camera.getPosition();

        poseStack.pushPose();
        poseStack.translate(plane.center().x - camPos.x, plane.center().y - camPos.y, plane.center().z - camPos.z);

        renderPaper(poseStack, plane);
        renderInk(poseStack, plane, strokes, currentStroke, currentHit);

        if (confirmProgress > 0.0f) {
            renderConfirmProgress(poseStack, plane, confirmProgress);
        }

        poseStack.popPose();
    }

    private void renderPaper(PoseStack poseStack, InkPlane plane) {
        float r = plane.radius();
        Vec3 rx = plane.right().scale(r);
        Vec3 ry = plane.up().scale(r);
        Vec3 n = plane.normal().scale(PAPER_LAYER_OFFSET);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderTexture(0, PAPER_TEXTURE);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        Matrix4f matrix = poseStack.last().pose();
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder builder = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        Vec3 bl = rx.scale(-1).add(ry.scale(-1)).add(n);
        builder.addVertex(matrix, (float) bl.x, (float) bl.y, (float) bl.z).setUv(0f, 1f);
        Vec3 br = rx.add(ry.scale(-1)).add(n);
        builder.addVertex(matrix, (float) br.x, (float) br.y, (float) br.z).setUv(1f, 1f);
        Vec3 tr = rx.add(ry).add(n);
        builder.addVertex(matrix, (float) tr.x, (float) tr.y, (float) tr.z).setUv(1f, 0f);
        Vec3 tl = rx.scale(-1).add(ry).add(n);
        builder.addVertex(matrix, (float) tl.x, (float) tl.y, (float) tl.z).setUv(0f, 0f);

        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void renderInk(PoseStack poseStack, InkPlane plane, List<InkStroke> strokes,
                           InkStroke currentStroke, PlaneHit currentHit) {
        boolean hasCurrentStroke = currentStroke != null && !currentStroke.isEmpty();
        if (strokes.isEmpty() && !hasCurrentStroke && currentHit == null) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = poseStack.last().pose();
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder builder = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (InkStroke stroke : strokes) {
            renderStroke(builder, matrix, plane, stroke);
        }
        if (currentStroke != null) {
            renderStroke(builder, matrix, plane, currentStroke);
        }
        if (currentHit != null) {
            renderCursor(builder, matrix, plane, currentHit);
        }

        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void renderStroke(BufferBuilder builder, Matrix4f matrix, InkPlane plane, InkStroke stroke) {
        if (stroke.isEmpty()) return;
        List<InkStrokePoint> points = stroke.points();

        for (InkStrokePoint point : points) {
            disk(builder, matrix, plane, point.u(), point.v(), STROKE_OUTLINE_WIDTH * 0.55F, 34, 28, 20, 245);
        }
        for (int i = 1; i < points.size(); i++) {
            InkStrokePoint a = points.get(i - 1);
            InkStrokePoint b = points.get(i);
            ribbon(builder, matrix, plane, a.u(), a.v(), b.u(), b.v(), STROKE_OUTLINE_WIDTH, 34, 28, 20, 245);
        }

        for (InkStrokePoint point : points) {
            disk(builder, matrix, plane, point.u(), point.v(), STROKE_CORE_WIDTH * 0.5F, 8, 8, 8, 255);
        }
        for (int i = 1; i < points.size(); i++) {
            InkStrokePoint a = points.get(i - 1);
            InkStrokePoint b = points.get(i);
            ribbon(builder, matrix, plane, a.u(), a.v(), b.u(), b.v(), STROKE_CORE_WIDTH, 8, 8, 8, 255);
        }
    }

    private void renderCursor(BufferBuilder builder, Matrix4f matrix, InkPlane plane, PlaneHit hit) {
        float u = hit.u();
        float v = hit.v();
        ribbon(builder, matrix, plane, u - CURSOR_SIZE, v, u + CURSOR_SIZE, v, 0.012F, 20, 90, 210, 220);
        ribbon(builder, matrix, plane, u, v - CURSOR_SIZE, u, v + CURSOR_SIZE, 0.012F, 20, 90, 210, 220);
    }

    private void ribbon(BufferBuilder builder, Matrix4f matrix, InkPlane plane,
                        float au, float av, float bu, float bv, float width,
                        int red, int green, int blue, int alpha) {
        float du = bu - au;
        float dv = bv - av;
        float length = (float) Math.sqrt(du * du + dv * dv);
        if (length < 0.0001F) {
            disk(builder, matrix, plane, au, av, width * 0.5F, red, green, blue, alpha);
            return;
        }

        float ou = -dv / length * width * 0.5F;
        float ov = du / length * width * 0.5F;
        quad(builder, matrix, plane,
            au + ou, av + ov,
            au - ou, av - ov,
            bu - ou, bv - ov,
            bu + ou, bv + ov,
            red, green, blue, alpha);
    }

    private void disk(BufferBuilder builder, Matrix4f matrix, InkPlane plane,
                      float u, float v, float radius, int red, int green, int blue, int alpha) {
        int segments = 18;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            double mid = (a0 + a1) * 0.5D;
            quad(builder, matrix, plane,
                u, v,
                u + (float) Math.cos(a0) * radius, v + (float) Math.sin(a0) * radius,
                u + (float) Math.cos(mid) * radius, v + (float) Math.sin(mid) * radius,
                u + (float) Math.cos(a1) * radius, v + (float) Math.sin(a1) * radius,
                red, green, blue, alpha);
        }
    }

    private void quad(BufferBuilder builder, Matrix4f matrix, InkPlane plane,
                      float u1, float v1, float u2, float v2, float u3, float v3, float u4, float v4,
                      int red, int green, int blue, int alpha) {
        vertex(builder, matrix, plane, u1, v1, red, green, blue, alpha);
        vertex(builder, matrix, plane, u2, v2, red, green, blue, alpha);
        vertex(builder, matrix, plane, u3, v3, red, green, blue, alpha);
        vertex(builder, matrix, plane, u4, v4, red, green, blue, alpha);
    }

    private void vertex(BufferBuilder builder, Matrix4f matrix, InkPlane plane,
                        float u, float v, int red, int green, int blue, int alpha) {
        float r = plane.radius();
        Vec3 point = plane.right().scale(u * r)
            .add(plane.up().scale(v * r))
            .add(plane.normal().scale(STROKE_LAYER_OFFSET));
        builder.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z).setColor(red, green, blue, alpha);
    }

    private void renderConfirmProgress(PoseStack poseStack, InkPlane plane, float progress) {
        RenderSystem.lineWidth(4.0f);
        RenderSystem.disableCull();
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder builder = tessellator.begin(VertexFormat.Mode.LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f mat = poseStack.last().pose();
        Vec3 rx = plane.right().scale(plane.radius() * 1.08f);
        Vec3 ry = plane.up().scale(plane.radius() * 1.08f);
        Vec3 n = plane.normal().scale(STROKE_LAYER_OFFSET - 0.006D);
        int segments = 48;
        for (int i = 0; i <= segments * progress; i++) {
            double angle = Math.PI * 2.0 * i / segments - Math.PI / 2.0;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Vec3 pt = rx.scale(cos).add(ry.scale(sin)).add(n);
            builder.addVertex(mat, (float) pt.x, (float) pt.y, (float) pt.z).setColor(0xFF, 0xFF, 0xFF, 0xCC);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableCull();
    }
}