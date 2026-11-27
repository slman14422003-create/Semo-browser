package com.nova.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdateTrustTest {
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String BASE = "https://github.com/o/r/releases/download/v1/a.apk";

    @Test
    public void trustedHostsOverHttps() {
        assertTrue(UpdateTrust.trusted(BASE));
        assertTrue(UpdateTrust.trusted("https://api.github.com/repos/o/r/releases/latest"));
        assertTrue(UpdateTrust.trusted("https://objects.githubusercontent.com/x"));
        assertTrue(UpdateTrust.trusted("https://release-assets.githubusercontent.com/x?y=1"));
        assertTrue(UpdateTrust.trusted("https://github-releases.githubusercontent.com/x"));
        assertTrue(UpdateTrust.trusted("HTTPS://GitHub.com/x"));
        assertTrue(UpdateTrust.trusted("https://github.com:443/x"));
    }

    @Test
    public void untrustedHostsRejected() {
        assertFalse(UpdateTrust.trusted("https://raw.githubusercontent.com/o/r/main/a.apk"));
        assertFalse(UpdateTrust.trusted("https://user-images.githubusercontent.com/x"));
        assertFalse(UpdateTrust.trusted("https://gist.githubusercontent.com/x"));
        assertFalse(UpdateTrust.trusted("https://github.com.evil.com/x"));
        assertFalse(UpdateTrust.trusted("https://evilgithub.com/x"));
        assertFalse(UpdateTrust.trusted("https://github.com./x"));
        assertFalse(UpdateTrust.trusted("https://sub.github.com/x"));
    }

    @Test
    public void schemeUserInfoAndPortRejected() {
        assertFalse(UpdateTrust.trusted("http://github.com/x"));
        assertFalse(UpdateTrust.trusted("ftp://github.com/x"));
        assertFalse(UpdateTrust.trusted("https://github.com@evil.com/x"));
        assertFalse(UpdateTrust.trusted("https://user@github.com/x"));
        assertFalse(UpdateTrust.trusted("https://github.com:8443/x"));
    }

    @Test
    public void malformedRejected() {
        assertFalse(UpdateTrust.trusted(null));
        assertFalse(UpdateTrust.trusted(""));
        assertFalse(UpdateTrust.trusted("   "));
        assertFalse(UpdateTrust.trusted("github.com/x"));
        assertFalse(UpdateTrust.trusted("https://github.com/a b"));
        assertFalse(UpdateTrust.trusted("https:///x"));
    }

    @Test
    public void redirectTargetsAreValidated() {
        assertEquals("https://objects.githubusercontent.com/z", UpdateTrust.next(BASE, "https://objects.githubusercontent.com/z"));
        assertEquals("https://github.com/a/b?x=1", UpdateTrust.next(BASE, "/a/b?x=1"));
        assertNull(UpdateTrust.next(BASE, "https://evil.com/z"));
        assertNull(UpdateTrust.next(BASE, "//evil.com/z"));
        assertNull(UpdateTrust.next(BASE, "http://github.com/z"));
        assertNull(UpdateTrust.next(BASE, "https://github.com:80/z"));
        assertNull(UpdateTrust.next(BASE, null));
        assertNull(UpdateTrust.next(BASE, ""));
    }

    @Test
    public void hopLimitIsFive() {
        assertEquals(5, UpdateTrust.MAX_HOPS);
    }

    @Test
    public void sha256Parsing() {
        assertEquals(HASH, UpdateTrust.sha256Of(HASH));
        assertEquals(HASH, UpdateTrust.sha256Of("sha256:" + HASH));
        assertEquals(HASH, UpdateTrust.sha256Of("SHA256:" + HASH.toUpperCase()));
        assertEquals(HASH, UpdateTrust.sha256Of(HASH + "  app-release.apk\n"));
        assertEquals(HASH, UpdateTrust.sha256Of("\n  " + HASH.toUpperCase() + "\n"));
        assertEquals("", UpdateTrust.sha256Of(null));
        assertEquals("", UpdateTrust.sha256Of(""));
        assertEquals("", UpdateTrust.sha256Of("sha256:abc"));
        assertEquals("", UpdateTrust.sha256Of("sha1:0123456789abcdef0123456789abcdef01234567"));
        assertEquals("", UpdateTrust.sha256Of(HASH + "00"));
        assertEquals("", UpdateTrust.sha256Of("zz" + HASH.substring(2)));
    }
}
