package com.example.identify.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FewShotSelector {
    private FewShotSelector() {}

    /**
     * Model labels that name no dish. A correction after one of these would read "You said Unknown food.
     * Correct answer: X" and teach the model to answer X whenever it is unsure, so it is never an example.
     */
    static final Set<String> NON_ANSWERS = new HashSet<>(Arrays.asList("unknown food", "unknown", "not food"));

    public static List<CorrectionExample> select(float[] query, List<CorrectionExample> pool,
                                                 int nSimilar, int maxTotal) {
        Map<String, CorrectionExample> byKey = new LinkedHashMap<>();
        for (CorrectionExample e : pool) {
            if (e == null || isBlank(e.modelLabel) || isBlank(e.correctLabel)) continue;
            if (NON_ANSWERS.contains(norm(e.modelLabel))) continue;
            String key = norm(e.modelLabel) + "|" + norm(e.correctLabel);
            CorrectionExample prev = byKey.get(key);
            if (prev == null || e.timestampMillis > prev.timestampMillis) byKey.put(key, e);
        }
        List<CorrectionExample> unique = new ArrayList<>(byKey.values());
        List<CorrectionExample> result = new ArrayList<>();
        if (maxTotal <= 0) return result;

        if (query != null && nSimilar > 0) {
            List<CorrectionExample> cands = new ArrayList<>();
            List<Float> scores = new ArrayList<>();
            for (CorrectionExample e : unique) {
                if (e.embedding != null && e.embedding.length == query.length) {
                    cands.add(e);
                    scores.add(CosineSimilarity.compute(query, e.embedding));
                }
            }
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < cands.size(); i++) order.add(i);
            order.sort((i, j) -> {
                int c = Float.compare(scores.get(j), scores.get(i));
                if (c != 0) return c;
                return Long.compare(cands.get(j).timestampMillis, cands.get(i).timestampMillis);
            });
            int take = Math.min(Math.min(nSimilar, maxTotal), order.size());
            for (int k = 0; k < take; k++) result.add(cands.get(order.get(k)));
        }

        List<CorrectionExample> byRecent = new ArrayList<>(unique);
        byRecent.sort((a, b) -> Long.compare(b.timestampMillis, a.timestampMillis));
        for (CorrectionExample e : byRecent) {
            if (result.size() >= maxTotal) break;
            if (!result.contains(e)) result.add(e);
        }
        return result;
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static String norm(String s) { return s.trim().toLowerCase(Locale.ROOT); }
}
