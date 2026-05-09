package com.micatechnologies.minecraft.sum.border;

public final class BorderEntry {

    public enum Mode { BOUNCE, LOOP }

    private final int dimId;
    private final double radius;
    private final Mode mode;

    public BorderEntry(int dimId, double radius, Mode mode) {
        this.dimId = dimId;
        this.radius = radius;
        this.mode = mode;
    }

    public int getDimId() {
        return dimId;
    }

    public double getRadius() {
        return radius;
    }

    public Mode getMode() {
        return mode;
    }
}
