package com.nova.browser;

public final class AccentTone {
    private static final float DARK_MIN_LUMA = 0.16f;
    private static final float LIGHT_MAX_LUMA = 0.30f;
    private static final float STEP = 0.04f;
    private static final int MAX_STEPS = 40;

    private AccentTone() {}

    public static float luminance(int rgb) {
        double r = lin(((rgb >> 16) & 0xFF) / 255.0);
        double g = lin(((rgb >> 8) & 0xFF) / 255.0);
        double b = lin((rgb & 0xFF) / 255.0);
        return (float) (0.2126 * r + 0.7152 * g + 0.0722 * b);
    }

    private static double lin(double c) {
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    public static int tone(int rgb, boolean dark) {
        float[] hsv = ColorPick.toHsv(rgb & 0xFFFFFF);
        int out = rgb & 0xFFFFFF;
        for (int i = 0; i < MAX_STEPS; i++) {
            float l = luminance(out);
            if (dark && l < DARK_MIN_LUMA) {
                hsv[2] = Math.min(1f, hsv[2] + STEP);
                if (hsv[2] >= 1f) hsv[1] = Math.max(0f, hsv[1] - STEP);
            } else if (!dark && l > LIGHT_MAX_LUMA) {
                hsv[2] = Math.max(0f, hsv[2] - STEP);
            } else {
                break;
            }
            out = ColorPick.fromHsv(hsv[0], hsv[1], hsv[2]);
        }
        return out;
    }

    public static int on(int rgb) {
        return luminance(rgb) > 0.4f ? 0x000000 : 0xFFFFFF;
    }

    public static int blend(int from, int to, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((from >> 16) & 0xFF) * (1f - k) + ((to >> 16) & 0xFF) * k);
        int g = Math.round(((from >> 8) & 0xFF) * (1f - k) + ((to >> 8) & 0xFF) * k);
        int b = Math.round((from & 0xFF) * (1f - k) + (to & 0xFF) * k);
        return (r << 16) | (g << 8) | b;
    }

    public static int gradientEnd(int rgb) {
        return blend(rgb & 0xFFFFFF, 0xFFFFFF, 0.35f);
    }
}
