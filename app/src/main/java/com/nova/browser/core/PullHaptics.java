package com.nova.browser;

public final class PullHaptics {
    public static final int NONE = 0;
    public static final int ENGAGED = 1;
    public static final int RELEASED = -1;

    private final long gapMs;
    private boolean engaged;
    private long lastClick = Long.MIN_VALUE / 2;

    public PullHaptics(long gapMs) {
        this.gapMs = gapMs;
    }

    public void reset() {
        engaged = false;
    }

    public boolean engaged() {
        return engaged;
    }

    public int onPull(float fraction, long now) {
        boolean next = fraction > 1f;
        if (next == engaged) return NONE;
        engaged = next;
        if (now - lastClick < gapMs) return NONE;
        lastClick = now;
        return next ? ENGAGED : RELEASED;
    }
}
