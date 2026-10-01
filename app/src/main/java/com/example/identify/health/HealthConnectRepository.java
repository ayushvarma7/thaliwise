package com.example.identify.health;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.HealthPermissions;
import android.health.connect.InsertRecordsResponse;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsResponse;
import android.health.connect.RecordIdFilter;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.ActiveCaloriesBurnedRecord;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.MealType;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.NutritionRecord;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.datatypes.TotalCaloriesBurnedRecord;
import android.health.connect.datatypes.units.Energy;
import android.health.connect.datatypes.units.Mass;
import android.os.OutcomeReceiver;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.identify.Config;
import com.example.identify.core.DailyHealth;
import com.example.identify.core.MealEntry;
import com.example.identify.core.Meals;
import com.example.identify.util.ExperimentLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    /** recordId is the Health Connect id of the new record; on failure it is null and error says why. Main thread. */
    public interface WriteCallback {
        void onResult(String recordId, String error);
    }

    /** error is null on success. Main thread. */
    public interface DeleteCallback {
        void onResult(String error);
    }

    public static int mealType(Meals.Slot slot) {
        switch (slot) {
            case BREAKFAST: return MealType.MEAL_TYPE_BREAKFAST;
            case LUNCH: return MealType.MEAL_TYPE_LUNCH;
            case DINNER: return MealType.MEAL_TYPE_DINNER;
            default: return MealType.MEAL_TYPE_SNACK;
        }
    }

    /**
     * Writes one meal as a NutritionRecord covering the last minute (the record type has a start and an
     * end). Energy goes in as small calories, the same unit the reads divide by 1000. NaN macros are skipped.
     */
    public static void insertMeal(Context ctx, String mealName, double kcal, double proteinG, double carbsG,
                                  double fatG, Meals.Slot slot, WriteCallback cb) {
        final Context app = ctx.getApplicationContext();
        final HealthConnectManager hc = manager(app);
        if (hc == null) {
            cb.onResult(null, "Health Connect is not available on this device");
            return;
        }
        if (!isGranted(app, HealthPermissions.WRITE_NUTRITION)) {
            cb.onResult(null, "nutrition write permission not granted");
            return;
        }
        Instant end = Instant.now();
        Instant start = end.minusSeconds(60);
        ZoneOffset offset = ZoneId.systemDefault().getRules().getOffset(end);
        Metadata metadata = new Metadata.Builder()
                .setClientRecordId("identifyvlm-meal-" + UUID.randomUUID())
                .setRecordingMethod(Metadata.RECORDING_METHOD_MANUAL_ENTRY)
                .build();
        NutritionRecord.Builder b = new NutritionRecord.Builder(metadata, start, end)
                .setStartZoneOffset(offset)
                .setEndZoneOffset(offset)
                .setMealName(mealName)
                .setMealType(mealType(slot))
                .setEnergy(Energy.fromCalories(kcal * 1000.0));
        if (!Double.isNaN(proteinG)) b.setProtein(Mass.fromGrams(proteinG));
        if (!Double.isNaN(carbsG)) b.setTotalCarbohydrate(Mass.fromGrams(carbsG));
        if (!Double.isNaN(fatG)) b.setTotalFat(Mass.fromGrams(fatG));
        List<Record> records = Collections.<Record>singletonList(b.build());
        Executor main = ContextCompat.getMainExecutor(app);
        try {
            hc.insertRecords(records, main, new OutcomeReceiver<InsertRecordsResponse, HealthConnectException>() {
                @Override
                public void onResult(InsertRecordsResponse response) {
                    List<Record> saved = response.getRecords();
                    String id = saved.isEmpty() ? null : saved.get(0).getMetadata().getId();
                    Log.i(Config.HEALTH_TAG, "meal written id=" + id + " kcal=" + kcal + " name=" + mealName);
                    cb.onResult(id, id == null ? "Health Connect returned no record" : null);
                }

                @Override
                public void onError(HealthConnectException e) {
                    Log.w(Config.HEALTH_TAG, "meal write failed", e);
                    cb.onResult(null, e.getMessage() == null ? "error " + e.getErrorCode() : e.getMessage());
                }
            });
        } catch (RuntimeException e) {   // SecurityException if access was removed a moment ago
            Log.w(Config.HEALTH_TAG, "meal write failed", e);
            main.execute(() -> cb.onResult(null, e.toString()));
        }
    }

    /** Deletes a meal this app wrote, by its Health Connect id. */
    public static void deleteMeal(Context ctx, String recordId, DeleteCallback cb) {
        final Context app = ctx.getApplicationContext();
        final HealthConnectManager hc = manager(app);
        if (hc == null) {
            cb.onResult("Health Connect is not available on this device");
            return;
        }
        Executor main = ContextCompat.getMainExecutor(app);
        List<RecordIdFilter> ids = Collections.singletonList(RecordIdFilter.fromId(NutritionRecord.class, recordId));
        try {
            hc.deleteRecords(ids, main, new OutcomeReceiver<Void, HealthConnectException>() {
                @Override
                public void onResult(Void unused) {
                    Log.i(Config.HEALTH_TAG, "meal deleted id=" + recordId);
                    cb.onResult(null);
                }

                @Override
                public void onError(HealthConnectException e) {
                    Log.w(Config.HEALTH_TAG, "meal delete failed", e);
                    cb.onResult(e.getMessage() == null ? "error " + e.getErrorCode() : e.getMessage());
                }
            });
        } catch (RuntimeException e) {
            Log.w(Config.HEALTH_TAG, "meal delete failed", e);
            main.execute(() -> cb.onResult(e.toString()));
        }
    }

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
