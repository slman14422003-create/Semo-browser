package com.nova.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SnackQueueTest {
    @Test public void dedupesAndCaps() {
        SnackQueue<String> q = new SnackQueue<>(2, 100);
        q.offer("a", "a", 0);
        q.offer("b", "b", 0);
        q.offer("a", "a2", 0);
        q.offer("c", "c", 0);
        assertEquals("a2", q.poll(1));
        assertEquals("c", q.poll(1));
        assertNull(q.poll(1));
    }

    @Test public void dropsStale() {
        SnackQueue<String> q = new SnackQueue<>(2, 100);
        q.offer("a", "a", 0);
        assertNull(q.poll(101));
    }
}
