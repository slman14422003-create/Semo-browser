package com.nova.browser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StartLayoutTest {
    @Test public void emptyGivesDefault() {
        assertEquals(StartLayout.DEFAULT, String.join(",", StartLayout.parse("")));
    }

    @Test public void dropsUnknownAndDuplicates() {
        assertEquals("ai,fav,privacy,shortcuts,-recent,-downloads", String.join(",", StartLayout.parse("ai,zzz,ai,fav")));
    }

    @Test public void movesAndToggles() {
        assertEquals("ai,fav,privacy,shortcuts,-recent,-downloads", StartLayout.move(StartLayout.DEFAULT, 0, 1));
        assertEquals("-fav,ai,privacy,shortcuts,-recent,-downloads", StartLayout.toggle(StartLayout.DEFAULT, 0));
        assertEquals(StartLayout.DEFAULT, StartLayout.move(StartLayout.DEFAULT, 0, -1));
    }
}
