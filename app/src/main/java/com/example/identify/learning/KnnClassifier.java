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
        public final String predictedLabel;
        public final boolean accepted;

        public Hit(String label, float score, long entryId, String predictedLabel, boolean accepted) {
            this.label = label;
            this.score = score;
            this.entryId = entryId;
            this.predictedLabel = predictedLabel;
            this.accepted = accepted;
        }
    }

    /** Nearest entry with the same embedding length, or null if none. No threshold applied. */
    public static Hit nearest(float[] query, List<EmbeddingCache.Entry> entries) {
        List<Hit> top = topK(query, entries, 1);
        return top.isEmpty() ? null : top.get(0);
    }

    /** Up to k entries with the same embedding length, most similar first. No threshold applied. */
    public static List<Hit> topK(float[] query, List<EmbeddingCache.Entry> entries, int k) {
        List<Hit> out = new ArrayList<>();
        if (query == null) return out;
        List<EmbeddingCache.Entry> kept = new ArrayList<>();
        List<float[]> vectors = new ArrayList<>();
        for (EmbeddingCache.Entry e : entries) {
            if (e.embedding != null && e.embedding.length == query.length) {
                kept.add(e);
                vectors.add(e.embedding);
            }
        }
        for (KnnSearch.Match m : KnnSearch.topK(query, vectors, k)) {
            EmbeddingCache.Entry e = kept.get(m.index);
            out.add(new Hit(e.finalLabel, m.score, e.id, e.predictedLabel, e.accepted));
        }
        return out;
    }
}
