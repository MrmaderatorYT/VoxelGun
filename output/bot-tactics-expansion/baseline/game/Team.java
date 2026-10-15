package com.ccs.voxelgun.game;

/** Team affiliation. Free-for-all modes put everyone in {@link #NONE}. */
public enum Team {
    NONE(0xFFB0B4BA, "Free"),
    BLUE(0xFF4FA3FF, "Blue"),
    RED(0xFFFF6B5A, "Red");

    private final int color;
    private final String label;

    Team(int color, String label) {
        this.color = color;
        this.label = label;
    }

    public int color() {
        return color;
    }

    public String label() {
        return label;
    }

    public Team opponent() {
        if (this == BLUE) {
            return RED;
        }
        return this == RED ? BLUE : NONE;
    }
}
