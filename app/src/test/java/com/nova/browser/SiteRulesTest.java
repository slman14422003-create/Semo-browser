package com.nova.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SiteRulesTest {
    @Test
    public void trustedHostsAreRelaxed() {
        String[] hosts = {
            "console.firebase.google.com", "my-app.web.app", "my-app.firebaseapp.com", "dash.cloudflare.com",
            "site.pages.dev", "api.workers.dev", "github.com", "gist.github.com", "app.supabase.com", "www.paypal.com"
        };
        for (String h : hosts) assertTrue(h, SiteRules.INSTANCE.relaxed(h));
    }

    @Test
    public void unknownHostsAreNotRelaxed() {
        String[] hosts = {"example.org", "news.example.com", "notgithub.com", "evilcloudflare.com.attacker.net"};
        for (String h : hosts) assertFalse(h, SiteRules.INSTANCE.relaxed(h));
    }

    @Test
    public void authHostsAreRecognised() {
        assertTrue(SiteRules.INSTANCE.authHost("my-app.firebaseapp.com"));
        assertTrue(SiteRules.INSTANCE.authHost("challenges.cloudflare.com"));
        assertTrue(SiteRules.INSTANCE.authHost("accounts.google.com"));
        assertFalse(SiteRules.INSTANCE.authHost("example.org"));
        assertFalse(SiteRules.INSTANCE.authHost(null));
    }

    @Test
    public void sslExceptionIsPerHostAndAskedOnce() {
        assertFalse(SiteRules.INSTANCE.sslAllowed("intranet.test"));
        assertTrue(SiteRules.INSTANCE.shouldAskSsl("intranet.test"));
        assertFalse(SiteRules.INSTANCE.shouldAskSsl("intranet.test"));
        SiteRules.INSTANCE.allowSsl("intranet.test");
        assertTrue(SiteRules.INSTANCE.sslAllowed("INTRANET.test"));
        assertFalse(SiteRules.INSTANCE.sslAllowed("other.test"));
    }

    @Test
    public void jsonListIsWellFormed() {
        String j = SiteRules.INSTANCE.json();
        assertTrue(j.startsWith("[\"") && j.endsWith("\"]"));
        assertEquals(-1, j.indexOf("''"));
    }
}
