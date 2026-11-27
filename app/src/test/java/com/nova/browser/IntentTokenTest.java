package com.nova.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.security.SecureRandom;
import org.junit.Test;

public class IntentTokenTest {
    private final SecureRandom rnd = new SecureRandom();

    @Test public void generatesLowerHexOf64Chars() {
        String t = IntentToken.generate(rnd);
        assertEquals(64, t.length());
        assertTrue(IntentToken.wellFormed(t));
    }

    @Test public void generatesDistinctTokens() {
        assertNotEquals(IntentToken.generate(rnd), IntentToken.generate(rnd));
    }

    @Test public void acceptsMatchingToken() {
        String t = IntentToken.generate(rnd);
        assertTrue(IntentToken.matches(t, new String(t)));
    }

    @Test public void rejectsDifferentToken() {
        assertFalse(IntentToken.matches(IntentToken.generate(rnd), IntentToken.generate(rnd)));
    }

    @Test public void rejectsMissingOrEmpty() {
        String t = IntentToken.generate(rnd);
        assertFalse(IntentToken.matches(t, null));
        assertFalse(IntentToken.matches(t, ""));
        assertFalse(IntentToken.matches(null, t));
        assertFalse(IntentToken.matches(null, null));
        assertFalse(IntentToken.matches("", ""));
    }

    @Test public void rejectsMalformedEvenWhenEqual() {
        assertFalse(IntentToken.matches("abc", "abc"));
        String t = IntentToken.generate(rnd);
        assertFalse(IntentToken.matches(t.toUpperCase(), t.toUpperCase()));
        assertFalse(IntentToken.matches(t + " ", t + " "));
        assertFalse(IntentToken.matches(t.substring(1), t.substring(1)));
    }

    @Test public void rejectsSingleCharDifference() {
        String t = IntentToken.generate(rnd);
        char c = t.charAt(10) == 'a' ? 'b' : 'a';
        String u = t.substring(0, 10) + c + t.substring(11);
        assertFalse(IntentToken.matches(t, u));
    }
}
