package com.aozainkmc.input.client;

import com.aozainkmc.input.effect.TalismanFormationEffect;
import com.aozainkmc.input.network.TalismanFormationPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

public final class TalismanFormationRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        "aozaink_input", "textures/block/yellow_talisman.png"
    );
    private static final int CHAOS_TICKS = 60;
    private static final double HALF_WIDTH = 0.44D;
    private static final double HALF_HEIGHT = 0.44D;
    private static final List<Animation> ACTIVE = new ArrayList<>();

    private TalismanFormationRenderer() {}

    public static void add(TalismanFormationPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        ACTIVE.add(new Animation(payload, minecraft.level.getGameTime()));
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || ACTIVE.isEmpty()) return;

        long now = level.getGameTime();
        Iterator<Animation> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Animation animation = iterator.next();
            int duration = animation.payload.chaos() ? CHAOS_TICKS : TalismanFormationEffect.SUCCESS_TICKS;
            if (now - animation.startTick >= duration) iterator.remove();
        }
        if (ACTIVE.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        Matrix4f matrix = event.getPoseStack().last().pose();
        Vec3 camera = event.getCamera().getPosition();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (Animation animation : ACTIVE) {
            renderAnimation(builder, matrix, level, camera, animation, now, partialTick);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderAnimation(BufferBuilder builder, Matrix4f matrix, ClientLevel level, Vec3 camera,
                                        Animation animation, long now, float partialTick) {
        TalismanFormationPayload payload = animation.payload;
        float age = now - animation.startTick + partialTick;
        Vec3 base = Vec3.atCenterOf(payload.pos()).add(0.0D, -0.43D, 0.0D);
        Vec3 center = Vec3.atCenterOf(payload.pos()).add(0.0D, 0.15D, 0.0D);
        float rise = smooth(Math.min(1.0F, age / 8.0F));
        Vec3 position = base.lerp(center, rise);
        double scale = 1.0D;

        Entity owner = level.getPlayerByUUID(payload.ownerId());
        Vec3 facing = owner == null ? new Vec3(0.0D, 0.0D, 1.0D)
            : owner.position().subtract(position).multiply(1.0D, 0.0D, 1.0D);
        if (facing.lengthSqr() < 0.0001D) facing = new Vec3(0.0D, 0.0D, 1.0D);
        facing = facing.normalize();
        Vec3 rightVertical = new Vec3(facing.z, 0.0D, -facing.x);
        Vec3 right = new Vec3(1.0D, 0.0D, 0.0D).lerp(rightVertical, rise).normalize();
        Vec3 up = new Vec3(0.0D, 0.0D, 1.0D).lerp(new Vec3(0.0D, 1.0D, 0.0D), rise).normalize();

        if (!payload.chaos() && age > 14.0F && owner != null) {
            float fly = smooth(Math.min(1.0F, (age - 14.0F) / 14.0F));
            Vec3 chest = owner.getPosition(partialTick).add(0.0D, owner.getBbHeight() * 0.58D, 0.0D);
            Vec3 control = center.add(0.0D, 0.7D, 0.0D);
            position = quadratic(center, control, chest, fly);
            scale = 1.0D - fly * 0.82D;
        } else if (payload.chaos() && age > 8.0F) {
            scale = 1.0D + Math.sin(age * 0.32D) * 0.035D;
        }

        Vec3 relative = position.subtract(camera);
        Vec3 rx = right.scale(HALF_WIDTH * scale);
        Vec3 uy = up.scale(HALF_HEIGHT * scale);
        Vec3 bl = relative.subtract(rx).subtract(uy);
        Vec3 br = relative.add(rx).subtract(uy);
        Vec3 tr = relative.add(rx).add(uy);
        Vec3 tl = relative.subtract(rx).add(uy);
        quad(builder, matrix, bl, br, tr, tl);
        quad(builder, matrix, tl, tr, br, bl);
    }

    private static void quad(BufferBuilder builder, Matrix4f matrix, Vec3 bl, Vec3 br, Vec3 tr, Vec3 tl) {
        builder.addVertex(matrix, (float) bl.x, (float) bl.y, (float) bl.z).setUv(0.0F, 1.0F);
        builder.addVertex(matrix, (float) br.x, (float) br.y, (float) br.z).setUv(1.0F, 1.0F);
        builder.addVertex(matrix, (float) tr.x, (float) tr.y, (float) tr.z).setUv(1.0F, 0.0F);
        builder.addVertex(matrix, (float) tl.x, (float) tl.y, (float) tl.z).setUv(0.0F, 0.0F);
    }

    private static Vec3 quadratic(Vec3 start, Vec3 control, Vec3 end, float progress) {
        double inverse = 1.0D - progress;
        return start.scale(inverse * inverse)
            .add(control.scale(2.0D * inverse * progress))
            .add(end.scale(progress * progress));
    }

    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    private record Animation(TalismanFormationPayload payload, long startTick) {}
}
