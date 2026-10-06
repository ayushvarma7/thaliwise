package com.example.identify.model;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.example.identify.Config;
import com.example.identify.core.PromptBuilder;

import java.nio.charset.StandardCharsets;

public final class VlmEngine {

    static { System.loadLibrary("vlm-bridge"); }

    private static final VlmEngine INSTANCE = new VlmEngine();
    public static VlmEngine get() { return INSTANCE; }

    // Written only inside synchronized methods. volatile so the UI can read them without waiting
    // for the lock, which a running inference holds for seconds.
    private volatile long handle = 0L;
    private volatile String systemInfo = "";
    private volatile int loadedThreads = 0;
    private volatile int loadedImageMaxTokens = 0;
    private volatile String loadStatsJson = "{}";
    private String lastEmbedStatsJson = "{}";
    private String lastGenerateStatsJson = "{}";

    private VlmEngine() {}

    public boolean isLoaded() { return handle != 0L; }
    public String getSystemInfo() { return systemInfo; }
    public int getLoadedThreads() { return loadedThreads; }
    public int getLoadedImageMaxTokens() { return loadedImageMaxTokens; }
    /** Native timings and model facts from the most recent load. */
    public String getLoadStatsJson() { return loadStatsJson; }

    /**
     * Loads model + projector with these settings, reloading when the settings changed.
     * Returns true if a load happened in this call. Call only on the model thread.
     */
    public synchronized boolean ensureLoaded(Context ctx, int nThreads, int imageMaxTokens) {
        assertNotMainThread();
        if (handle != 0L && loadedThreads == nThreads && loadedImageMaxTokens == imageMaxTokens) return false;
        if (handle != 0L) {
            Log.i(Config.LOG_TAG, "settings changed, reloading model: threads " + loadedThreads + " -> " + nThreads
                    + ", image_max_tokens " + loadedImageMaxTokens + " -> " + imageMaxTokens);
            nativeUnloadModel(handle);
            handle = 0L;
        }
        if (!ModelDownloader.filesReady(ctx)) {
            throw new IllegalStateException("Model files are missing. Download them in Settings.");
        }
        long t0 = SystemClock.elapsedRealtime();
        long h = nativeLoadModel(
                ModelDownloader.modelFile(ctx).getAbsolutePath(),
                ModelDownloader.mmprojFile(ctx).getAbsolutePath(),
                Config.N_CTX, nThreads, imageMaxTokens);
        if (h == 0L) throw new IllegalStateException("Native model load failed. See logcat tag VlmBridge.");
        handle = h;
        loadedThreads = nThreads;
        loadedImageMaxTokens = imageMaxTokens;
        systemInfo = nativeSystemInfo();
        loadStatsJson = stats(h);
        Log.i(Config.LOG_TAG, "model loaded threads=" + nThreads + " image_max_tokens=" + imageMaxTokens
                + " ms=" + (SystemClock.elapsedRealtime() - t0));
        return true;
    }

    /** Encodes the image once and keeps the encoding for the next generation on the same file. */
    public synchronized float[] getImageEmbedding(String imagePath) {
        assertNotMainThread();
        requireLoaded();
        float[] e = nativeGetImageEmbedding(handle, imagePath);
        lastEmbedStatsJson = stats(handle);
        return e;
    }

    public synchronized String generateWithImage(String imagePath, String systemPrompt, String userPrompt) {
        assertNotMainThread();
        requireLoaded();
        byte[] out = nativeGenerateWithImage(handle, imagePath,
                systemPrompt.getBytes(StandardCharsets.UTF_8),
                userPrompt.getBytes(StandardCharsets.UTF_8),
                Config.MAX_NEW_TOKENS, Config.TEMPERATURE, Config.MIN_P,
                Config.REPEAT_PENALTY, Config.TOP_K,
                PromptBuilder.ANSWER_GRAMMAR.getBytes(StandardCharsets.UTF_8));
        lastGenerateStatsJson = stats(handle);
        return new String(out, StandardCharsets.UTF_8);
    }

    /** Native timings of the last embedding call. Model thread only. */
    public synchronized String getLastEmbedStatsJson() { return lastEmbedStatsJson; }

    /** Native timings and per-token probabilities of the last generation. Model thread only. */
    public synchronized String getLastGenerateStatsJson() { return lastGenerateStatsJson; }

    public synchronized void unload() {
        assertNotMainThread();
        if (handle != 0L) {
            nativeUnloadModel(handle);
            handle = 0L;
            loadedThreads = 0;
            loadedImageMaxTokens = 0;
            Log.i(Config.LOG_TAG, "model unloaded");
        }
    }

    private void requireLoaded() {
        if (handle == 0L) throw new IllegalStateException("Model not loaded");
    }

    private static String stats(long h) {
        return new String(nativeGetLastStats(h), StandardCharsets.UTF_8);
    }

    private static void assertNotMainThread() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("VlmEngine must not run on the main thread");
        }
    }

    private static native long nativeLoadModel(String modelPath, String mmprojPath, int nCtx, int nThreads,
                                               int imageMaxTokens);
    private static native byte[] nativeGenerateWithImage(long handle, String imagePath,
            byte[] systemPromptUtf8, byte[] userPromptUtf8, int maxTokens,
            float temperature, float minP, float repeatPenalty, int topK, byte[] grammarUtf8);
    private static native float[] nativeGetImageEmbedding(long handle, String imagePath);
    private static native byte[] nativeGetLastStats(long handle);
    private static native void nativeUnloadModel(long handle);
    private static native String nativeSystemInfo();
}
