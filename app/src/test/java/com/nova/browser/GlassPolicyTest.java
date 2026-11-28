package com.nova.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GlassPolicyTest {
    @Test
    public void honoursPickWhenNothingForcesFlat() {
        assertEquals(GlassPolicy.FULL, GlassPolicy.effective(GlassPolicy.FULL, false, true, 0, false));
        assertEquals(GlassPolicy.LITE, GlassPolicy.effective(GlassPolicy.LITE, false, true, 0, false));
        assertEquals(GlassPolicy.FLAT, GlassPolicy.effective(GlassPolicy.FLAT, false, true, 0, false));
    }

    @Test
    public void dropsToFlatOnAdaptiveLevel() {
        assertEquals(GlassPolicy.FLAT, GlassPolicy.effective(GlassPolicy.FULL, false, true, 1, false));
        assertEquals(GlassPolicy.FLAT, GlassPolicy.effective(GlassPolicy.FULL, false, true, 2, false));
    }

    @Test
    public void dropsToFlatOnBatterySaver() {
        assertEquals(GlassPolicy.FLAT, GlassPolicy.effective(GlassPolicy.FULL, false, true, 0, true));
    }

    @Test
    public void dropsToFlatWhenAnimationsOff() {
        assertEquals(GlassPolicy.FLAT, GlassPolicy.effective(GlassPolicy.LITE, false, false, 0, false));
    }

    @Test
    public void unsetPickFollowsDevice() {
        assertEquals(GlassPolicy.LITE, GlassPolicy.effective(GlassPolicy.UNSET, false, true, 0, false));
        assertEquals(GlassPolicy.FLAT, GlassPolicy.effective(GlassPolicy.UNSET, true, true, 0, false));
    }

    @Test
    public void clampsOutOfRangePicks() {
        assertEquals(GlassPolicy.FULL, GlassPolicy.clamp(-5));
        assertEquals(GlassPolicy.FLAT, GlassPolicy.clamp(9));
    }

    @Test
    public void forcedFlatReportsEachCause() {
        assertTrue(GlassPolicy.forcedFlat(false, 0, false));
        assertTrue(GlassPolicy.forcedFlat(true, 1, false));
        assertTrue(GlassPolicy.forcedFlat(true, 0, true));
        assertFalse(GlassPolicy.forcedFlat(true, 0, false));
    }

    @Test
    public void lensRules() {
        assertEquals(5, GlassPolicy.lensTaps(GlassPolicy.FULL));
        assertEquals(3, GlassPolicy.lensTaps(GlassPolicy.LITE));
        assertEquals(0, GlassPolicy.lensTaps(GlassPolicy.FLAT));
        assertTrue(GlassPolicy.lensAllowed(GlassPolicy.FULL, 300, 120, 1080, 2400));
        assertFalse(GlassPolicy.lensAllowed(GlassPolicy.FULL, 1080, 2000, 1080, 2400));
        assertFalse(GlassPolicy.lensAllowed(GlassPolicy.FLAT, 300, 120, 1080, 2400));
    }

    @Test
    public void glowAndGrainOnlyAtFull() {
        assertTrue(GlassPolicy.glowHead(GlassPolicy.FULL));
        assertFalse(GlassPolicy.glowHead(GlassPolicy.LITE));
        assertTrue(GlassPolicy.grain(GlassPolicy.FULL));
        assertFalse(GlassPolicy.grain(GlassPolicy.FLAT));
    }

    @Test
    public void progressFillStaysVisibleAndBounded() {
        assertEquals(0.05f, GlassPolicy.progressFill(0f), 0f);
        assertEquals(0.5f, GlassPolicy.progressFill(0.5f), 0f);
        assertEquals(1f, GlassPolicy.progressFill(3f), 0f);
    }
}
