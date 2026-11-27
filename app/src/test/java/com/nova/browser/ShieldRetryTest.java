package com.nova.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ShieldRetryTest {
    @Test
    public void backoffThenGivesUp() {
        assertEquals(Long.valueOf(1000), ShieldRetry.delayMs(0));
        assertEquals(Long.valueOf(8000), ShieldRetry.delayMs(2));
        assertNull(ShieldRetry.delayMs(3));
    }
}
