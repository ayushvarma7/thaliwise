package com.example.identify.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CosineSimilarityTest {

    @Test
    public void identicalVectorsGiveOne() {
        assertEquals(1.0f, CosineSimilarity.compute(new float[]{1, 2, 3}, new float[]{1, 2, 3}), 1e-6f);
    }

    @Test
    public void orthogonalVectorsGiveZero() {
        assertEquals(0.0f, CosineSimilarity.compute(new float[]{1, 0}, new float[]{0, 1}), 1e-6f);
    }

    @Test
    public void oppositeVectorsGiveMinusOne() {
        assertEquals(-1.0f, CosineSimilarity.compute(new float[]{1, 2}, new float[]{-1, -2}), 1e-6f);
    }

    @Test
    public void zeroVectorGivesZero() {
        assertEquals(0.0f, CosineSimilarity.compute(new float[]{0, 0}, new float[]{1, 1}), 0.0f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void lengthMismatchThrows() {
        CosineSimilarity.compute(new float[]{1, 2, 3}, new float[]{1, 2});
    }
}
