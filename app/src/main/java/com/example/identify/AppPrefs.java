package com.example.identify;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.identify.core.ProfileMath;
import com.example.identify.core.UserProfile;

import java.util.Collections;
import java.util.HashSet;

public final class AppPrefs {

    private static final String FILE_NAME = "settings";
    private static final String KEY_MEMORY_ENABLED = "memory_enabled";
    private static final String KEY_KNN_THRESHOLD = "knn_threshold";
    private static final String KEY_DL_MODEL_ID = "dl_model_id";
    private static final String KEY_DL_MMPROJ_ID = "dl_mmproj_id";
    private static final String KEY_N_THREADS = "n_threads";
    private static final String KEY_IMAGE_MAX_TOKENS = "image_max_tokens";
    private static final String KEY_STEP_GOAL = "step_goal";
    private static final String KEY_ONBOARDED = "onboarded";
    private static final String KEY_KCAL_BUDGET = "kcal_budget";
    private static final String KEY_US_UNITS = "us_units";
    private static final String KEY_NAME = "profile_name";
    private static final String KEY_AGE = "profile_age";
    private static final String KEY_SEX = "profile_sex";
    private static final String KEY_HEIGHT_CM = "profile_height_cm";
    private static final String KEY_WEIGHT_KG = "profile_weight_kg";
    private static final String KEY_ACTIVITY = "profile_activity";
    private static final String KEY_GOAL = "profile_goal";
    private static final String KEY_REASONS = "profile_reasons";
    private static final String KEY_CUISINES = "profile_cuisines";
    private static final String KEY_DIET = "profile_diet";
    private static final String KEY_EAT_MORE = "profile_eat_more";

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

    public long getStepGoal() { return prefs.getLong(KEY_STEP_GOAL, Config.DEFAULT_STEP_GOAL); }

    public void setStepGoal(long goal) { prefs.edit().putLong(KEY_STEP_GOAL, goal).apply(); }

    /** False until the user finishes onboarding once. */
    public boolean isOnboarded() { return prefs.getBoolean(KEY_ONBOARDED, false); }

    public void setOnboarded(boolean done) { prefs.edit().putBoolean(KEY_ONBOARDED, done).apply(); }

    public long getCalorieBudget() { return prefs.getLong(KEY_KCAL_BUDGET, ProfileMath.DEFAULT_BUDGET); }

    public void setCalorieBudget(long kcal) { prefs.edit().putLong(KEY_KCAL_BUDGET, kcal).apply(); }

    /** Feet, inches, and pounds in the onboarding form (default, the app is used in the USA). */
    public boolean usesUsUnits() { return prefs.getBoolean(KEY_US_UNITS, true); }

    public void setUsUnits(boolean us) { prefs.edit().putBoolean(KEY_US_UNITS, us).apply(); }

    /** The onboarding answers; safe defaults when onboarding never ran. */
    public UserProfile getProfile() {
        return new UserProfile(
                prefs.getString(KEY_NAME, ""),
                prefs.getInt(KEY_AGE, 0),
                enumOr(UserProfile.Sex.class, prefs.getString(KEY_SEX, null), UserProfile.Sex.UNSPECIFIED),
                prefs.getFloat(KEY_HEIGHT_CM, 0f),
                prefs.getFloat(KEY_WEIGHT_KG, 0f),
                enumOr(UserProfile.Activity.class, prefs.getString(KEY_ACTIVITY, null), UserProfile.Activity.LIGHT),
                enumOr(UserProfile.Goal.class, prefs.getString(KEY_GOAL, null), UserProfile.Goal.TRACK),
                prefs.getStringSet(KEY_REASONS, Collections.emptySet()),
                prefs.getStringSet(KEY_CUISINES, Collections.emptySet()),
                prefs.getStringSet(KEY_DIET, Collections.emptySet()),
                prefs.getStringSet(KEY_EAT_MORE, Collections.emptySet()));
    }

    public void saveProfile(UserProfile p) {
        prefs.edit()
                .putString(KEY_NAME, p.name)
                .putInt(KEY_AGE, p.ageYears)
                .putString(KEY_SEX, p.sex.name())
                .putFloat(KEY_HEIGHT_CM, (float) p.heightCm)
                .putFloat(KEY_WEIGHT_KG, (float) p.weightKg)
                .putString(KEY_ACTIVITY, p.activity.name())
                .putString(KEY_GOAL, p.goal.name())
                .putStringSet(KEY_REASONS, new HashSet<>(p.reasons))
                .putStringSet(KEY_CUISINES, new HashSet<>(p.cuisines))
                .putStringSet(KEY_DIET, new HashSet<>(p.diet))
                .putStringSet(KEY_EAT_MORE, new HashSet<>(p.eatMore))
                .apply();
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
        if (name == null) return fallback;
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Vision token cap per image. A change reloads the model on the next identification. */
    public int getImageMaxTokens() {
        return clamp(prefs.getInt(KEY_IMAGE_MAX_TOKENS, Config.DEFAULT_IMAGE_MAX_TOKENS),
                Config.MIN_IMAGE_MAX_TOKENS, Config.MAX_IMAGE_MAX_TOKENS);
    }

    public void setImageMaxTokens(int tokens) { prefs.edit().putInt(KEY_IMAGE_MAX_TOKENS, tokens).apply(); }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
