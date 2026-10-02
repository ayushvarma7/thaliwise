package com.example.identify.learning;

import com.example.identify.Config;
import com.example.identify.core.AnswerParser;
import com.example.identify.core.EmbeddingCodec;
import com.example.identify.data.CorrectionEntity;

import java.util.ArrayList;
import java.util.List;

/** In-memory copy of every stored embedding for the current model. Thread-safe. */
public final class EmbeddingCache {

    public static final class Entry {
        public final long id;
        public final float[] embedding;
        public final String predictedLabel;
        public final String finalLabel;
        public final String userCorrection;   // nullable
        public final boolean accepted;
        public final long timestampMillis;

        public Entry(long id, float[] embedding, String predictedLabel, String finalLabel,
                     String userCorrection, boolean accepted, long timestampMillis) {
            this.id = id;
            this.embedding = embedding;
            this.predictedLabel = predictedLabel;
            this.finalLabel = finalLabel;
            this.userCorrection = userCorrection;
            this.accepted = accepted;
            this.timestampMillis = timestampMillis;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private boolean loaded = false;

    /** Reads the database once. Call off the main thread. */
    public synchronized void loadIfNeeded(CorrectionRepository repo) {
        if (loaded) return;
        for (CorrectionEntity e : repo.getAllSync()) {
            Entry entry = toEntry(e);
            if (entry != null) entries.add(entry);
        }
        loaded = true;
    }

    public synchronized void add(CorrectionEntity e) {
        Entry entry = toEntry(e);
        if (entry != null) entries.add(entry);
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized List<Entry> snapshot() {
        return new ArrayList<>(entries);
    }

    public synchronized int size() {
        return entries.size();
    }

    /** Null unless the row belongs to the current model and has a well-formed embedding. */
    private static Entry toEntry(CorrectionEntity e) {
        if (e == null || !Config.MODEL_ID.equals(e.modelId) || e.embedding == null || e.embeddingDim <= 0) {
            return null;
        }
        if (e.embedding.length != e.embeddingDim * 4) return null;
        // Labels saved before step 11.5 can hold copied prompt words (", at most 6 words"); clean them here.
        return new Entry(e.id, EmbeddingCodec.fromBytes(e.embedding), AnswerParser.cleanLabel(e.predictedLabel),
                AnswerParser.cleanLabel(e.finalLabel), e.userCorrection, e.accepted, e.timestampMillis);
    }
}
