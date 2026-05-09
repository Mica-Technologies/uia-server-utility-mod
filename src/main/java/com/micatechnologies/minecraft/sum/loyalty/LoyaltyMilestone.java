package com.micatechnologies.minecraft.sum.loyalty;

public final class LoyaltyMilestone {

    public enum Type { MONEY, COMMAND }

    private final int minutes;
    private final Type type;
    private final String value;

    public LoyaltyMilestone(int minutes, Type type, String value) {
        this.minutes = minutes;
        this.type = type;
        this.value = value;
    }

    public int getMinutes() {
        return minutes;
    }

    public int getTicks() {
        return minutes * 20 * 60;
    }

    public Type getType() {
        return type;
    }

    public String getValue() {
        return value;
    }
}
