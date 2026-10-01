package com.example.identify.learning;

import com.example.identify.core.KnnSearch;

import java.util.ArrayList;
import java.util.List;

public final class KnnClassifier {
    private KnnClassifier() {}

    public static final class Hit {
        public final String label;
        public final float score;
        public final long entryId;

        public Hit(String label, float score, long entryId) {
            this.label = label;
            this.score = score;
            this.entryId = entryId;
        }
    }

    /** Nearest entry with the same embedding length, or null if none. No threshold applied. */
    public static Hit nearest(float[] query, List<EmbeddingCache.Entry> entries) {
        if (query == null) return null;
        List<EmbeddingCache.Entry> kept = new ArrayList<>();
        List<float[]> vectors = new ArrayList<>();
        for (EmbeddingCache.Entry e : entries) {
            if (e.embedding != null && e.embedding.length == query.length) {
                kept.add(e);
                vectors.add(e.embedding);
            }
        }
        KnnSearch.Match m = KnnSearch.nearest(query, vectors);
        if (m == null) return null;
        EmbeddingCache.Entry best = kept.get(m.index);
        return new Hit(best.finalLabel, m.score, best.id);
    }
}
