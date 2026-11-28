package com.nova.browser;

public final class GlassPolicy {
    public static final int FULL = 0;
    public static final int LITE = 1;
    public static final int FLAT = 2;
    public static final int UNSET = -1;
    private static final double LENS_MAX_SCREEN_SHARE = 0.4;

    private GlassPolicy() {}

    public static int clamp(int pick) {
        if (pick < FULL) return FULL;
        if (pick > FLAT) return FLAT;
        return pick;
    }

    public static int defaultPick(boolean weakDevice) {
        return weakDevice ? FLAT : LITE;
    }

    public static boolean forcedFlat(boolean smoothAnim, int adaptiveLevel, boolean batterySaver) {
        return !smoothAnim || adaptiveLevel >= 1 || batterySaver;
    }

    public static int effective(int pick, boolean weakDevice, boolean smoothAnim, int adaptiveLevel, boolean batterySaver) {
        if (forcedFlat(smoothAnim, adaptiveLevel, batterySaver)) return FLAT;
        return pick == UNSET ? defaultPick(weakDevice) : clamp(pick);
    }

    public static int lensTaps(int level) {
        if (level == FULL) return 5;
        if (level == LITE) return 3;
        return 0;
    }

    public static boolean lensAllowed(int level, int w, int h, int screenW, int screenH) {
        if (level == FLAT || w <= 0 || h <= 0 || screenW <= 0 || screenH <= 0) return false;
        return (long) w * h <= LENS_MAX_SCREEN_SHARE * screenW * screenH;
    }

    public static float blurScale(int level) {
        if (level == FULL) return 1f;
        if (level == LITE) return 0.5f;
        return 0f;
    }

    public static boolean glowHead(int level) {
        return level == FULL;
    }

    public static boolean grain(int level) {
        return level == FULL;
    }

    public static float progressFill(float p) {
        if (p < 0.05f) return 0.05f;
        if (p > 1f) return 1f;
        return p;
    }
}
