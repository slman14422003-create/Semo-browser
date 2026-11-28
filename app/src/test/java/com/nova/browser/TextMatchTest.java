package com.nova.browser;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TextMatchTest {
    @Test public void ignoresCase() {
        assertArrayEquals(new int[] { 7, 11 }, TextMatch.find("Search font size", "FONT"));
    }

    @Test public void foldsArabicAndSkipsMarks() {
        assertArrayEquals(new int[] { 0, 4 }, TextMatch.find("\u0623\u064E\u0645\u0627\u0646", "\u0627\u0645\u0627"));
    }

    @Test public void noMatch() {
        assertNull(TextMatch.find("abc", "z"));
        assertNull(TextMatch.find("abc", "  "));
    }
}
