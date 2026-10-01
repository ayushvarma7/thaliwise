package com.example.identify.model;

import android.content.Context;
import android.os.Looper;

import com.example.identify.Config;

import java.nio.charset.StandardCharsets;

public final class VlmEngine {

    static { System.loadLibrary("vlm-bridge"); }

    private static final VlmEngine INSTANCE = new VlmEngine();
    public static VlmEngine get() { return INSTANCE; }

    // Written only inside synchronized methods. volatile so the UI can read them without waiting
    // for the lock, which a running inference holds for seconds.
    private volatile long handle = 0L;
    private volatile String systemInfo = "";

    private VlmEngine() {}

    public boolean isLoaded() { return handle != 0L; }
    public String getSystemInfo() { return systemInfo; }

    /** Loads model + projector once. Call only on the model thread. */
    public synchronized void ensureLoaded(Context ctx) {
        assertNotMainThread();
        if (handle != 0L) return;
        if (!ModelDownloader.filesReady(ctx)) {
            throw new IllegalStateException("Model files are missing. Download them in Settings.");
        }
        long h = nativeLoadModel(
                ModelDownloader.modelFile(ctx).getAbsolutePath(),
                ModelDownloader.mmprojFile(ctx).getAbsolutePath(),
                Config.N_CTX, Config.N_THREADS);
        if (h == 0L) throw new IllegalStateException("Native model load failed. See logcat tag VlmBridge.");
        handle = h;
        systemInfo = nativeSystemInfo();
    }

    public synchronized float[] getImageEmbedding(String imagePath) {
        assertNotMainThread();
        requireLoaded();
        return nativeGetImageEmbedding(handle, imagePath);
    }

    public synchronized String generateWithImage(String imagePath, String systemPrompt, String userPrompt) {
        assertNotMainThread();
        requireLoaded();
        byte[] out = nativeGenerateWithImage(handle, imagePath,
                systemPrompt.getBytes(StandardCharsets.UTF_8),
                userPrompt.getBytes(StandardCharsets.UTF_8),
                Config.MAX_NEW_TOKENS, Config.TEMPERATURE, Config.MIN_P,
                Config.REPEAT_PENALTY, Config.TOP_K);
        return new String(out, StandardCharsets.UTF_8);
    }

    public synchronized void unload() {
        assertNotMainThread();
        if (handle != 0L) {
            nativeUnloadModel(handle);
            handle = 0L;
        }
    }

    private void requireLoaded() {
        if (handle == 0L) throw new IllegalStateException("Model not loaded");
    }

    private static void assertNotMainThread() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("VlmEngine must not run on the main thread");
        }
    }

    private static native long nativeLoadModel(String modelPath, String mmprojPath, int nCtx, int nThreads);
    private static native byte[] nativeGenerateWithImage(long handle, String imagePath,
            byte[] systemPromptUtf8, byte[] userPromptUtf8, int maxTokens,
            float temperature, float minP, float repeatPenalty, int topK);
    private static native float[] nativeGetImageEmbedding(long handle, String imagePath);
    private static native void nativeUnloadModel(long handle);
    private static native String nativeSystemInfo();
}
