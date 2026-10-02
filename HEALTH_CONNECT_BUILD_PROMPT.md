# BUILD SPEC: IdentifyVLM Phase 8, Health Connect connection

You are extending an existing, working Android app. This document is the single source of truth for this phase. Follow it literally and in order. Where it gives exact code, use that code. Where it says "find this text, replace with", the find text is copied from the current files and must match exactly. Do not improvise features.

Goal of this phase, and nothing more: the app connects to Android Health Connect, asks the user for permission, reads today's steps, calories burned, and calories eaten, and shows them in Settings. Food calorie tracking and nudges are later phases (see Section 12). Do not build them now.

---

## 0. RULES FOR YOU (THE IMPLEMENTING AGENT)

1. Project root: `/Users/ayush/Downloads/CLAUDE/IdentifyVLM`. Work only inside it. Use absolute paths.
2. Before editing any file, read it. Edit only the regions this document names. Keep everything else byte for byte.
3. Language: Java 17 only. Zero Kotlin files. No coroutines, no Flow, no Compose, no RxJava.
4. Do NOT add any dependency. Use the Android platform Health Connect API in package `android.health.connect` (built into Android 14+). Do NOT add `androidx.health.connect:connect-client` or anything named `androidx.health`. That library is Kotlin-first and needs coroutines.
5. Do not touch native code (`app/src/main/cpp/`), the model, the prompts, the sampling settings, or `third_party/`.
6. Never write the em-dash character (U+2014) or the en-dash character (U+2013) in any file. Use hyphens, colons, commas, or parentheses.
7. No `TODO`, no `FIXME`, no stubs, no placeholder code. Every file complete.
8. Never grant health permissions with `adb shell pm grant`. The user approves them on the phone. Never write test or fake records into Health Connect. This phase only reads.
9. Build commands always use these two variables inline (they point at the project-local toolchain):
   ```bash
   cd /Users/ayush/Downloads/CLAUDE/IdentifyVLM
   JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew <tasks>
   ```
   Do not install Gradle, the SDK, the NDK, or anything else globally. They are already in `.toolchain/`.
10. adb lives at `/Users/ayush/Library/Android/sdk/platform-tools/adb` (the project SDK has no platform-tools). Call it by that full path.
11. After each step's VERIFY passes, continue. If a command fails: read the whole error, fix the cause, rerun. At most 5 attempts per error. Then write the error and what you tried to `BLOCKERS.md` and STOP and report.
12. Append one line per step to `PROGRESS.md` under a new heading `## Phase 8: Health Connect` (format: `- 8.N <name>: DONE - <one-line verify result>`).
13. Commit at the end (Step 8.11) with `git add -A && git commit -m "Phase 8: Health Connect connection"`. Never `git push`.

### 0.1 STOP conditions (stop and report to the user)

- `git status --short` is not empty before you start (someone has uncommitted work). Report it; do not commit or discard it.
- The build fails 5 times on the same error.
- On the phone, the permission screen never appears after the fixes in Section 11.

---

## 1. FACTS ABOUT THE CURRENT STATE (already verified, do not re-derive)

- Last commit: `fc82479 Record first on-device timings; fix battery energy on external power; enable llama perf timers`.
- App id and base package: `com.example.identify`. Modules: `:app` (Android) and `:core` (plain Java, unit-tested, no `android.*` imports).
- `compileSdk = 35`, `targetSdk = 35`, `minSdk = 31` (this phase changes minSdk to 34).
- Existing helpers you will use:
  - `com.example.identify.util.ExperimentLog` with `event(String type)`, `put(JSONObject, String, Object)` (never throws, NaN becomes null), `append(Context, JSONObject)`.
  - `com.example.identify.AppPrefs.get(Context)` (SharedPreferences singleton).
  - `com.example.identify.Config` (constants).
- Settings screen: `app/src/main/res/layout/fragment_settings.xml` and `app/src/main/java/com/example/identify/ui/SettingsFragment.java` (view binding class `FragmentSettingsBinding`).
- Test phone: Pixel 8 (serial `48071VDJH00284`), Android 17 (API 37). Health Connect is built into the system (`com.google.android.healthconnect.controller`, version 17). Fitbit (`com.fitbit.FitbitMobile`) and Google Fit (`com.google.android.apps.fitness`) are installed; either can be the source of step data if its sync to Health Connect is on.
- Health Connect platform API facts (checked against `.toolchain/android-sdk/platforms/android-35/android.jar`):
  - Manager: `context.getSystemService(android.health.connect.HealthConnectManager.class)`; null if unavailable.
  - `aggregate(AggregateRecordsRequest<T>, Executor, OutcomeReceiver<AggregateRecordsResponse<T>, HealthConnectException>)`.
  - `new AggregateRecordsRequest.Builder<T>(TimeRangeFilter).addAggregationType(AggregationType<T>).build()`. All types in one request must share `T`, so this spec sends one request per metric.
  - `new TimeInstantRangeFilter.Builder().setStartTime(Instant).setEndTime(Instant).build()`.
  - `AggregateRecordsResponse.get(AggregationType<T>)` returns `T`, or null when there is no data.
  - Aggregation types: `StepsRecord.STEPS_COUNT_TOTAL` (Long), `ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL` (Energy), `TotalCaloriesBurnedRecord.ENERGY_TOTAL` (Energy), `NutritionRecord.ENERGY_TOTAL` (Energy).
  - `Energy.getInCalories()` returns small calories. Kilocalories = value / 1000. (Step 8.10 double-checks this on the phone.)
  - Permission strings (class `android.health.connect.HealthPermissions`): `android.permission.health.READ_STEPS`, `...READ_ACTIVE_CALORIES_BURNED`, `...READ_TOTAL_CALORIES_BURNED`, `...READ_NUTRITION`, `...WRITE_NUTRITION`.
  - Health permissions on Android 14+ are runtime permissions. Request them with `ActivityResultContracts.RequestMultiplePermissions`; the system shows the Health Connect permission screen. Check them with `ContextCompat.checkSelfPermission`.
  - Health Connect refuses to show the permission screen unless the app declares an `activity-alias` with action `android.intent.action.VIEW_PERMISSION_USAGE`, category `android.intent.category.HEALTH_PERMISSIONS`, and permission `android.permission.START_VIEW_PERMISSION_USAGE`, pointing at a privacy policy screen.
  - Intent to open Health Connect: action string `android.health.connect.action.HEALTH_HOME_SETTINGS` (no SDK 35 constant). Do NOT use `HealthConnectManager.ACTION_MANAGE_HEALTH_PERMISSIONS`: on the phone its activity requires `android.permission.GRANT_RUNTIME_PERMISSIONS` and crashes a normal app with `SecurityException` (found on the Pixel 8 during Phase 8).

