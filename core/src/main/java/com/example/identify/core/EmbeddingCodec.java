package com.example.identify.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class EmbeddingCodec {
    private EmbeddingCodec() {}

    public static byte[] toBytes(float[] v) {
        ByteBuffer buf = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : v) buf.putFloat(f);
        return buf.array();
    }

    public static float[] fromBytes(byte[] b) {
        if (b == null || b.length % 4 != 0) throw new IllegalArgumentException("bad embedding bytes");
        ByteBuffer buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        float[] v = new float[b.length / 4];
        for (int i = 0; i < v.length; i++) v[i] = buf.getFloat();
        return v;
    }
}
