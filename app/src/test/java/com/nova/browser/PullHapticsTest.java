package com.nova.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PullHapticsTest {
    @Test
    public void clicksOnceWhenPassingTheLine() {
        PullHaptics p = new PullHaptics(120L);
        assertEquals(PullHaptics.NONE, p.onPull(0.5f, 0L));
        assertEquals(PullHaptics.ENGAGED, p.onPull(1.05f, 100L));
        assertEquals(PullHaptics.NONE, p.onPull(1.3f, 116L));
        assertTrue(p.engaged());
    }

    @Test
    public void clicksWhenComingBackUnder() {
        PullHaptics p = new PullHaptics(120L);
        p.onPull(1.2f, 0L);
        assertEquals(PullHaptics.RELEASED, p.onPull(0.8f, 500L));
        assertFalse(p.engaged());
    }

    @Test
    public void tremblingOnTheLineClicksAtMostOncePerGap() {
        PullHaptics p = new PullHaptics(120L);
        int clicks = 0;
        long t = 1000L;
        for (int i = 0; i < 20; i++) {
            t += 16L;
            if (p.onPull(i % 2 == 0 ? 1.02f : 0.98f, t) != PullHaptics.NONE) clicks++;
        }
        assertTrue(clicks <= 3);
    }

    @Test
    public void resetDisengages() {
        PullHaptics p = new PullHaptics(120L);
        p.onPull(1.5f, 0L);
        p.reset();
        assertFalse(p.engaged());
        assertEquals(PullHaptics.ENGAGED, p.onPull(1.5f, 1000L));
    }
}