---

## 2. FILES IN THIS PHASE (complete list)

Create:
```
core/src/main/java/com/example/identify/core/DailyHealth.java
core/src/test/java/com/example/identify/core/DailyHealthTest.java
app/src/main/java/com/example/identify/health/HealthConnectRepository.java
app/src/main/java/com/example/identify/PrivacyPolicyActivity.java
app/src/main/res/layout/activity_privacy_policy.xml
```
Modify:
```
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/example/identify/Config.java
app/src/main/java/com/example/identify/AppPrefs.java
app/src/main/java/com/example/identify/ui/ResultViewModel.java
app/src/main/java/com/example/identify/ui/SettingsFragment.java
app/src/main/res/layout/fragment_settings.xml
app/src/main/res/values/strings.xml
README.md
PROGRESS.md
```
No other files.

---

## 3. STEP 8.0: PREFLIGHT

```bash
cd /Users/ayush/Downloads/CLAUDE/IdentifyVLM
git status --short
git log --oneline | head -n 1
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew :core:test :app:assembleDebug --console=plain -q
```
VERIFY: `git status --short` prints nothing (except possibly this file, `HEALTH_CONNECT_BUILD_PROMPT.md`, as untracked; that is fine), and the build exits 0. Otherwise STOP (Section 0.1).

---

## 4. STEP 8.1: RAISE minSdk TO 34

In `app/build.gradle.kts` find:
```
        minSdk = 31
```
replace with:
```
        minSdk = 34
```

In `app/src/main/java/com/example/identify/ui/ResultViewModel.java` find:
```
            PackageManager pm = app.getPackageManager();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return pm.getPackageInfo(app.getPackageName(), PackageManager.PackageInfoFlags.of(0)).versionName;
            }
            return pm.getPackageInfo(app.getPackageName(), 0).versionName;
```
replace with:
```
            PackageManager pm = app.getPackageManager();
            return pm.getPackageInfo(app.getPackageName(), PackageManager.PackageInfoFlags.of(0)).versionName;
```
Then check whether `Build` is still used in that file:
```bash
grep -n "Build\." app/src/main/java/com/example/identify/ui/ResultViewModel.java
```
If that prints nothing, delete the line `import android.os.Build;` from the file. If it prints something, keep the import.

VERIFY: `./gradlew :app:assembleDebug` (with the variables from Rule 9) exits 0.

---

## 5. STEP 8.2: MANIFEST

In `app/src/main/AndroidManifest.xml` find:
```
    <!-- Used ONLY by ModelDownloader for the one-time model download. -->
    <uses-permission android:name="android.permission.INTERNET" />
```
replace with:
```
    <!-- Used ONLY by ModelDownloader for the one-time model download. -->
    <uses-permission android:name="android.permission.INTERNET" />

    <!-- Health Connect. Read today's steps and calories; WRITE_NUTRITION is for logging meals in a later phase. -->
    <uses-permission android:name="android.permission.health.READ_STEPS" />
    <uses-permission android:name="android.permission.health.READ_ACTIVE_CALORIES_BURNED" />
    <uses-permission android:name="android.permission.health.READ_TOTAL_CALORIES_BURNED" />
    <uses-permission android:name="android.permission.health.READ_NUTRITION" />
    <uses-permission android:name="android.permission.health.WRITE_NUTRITION" />
```

In the same file find:
```
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
```
replace with:
```
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Privacy policy. Health Connect opens it from its permission screen. -->
        <activity
            android:name=".PrivacyPolicyActivity"
            android:exported="true"
            android:label="@string/privacy_title" />

        <!-- Required by Health Connect on Android 14+, or it will not show the permission screen. -->
        <activity-alias
            android:name="ViewPermissionUsageActivity"
            android:exported="true"
            android:permission="android.permission.START_VIEW_PERMISSION_USAGE"
            android:targetActivity=".PrivacyPolicyActivity">
            <intent-filter>
                <action android:name="android.intent.action.VIEW_PERMISSION_USAGE" />
                <category android:name="android.intent.category.HEALTH_PERMISSIONS" />
            </intent-filter>
        </activity-alias>
```
The `activity-alias` must come after the activity it targets. Do not add any other permission (no `CAMERA`, no storage, no `POST_NOTIFICATIONS`, no `READ_HEALTH_DATA_IN_BACKGROUND` in this phase).

VERIFY: `grep -c "android.permission.health" app/src/main/AndroidManifest.xml` prints `5`.

