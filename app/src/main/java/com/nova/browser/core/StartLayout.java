package com.nova.browser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class StartLayout {
    private StartLayout() {}

    public static final String DEFAULT = "fav,ai,privacy,shortcuts,-recent,-downloads";

    public static String id(String token) {
        return token.startsWith("-") ? token.substring(1) : token;
    }

    public static boolean visible(String token) {
        return !token.startsWith("-");
    }

    public static List<String> parse(String raw) {
        List<String> known = Arrays.asList(DEFAULT.split(","));
        List<String> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        String source = raw == null ? "" : raw;
        for (String part : (source + "," + DEFAULT).split(",")) {
            String token = part.trim();
            String id = id(token);
            boolean exists = false;
            for (String k : known) exists |= id(k).equals(id);
            if (!exists || seen.contains(id)) continue;
            seen.add(id);
            out.add(token);
        }
        return out;
    }

    public static String move(String raw, int index, int delta) {
        List<String> list = parse(raw);
        int to = index + delta;
        if (index >= 0 && index < list.size() && to >= 0 && to < list.size()) Collections.swap(list, index, to);
        return String.join(",", list);
    }

    public static String toggle(String raw, int index) {
        List<String> list = parse(raw);
        if (index >= 0 && index < list.size()) {
            String t = list.get(index);
            list.set(index, visible(t) ? "-" + t : id(t));
        }
        return String.join(",", list);
    }
}
