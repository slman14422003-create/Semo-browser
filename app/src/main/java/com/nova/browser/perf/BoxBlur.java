package com.nova.browser;

public final class BoxBlur {
    private BoxBlur() {}

    public static void apply(int[] px, int w, int h, int radius, int passes) {
        if (px == null || w <= 0 || h <= 0 || radius <= 0 || passes <= 0 || px.length < w * h) return;
        int[] tmp = new int[px.length];
        for (int i = 0; i < passes; i++) {
            pass(px, tmp, w, h, radius, true);
            pass(tmp, px, w, h, radius, false);
        }
    }

    private static void pass(int[] src, int[] dst, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w;
        int len = horizontal ? w : h;
        int stride = horizontal ? 1 : w;
        int window = 2 * r + 1;
        for (int line = 0; line < lines; line++) {
            int base = horizontal ? line * w : line;
            long sa = 0, sr = 0, sg = 0, sb = 0;
            for (int k = -r; k <= r; k++) {
                int c = src[base + clamp(k, len) * stride];
                sa += c >>> 24; sr += (c >> 16) & 0xFF; sg += (c >> 8) & 0xFF; sb += c & 0xFF;
            }
            for (int i = 0; i < len; i++) {
                dst[base + i * stride] = ((int) (sa / window) << 24) | ((int) (sr / window) << 16)
                        | ((int) (sg / window) << 8) | (int) (sb / window);
                int add = src[base + clamp(i + r + 1, len) * stride];
                int sub = src[base + clamp(i - r, len) * stride];
                sa += (add >>> 24) - (sub >>> 24);
                sr += ((add >> 16) & 0xFF) - ((sub >> 16) & 0xFF);
                sg += ((add >> 8) & 0xFF) - ((sub >> 8) & 0xFF);
                sb += (add & 0xFF) - (sub & 0xFF);
            }
        }
    }

    private static int clamp(int i, int len) {
        if (i < 0) return 0;
        if (i >= len) return len - 1;
        return i;
    }
}