---

## 6. STEP 8.3: CONFIG AND PREFS

In `app/src/main/java/com/example/identify/Config.java` find:
```
    public static final String EXP_TAG = "IdentifyExp";
```
replace with:
```
    public static final String EXP_TAG = "IdentifyExp";

    /** Logcat tag for Health Connect. */
    public static final String HEALTH_TAG = "IdentifyHealth";
    /** Daily step goal until the user can change it (later phase). */
    public static final long DEFAULT_STEP_GOAL = 10_000L;
```

In `app/src/main/java/com/example/identify/AppPrefs.java` find:
```
    private static final String KEY_IMAGE_MAX_TOKENS = "image_max_tokens";
```
replace with:
```
    private static final String KEY_IMAGE_MAX_TOKENS = "image_max_tokens";
    private static final String KEY_STEP_GOAL = "step_goal";
```
In the same file find:
```
    public void setThreads(int threads) { prefs.edit().putInt(KEY_N_THREADS, threads).apply(); }
```
replace with:
```
    public void setThreads(int threads) { prefs.edit().putInt(KEY_N_THREADS, threads).apply(); }

    public long getStepGoal() { return prefs.getLong(KEY_STEP_GOAL, Config.DEFAULT_STEP_GOAL); }

    public void setStepGoal(long goal) { prefs.edit().putLong(KEY_STEP_GOAL, goal).apply(); }
```

VERIFY: `./gradlew :app:compileDebugJavaWithJavac` exits 0.

---

## 7. STEP 8.4: :core DailyHealth (pure Java) + TEST

Create `core/src/main/java/com/example/identify/core/DailyHealth.java` with exactly:
```java
package com.example.identify.core;

/** Today's totals from Health Connect. Unknown values: steps UNKNOWN_STEPS, energies NaN. */
public final class DailyHealth {

    public static final long UNKNOWN_STEPS = -1L;

    public final long steps;
    public final double activeKcal;
    public final double burnedKcal;
    public final double eatenKcal;

    public DailyHealth(long steps, double activeKcal, double burnedKcal, double eatenKcal) {
        this.steps = steps;
        this.activeKcal = activeKcal;
        this.burnedKcal = burnedKcal;
        this.eatenKcal = eatenKcal;
    }

    /** Steps still needed to reach the goal: 0 when reached, UNKNOWN_STEPS when steps or goal are unknown. */
    public long stepsRemaining(long goal) {
        if (steps < 0 || goal <= 0) return UNKNOWN_STEPS;
        return Math.max(0L, goal - steps);
    }

    /** Progress toward the goal from 0 to 100, or -1 when unknown. */
    public int goalPercent(long goal) {
        if (steps < 0 || goal <= 0) return -1;
        return (int) Math.min(100L, steps * 100L / goal);
    }

    /** Eaten minus burned in kcal, NaN unless both are known. Positive means more eaten than burned. */
    public double netKcal() {
        if (Double.isNaN(eatenKcal) || Double.isNaN(burnedKcal)) return Double.NaN;
        return eatenKcal - burnedKcal;
    }
}
```

Create `core/src/test/java/com/example/identify/core/DailyHealthTest.java` with exactly:
```java
package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DailyHealthTest {

    @Test
    public void remainingAndPercent() {
        DailyHealth h = new DailyHealth(3200, 150, 1800, 2400);
        assertEquals(6800, h.stepsRemaining(10_000));
        assertEquals(32, h.goalPercent(10_000));
    }

    @Test
    public void goalReachedClampsToZeroAndHundred() {
        DailyHealth h = new DailyHealth(12_500, 300, 2200, 1900);
        assertEquals(0, h.stepsRemaining(10_000));
        assertEquals(100, h.goalPercent(10_000));
    }

    @Test
    public void unknownStepsOrGoal() {
        DailyHealth h = new DailyHealth(DailyHealth.UNKNOWN_STEPS, Double.NaN, Double.NaN, Double.NaN);
        assertEquals(DailyHealth.UNKNOWN_STEPS, h.stepsRemaining(10_000));
        assertEquals(-1, h.goalPercent(10_000));
        DailyHealth known = new DailyHealth(500, 0, 0, 0);
        assertEquals(DailyHealth.UNKNOWN_STEPS, known.stepsRemaining(0));
        assertEquals(-1, known.goalPercent(0));
    }

    @Test
    public void netKcal() {
        assertEquals(600.0, new DailyHealth(0, 0, 1800, 2400).netKcal(), 1e-9);
        assertTrue(Double.isNaN(new DailyHealth(0, 0, Double.NaN, 2400).netKcal()));
        assertTrue(Double.isNaN(new DailyHealth(0, 0, 1800, Double.NaN).netKcal()));
    }
}
```

VERIFY:
```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew :core:test --console=plain -q
ls core/build/test-results/test/ | grep -c "^TEST-"
grep -rn "import android" core/src && echo "FAIL: android import in core" || echo "OK: core is android-free"
```
Exit 0, the count is `10`, and it prints `OK: core is android-free`.

---

## 8. STEP 8.5: HealthConnectRepository

