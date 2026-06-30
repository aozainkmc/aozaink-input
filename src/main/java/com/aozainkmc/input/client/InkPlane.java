package com.aozainkmc.input.client;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

public final class InkPlane {
    private final Vec3 center;
    private final Vec3 normal;
    private final Vec3 right;
    private final Vec3 up;
    private final float radius;

    private InkPlane(Vec3 center, Vec3 normal, Vec3 right, Vec3 up, float radius) {
        this.center = center;
        this.normal = normal;
        this.right = right;
        this.up = up;
        this.radius = radius;
    }

    public static InkPlane create(Vec3 eye, Vec3 look) {
        Vec3 normal = look.normalize();
        Vec3 center = eye.add(normal.scale(2.2));
        Vec3 right = normal.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
        if (right.lengthSqr() < 0.01) right = normal.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
        Vec3 up = right.cross(normal).normalize();
        return new InkPlane(center, normal, right, up, 1.18f);
    }

    public Optional<PlaneHit> raycast(Vec3 origin, Vec3 rayDir) {
        double denom = rayDir.dot(normal);
        if (Math.abs(denom) < 1e-9) return Optional.empty();
        double t = center.subtract(origin).dot(normal) / denom;
        if (t <= 0.0) return Optional.empty();
        Vec3 hit = origin.add(rayDir.scale(t));
        Vec3 local = hit.subtract(center);
        float u = (float) local.dot(right) / radius;
        float v = (float) local.dot(up) / radius;
        if (u < -1.0f || u > 1.0f || v < -1.0f || v > 1.0f) return Optional.empty();
        return Optional.of(new PlaneHit(u, v, hit));
    }

    public Vec3 pointAt(float u, float v) {
        return center.add(right.scale(u * radius)).add(up.scale(v * radius));
    }

    public Vec3 center() { return center; }
    public Vec3 normal() { return normal; }
    public Vec3 right() { return right; }
    public Vec3 up() { return up; }
    public float radius() { return radius; }
}
