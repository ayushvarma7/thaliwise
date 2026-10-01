package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class KnnSearchTest {

    private static List<float[]> candidates() {
        return Arrays.asList(new float[]{0, 1}, new float[]{0.9f, 0.1f}, new float[]{-1, 0});
    }

    @Test
    public void nearestOnEmptyListIsNull() {
        assertNull(KnnSearch.nearest(new float[]{1, 0}, new ArrayList<>()));
    }

    @Test
    public void nearestPicksMostSimilar() {
        KnnSearch.Match m = KnnSearch.nearest(new float[]{1, 0}, candidates());
        assertNotNull(m);
        assertEquals(1, m.index);
    }

    @Test
    public void topKTwoReturnsSortedIndices() {
        List<KnnSearch.Match> top = KnnSearch.topK(new float[]{1, 0}, candidates(), 2);
        assertEquals(2, top.size());
        assertEquals(1, top.get(0).index);
        assertEquals(0, top.get(1).index);
    }

    @Test
    public void topKLargerThanCandidatesReturnsAll() {
        assertEquals(3, KnnSearch.topK(new float[]{1, 0}, candidates(), 10).size());
    }

    @Test
    public void topKZeroReturnsEmpty() {
        assertTrue(KnnSearch.topK(new float[]{1, 0}, candidates(), 0).isEmpty());
    }
}
