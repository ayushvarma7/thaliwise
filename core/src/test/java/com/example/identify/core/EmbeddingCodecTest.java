package com.example.identify.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EmbeddingCodecTest {

    @Test
    public void roundTripPreservesValues() {
        float[] v = {1.5f, -2.25f, 0f, 3.4028235e38f};
        assertArrayEquals(v, EmbeddingCodec.fromBytes(EmbeddingCodec.toBytes(v)), 0f);
    }

    @Test
    public void byteLengthIsFourTimesFloatCount() {
        float[] v = {1f, 2f, 3f};
        assertEquals(v.length * 4, EmbeddingCodec.toBytes(v).length);
    }

    @Test(expected = IllegalArgumentException.class)
    public void badLengthThrows() {
        EmbeddingCodec.fromBytes(new byte[5]);
    }
}
