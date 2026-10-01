package com.example.identify.core;

public final class CosineSimilarity {
    private CosineSimilarity() {}

    public static float compute(float[] a, float[] b) {
        if (a == null || b == null) throw new IllegalArgumentException("null vector");
        if (a.length != b.length) {
            throw new IllegalArgumentException("dimension mismatch: " + a.length + " vs " + b.length);
        }
        double dot = 0.0, na = 0.0, nb = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na  += (double) a[i] * a[i];
            nb  += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(na) * Math.sqrt(nb);
        return denom == 0.0 ? 0f : (float) (dot / denom);
    }
}
