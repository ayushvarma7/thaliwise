package com.example.identify.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AnswerParser {
    private AnswerParser() {}

    public static final int MAX_LABEL_CHARS = 60;

    public static final class ParsedAnswer {
        public final String label;
        public final String description;
        public ParsedAnswer(String label, String description) { this.label = label; this.description = description; }
    }

    public static ParsedAnswer parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new ParsedAnswer("Unknown", "");
        String label = null;
        String description = null;
        List<String> other = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            String l = line.replace("*", "").trim();
            if (l.isEmpty()) continue;
            String lower = l.toLowerCase(Locale.ROOT);
            if (label == null && lower.startsWith("label:")) {
                label = l.substring(6).trim();
            } else if (description == null && lower.startsWith("description:")) {
                description = l.substring(12).trim();
            } else {
                other.add(l);
            }
        }
        if (label == null || label.isEmpty()) label = other.isEmpty() ? "" : other.remove(0);
        if (description == null) description = String.join(" ", other).trim();
        label = cleanLabel(label);
        if (label.isEmpty()) label = "Unknown";
        return new ParsedAnswer(label, description);
    }

    static String cleanLabel(String s) {
        String t = s.trim();
        while (!t.isEmpty() && (t.startsWith("\"") || t.startsWith("'"))) t = t.substring(1).trim();
        while (!t.isEmpty() && (t.endsWith("\"") || t.endsWith("'") || t.endsWith("."))) {
            t = t.substring(0, t.length() - 1).trim();
        }
        t = t.replaceAll("\\s+", " ");
        if (t.length() > MAX_LABEL_CHARS) t = t.substring(0, MAX_LABEL_CHARS).trim();
        return t;
    }
}
