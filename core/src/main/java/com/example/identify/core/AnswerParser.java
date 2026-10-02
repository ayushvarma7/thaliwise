package com.example.identify.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the model's three-line answer (Label, Cuisine, Description). Small models drift: they sometimes
 * copy instruction words into the label (", at most 6 words"), return template words ("dish name"), or
 * answer in free text ("The food in this photo is ..."). Labels are cleaned of all three, and without a
 * Label line the first sentence of the answer becomes the label.
 */
public final class AnswerParser {
    private AnswerParser() {}

    public static final int MAX_LABEL_CHARS = 60;

    /** Instruction words copied from the prompt at the end of a label: ", at most 6 words", "(max 6 words)". */
    private static final Pattern ECHO = Pattern.compile(
            "[\\s,;:(\\[-]*(at most|no more than|up to|maximum|max)\\s+\\d+\\s+words?[\\s)\\].]*$",
            Pattern.CASE_INSENSITIVE);

    /** Openings of free-text answers: "The food in this photo is", "This image shows a", "It is". */
    private static final Pattern LEAD_IN = Pattern.compile(
            "^(the\\s+(main\\s+)?(food|dish|item|drink|snack)s?\\s+(in|shown in|on)\\s+(this|the)\\s+"
                    + "(photo|image|picture)\\s+(is|are|appears to be|looks like)"
                    + "|this\\s+(photo|image|picture)\\s+(shows|contains|is of|is)"
                    + "|this\\s+is|these\\s+are|it\\s+is|it's|there\\s+(is|are))\\s+((a|an|some)\\s+)?",
            Pattern.CASE_INSENSITIVE);

    /** A sentence end: '.', '!', or '?' followed by a space or the end of the text. */
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?](\\s|$)");

    /** Template words a model may return unchanged. */
    private static final Set<String> PLACEHOLDERS = new HashSet<>(Arrays.asList(
            "dish name", "cuisine", "one cuisine", "one sentence", "label", "description"));

    public static final class ParsedAnswer {
        public final String label;
        public final String description;
        /** The model's "Cuisine:" line, cleaned; empty when it gave none. */
        public final String cuisine;

        public ParsedAnswer(String label, String description) {
            this(label, description, "");
        }

        public ParsedAnswer(String label, String description, String cuisine) {
            this.label = label;
            this.description = description;
            this.cuisine = cuisine;
        }
    }

    public static ParsedAnswer parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new ParsedAnswer("Unknown", "");
        String label = null;
        String description = null;
        String cuisine = null;
        List<String> other = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            String l = line.replace("*", "").trim();
            if (l.isEmpty()) continue;
            String lower = l.toLowerCase(Locale.ROOT);
            if (label == null && lower.startsWith("label:")) {
                label = l.substring(6).trim();
            } else if (cuisine == null && lower.startsWith("cuisine:")) {
                cuisine = l.substring(8).trim();
            } else if (description == null && lower.startsWith("description:")) {
                description = l.substring(12).trim();
            } else {
                other.add(l);
            }
        }
        if (label == null || label.isEmpty()) {
            // No Label line: the first sentence is the label, the rest of that line starts the description.
            label = "";
            if (!other.isEmpty()) {
                String first = other.remove(0);
                Matcher end = SENTENCE_END.matcher(first);
                if (end.find()) {
                    String rest = first.substring(end.end()).trim();
                    first = first.substring(0, end.start());
                    if (!rest.isEmpty()) other.add(0, rest);
                }
                label = first;
            }
        }
        if (description == null) description = String.join(" ", other).trim();
        label = cleanLabel(label);
        if (label.isEmpty()) label = "Unknown";
        return new ParsedAnswer(label, description, cuisine == null ? "" : cleanLabel(cuisine));
    }

    /**
     * A label without surrounding quotes, a final period or comma, template brackets and words, free-text
     * openings, or copied instruction words; at most MAX_LABEL_CHARS. Also used for labels saved earlier.
     */
    public static String cleanLabel(String s) {
        if (s == null) return "";
        String t = s.replace('<', ' ').replace('>', ' ').replaceAll("\\s+", " ").trim();
        t = stripEnds(t);
        t = LEAD_IN.matcher(t).replaceFirst("");
        t = ECHO.matcher(t).replaceFirst("");
        t = stripEnds(t);
        if (PLACEHOLDERS.contains(t.toLowerCase(Locale.ROOT))) return "";
        if (t.length() > MAX_LABEL_CHARS) t = t.substring(0, MAX_LABEL_CHARS).trim();
        return t;
    }

    private static String stripEnds(String s) {
        String t = s.trim();
        while (!t.isEmpty() && (t.startsWith("\"") || t.startsWith("'"))) t = t.substring(1).trim();
        while (!t.isEmpty() && (t.endsWith("\"") || t.endsWith("'") || t.endsWith(".") || t.endsWith(","))) {
            t = t.substring(0, t.length() - 1).trim();
        }
        return t;
    }
}
