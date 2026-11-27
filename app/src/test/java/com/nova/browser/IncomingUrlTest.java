package com.nova.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IncomingUrlTest {
    private static final String BASE = "https://s.test/?q=";

    private static final String[] DANGEROUS = {
        "javascript:alert(1)",
        "JaVaScRiPt:alert(1)//a.b",
        "  javascript:alert(1)",
        "\u0001javascript:alert(1)",
        "java\tscript:alert(1)//a.b",
        "java\nscript:alert(1)//a.b",
        "file:///sdcard/a.html",
        "file:///data/data/com.nova.browser/shared_prefs/vault.xml",
        "intent://x#Intent;scheme=http;package=a.b;end",
        "content://com.nova.browser.provider/file.txt",
        "data:text/html,<script>alert(1)</script>",
        "DATA:text/html;base64,AAAA",
        "vbscript:msgbox(1)",
        "about:blank",
        "blob:https://a.com/1",
        "view-source:https://a.com"
    };

    @Test public void dangerousTextNeverBecomesDangerousUrl() {
        for (String s : DANGEROUS) {
            for (boolean extract : new boolean[] {true, false}) {
                String r = IncomingUrl.resolve(s, BASE, extract);
                assertTrue(s, r != null && IncomingUrl.isWeb(r));
                assertTrue(s, r.startsWith(BASE) || (extract && s.contains(r)));
            }
        }
    }

    @Test public void dangerousSchemesAreSearchedNotLoaded() {
        assertEquals(BASE + "javascript%3Aalert%281%29", IncomingUrl.resolve("javascript:alert(1)", BASE, true));
    }

    @Test public void webUrlsPassThrough() {
        assertEquals("https://a.com/x?y=1", IncomingUrl.resolve("https://a.com/x?y=1", BASE, false));
        assertEquals("http://a.com", IncomingUrl.resolve("http://a.com", BASE, true));
        assertEquals("HTTPS://A.com", IncomingUrl.resolve("HTTPS://A.com", BASE, true));
    }

    @Test public void bareDomainGetsHttps() {
        assertEquals("https://example.com", IncomingUrl.resolve("example.com", BASE, true));
        assertEquals("https://example.com:8080/x", IncomingUrl.resolve("example.com:8080/x", BASE, false));
    }

    @Test public void plainTextIsSearched() {
        assertEquals(BASE + "hello+world", IncomingUrl.resolve("hello world", BASE, false));
        assertTrue(IncomingUrl.resolve("localhost:3000", BASE, false).startsWith(BASE));
    }

    @Test public void sharedTextExtractsFirstUrlOnlyWhenAsked() {
        assertEquals("https://a.com/x", IncomingUrl.resolve("look https://a.com/x now", BASE, true));
        assertTrue(IncomingUrl.resolve("look https://a.com/x now", BASE, false).startsWith(BASE));
    }

    @Test public void sharedTextWithEmbeddedScriptUrlStaysWeb() {
        String r = IncomingUrl.resolve("see javascript:alert(1) and https://a.com", BASE, true);
        assertEquals("https://a.com", r);
    }

    @Test public void emptyAndOversizedAreRejected() {
        assertNull(IncomingUrl.resolve("   ", BASE, true));
        StringBuilder sb = new StringBuilder("https://a.com/");
        while (sb.length() <= IncomingUrl.MAX_LEN) sb.append('a');
        assertNull(IncomingUrl.resolve(sb.toString(), BASE, false));
    }

    @Test public void badSearchBaseYieldsNull() {
        assertNull(IncomingUrl.resolve("hello", "javascript:alert(1)//", false));
    }

    @Test public void isWebRejectsOtherSchemesAndEmptyHosts() {
        assertFalse(IncomingUrl.isWeb("javascript:alert(1)"));
        assertFalse(IncomingUrl.isWeb("https://"));
        assertFalse(IncomingUrl.isWeb("https:///x"));
        assertFalse(IncomingUrl.isWeb("https://a.com/\u0000"));
        assertTrue(IncomingUrl.isWeb("https://a.com"));
    }
}
