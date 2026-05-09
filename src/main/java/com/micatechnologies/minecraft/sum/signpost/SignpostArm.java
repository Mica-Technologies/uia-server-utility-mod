package com.micatechnologies.minecraft.sum.signpost;

import net.minecraft.nbt.NBTTagCompound;

/**
 * One arm on a signpost. Holds a label and a compass-bearing angle in degrees
 * (0 = north / -Z, 90 = east / +X, 180 = south / +Z, 270 = west / -X).
 */
public final class SignpostArm {

    private static final String NBT_LABEL = "label";
    private static final String NBT_ANGLE = "angle";

    private String label;
    private float angleDegrees;

    public SignpostArm(String label, float angleDegrees) {
        this.label = label == null ? "" : label;
        this.angleDegrees = normalize(angleDegrees);
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
    }

    public float getAngleDegrees() {
        return angleDegrees;
    }

    public void setAngleDegrees(float angleDegrees) {
        this.angleDegrees = normalize(angleDegrees);
    }

    public NBTTagCompound serialize() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(NBT_LABEL, label);
        tag.setFloat(NBT_ANGLE, angleDegrees);
        return tag;
    }

    public static SignpostArm deserialize(NBTTagCompound tag) {
        return new SignpostArm(tag.getString(NBT_LABEL), tag.getFloat(NBT_ANGLE));
    }

    private static float normalize(float angle) {
        float a = angle % 360.0F;
        return a < 0 ? a + 360.0F : a;
    }
}
