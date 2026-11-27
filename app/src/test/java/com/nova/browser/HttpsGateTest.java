package com.nova.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class HttpsGateTest {
    private final AtomicInteger n = new AtomicInteger();
    private final HttpsGate gate = new HttpsGate(() -> "t" + n.incrementAndGet());

    @Test
    public void tokenIsSingleUse() {
        String t = gate.issue("http://a.com/x");
        assertEquals("http://a.com/x", gate.redeem(t));
        assertNull(gate.redeem(t));
    }

    @Test
    public void unknownOrNullTokenRejected() {
        assertNull(gate.redeem("nope"));
        assertNull(gate.redeem(null));
    }

    @Test
    public void oldTokensExpireBeyondCap() {
        String first = gate.issue("http://first.com");
        for (int i = 0; i < 8; i++) gate.issue("http://x" + i + ".com");
        assertNull(gate.redeem(first));
    }

    @Test
    public void tokenParsedOnlyFromContinueUri() {
        assertEquals("abc", HttpsGate.tokenOf(HttpsGate.CONTINUE_PREFIX + "abc"));
        assertNull(HttpsGate.tokenOf("semo-http://other?t=abc"));
        assertNull(HttpsGate.tokenOf("https://a.com"));
        assertNull(HttpsGate.tokenOf(null));
    }

    @Test
    public void allowedHostIsInsecureOnlyForThatHost() {
        gate.allow("a.com");
        assertTrue(gate.insecureFor(true, "a.com"));
        assertTrue(gate.insecureFor(true, "a.com"));
        assertFalse(gate.insecureFor(true, "b.com"));
        assertFalse(gate.insecureFor(true, "a.com"));
        assertNull(gate.allowedHost());
    }

    @Test
    public void httpsFirstOffAllowsEverythingAndClearsException() {
        gate.allow("a.com");
        assertTrue(gate.insecureFor(false, "z.com"));
        assertNull(gate.allowedHost());
        assertFalse(gate.insecureFor(true, "a.com"));
    }

    @Test
    public void defaultTokensAreRandomHex() {
        HttpsGate g = new HttpsGate();
        String a = g.issue("http://a.com");
        String b = g.issue("http://a.com");
        assertEquals(32, a.length());
        assertFalse(a.equals(b));
    }
}