Create `app/src/main/java/com/example/identify/health/HealthConnectRepository.java` with exactly:
```java
package com.example.identify.health;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.HealthPermissions;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.ActiveCaloriesBurnedRecord;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.NutritionRecord;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.datatypes.TotalCaloriesBurnedRecord;
import android.health.connect.datatypes.units.Energy;
import android.os.OutcomeReceiver;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.identify.Config;
import com.example.identify.core.DailyHealth;
import com.example.identify.util.ExperimentLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/** Reads today's totals from the platform Health Connect API (Android 14+). No library, no network. */
public final class HealthConnectRepository {
    private HealthConnectRepository() {}

    /** Every permission the app asks for. WRITE_NUTRITION is used by the food logging phase. */
    public static final String[] PERMISSIONS = {
            HealthPermissions.READ_STEPS,
            HealthPermissions.READ_ACTIVE_CALORIES_BURNED,
            HealthPermissions.READ_TOTAL_CALORIES_BURNED,
            HealthPermissions.READ_NUTRITION,
            HealthPermissions.WRITE_NUTRITION,
    };

    /** today is null only when nothing could be read at all; error is null when every read succeeded. */
    public interface Callback {
        void onResult(DailyHealth today, String error);
    }

    private interface Value<T> {
        void done(T value, String error);
    }

    /** Collects the four reads. Touched only on the main thread, so it needs no locking. */
    private static final class Pending {
        int left;
        long steps = DailyHealth.UNKNOWN_STEPS;
        double activeKcal = Double.NaN;
        double burnedKcal = Double.NaN;
        double eatenKcal = Double.NaN;
        final List<String> errors = new ArrayList<>();
    }

    public static HealthConnectManager manager(Context ctx) {
        return ctx.getSystemService(HealthConnectManager.class);
    }

    public static boolean isAvailable(Context ctx) {
        return manager(ctx) != null;
    }

    public static boolean isGranted(Context ctx, String permission) {
        return ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static List<String> missingPermissions(Context ctx) {
        List<String> missing = new ArrayList<>();
        for (String p : PERMISSIONS) {
            if (!isGranted(ctx, p)) missing.add(p);
        }
        return missing;
    }

    /**
     * Health Connect's home screen, where the user manages app access. ACTION_MANAGE_HEALTH_PERMISSIONS is
     * not usable here: its activity requires android.permission.GRANT_RUNTIME_PERMISSIONS (system apps only)
     * and throws SecurityException for this app. HEALTH_HOME_SETTINGS has no SDK 35 constant, so the
     * action string is written out; it resolves to the exported, unprotected TrampolineActivity.
     */
    public static final String ACTION_HEALTH_HOME_SETTINGS = "android.health.connect.action.HEALTH_HOME_SETTINGS";

    public static Intent manageIntent(Context ctx) {
        return new Intent(ACTION_HEALTH_HOME_SETTINGS);
    }

    /** Logs what the user granted on the permission screen. */
    public static void logPermissionResult(Context ctx, Map<String, Boolean> result) {
        JSONObject e = ExperimentLog.event("health_permission_result");
        JSONArray granted = new JSONArray();
        JSONArray denied = new JSONArray();
        for (String p : PERMISSIONS) {
            if (isGranted(ctx, p)) granted.put(p);
            else denied.put(p);
        }
        ExperimentLog.put(e, "granted", granted);
        ExperimentLog.put(e, "denied", denied);
        ExperimentLog.put(e, "returned_entries", result.size());
        ExperimentLog.append(ctx, e);
        Log.i(Config.HEALTH_TAG, "permission result granted=" + granted + " denied=" + denied);
    }

    /** Reads today's totals (local midnight until now). Callback runs on the main thread. */
    public static void readToday(Context ctx, Callback cb) {
        final Context app = ctx.getApplicationContext();
        final HealthConnectManager hc = manager(app);
        if (hc == null) {
            cb.onResult(null, "Health Connect is not available on this device");
            return;
        }
        ZoneId zone = ZoneId.systemDefault();
        Instant start = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant end = Instant.now();
        TimeInstantRangeFilter range = new TimeInstantRangeFilter.Builder()
                .setStartTime(start)
                .setEndTime(end)
                .build();
        Executor main = ContextCompat.getMainExecutor(app);
        long t0 = SystemClock.elapsedRealtime();

        boolean steps = isGranted(app, HealthPermissions.READ_STEPS);
        boolean active = isGranted(app, HealthPermissions.READ_ACTIVE_CALORIES_BURNED);
        boolean burned = isGranted(app, HealthPermissions.READ_TOTAL_CALORIES_BURNED);
        boolean eaten = isGranted(app, HealthPermissions.READ_NUTRITION);

        Pending p = new Pending();
        p.left = (steps ? 1 : 0) + (active ? 1 : 0) + (burned ? 1 : 0) + (eaten ? 1 : 0);
        if (p.left == 0) {
            cb.onResult(null, "no read permission granted");
            return;
        }
        if (steps) {
            aggregate(hc, range, StepsRecord.STEPS_COUNT_TOTAL, main, (v, err) -> {
                if (v != null) p.steps = v;
                if (err != null) p.errors.add("steps: " + err);
                if (--p.left == 0) finish(app, p, t0, cb);
            });
        }
        if (active) {
            aggregate(hc, range, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, main, (v, err) -> {
                if (v != null) p.activeKcal = kcal(v);
                if (err != null) p.errors.add("active calories: " + err);
                if (--p.left == 0) finish(app, p, t0, cb);
            });
        }
        if (burned) {
            aggregate(hc, range, TotalCaloriesBurnedRecord.ENERGY_TOTAL, main, (v, err) -> {
                if (v != null) p.burnedKcal = kcal(v);
                if (err != null) p.errors.add("total calories: " + err);
                if (--p.left == 0) finish(app, p, t0, cb);
            });
        }
        if (eaten) {
            aggregate(hc, range, NutritionRecord.ENERGY_TOTAL, main, (v, err) -> {
                if (v != null) p.eatenKcal = kcal(v);
                if (err != null) p.errors.add("nutrition: " + err);
                if (--p.left == 0) finish(app, p, t0, cb);
            });
        }
    }

    private static <T> void aggregate(HealthConnectManager hc, TimeInstantRangeFilter range,
                                      AggregationType<T> type, Executor executor, Value<T> out) {
        AggregateRecordsRequest<T> request = new AggregateRecordsRequest.Builder<T>(range)
                .addAggregationType(type)
                .build();
        try {
            hc.aggregate(request, executor, new OutcomeReceiver<AggregateRecordsResponse<T>, HealthConnectException>() {
                @Override
                public void onResult(AggregateRecordsResponse<T> response) {
                    out.done(response.get(type), null);
                }

                @Override
                public void onError(HealthConnectException e) {
                    out.done(null, e.getMessage() == null ? "error " + e.getErrorCode() : e.getMessage());
                }
            });
        } catch (RuntimeException e) {   // SecurityException if access was removed a moment ago
            executor.execute(() -> out.done(null, e.toString()));
        }
    }

    /** Health Connect energy is in small calories; the app shows kilocalories. */
    private static double kcal(Energy e) {
        return e.getInCalories() / 1000.0;
    }

    private static void finish(Context app, Pending p, long t0, Callback cb) {
        DailyHealth today = new DailyHealth(p.steps, p.activeKcal, p.burnedKcal, p.eatenKcal);
        long ms = SystemClock.elapsedRealtime() - t0;
        String error = p.errors.isEmpty() ? null : String.join("; ", p.errors);

        JSONObject e = ExperimentLog.event("health_read");
        ExperimentLog.put(e, "steps", today.steps < 0 ? null : today.steps);
        ExperimentLog.put(e, "active_kcal", today.activeKcal);
        ExperimentLog.put(e, "burned_kcal", today.burnedKcal);
        ExperimentLog.put(e, "eaten_kcal", today.eatenKcal);
        ExperimentLog.put(e, "ms", ms);
        ExperimentLog.put(e, "error", error);
        ExperimentLog.append(app, e);
        Log.i(Config.HEALTH_TAG, "read today steps=" + today.steps + " active_kcal=" + today.activeKcal
                + " burned_kcal=" + today.burnedKcal + " eaten_kcal=" + today.eatenKcal + " ms=" + ms
                + " error=" + error);
        cb.onResult(today, error);
    }
}
```

