package com.nova.browser;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class TabPersistTest {
    private static TabPersist.Entry e(String url, int tag, boolean pinned, boolean priv) {
        return new TabPersist.Entry(url, tag, pinned, priv);
    }

    @Test public void dropsPrivateTabsAndMeta() {
        List<TabPersist.Entry> l = Arrays.asList(
            e("https://a.test", 1, false, false),
            e("https://secret.test", 2, true, true),
            e("https://b.test", 3, true, false));
        TabPersist.Result r = TabPersist.build(l, 0);
        assertEquals("https://a.test\nhttps://b.test", r.getTabs());
        assertEquals("1:0,3:1", r.getMeta());
        assertEquals(0, r.getCur());
    }

    @Test public void remapsCurrentAfterRemovedTabs() {
        List<TabPersist.Entry> l = Arrays.asList(
            e("https://p1.test", 0, false, true),
            e("https://a.test", 0, false, false),
            e("https://p2.test", 0, false, true),
            e("https://b.test", 0, false, false));
        assertEquals(1, TabPersist.build(l, 3).getCur());
        assertEquals(0, TabPersist.build(l, 1).getCur());
    }

    @Test public void privateCurrentFallsBackToPreviousKept() {
        List<TabPersist.Entry> l = Arrays.asList(
            e("https://a.test", 0, false, false),
            e("https://b.test", 0, false, false),
            e("https://p.test", 0, false, true));
        assertEquals(1, TabPersist.build(l, 2).getCur());
    }

    @Test public void privateCurrentAtStartGoesToFirst() {
        List<TabPersist.Entry> l = Arrays.asList(
            e("https://p.test", 0, false, true),
            e("https://a.test", 0, false, false));
        assertEquals(0, TabPersist.build(l, 0).getCur());
    }

    @Test public void onlyPrivateTabsYieldsEmpty() {
        List<TabPersist.Entry> l = Arrays.asList(e("https://p.test", 0, false, true));
        TabPersist.Result r = TabPersist.build(l, 0);
        assertEquals("", r.getTabs());
        assertEquals("", r.getMeta());
        assertEquals(0, r.getCur());
    }

    @Test public void blankUrlBecomesDash() {
        List<TabPersist.Entry> l = Arrays.asList(e("", 0, false, false));
        assertEquals("-", TabPersist.build(l, 0).getTabs());
    }

    @Test public void outOfRangeCurrentIsClamped() {
        List<TabPersist.Entry> l = new ArrayList<>();
        l.add(e("https://a.test", 0, false, false));
        assertEquals(0, TabPersist.build(l, 9).getCur());
        assertEquals(0, TabPersist.build(l, -3).getCur());
    }
}
