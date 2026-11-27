package com.nova.browser;

public final class ColorPick {
    private ColorPick() {}

    public static int parse(String raw) {
        if (raw == null) return -1;
        String s = raw.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 3) {
            StringBuilder b = new StringBuilder(6);
            for (int i = 0; i < 3; i++) {
                b.append(s.charAt(i)).append(s.charAt(i));
            }
            s = b.toString();
        }
        if (s.length() != 6) return -1;
        for (int i = 0; i < 6; i++) {
            if (Character.digit(s.charAt(i), 16) < 0) return -1;
        }
        return Integer.parseInt(s, 16);
    }

    public static String hex(int rgb) {
        return String.format("#%06x", rgb & 0xFFFFFF);
    }

    public static String normalize(String raw) {
        int c = parse(raw);
        return c < 0 ? null : hex(c);
    }

    public static int fromHsv(float h, float s, float v) {
        float hue = h % 360f;
        if (hue < 0) hue += 360f;
        float sat = Math.max(0f, Math.min(1f, s));
        float val = Math.max(0f, Math.min(1f, v));
        float c = val * sat;
        float x = c * (1f - Math.abs((hue / 60f) % 2f - 1f));
        float m = val - c;
        float r;
        float g;
        float b;
        if (hue < 60f) { r = c; g = x; b = 0; }
        else if (hue < 120f) { r = x; g = c; b = 0; }
        else if (hue < 180f) { r = 0; g = c; b = x; }
        else if (hue < 240f) { r = 0; g = x; b = c; }
        else if (hue < 300f) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        int ri = Math.round((r + m) * 255f);
        int gi = Math.round((g + m) * 255f);
        int bi = Math.round((b + m) * 255f);
        return (ri << 16) | (gi << 8) | bi;
    }

    public static float[] toHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0f) h = 0f;
        else if (max == r) h = 60f * (((g - b) / d) % 6f);
        else if (max == g) h = 60f * (((b - r) / d) + 2f);
        else h = 60f * (((r - g) / d) + 4f);
        if (h < 0f) h += 360f;
        float s = max == 0f ? 0f : d / max;
        return new float[] {h, s, max};
    }
}
