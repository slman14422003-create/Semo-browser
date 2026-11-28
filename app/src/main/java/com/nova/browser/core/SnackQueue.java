package com.nova.browser;

import java.util.ArrayDeque;
import java.util.Iterator;

public final class SnackQueue<T> {
    private static final class Item<T> {
        final String key;
        final T value;
        final long at;

        Item(String key, T value, long at) {
            this.key = key;
            this.value = value;
            this.at = at;
        }
    }

    private final ArrayDeque<Item<T>> items = new ArrayDeque<>();
    private final int capacity;
    private final long ttlMs;

    public SnackQueue(int capacity, long ttlMs) {
        this.capacity = capacity;
        this.ttlMs = ttlMs;
    }

    public void offer(String key, T value, long now) {
        for (Iterator<Item<T>> i = items.iterator(); i.hasNext(); ) {
            if (i.next().key.equals(key)) i.remove();
        }
        items.addLast(new Item<>(key, value, now));
        while (items.size() > capacity) items.removeFirst();
    }

    public T poll(long now) {
        while (!items.isEmpty()) {
            Item<T> item = items.removeFirst();
            if (now - item.at <= ttlMs) return item.value;
        }
        return null;
    }
}
