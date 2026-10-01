package com.example.identify.core;

public final class CorrectionExample {
    public final String modelLabel;     // what was shown to the user
    public final String correctLabel;   // what the user typed
    public final float[] embedding;     // may be null
    public final long timestampMillis;

    public CorrectionExample(String modelLabel, String correctLabel, float[] embedding, long timestampMillis) {
        this.modelLabel = modelLabel;
        this.correctLabel = correctLabel;
        this.embedding = embedding;
        this.timestampMillis = timestampMillis;
    }
}
