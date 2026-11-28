package com.nova.browser;

public final class MagnetPolicy {
    public static final int NONE = 0;
    public static final int DETACHED = 1;
    public static final int ATTACHED = -1;

    private final float detach;
    private final float attach;
    private final float pull;
    private boolean detached;

    public MagnetPolicy(float detachPx, float attachPx, float pull) {
        this.detach = detachPx;
        this.attach = Math.min(attachPx, detachPx);
        this.pull = pull;
    }

    public void start() {
        detached = false;
    }

    public boolean isDetached() {
        return detached;
    }

    public int move(float distance) {
        float d = Math.abs(distance);
        if (!detached && d >= detach) {
            detached = true;
            return DETACHED;
        }
        if (detached && d <= attach) {
            detached = false;
            return ATTACHED;
        }
        return NONE;
    }

    public float factor() {
        return detached ? 1f : pull;
    }

    public static float snapPull(float distance, float radius, float strength) {
        if (radius <= 0f || distance >= radius) return 0f;
        float near = 1f - distance / radius;
        return strength * near * near;
    }

    public static boolean flingCloses(float velocity, float minVelocity, float travel, float minTravel) {
        if (Math.abs(velocity) < minVelocity || Math.abs(travel) < minTravel) return false;
        return Math.signum(velocity) == Math.signum(travel);
    }

    public static int hoverTarget(float cx, float cy, float[] lefts, float[] tops, float[] widths, float[] heights, int self, float inset) {
        for (int i = 0; i < lefts.length; i++) {
            if (i == self) continue;
            float ix = widths[i] * inset;
            float iy = heights[i] * inset;
            if (cx >= lefts[i] + ix && cx <= lefts[i] + widths[i] - ix && cy >= tops[i] + iy && cy <= tops[i] + heights[i] - iy) return i;
        }
        return -1;
    }
}
