package com.example.identify.ui;

import android.content.Context;

import com.example.identify.core.Tip;
import com.example.identify.util.ExperimentLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/** nudge_shown and nudge_dismissed events: which tips a screen showed, for tuning the rules later. */
final class CoachEvents {
    private CoachEvents() {}

    /** The tip kinds, for example ["walk","over_budget"]. */
    static JSONArray kinds(List<Tip> tips) {
        JSONArray a = new JSONArray();
        for (Tip t : tips) a.put(t.kind.name().toLowerCase(Locale.ROOT));
        return a;
    }

    static void shown(Context ctx, String screen, String runId, String foodId, List<Tip> tips) {
        log(ctx, "nudge_shown", screen, runId, foodId, tips);
    }

    static void dismissed(Context ctx, String screen, String runId, List<Tip> tips) {
        log(ctx, "nudge_dismissed", screen, runId, null, tips);
    }

    private static void log(Context ctx, String type, String screen, String runId, String foodId, List<Tip> tips) {
        JSONArray details = new JSONArray();
        for (Tip t : tips) details.put(t.toString());
        JSONObject e = ExperimentLog.event(type);
        ExperimentLog.put(e, "screen", screen);
        ExperimentLog.put(e, "run_id", runId);
        ExperimentLog.put(e, "food_id", foodId);
        ExperimentLog.put(e, "kinds", kinds(tips));
        ExperimentLog.put(e, "tips", details);
        ExperimentLog.append(ctx.getApplicationContext(), e);
    }
}
