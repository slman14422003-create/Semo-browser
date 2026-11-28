package com.nova.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AccentToneTest {
    @Test
    public void luminanceExtremes() {
        assertEquals(0f, AccentTone.luminance(0x000000), 0.001f);
        assertEquals(1f, AccentTone.luminance(0xFFFFFF), 0.001f);
    }

    @Test
    public void darkThemeLiftsDarkColours() {
        int out = AccentTone.tone(0x101040, true);
        assertTrue(AccentTone.luminance(out) >= 0.12f);
    }

    @Test
    public void lightThemeDeepensBrightColours() {
        int out = AccentTone.tone(0xFFEE00, false);
        assertTrue(AccentTone.luminance(out) <= 0.34f);
    }

    @Test
    public void goodColoursPassThrough() {
        assertEquals(0x0A84FF, AccentTone.tone(0x0A84FF, true));
        assertEquals(0x007AFF, AccentTone.tone(0x007AFF, false));
    }

    @Test
    public void ignoresAlphaByte() {
        assertEquals(AccentTone.tone(0x0A84FF, true), AccentTone.tone(0xFF0A84FF, true));
    }

    @Test
    public void textOnAccent() {
        assertEquals(0x000000, AccentTone.on(0xFFFF00));
        assertEquals(0xFFFFFF, AccentTone.on(0x0A2A66));
    }

    @Test
    public void blendEndpoints() {
        assertEquals(0x102030, AccentTone.blend(0x102030, 0xFFFFFF, 0f));
        assertEquals(0xFFFFFF, AccentTone.blend(0x102030, 0xFFFFFF, 1f));
        assertEquals(0x808080, AccentTone.blend(0x000000, 0xFFFFFF, 0.5f));
    }

    @Test
    public void gradientEndIsLighter() {
        assertTrue(AccentTone.luminance(AccentTone.gradientEnd(0x0A84FF)) > AccentTone.luminance(0x0A84FF));
    }
}
