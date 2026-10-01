package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class FewShotSelectorTest {

    private static CorrectionExample ex(String model, String correct, float[] emb, long ts) {
        return new CorrectionExample(model, correct, emb, ts);
    }

    @Test
    public void nullQueryReturnsMostRecentFive() {
        List<CorrectionExample> pool = new ArrayList<>();
        for (int i = 1; i <= 7; i++) pool.add(ex("model " + i, "correct " + i, null, i));
        List<CorrectionExample> out = FewShotSelector.select(null, pool, 3, 5);
        assertEquals(5, out.size());
        long[] expected = {7, 6, 5, 4, 3};
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], out.get(i).timestampMillis);
    }

    @Test
    public void duplicatesKeepNewestEntry() {
        CorrectionExample older = ex("Golden Retriever", "Labradoodle", null, 10);
        CorrectionExample newer = ex("  golden retriever ", "LABRADOODLE ", null, 20);
        List<CorrectionExample> out = FewShotSelector.select(null, Arrays.asList(older, newer), 3, 5);
        assertEquals(1, out.size());
        assertSame(newer, out.get(0));
        assertEquals(20, out.get(0).timestampMillis);
    }

    @Test
    public void similarFirstThenRecency() {
        CorrectionExample a = ex("a", "A", new float[]{1, 0}, 1);
        CorrectionExample b = ex("b", "B", new float[]{0, 1}, 9);
        CorrectionExample c = ex("c", "C", new float[]{0.9f, 0.1f}, 2);
        CorrectionExample d = ex("d", "D", null, 8);
        List<CorrectionExample> out = FewShotSelector.select(new float[]{1, 0}, Arrays.asList(a, b, c, d), 2, 3);
        assertEquals(3, out.size());
        assertSame(a, out.get(0));
        assertSame(c, out.get(1));
        assertSame(b, out.get(2));
    }

    @Test
    public void differentLengthEmbeddingSkippedForSimilarityButKeptForRecency() {
        CorrectionExample wrongDim = ex("wrong", "dim", new float[]{1, 0, 0}, 5);
        CorrectionExample rightDim = ex("right", "dim", new float[]{0, 1}, 1);
        List<CorrectionExample> out = FewShotSelector.select(new float[]{1, 0}, Arrays.asList(wrongDim, rightDim), 1, 2);
        assertEquals(2, out.size());
        assertSame(rightDim, out.get(0));
        assertSame(wrongDim, out.get(1));
    }

    @Test
    public void blankLabelsAreDropped() {
        CorrectionExample valid = ex("cat", "lynx", null, 4);
        List<CorrectionExample> pool = Arrays.asList(
                ex("", "x", null, 1),
                ex("y", "   ", null, 2),
                ex(null, "z", null, 3),
                null,
                valid);
        List<CorrectionExample> out = FewShotSelector.select(null, pool, 3, 5);
        assertEquals(1, out.size());
        assertSame(valid, out.get(0));
    }

    @Test
    public void maxTotalZeroReturnsEmpty() {
        List<CorrectionExample> pool = Arrays.asList(ex("a", "b", new float[]{1, 0}, 1));
        assertTrue(FewShotSelector.select(new float[]{1, 0}, pool, 3, 0).isEmpty());
    }
}