VERIFY: `./gradlew :app:compileDebugJavaWithJavac` exits 0.

---

## 9. STEP 8.6: PRIVACY POLICY SCREEN

Create `app/src/main/res/layout/activity_privacy_policy.xml` with exactly:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fitsSystemWindows="true"
    android:orientation="vertical">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/toolbar"
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize" />

    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:padding="16dp"
            android:text="@string/privacy_policy_text"
            android:textAppearance="?attr/textAppearanceBodyLarge" />
    </androidx.core.widget.NestedScrollView>
</LinearLayout>
```

Create `app/src/main/java/com/example/identify/PrivacyPolicyActivity.java` with exactly:
```java
package com.example.identify;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.identify.databinding.ActivityPrivacyPolicyBinding;

/** Shown by Health Connect from its permission screen, and required for that screen to appear. */
public class PrivacyPolicyActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityPrivacyPolicyBinding binding = ActivityPrivacyPolicyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        setTitle(R.string.privacy_title);
    }
}
```

---

## 10. STEP 8.7: STRINGS AND SETTINGS UI

### 10.1 strings.xml

In `app/src/main/res/values/strings.xml` find:
```
    <string name="experiment_log_format">Experiment log: %1$d KB\n%2$s</string>
</resources>
```
replace with (no apostrophes anywhere, so nothing needs escaping):
```
    <string name="experiment_log_format">Experiment log: %1$d KB\n%2$s</string>

    <string name="health_title">Health Connect</string>
    <string name="health_connect_button">Connect Health Connect</string>
    <string name="health_refresh_button">Refresh</string>
    <string name="health_open_button">Open Health Connect</string>
    <string name="health_unavailable">Health Connect is not available on this phone.</string>
    <string name="health_not_connected">Not connected. Tap Connect to allow reading steps and calories.</string>
    <string name="health_partial">Connected with %1$d of %2$d permissions. Open Health Connect to allow the rest.</string>
    <string name="health_connected">Connected.</string>
    <string name="health_loading">Reading today from Health Connect</string>
    <string name="health_no_data">no data</string>
    <string name="health_today_format">Today: %1$s steps (goal %2$s, %3$s to go)\nBurned: %4$s kcal total, %5$s kcal active\nEaten (logged in Health Connect): %6$s kcal</string>
    <string name="health_read_failed">Could not read Health Connect: %1$s</string>
    <string name="health_denied">No access granted. Open Health Connect to allow it.</string>
    <string name="health_open_failed">Could not open Health Connect.</string>
    <string name="privacy_title">Privacy</string>
    <string name="privacy_policy_text">IdentifyVLM reads your steps, calories burned, and logged nutrition from Health Connect only to show your daily progress inside the app. In a later version it will also write meals that you confirm as nutrition records.\n\nAll data stays on this phone. Nothing is sent to any server. The app uses the internet only once, to download the AI model.\n\nYou can remove access at any time in Health Connect.</string>
</resources>
```

### 10.2 fragment_settings.xml

In `app/src/main/res/layout/fragment_settings.xml` find:
```
            android:text="@string/delete_model"
            android:visibility="gone" />
