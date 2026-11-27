package com.nova.browser;

import java.util.function.LongSupplier;

public final class PopupGate {
    public static final long WINDOW_MS = 1500;
    public static final long PENDING_MS = 10000;

    public static final PopupGate SHARED = new PopupGate(() -> System.nanoTime() / 1000000L);

    private final LongSupplier clock;
    private long last = Long.MIN_VALUE;
    private boolean pending;

    public PopupGate(LongSupplier clock) {
        this.clock = clock;
    }

    public synchronized void touch() {
        last = clock.getAsLong();
    }

    public synchronized boolean consume() {
        if (last == Long.MIN_VALUE) return false;
        boolean fresh = clock.getAsLong() - last <= WINDOW_MS;
        last = Long.MIN_VALUE;
        return fresh;
    }

    public synchronized boolean beginPending() {
        if (pending) return false;
        pending = true;
        return true;
    }

    public synchronized void endPending() {
        pending = false;
    }
}
