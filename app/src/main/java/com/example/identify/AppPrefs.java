package com.example.identify;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {

    private static final String FILE_NAME = "settings";
    private static final String KEY_MEMORY_ENABLED = "memory_enabled";
    private static final String KEY_KNN_THRESHOLD = "knn_threshold";
    private static final String KEY_DL_MODEL_ID = "dl_model_id";
    private static final String KEY_DL_MMPROJ_ID = "dl_mmproj_id";
    private static final String KEY_N_THREADS = "n_threads";
    private static final String KEY_IMAGE_MAX_TOKENS = "image_max_tokens";

    private static volatile AppPrefs instance;

    private final SharedPreferences prefs;

    private AppPrefs(Context appContext) {
        prefs = appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public static AppPrefs get(Context ctx) {
        if (instance == null) {
            synchronized (AppPrefs.class) {
                if (instance == null) instance = new AppPrefs(ctx.getApplicationContext());
            }
        }
        return instance;
    }

    public boolean isMemoryEnabled() { return prefs.getBoolean(KEY_MEMORY_ENABLED, true); }

    public void setMemoryEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MEMORY_ENABLED, enabled).apply();
    }

    public float getKnnThreshold() { return prefs.getFloat(KEY_KNN_THRESHOLD, Config.DEFAULT_KNN_THRESHOLD); }

    public void setKnnThreshold(float threshold) {
        prefs.edit().putFloat(KEY_KNN_THRESHOLD, threshold).apply();
    }

    public long getModelDownloadId() { return prefs.getLong(KEY_DL_MODEL_ID, -1L); }

    public void setModelDownloadId(long id) { prefs.edit().putLong(KEY_DL_MODEL_ID, id).apply(); }

    public long getMmprojDownloadId() { return prefs.getLong(KEY_DL_MMPROJ_ID, -1L); }

    public void setMmprojDownloadId(long id) { prefs.edit().putLong(KEY_DL_MMPROJ_ID, id).apply(); }

    /** CPU threads for the model. A change reloads the model on the next identification. */
    public int getThreads() {
        return clamp(prefs.getInt(KEY_N_THREADS, Config.N_THREADS), Config.MIN_THREADS, Config.MAX_THREADS);
    }

    public void setThreads(int threads) { prefs.edit().putInt(KEY_N_THREADS, threads).apply(); }

    /** Vision token cap per image. A change reloads the model on the next identification. */
    public int getImageMaxTokens() {
        return clamp(prefs.getInt(KEY_IMAGE_MAX_TOKENS, Config.DEFAULT_IMAGE_MAX_TOKENS),
                Config.MIN_IMAGE_MAX_TOKENS, Config.MAX_IMAGE_MAX_TOKENS);
    }

    public void setImageMaxTokens(int tokens) { prefs.edit().putInt(KEY_IMAGE_MAX_TOKENS, tokens).apply(); }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
