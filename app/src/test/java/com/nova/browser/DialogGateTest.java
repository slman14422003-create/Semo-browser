package com.nova.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class DialogGateTest {
    private final AtomicLong now = new AtomicLong(1000);
    private final DialogGate gate = new DialogGate(now::get, 10_000, 3);

    @Test
    public void allowsUpToLimitThenTripsOnceThenLimits() {
        for (int i = 0; i < 3; i++) assertEquals(DialogGate.Verdict.ALLOW, gate.check("a.com", true));
        assertEquals(DialogGate.Verdict.TRIPPED, gate.check("a.com", true));
        assertEquals(DialogGate.Verdict.LIMITED, gate.check("a.com", true));
        assertEquals(DialogGate.Verdict.LIMITED, gate.check("a.com", true));
    }

    @Test
    public void windowExpiryAllowsAgain() {
        for (int i = 0; i < 4; i++) gate.check("a.com", true);
        now.addAndGet(10_001);
        assertEquals(DialogGate.Verdict.ALLOW, gate.check("a.com", true));
    }

    @Test
    public void hostsAreIndependent() {
        for (int i = 0; i < 4; i++) gate.check("a.com", true);
        assertEquals(DialogGate.Verdict.ALLOW, gate.check("b.com", true));
    }

    @Test
    public void limitDisabledNeverRateLimits() {
        for (int i = 0; i < 20; i++) assertTrue(gate.check("a.com", false).allowed());
    }

    @Test
    public void suppressBlocksEvenWithoutLimit() {
        gate.suppress("a.com");
        assertEquals(DialogGate.Verdict.SUPPRESSED, gate.check("a.com", false));
        assertEquals(DialogGate.Verdict.SUPPRESSED, gate.check("a.com", true));
        assertTrue(gate.isSuppressed("a.com"));
        assertTrue(gate.check("b.com", true).allowed());
    }

    @Test
    public void suppressSurvivesWindowExpiry() {
        gate.suppress("a.com");
        now.addAndGet(60_000);
        assertFalse(gate.check("a.com", true).allowed());
    }

    @Test
    public void resetClearsSuppressionAndCounters() {
        gate.suppress("a.com");
        gate.reset("a.com");
        assertFalse(gate.isSuppressed("a.com"));
        assertTrue(gate.check("a.com", true).allowed());
    }

    @Test
    public void suppressOfferedFromSecondDialog() {
        assertFalse(gate.offersSuppress("a.com"));
        gate.check("a.com", true);
        assertFalse(gate.offersSuppress("a.com"));
        gate.check("a.com", true);
        assertTrue(gate.offersSuppress("a.com"));
        gate.suppress("a.com");
        assertFalse(gate.offersSuppress("a.com"));
    }

    @Test
    public void rejectedDialogsDoNotCountAsShown() {
        for (int i = 0; i < 10; i++) gate.check("a.com", true);
        now.addAndGet(10_001);
        gate.reset("a.com");
        gate.check("a.com", true);
        assertFalse(gate.offersSuppress("a.com"));
    }

    @Test
    public void resetKeepsRateLimitWindow() {
        for (int i = 0; i < 4; i++) gate.check("a.com", true);
        gate.reset("a.com");
        assertEquals(DialogGate.Verdict.LIMITED, gate.check("a.com", true));
        now.addAndGet(10_001);
        assertEquals(DialogGate.Verdict.ALLOW, gate.check("a.com", true));
    }

    @Test
    public void resetOfUnknownKeyIsHarmless() {
        gate.reset("never.seen");
        assertFalse(gate.isSuppressed("never.seen"));
    }

    @Test
    public void pruningKeepsSuppressedHosts() {
        gate.suppress("keep.com");
        for (int i = 0; i < 400; i++) gate.check("h" + i + ".com", true);
        assertTrue(gate.isSuppressed("keep.com"));
        assertEquals(DialogGate.Verdict.SUPPRESSED, gate.check("keep.com", true));
    }
}
