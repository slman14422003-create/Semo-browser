package com.nova.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BarHidePolicyTest {
    private BarHidePolicy policy() {
        return new BarHidePolicy(56, 24, 8, 350L);
    }

    @Test
    public void hidesAfterSlopOfDownwardTravel() {
        BarHidePolicy p = policy();
        assertEquals(BarHidePolicy.NONE, p.onScroll(200, 30, 0L));
        assertEquals(BarHidePolicy.NONE, p.onScroll(226, 26, 16L));
        assertEquals(BarHidePolicy.HIDE, p.onScroll(260, 34, 32L));
        assertTrue(p.hidden());
    }

    @Test
    public void showsAfterSmallerUpwardTravel() {
        BarHidePolicy p = policy();
        p.onScroll(300, 60, 0L);
        assertTrue(p.hidden());
        assertEquals(BarHidePolicy.NONE, p.onScroll(290, -10, 400L));
        assertEquals(BarHidePolicy.SHOW, p.onScroll(270, -20, 420L));
        assertFalse(p.hidden());
    }

    @Test
    public void lockIgnoresScrollRightAfterToggle() {
        BarHidePolicy p = policy();
        assertEquals(BarHidePolicy.HIDE, p.onScroll(300, 60, 0L));
        assertEquals(BarHidePolicy.NONE, p.onScroll(250, -50, 100L));
        assertEquals(BarHidePolicy.NONE, p.onScroll(200, -50, 200L));
        assertTrue(p.hidden());
    }

    @Test
    public void jitterNeverToggles() {
        BarHidePolicy p = policy();
        long t = 0L;
        for (int i = 0; i < 200; i++) {
            t += 16L;
            assertEquals(BarHidePolicy.NONE, p.onScroll(300, i % 2 == 0 ? 6 : -6, t));
        }
        assertFalse(p.hidden());
    }

    @Test
    public void directionChangeRestartsTravel() {
        BarHidePolicy p = policy();
        p.onScroll(300, 40, 0L);
        p.onScroll(290, -5, 16L);
        assertEquals(BarHidePolicy.NONE, p.onScroll(310, 40, 32L));
        assertFalse(p.hidden());
    }

    @Test
    public void topShowsAtOnceEvenInsideLock() {
        BarHidePolicy p = policy();
        assertEquals(BarHidePolicy.HIDE, p.onScroll(300, 60, 0L));
        assertEquals(BarHidePolicy.SHOW, p.onScroll(4, -296, 50L));
        assertFalse(p.hidden());
    }

    @Test
    public void resetShowsBar() {
        BarHidePolicy p = policy();
        p.onScroll(300, 60, 0L);
        p.reset();
        assertFalse(p.hidden());
        assertEquals(BarHidePolicy.HIDE, p.onScroll(300, 60, 0L));
    }
}
