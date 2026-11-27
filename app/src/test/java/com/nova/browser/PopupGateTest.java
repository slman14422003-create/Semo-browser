package com.nova.browser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class PopupGateTest {
    private final AtomicLong now = new AtomicLong(5_000);
    private final PopupGate gate = new PopupGate(now::get);

    @Test
    public void noTouchMeansBlocked() {
        assertFalse(gate.consume());
    }

    @Test
    public void touchWithinWindowAllowsOnceThenConsumed() {
        gate.touch();
        now.addAndGet(1_400);
        assertTrue(gate.consume());
        assertFalse(gate.consume());
    }

    @Test
    public void boundaryIsInclusive() {
        gate.touch();
        now.addAndGet(PopupGate.WINDOW_MS);
        assertTrue(gate.consume());
    }

    @Test
    public void staleTouchBlocksAndIsDropped() {
        gate.touch();
        now.addAndGet(PopupGate.WINDOW_MS + 1);
        assertFalse(gate.consume());
        now.addAndGet(-10);
        assertFalse(gate.consume());
    }

    @Test
    public void newTouchRefreshesWindow() {
        gate.touch();
        now.addAndGet(1_000);
        gate.touch();
        now.addAndGet(1_000);
        assertTrue(gate.consume());
    }

    @Test
    public void onlyOnePendingAtATime() {
        assertTrue(gate.beginPending());
        assertFalse(gate.beginPending());
        gate.endPending();
        assertTrue(gate.beginPending());
    }
}
