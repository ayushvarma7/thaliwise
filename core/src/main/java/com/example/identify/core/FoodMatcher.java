package com.example.identify.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Finds the nutrition table rows that a free-text food name (a model label or user text) refers to. */
public final class FoodMatcher {
    private FoodMatcher() {}

    /** Matches scoring below this are dropped. */
    public static final double MIN_SCORE = 0.5;

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "of", "with", "and", "on", "in", "some", "my", "fresh", "homemade",
            "food", "meal", "dish", "plate", "bowl", "slice", "piece", "serving", "cup", "glass",
            "side", "one", "two", "small", "medium", "large"));

    public static final class Match {
        public final FoodItem item;
        public final double score;

        Match(FoodItem item, double score) {
            this.item = item;
            this.score = score;
        }
    }

    /**
     * Up to max foods, best first. Each food scores by its best name or alias; naming the brand adds 0.2,
     * and a branded food loses 0.05 when the text does not name the brand, so "cheeseburger" prefers the
     * generic row while "McDonald's cheeseburger" prefers the branded one.
     */
    public static List<Match> match(String text, List<FoodItem> foods, int max) {
        List<Match> out = new ArrayList<>();
        Set<String> query = new HashSet<>(tokens(text));
        if (query.isEmpty() || max <= 0) return out;
        for (FoodItem f : foods) {
            double best = score(tokens(f.name), query);
            for (String alias : f.aliases) best = Math.max(best, score(tokens(alias), query));
            if (best <= 0) continue;
            List<String> brand = tokens(f.brand);
            if (!brand.isEmpty()) {
                boolean named = false;
                for (String t : brand) {
                    if (query.contains(t)) named = true;
                }
                best += named ? 0.2 : -0.05;
            }
            if (best >= MIN_SCORE) out.add(new Match(f, best));
        }
        out.sort((a, b) -> {
            int c = Double.compare(b.score, a.score);
            return c != 0 ? c : a.item.name.compareTo(b.item.name);
        });
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** 0 when no word is shared. A name whose every word appears in the query gets a 0.2 bonus. */
    static double score(List<String> name, Set<String> query) {
        if (name.isEmpty()) return 0;
        Set<String> words = new HashSet<>(name);
        int hits = 0;
        for (String t : words) {
            if (query.contains(t)) hits++;
        }
        if (hits == 0) return 0;
        double recall = (double) hits / words.size();
        double precision = (double) hits / query.size();
        double s = 0.7 * recall + 0.3 * precision;
        if (hits == words.size()) s += 0.2;
        return s;
    }

    /** Lowercase words without punctuation, apostrophes, stop words, or a plural s. */
    public static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        String t = text.toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replace("’", "")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        if (t.isEmpty()) return out;
        for (String w : t.split(" ")) {
            String s = stem(w);
            if (!STOPWORDS.contains(s)) out.add(s);
        }
        return out;
    }

    /** Drops one plural s ("bananas" to "banana"). Applied to both sides, so odd stems still match. */
    static String stem(String w) {
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) return w.substring(0, w.length() - 1);
        return w;
    }
}