```
replace with:
```
            android:text="@string/delete_model"
            android:visibility="gone" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="@string/health_title"
            android:textAppearance="?attr/textAppearanceTitleMedium" />

        <TextView
            android:id="@+id/health_status_text"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textAppearance="?attr/textAppearanceBodyMedium" />

        <TextView
            android:id="@+id/health_today_text"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textAppearance="?attr/textAppearanceBodyMedium"
            android:visibility="gone" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:orientation="horizontal">

            <com.google.android.material.button.MaterialButton
                android:id="@+id/health_connect_button"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/health_connect_button" />

            <com.google.android.material.button.MaterialButton
                android:id="@+id/health_refresh_button"
                style="@style/Widget.Material3.Button.OutlinedButton"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:text="@string/health_refresh_button"
                android:visibility="gone" />
        </LinearLayout>

        <com.google.android.material.button.MaterialButton
            android:id="@+id/health_open_button"
            style="@style/Widget.Material3.Button.TextButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/health_open_button" />
```

### 10.3 SettingsFragment.java

Make these five edits in `app/src/main/java/com/example/identify/ui/SettingsFragment.java`.

Edit A, imports. Find:
```
import android.os.Bundle;
import android.view.LayoutInflater;
```
replace with:
```
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
```
Find:
```
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
```
replace with:
```
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
```
Find:
```
import com.example.identify.R;
import com.example.identify.databinding.FragmentSettingsBinding;
```
replace with:
```
import com.example.identify.R;
import com.example.identify.core.DailyHealth;
import com.example.identify.databinding.FragmentSettingsBinding;
import com.example.identify.health.HealthConnectRepository;
```
Find:
```
import com.google.android.material.snackbar.Snackbar;
```
replace with:
```
import com.google.android.material.snackbar.Snackbar;

