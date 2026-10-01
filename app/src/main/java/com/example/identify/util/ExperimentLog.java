package com.example.identify.util;

import android.content.Context;
import android.util.Log;

import com.example.identify.App;
import com.example.identify.Config;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Append-only experiment log: one JSON object per line in
 * Android/data/com.example.identify/files/experiments/experiment_log.jsonl (pull it with adb).
 * Every write happens on the IO thread, so lines never interleave.
 */
public final class ExperimentLog {
    private ExperimentLog() {}

    public static File file(Context ctx) {
        return new File(new File(ctx.getExternalFilesDir(null), Config.EXPERIMENT_DIR), Config.EXPERIMENT_LOG_FILE);
    }

    /** A new event with its type and wall-clock timestamps. */
    public static JSONObject event(String type) {
        JSONObject o = new JSONObject();
        long now = System.currentTimeMillis();
        put(o, "type", type);
        put(o, "ts", now);
        put(o, "ts_iso", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(new Date(now)));
        return o;
    }

    public static void append(Context ctx, JSONObject event) {
        final Context app = ctx.getApplicationContext();
        final String line = event.toString();
        Log.d(Config.EXP_TAG, "event type=" + event.optString("type") + " bytes=" + line.length());
        App.runOnIoThread(() -> write(app, line));
    }

    /** Deletes the log. Posted to the IO thread so it runs after any pending appends; then runs onDone there. */
    public static void delete(Context ctx, Runnable onDone) {
        final Context app = ctx.getApplicationContext();
        App.runOnIoThread(() -> {
            File f = file(app);
            if (f.exists() && !f.delete()) Log.w(Config.EXP_TAG, "could not delete " + f);
            onDone.run();
        });
    }

    public static long sizeBytes(Context ctx) {
        File f = file(ctx);
        return f.exists() ? f.length() : 0L;
    }

    private static void write(Context ctx, String line) {
        File f = file(ctx);
        File dir = f.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            Log.w(Config.EXP_TAG, "could not create " + dir);
            return;
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8)) {
            w.write(line);
            w.write('\n');
        } catch (IOException e) {
            Log.w(Config.EXP_TAG, "could not append to " + f, e);
        }
    }

    /** JSONObject.put that never throws. NaN and infinite numbers become null. */
    public static void put(JSONObject o, String key, Object value) {
        Object v = value;
        if (v instanceof Double && (((Double) v).isNaN() || ((Double) v).isInfinite())) v = JSONObject.NULL;
        if (v instanceof Float && (((Float) v).isNaN() || ((Float) v).isInfinite())) v = JSONObject.NULL;
        try {
            o.put(key, v == null ? JSONObject.NULL : v);
        } catch (JSONException e) {
            Log.w(Config.EXP_TAG, "could not put " + key, e);
        }
    }

    /** Parses JSON from the native layer; on failure returns an object that records the error. */
    public static JSONObject parse(String json) {
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            JSONObject o = new JSONObject();
            put(o, "parse_error", e.getMessage());
            return o;
        }
    }
}
