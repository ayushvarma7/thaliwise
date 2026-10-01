package com.example.identify;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {

    private static final String FILE_NAME = "settings";
    private static final String KEY_MEMORY_ENABLED = "memory_enabled";
    private static final String KEY_KNN_THRESHOLD = "knn_threshold";
    private static final String KEY_DL_MODEL_ID = "dl_model_id";
    private static final String KEY_DL_MMPROJ_ID = "dl_mmproj_id";

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
}
