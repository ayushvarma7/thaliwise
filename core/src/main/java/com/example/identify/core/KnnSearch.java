package com.example.identify.core;

import java.util.ArrayList;
import java.util.List;

public final class KnnSearch {
    private KnnSearch() {}

    public static final class Match {
        public final int index;
        public final float score;
        public Match(int index, float score) { this.index = index; this.score = score; }
    }

    /** Nearest neighbor by cosine similarity, or null if candidates is empty. */
    public static Match nearest(float[] query, List<float[]> candidates) {
        int best = -1;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < candidates.size(); i++) {
            float s = CosineSimilarity.compute(query, candidates.get(i));
            if (s > bestScore) { bestScore = s; best = i; }
        }
        return best < 0 ? null : new Match(best, bestScore);
    }

    /** Up to k matches, sorted by score descending; ties keep the lower index first. */
    public static List<Match> topK(float[] query, List<float[]> candidates, int k) {
        List<Match> all = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            all.add(new Match(i, CosineSimilarity.compute(query, candidates.get(i))));
        }
        all.sort((x, y) -> {
            int c = Float.compare(y.score, x.score);
            return c != 0 ? c : Integer.compare(x.index, y.index);
        });
        int n = Math.min(Math.max(k, 0), all.size());
        return new ArrayList<>(all.subList(0, n));
    }
}
