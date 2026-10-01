# STEP 10.3: App data (profile storage, meals from Health Connect, cuisine in results)

Goal: the Android-side data the new screens need. AppPrefs stores the onboarding profile, budget, units, and an onboarded flag; HealthConnectRepository lists meals (every app's NutritionRecords) for Today and Diary; ResultViewModel carries the model's cuisine to the result screen and the experiment log.

EDIT `app/src/main/java/com/example/identify/AppPrefs.java`
Find:
```java
import android.content.Context;
import android.content.SharedPreferences;
```
Replace with:
```java
import android.content.Context;
import android.content.SharedPreferences;

import com.example.identify.core.ProfileMath;
import com.example.identify.core.UserProfile;

import java.util.Collections;
import java.util.HashSet;
```

EDIT `app/src/main/java/com/example/identify/AppPrefs.java`
Find:
```java
    private static final String KEY_STEP_GOAL = "step_goal";
```
Replace with:
```java
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
```

EDIT `app/src/main/java/com/example/identify/AppPrefs.java`
Find:
```java
    public void setStepGoal(long goal) { prefs.edit().putLong(KEY_STEP_GOAL, goal).apply(); }
```
Replace with:
```java
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
```

EDIT `app/src/main/java/com/example/identify/health/HealthConnectRepository.java`
Find:
```java
import android.health.connect.RecordIdFilter;
```
Replace with:
```java
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsResponse;
import android.health.connect.RecordIdFilter;
```

EDIT `app/src/main/java/com/example/identify/health/HealthConnectRepository.java`
Find:
```java
import com.example.identify.core.DailyHealth;
```
Replace with:
```java
import com.example.identify.core.DailyHealth;
import com.example.identify.core.MealEntry;
```

EDIT `app/src/main/java/com/example/identify/health/HealthConnectRepository.java`
Find:
```java
    /** Health Connect energy is in small calories; the app shows kilocalories. */
```
Replace with:
```java
    /** meals is never null; on failure it is empty and error says why. Main thread. */
    public interface MealsCallback {
        void onResult(List<MealEntry> meals, String error);
    }

    /** A NutritionRecord meal type as a slot; null for MEAL_TYPE_UNKNOWN and values this app does not know. */
    public static Meals.Slot slotFromMealType(int mealType) {
        switch (mealType) {
            case MealType.MEAL_TYPE_BREAKFAST: return Meals.Slot.BREAKFAST;
            case MealType.MEAL_TYPE_LUNCH: return Meals.Slot.LUNCH;
            case MealType.MEAL_TYPE_DINNER: return Meals.Slot.DINNER;
            case MealType.MEAL_TYPE_SNACK: return Meals.Slot.SNACK;
            default: return null;
        }
    }

    /** Every app's nutrition records starting between from and to; mine marks the ones this app wrote. */
    public static void readMeals(Context ctx, Instant from, Instant to, MealsCallback cb) {
        final Context app = ctx.getApplicationContext();
        final HealthConnectManager hc = manager(app);
        if (hc == null) {
            cb.onResult(new ArrayList<>(), "Health Connect is not available on this device");
            return;
        }
        if (!isGranted(app, HealthPermissions.READ_NUTRITION)) {
            cb.onResult(new ArrayList<>(), "nutrition read permission not granted");
            return;
        }
        TimeInstantRangeFilter range = new TimeInstantRangeFilter.Builder()
                .setStartTime(from)
                .setEndTime(to)
                .build();
        ReadRecordsRequestUsingFilters<NutritionRecord> request =
                new ReadRecordsRequestUsingFilters.Builder<>(NutritionRecord.class)
                        .setTimeRangeFilter(range)
                        .setPageSize(1000)
                        .build();
        final String me = app.getPackageName();
        final long t0 = SystemClock.elapsedRealtime();
        Executor main = ContextCompat.getMainExecutor(app);
        try {
            hc.readRecords(request, main,
                    new OutcomeReceiver<ReadRecordsResponse<NutritionRecord>, HealthConnectException>() {
                        @Override
                        public void onResult(ReadRecordsResponse<NutritionRecord> response) {
                            List<MealEntry> meals = new ArrayList<>();
                            for (NutritionRecord r : response.getRecords()) {
                                Energy e = r.getEnergy();
                                meals.add(new MealEntry(r.getMetadata().getId(), r.getMealName(),
                                        e == null ? Double.NaN : kcal(e), r.getStartTime().toEpochMilli(),
                                        slotFromMealType(r.getMealType()),
                                        me.equals(r.getMetadata().getDataOrigin().getPackageName())));
                            }
                            Log.i(Config.HEALTH_TAG, "read meals count=" + meals.size()
                                    + " ms=" + (SystemClock.elapsedRealtime() - t0));
                            cb.onResult(meals, null);
                        }

                        @Override
                        public void onError(HealthConnectException e) {
                            Log.w(Config.HEALTH_TAG, "read meals failed", e);
                            cb.onResult(new ArrayList<>(), e.getMessage() == null ? "error " + e.getErrorCode() : e.getMessage());
                        }
                    });
        } catch (RuntimeException e) {   // SecurityException if access was removed a moment ago
            Log.w(Config.HEALTH_TAG, "read meals failed", e);
            main.execute(() -> cb.onResult(new ArrayList<>(), e.toString()));
        }
    }

    /** Health Connect energy is in small calories; the app shows kilocalories. */
```

EDIT `app/src/main/java/com/example/identify/ui/ResultViewModel.java`
Find:
```java
        public final String details;       // time, CPU, memory, confidence, and neighbor summary

        IdentifyResult(String label, String description, String rawOutput, String source,
                       float nearestScore, long latencyMs, float[] embedding, String runId, String details) {
            this.label = label; this.description = description; this.rawOutput = rawOutput;
            this.source = source; this.nearestScore = nearestScore; this.latencyMs = latencyMs;
            this.embedding = embedding; this.runId = runId; this.details = details;
        }
```
Replace with:
```java
        public final String details;       // time, CPU, memory, confidence, and neighbor summary
        public final String cuisine;       // the model's "Cuisine:" line; empty for memory hits

        IdentifyResult(String label, String description, String rawOutput, String source,
                       float nearestScore, long latencyMs, float[] embedding, String runId, String details,
                       String cuisine) {
            this.label = label; this.description = description; this.rawOutput = rawOutput;
            this.source = source; this.nearestScore = nearestScore; this.latencyMs = latencyMs;
            this.embedding = embedding; this.runId = runId; this.details = details;
            this.cuisine = cuisine == null ? "" : cuisine;
        }
```

EDIT `app/src/main/java/com/example/identify/ui/ResultViewModel.java`
Find:
```java
                IdentifyResult r = new IdentifyResult(hit.label, "", null, Config.SOURCE_MEMORY,
                        nearestScore, total, emb, runId, details);
```
Replace with:
```java
                IdentifyResult r = new IdentifyResult(hit.label, "", null, Config.SOURCE_MEMORY,
                        nearestScore, total, emb, runId, details, "");
```

EDIT `app/src/main/java/com/example/identify/ui/ResultViewModel.java`
Find:
```java
            IdentifyResult r = new IdentifyResult(parsed.label, parsed.description, raw,
                    Config.SOURCE_MODEL, nearestScore, total, emb, runId, details);
```
Replace with:
```java
            IdentifyResult r = new IdentifyResult(parsed.label, parsed.description, raw,
                    Config.SOURCE_MODEL, nearestScore, total, emb, runId, details, parsed.cuisine);
```

EDIT `app/src/main/java/com/example/identify/ui/ResultViewModel.java`
Find:
```java
            put(rec, "description", parsed.description);
```
Replace with:
```java
            put(rec, "description", parsed.description);
            put(rec, "cuisine", parsed.cuisine);
```

## VERIFY 10.3

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew :core:test :app:compileDebugJavaWithJavac --console=plain -q
```
Exit 0.

Commit subject: `Store the user profile, list meals from Health Connect, carry cuisine`.
