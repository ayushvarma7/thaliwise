package com.example.identify.core;

import java.util.Locale;

public final class SearchMatcher {
    private SearchMatcher() {}

    public static boolean matches(String query, String... fields) {
        if (query == null || query.trim().isEmpty()) return true;
        StringBuilder hay = new StringBuilder();
        for (String f : fields) if (f != null) hay.append(f.toLowerCase(Locale.ROOT)).append(' ');
        String h = hay.toString();
        for (String token : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!h.contains(token)) return false;
        }
        return true;
    }
}
