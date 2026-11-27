package com.nova.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ColorPickTest {
    @Test
    public void parsesHexForms() {
        assertEquals(0xFF3B30, ColorPick.parse("#ff3b30"));
        assertEquals(0xFF3B30, ColorPick.parse("FF3B30"));
        assertEquals(0xAABBCC, ColorPick.parse(" #abc "));
        assertEquals(0x000000, ColorPick.parse("#000"));
    }

    @Test
    public void rejectsInvalid() {
        assertEquals(-1, ColorPick.parse(null));
        assertEquals(-1, ColorPick.parse(""));
        assertEquals(-1, ColorPick.parse("#12"));
        assertEquals(-1, ColorPick.parse("#1234567"));
        assertEquals(-1, ColorPick.parse("#gg0000"));
        assertEquals(-1, ColorPick.parse("#ff3b30ff"));
    }

    @Test
    public void normalizeLowercasesAndExpands() {
        assertEquals("#aabbcc", ColorPick.normalize("#ABC"));
        assertEquals("#007aff", ColorPick.normalize("007AFF"));
        assertNull(ColorPick.normalize("nope"));
    }

    @Test
    public void hexIsPaddedAndMasked() {
        assertEquals("#000001", ColorPick.hex(1));
        assertEquals("#ffffff", ColorPick.hex(0xFFFFFFFF));
    }

    @Test
    public void hsvPrimaries() {
        assertEquals(0xFF0000, ColorPick.fromHsv(0, 1, 1));
        assertEquals(0x00FF00, ColorPick.fromHsv(120, 1, 1));
        assertEquals(0x0000FF, ColorPick.fromHsv(240, 1, 1));
        assertEquals(0xFFFFFF, ColorPick.fromHsv(77, 0, 1));
        assertEquals(0x000000, ColorPick.fromHsv(200, 1, 0));
        assertEquals(0xFF0000, ColorPick.fromHsv(360, 1, 1));
        assertEquals(0x0000FF, ColorPick.fromHsv(-120, 1, 1));
    }

    @Test
    public void toHsvKnownValues() {
        float[] red = ColorPick.toHsv(0xFF0000);
        assertEquals(0f, red[0], 0.001f);
        assertEquals(1f, red[1], 0.001f);
        assertEquals(1f, red[2], 0.001f);
        float[] cyan = ColorPick.toHsv(0x00FFFF);
        assertEquals(180f, cyan[0], 0.001f);
        float[] gray = ColorPick.toHsv(0x808080);
        assertEquals(0f, gray[1], 0.001f);
        assertEquals(128f / 255f, gray[2], 0.001f);
        float[] magenta = ColorPick.toHsv(0xFF00FF);
        assertEquals(300f, magenta[0], 0.001f);
    }

    @Test
    public void roundTripStaysWithinOneStep() {
        for (int r = 0; r < 256; r += 15) {
            for (int g = 0; g < 256; g += 15) {
                for (int b = 0; b < 256; b += 15) {
                    int rgb = (r << 16) | (g << 8) | b;
                    float[] h = ColorPick.toHsv(rgb);
                    int back = ColorPick.fromHsv(h[0], h[1], h[2]);
                    assertTrue(ColorPick.hex(rgb) + " -> " + ColorPick.hex(back), Math.abs(((back >> 16) & 255) - r) <= 1
                        && Math.abs(((back >> 8) & 255) - g) <= 1 && Math.abs((back & 255) - b) <= 1);
                }
            }
        }
    }
}
