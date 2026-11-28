package com.nova.browser;

public final class BarHidePolicy {
    public static final int NONE = 0;
    public static final int HIDE = 1;
    public static final int SHOW = -1;

    private final int hideSlop;
    private final int showSlop;
    private final int top;
    private final long lockMs;
    private boolean hidden;
    private int travel;
    private long lockUntil;

    public BarHidePolicy(int hideSlop, int showSlop, int top, long lockMs) {
        this.hideSlop = hideSlop;
        this.showSlop = showSlop;
        this.top = top;
        this.lockMs = lockMs;
    }

    public boolean hidden() {
        return hidden;
    }

    public void reset() {
        hidden = false;
        travel = 0;
        lockUntil = 0L;
    }

    public int onScroll(int y, int dy, long now) {
        if (y <= top) {
            travel = 0;
            if (hidden) {
                hidden = false;
                lockUntil = now + lockMs;
                return SHOW;
            }
            return NONE;
        }
        if (dy == 0) return NONE;
        if (now < lockUntil) {
            travel = 0;
            return NONE;
        }
        travel = Integer.signum(dy) == Integer.signum(travel) ? travel + dy : dy;
        if (!hidden && travel > hideSlop) {
            hidden = true;
            travel = 0;
            lockUntil = now + lockMs;
            return HIDE;
        }
        if (hidden && travel < -showSlop) {
            hidden = false;
            travel = 0;
            lockUntil = now + lockMs;
            return SHOW;
        }
        return NONE;
    }
}
