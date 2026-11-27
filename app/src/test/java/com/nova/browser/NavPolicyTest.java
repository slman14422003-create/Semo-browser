package com.nova.browser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NavPolicyTest {
    @Test
    public void pageCannotNavigateToInternalSchemes() {
        assertTrue(NavPolicy.blocksPageNavigation("data", false));
        assertTrue(NavPolicy.blocksPageNavigation("RESOURCE", false));
        assertTrue(NavPolicy.blocksPageNavigation("moz-extension", false));
    }

    @Test
    public void appInitiatedAndWebSchemesAllowed() {
        assertFalse(NavPolicy.blocksPageNavigation("data", true));
        assertFalse(NavPolicy.blocksPageNavigation("https", false));
        assertFalse(NavPolicy.blocksPageNavigation(null, false));
    }

    @Test
    public void rewriteOnlyForDirectCurrentTabLoads() {
        assertTrue(NavPolicy.mayRewrite(true, true));
        assertFalse(NavPolicy.mayRewrite(false, true));
        assertFalse(NavPolicy.mayRewrite(true, false));
    }

    @Test
    public void hintOnlyForDirectCurrentNonPopupLoads() {
        assertTrue(NavPolicy.mayInjectHint(true, true, false));
        assertFalse(NavPolicy.mayInjectHint(false, true, false));
        assertFalse(NavPolicy.mayInjectHint(true, false, false));
        assertFalse(NavPolicy.mayInjectHint(true, true, true));
    }
}
