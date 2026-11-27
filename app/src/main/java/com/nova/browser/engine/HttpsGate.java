package com.nova.browser;

import java.security.SecureRandom;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class HttpsGate {
    public static final String CONTINUE_PREFIX = "semo-http://continue?t=";
    private static final int MAX_TOKENS = 8;

    private final Supplier<String> source;
    private final Map<String, String> tokens = new LinkedHashMap<>();
    private String allowed;

    public HttpsGate() {
        this(HttpsGate::randomToken);
    }

    public HttpsGate(Supplier<String> source) {
        this.source = source;
    }

    private static String randomToken() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }

    public synchronized String issue(String httpUrl) {
        while (tokens.size() >= MAX_TOKENS) {
            Iterator<String> it = tokens.keySet().iterator();
            it.next();
            it.remove();
        }
        String t = source.get();
        tokens.put(t, httpUrl);
        return t;
    }

    public synchronized String redeem(String token) {
        if (token == null) return null;
        return tokens.remove(token);
    }

    public static String tokenOf(String uri) {
        if (uri == null || !uri.startsWith(CONTINUE_PREFIX)) return null;
        return uri.substring(CONTINUE_PREFIX.length());
    }

    public synchronized void allow(String host) {
        allowed = host;
    }

    public synchronized boolean insecureFor(boolean httpsFirst, String host) {
        if (!httpsFirst) {
            allowed = null;
            return true;
        }
        if (allowed != null && allowed.equals(host)) return true;
        allowed = null;
        return false;
    }

    public synchronized String allowedHost() {
        return allowed;
    }
}
