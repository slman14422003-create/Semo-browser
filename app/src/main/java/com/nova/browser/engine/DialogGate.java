package com.nova.browser;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.LongSupplier;

public final class DialogGate {
    public enum Verdict {
        ALLOW, TRIPPED, LIMITED, SUPPRESSED;

        public boolean allowed() {
            return this == ALLOW;
        }
    }

    private static final class Entry {
        int n;
        long t0;
        int shown;
        boolean suppressed;
    }

    private static final int MAX_KEYS = 300;
    private static final int OFFER_FROM = 2;

    private final LongSupplier clock;
    private final long windowMs;
    private final int max;
    private final Map<String, Entry> map = new HashMap<>();

    public DialogGate(LongSupplier clock, long windowMs, int max) {
        this.clock = clock;
        this.windowMs = windowMs;
        this.max = max;
    }

    public synchronized Verdict check(String key, boolean limit) {
        Entry e = entry(key);
        if (e.suppressed) return Verdict.SUPPRESSED;
        if (limit) {
            long now = clock.getAsLong();
            if (e.n == 0 || now - e.t0 > windowMs) {
                e.n = 0;
                e.t0 = now;
            }
            e.n++;
            if (e.n > max) return e.n == max + 1 ? Verdict.TRIPPED : Verdict.LIMITED;
        }
        e.shown++;
        return Verdict.ALLOW;
    }

    public synchronized boolean offersSuppress(String key) {
        Entry e = map.get(key);
        return e != null && !e.suppressed && e.shown >= OFFER_FROM;
    }

    public synchronized void suppress(String key) {
        entry(key).suppressed = true;
    }

    public synchronized boolean isSuppressed(String key) {
        Entry e = map.get(key);
        return e != null && e.suppressed;
    }

    public synchronized void reset(String key) {
        Entry e = map.get(key);
        if (e == null) return;
        e.suppressed = false;
        e.shown = 0;
    }

    private Entry entry(String key) {
        Entry e = map.get(key);
        if (e == null) {
            if (map.size() >= MAX_KEYS) prune();
            e = new Entry();
            map.put(key, e);
        }
        return e;
    }

    private void prune() {
        Iterator<Map.Entry<String, Entry>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            if (!it.next().getValue().suppressed) it.remove();
        }
    }
}
