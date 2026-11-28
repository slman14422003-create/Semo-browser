package com.nova.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BoxBlurTest {
    @Test
    public void uniformImageStaysUniform() {
        int[] px = new int[8 * 6];
        java.util.Arrays.fill(px, 0xFF336699);
        BoxBlur.apply(px, 8, 6, 2, 2);
        for (int p : px) assertEquals(0xFF336699, p);
    }

    @Test
    public void spreadsASinglePoint() {
        int[] px = new int[9 * 9];
        px[4 * 9 + 4] = 0xFFFFFFFF;
        BoxBlur.apply(px, 9, 9, 1, 1);
        int centre = px[4 * 9 + 4] & 0xFF;
        int side = px[4 * 9 + 5] & 0xFF;
        assertTrue(side > 0);
        assertTrue(centre < 0xFF);
        assertTrue(centre >= side);
    }

    @Test
    public void ignoresBadArguments() {
        int[] px = {1, 2, 3, 4};
        BoxBlur.apply(px, 2, 2, 0, 1);
        BoxBlur.apply(null, 2, 2, 1, 1);
        BoxBlur.apply(px, 4, 4, 1, 1);
        assertEquals(1, px[0]);
        assertEquals(4, px[3]);
    }
}
