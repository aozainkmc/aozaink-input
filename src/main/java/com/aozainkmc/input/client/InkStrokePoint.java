package com.aozainkmc.input.client;

public record InkStrokePoint(float u, float v, long timeMs) {
    public double distanceTo(float otherU, float otherV) {
        double du = this.u - otherU;
        double dv = this.v - otherV;
        return Math.sqrt(du * du + dv * dv);
    }
}