import java.util.Locale;
```

Edit B, field and onCreate. Find:
```
    private int memoryHitsCorrected;

    @Nullable
    @Override
    public View onCreateView(
```
replace with:
```
    private int memoryHitsCorrected;
    private ActivityResultLauncher<String[]> healthPermissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Registered before STARTED, as the Activity Result API requires.
        healthPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    if (!isAdded()) return;
                    HealthConnectRepository.logPermissionResult(requireContext(), result);
                    if (HealthConnectRepository.missingPermissions(requireContext()).size()
                            == HealthConnectRepository.PERMISSIONS.length && binding != null) {
                        Snackbar.make(binding.getRoot(), R.string.health_denied, Snackbar.LENGTH_LONG)
                                .setAction(R.string.health_open_button, v -> openHealthConnect())
                                .show();
                    }
                    refreshHealth();
                });
    }

    @Nullable
    @Override
    public View onCreateView(
```
(If the find text does not match because `onCreateView(` is followed by parameters on the same line, match only `    private int memoryHitsCorrected;\n\n    @Nullable\n    @Override\n    public View onCreateView(` exactly as shown; the parameters after `(` stay untouched.)

Edit C, button listeners. Find:
```
        updateStats();
    }

    @Override
    public void onResume() {
        super.onResume();
        vm.startPolling();
        refreshUi();
    }
```
replace with:
```
        updateStats();

        binding.healthConnectButton.setOnClickListener(v -> {
            if (!HealthConnectRepository.isAvailable(requireContext())) {
                Snackbar.make(binding.getRoot(), R.string.health_unavailable, Snackbar.LENGTH_LONG).show();
                return;
            }
            healthPermissionLauncher.launch(HealthConnectRepository.PERMISSIONS);
        });
        binding.healthRefreshButton.setOnClickListener(v -> refreshHealth());
        binding.healthOpenButton.setOnClickListener(v -> openHealthConnect());
    }

    @Override
    public void onResume() {
        super.onResume();
        vm.startPolling();
        refreshUi();
        refreshHealth();
    }
```

Edit D, new methods. Find:
```
    private void refreshUi() {
        if (binding == null || !isAdded()) return;
```
replace with:
```
    /** Health Connect section: status line, buttons, and today's numbers. Reads once per call, not per poll. */
    private void refreshHealth() {
        if (binding == null || !isAdded()) return;
        Context ctx = requireContext();
        if (!HealthConnectRepository.isAvailable(ctx)) {
            binding.healthStatusText.setText(R.string.health_unavailable);
            binding.healthTodayText.setVisibility(View.GONE);
            binding.healthConnectButton.setVisibility(View.GONE);
            binding.healthRefreshButton.setVisibility(View.GONE);
            binding.healthOpenButton.setVisibility(View.GONE);
            return;
        }
        int needed = HealthConnectRepository.PERMISSIONS.length;
        int granted = needed - HealthConnectRepository.missingPermissions(ctx).size();
        if (granted == 0) {
            binding.healthStatusText.setText(R.string.health_not_connected);
        } else if (granted < needed) {
            binding.healthStatusText.setText(getString(R.string.health_partial, granted, needed));
        } else {
            binding.healthStatusText.setText(R.string.health_connected);
        }
        binding.healthConnectButton.setVisibility(granted < needed ? View.VISIBLE : View.GONE);
        binding.healthRefreshButton.setVisibility(granted > 0 ? View.VISIBLE : View.GONE);
        binding.healthOpenButton.setVisibility(View.VISIBLE);
        if (granted == 0) {
            binding.healthTodayText.setVisibility(View.GONE);
            return;
        }
        binding.healthTodayText.setVisibility(View.VISIBLE);
        binding.healthTodayText.setText(R.string.health_loading);
        HealthConnectRepository.readToday(ctx, (today, error) -> {
            if (binding == null || !isAdded()) return;
            if (today == null) {
                binding.healthTodayText.setText(getString(R.string.health_read_failed, String.valueOf(error)));
                return;
            }
            long goal = prefs.getStepGoal();
            String text = getString(R.string.health_today_format,
                    formatSteps(today.steps), formatSteps(goal), formatSteps(today.stepsRemaining(goal)),
                    formatKcal(today.burnedKcal), formatKcal(today.activeKcal), formatKcal(today.eatenKcal));
            if (error != null) text = text + "\n" + getString(R.string.health_read_failed, error);
            binding.healthTodayText.setText(text);
        });
    }

    private void openHealthConnect() {
        try {
            startActivity(HealthConnectRepository.manageIntent(requireContext()));
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(Config.HEALTH_TAG, "could not open Health Connect", e);
            if (binding != null) {
                Snackbar.make(binding.getRoot(), R.string.health_open_failed, Snackbar.LENGTH_LONG).show();
            }
        }
    }

    private String formatSteps(long v) {
        return v < 0 ? getString(R.string.health_no_data) : String.format(Locale.getDefault(), "%,d", v);
    }

    private String formatKcal(double v) {
        return Double.isNaN(v) ? getString(R.string.health_no_data) : String.format(Locale.getDefault(), "%,.0f", v);
    }

    private void refreshUi() {
        if (binding == null || !isAdded()) return;
```

Edit E: nothing else. Do not change `refreshUi()`, the sliders, or the stats code.

### 10.4 VERIFY 8.7
```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew :core:test :app:assembleDebug :app:lintDebug --console=plain
tail -n 1 app/build/reports/lint-results-debug.txt
```
BUILD SUCCESSFUL; the lint line says `0 errors` (warnings are fine). If lint reports an error, fix it inside the files of this phase only.

---

## 11. STEP 8.8 to 8.10: CHECKS, INSTALL, AND TEST ON THE PHONE

### 8.8 Compliance checks

Write the checks to a script file (the folder `.toolchain/` is ignored by git), then run it with bash:
```bash
cd /Users/ayush/Downloads/CLAUDE/IdentifyVLM
cat > .toolchain/phase8_checks.sh <<'EOF'
#!/bin/bash
cd /Users/ayush/Downloads/CLAUDE/IdentifyVLM
echo "## 1 kt files (must be empty)"; find app/src core/src -name "*.kt"
echo "## 2 no androidx.health library (must be empty)"; grep -rn "androidx.health\|connect-client" app core --include=*.kts --include=*.java
echo "## 3 network only in ModelDownloader (must list only ModelDownloader.java)"; grep -rln "DownloadManager\|java.net\|HttpURLConnection\|okhttp\|retrofit" app/src/main/java
echo "## 4 no android imports in core (must be empty)"; grep -rn "import android" core/src
echo "## 5 forbidden words (must be empty)"; grep -rni "reinforcement\|coroutine\|StateFlow\|kotlinx\|compose" app/src core/src
echo "## 6 no em or en dash (must be empty)"; grep -rlI $'\xe2\x80\x94\|\xe2\x80\x93' --exclude-dir=third_party --exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle --exclude-dir=.cxx --exclude-dir=.toolchain .
echo "## 7 no camera or storage permission (must be empty)"; grep -n "CAMERA\|READ_EXTERNAL\|WRITE_EXTERNAL\|READ_MEDIA" app/src/main/AndroidManifest.xml
echo "## 8 no TODO (must be empty)"; grep -rn "TODO\|FIXME\|implement later\|not implemented" app/src core/src
echo "## 9 health alias present (must print 1)"; grep -c "android.intent.category.HEALTH_PERMISSIONS" app/src/main/AndroidManifest.xml
echo "## 10 minSdk (must print minSdk = 34)"; grep -o "minSdk = [0-9]*" app/build.gradle.kts
EOF
bash .toolchain/phase8_checks.sh
```
Every check must give the stated output. Fix and rerun until they do.

### 8.9 Install on the phone

```bash
ADB=/Users/ayush/Library/Android/sdk/platform-tools/adb
"$ADB" devices
```
If no line ends in `device`, tell the user: "Connect the Pixel 8 with USB debugging on, then tell me." Wait for them. Then:
```bash
"$ADB" install -r /Users/ayush/Downloads/CLAUDE/IdentifyVLM/app/build/outputs/apk/debug/app-debug.apk
"$ADB" shell am start -n com.example.identify/.MainActivity
sleep 3
"$ADB" logcat -d -s AndroidRuntime:E | grep -A3 "com.example.identify" | tail -n 20
"$ADB" shell dumpsys package com.example.identify | grep "android.permission.health"
```
VERIFY: install prints `Success`; there is no `FATAL EXCEPTION` for `com.example.identify`; the dumpsys lines list the 5 health permissions (each `granted=false` before the user approves).

`install -r` keeps the app's data. Never uninstall: that deletes the downloaded model and the experiment log.

### 8.10 User test (you cannot tap the phone; the user does)

Send the user exactly these instructions:

1. Open IdentifyVLM, go to the Settings tab, scroll to "Health Connect". It should say "Not connected."
2. Tap "Connect Health Connect". The Health Connect permission screen opens. Tap "Allow all" (or switch on every item) and confirm.
3. Back in Settings it should say "Connected." and show today's steps, calories burned, and calories eaten.
4. Open the Fitbit or Google Fit app and compare today's step count with the app. Tell me both numbers.
5. If Settings says "no data" for steps: open Settings on the phone, then Security and privacy, Privacy, Health Connect, App permissions, Fitbit (or Fit), and allow it to write steps. Then tap "Refresh" in IdentifyVLM.

When the user reports back, run:
```bash
ADB=/Users/ayush/Library/Android/sdk/platform-tools/adb
"$ADB" shell dumpsys package com.example.identify | grep "android.permission.health"
"$ADB" shell grep "health_" /sdcard/Android/data/com.example.identify/files/experiments/experiment_log.jsonl | tail -n 5
"$ADB" logcat -d -s IdentifyHealth:I | tail -n 10
```
VERIFY:
- The granted permissions show `granted=true`.
- There is a `health_permission_result` line and at least one `health_read` line.
- `steps` in the latest `health_read` is within a few hundred of the Fitbit/Fit number (sources sync at different times; a small gap is normal).
- Unit check: `burned_kcal` must be a normal daily figure (roughly 500 to 4000 by evening). If it is about 1000 times too small (for example 1.8 instead of 1800), the platform returned kilocalories: in `HealthConnectRepository.kcal()` change `e.getInCalories() / 1000.0` to `e.getInCalories()`, rebuild, reinstall, and recheck. If it is 1000 times too large, the division is missing; restore it.

---

## 12. STEP 8.11: DOCS AND COMMIT

Append this section to the end of `README.md`:
```
## 11. Health Connect

The app reads today's steps, calories burned (total and active), and calories eaten (nutrition logged by any app) from Health Connect, which is built into Android 14 and newer. It uses the platform API in `android.health.connect`, so there is no extra library and no network use. This is why the minimum Android version is now 14.

Permissions (each one approved by the user on the Health Connect screen): READ_STEPS, READ_ACTIVE_CALORIES_BURNED, READ_TOTAL_CALORIES_BURNED, READ_NUTRITION, and WRITE_NUTRITION (for logging meals in a later version). Health Connect only shows its permission screen for apps that declare a privacy policy screen, which is `PrivacyPolicyActivity` behind the `ViewPermissionUsageActivity` alias in the manifest.

Settings > Health Connect shows the connection status and today's numbers. Step data comes from whichever app writes it into Health Connect (Fitbit or Google Fit on the test phone), so that app's Health Connect sync must be on. Every read is logged in the experiment log as a `health_read` event, and the permission screen outcome as `health_permission_result`.
```

Append to `PROGRESS.md` under `## Phase 8: Health Connect` one line per step 8.0 to 8.10, with the real verify results (for 8.10, the steps from the app and from Fitbit/Fit).

Then:
```bash
cd /Users/ayush/Downloads/CLAUDE/IdentifyVLM
git add -A
git commit -m "Phase 8: Health Connect connection"
git log --oneline | head -n 1
```

Final message to the user: what was built, the permissions requested, the lint and test results, the steps the app read versus Fitbit/Fit, and anything written to `BLOCKERS.md`.

---

## 13. TROUBLESHOOTING (apply the matching fix)

| Symptom | Fix |
|---|---|
| `cannot find symbol HealthConnectManager` or another `android.health.connect` class | `compileSdk` must be 35 in `app/build.gradle.kts`. Do not add a library. |
| Tapping Connect does nothing, or returns at once with everything denied and no screen | The alias is missing or wrong. Check Section 5 character by character: action `android.intent.action.VIEW_PERMISSION_USAGE`, category `android.intent.category.HEALTH_PERMISSIONS`, permission `android.permission.START_VIEW_PERMISSION_USAGE`, `android:exported="true"`, alias placed after `PrivacyPolicyActivity`. Then run `"$ADB" logcat -d | grep -i "healthconnect\|PermissionController" | tail -n 30` and read the error. |
| The user denied once and the screen no longer appears | Android stops showing it after repeated denials. The user taps "Open Health Connect" in Settings and allows access there. |
| `SecurityException` in logcat during a read | Access was removed. The repository already catches it and shows the error; the user reconnects. |
| Steps show "no data" but Fitbit shows steps | Fitbit/Fit is not writing to Health Connect. See 8.10 item 5. |
| Calories about 1000x off | See the unit check in 8.10. |
| Lint error on the alias or permissions | Read the message and fix only Phase 8 files. Do not suppress lint globally. |
| `SecurityException ... requires android.permission.GRANT_RUNTIME_PERMISSIONS` when tapping Open Health Connect | Use the `HEALTH_HOME_SETTINGS` action from Section 8, not `ACTION_MANAGE_HEALTH_PERMISSIONS`, and keep `SecurityException` in the catch. |
| `ActivityResultLauncher` crash "register before STARTED" | The launcher must be registered in `onCreate` (Edit B), not in a click listener. |

---

## 14. LATER PHASES (do NOT implement now; listed so the plan is visible)

- Phase 9, food logging: when a photo is identified as food, look the label up in a nutrition table shipped inside the app (assets JSON built from USDA FoodData Central plus published restaurant values such as McDonald's), let the user confirm the portion, then write a `NutritionRecord` with `Energy.fromCalories(kcal * 1000)` (same unit rule as above), `setMealName(label)`, and a meal type from the time of day (`MealType.MEAL_TYPE_BREAKFAST/LUNCH/DINNER/SNACK`). The model only names the food; it does not invent calorie numbers.
- Phase 10, nudges: compare eaten versus burned and steps versus goal after each logged meal, and show a message such as "Ayush, you are at 3,200 of 10,000 steps and this double cheeseburger is about 740 kcal. A 25 minute walk would burn about 150 kcal." Needs `POST_NOTIFICATIONS` for notifications and `READ_HEALTH_DATA_IN_BACKGROUND` for checks while the app is closed, plus a step goal setting.
- Phase 11, capture: Android does not allow silent background photos. Options are a quick-capture button or widget, or optionally scanning new gallery photos (needs a photo-library permission, which the current spec forbids, so it needs the user's decision first).
