package com.nova.browser;

public final class TextMatch {
    private TextMatch() {}

    private static boolean ignored(char c) {
        return (c >= '\u064B' && c <= '\u065F') || c == '\u0670' || c == '\u0640';
    }

    private static char fold(char c) {
        if (c == '\u0623' || c == '\u0625' || c == '\u0622' || c == '\u0671') return '\u0627';
        if (c == '\u0649') return '\u064A';
        if (c == '\u0629') return '\u0647';
        return Character.toLowerCase(c);
    }

    private static String normalize(String s, int[] map) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (ignored(c)) continue;
            if (map != null) map[out.length()] = i;
            out.append(fold(c));
        }
        return out.toString();
    }

    public static int[] find(String text, String query) {
        if (text == null || query == null) return null;
        String q = normalize(query.trim(), null);
        if (q.isEmpty()) return null;
        int[] map = new int[text.length()];
        int at = normalize(text, map).indexOf(q);
        if (at < 0) return null;
        int end = map[at + q.length() - 1] + 1;
        while (end < text.length() && ignored(text.charAt(end))) end++;
        return new int[] { map[at], end };
    }
}
