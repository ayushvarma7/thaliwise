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

    /** Opens this app's page inside Health Connect, where the user can change access. */
    public static Intent manageIntent(Context ctx) {
        Intent i = new Intent(HealthConnectManager.ACTION_MANAGE_HEALTH_PERMISSIONS);
        i.putExtra(Intent.EXTRA_PACKAGE_NAME, ctx.getPackageName());
        return i;
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
