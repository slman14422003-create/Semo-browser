package com.nova.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MagnetPolicyTest {
    @Test
    public void stuckFollowsAShareThenDetachesWithOneEvent() {
        MagnetPolicy m = new MagnetPolicy(72f, 56f, 0.5f);
        m.start();
        assertEquals(0.5f, m.factor(), 0f);
        assertEquals(MagnetPolicy.NONE, m.move(40f));
        assertEquals(MagnetPolicy.DETACHED, m.move(72f));
        assertEquals(1f, m.factor(), 0f);
        assertEquals(MagnetPolicy.NONE, m.move(90f));
    }

    @Test
    public void attachesAgainOnlyBelowAttachLine() {
        MagnetPolicy m = new MagnetPolicy(72f, 56f, 0.5f);
        m.move(80f);
        assertEquals(MagnetPolicy.NONE, m.move(60f));
        assertTrue(m.isDetached());
        assertEquals(MagnetPolicy.ATTACHED, m.move(-55f));
        assertFalse(m.isDetached());
    }

    @Test
    public void negativeDistanceDetachesToo() {
        MagnetPolicy m = new MagnetPolicy(72f, 56f, 0.5f);
        assertEquals(MagnetPolicy.DETACHED, m.move(-100f));
    }

    @Test
    public void startReArms() {
        MagnetPolicy m = new MagnetPolicy(72f, 56f, 0.5f);
        m.move(100f);
        m.start();
        assertFalse(m.isDetached());
    }

    @Test
    public void snapPullIsZeroOutsideAndStrongestAtCentre() {
        assertEquals(0f, MagnetPolicy.snapPull(50f, 40f, 0.5f), 0f);
        assertEquals(0.5f, MagnetPolicy.snapPull(0f, 40f, 0.5f), 1e-6f);
        assertTrue(MagnetPolicy.snapPull(10f, 40f, 0.5f) > MagnetPolicy.snapPull(30f, 40f, 0.5f));
    }

    @Test
    public void flingNeedsSpeedTravelAndSameDirection() {
        assertTrue(MagnetPolicy.flingCloses(900f, 700f, 30f, 24f));
        assertFalse(MagnetPolicy.flingCloses(600f, 700f, 30f, 24f));
        assertFalse(MagnetPolicy.flingCloses(900f, 700f, 10f, 24f));
        assertFalse(MagnetPolicy.flingCloses(-900f, 700f, 30f, 24f));
    }

    @Test
    public void hoverTargetUsesInnerZoneAndSkipsSelf() {
        float[] l = {0f, 100f, 200f};
        float[] t = {0f, 0f, 0f};
        float[] w = {100f, 100f, 100f};
        float[] h = {100f, 100f, 100f};
        assertEquals(1, MagnetPolicy.hoverTarget(150f, 50f, l, t, w, h, 0, 0.2f));
        assertEquals(-1, MagnetPolicy.hoverTarget(105f, 50f, l, t, w, h, 0, 0.2f));
        assertEquals(-1, MagnetPolicy.hoverTarget(150f, 50f, l, t, w, h, 1, 0.2f));
    }
}
