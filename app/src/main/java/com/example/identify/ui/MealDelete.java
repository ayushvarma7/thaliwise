package com.example.identify.ui;

import android.content.Context;
import android.view.View;

import androidx.fragment.app.Fragment;

import com.example.identify.R;
import com.example.identify.core.MealEntry;
import com.example.identify.health.HealthConnectRepository;
import com.example.identify.util.ExperimentLog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONObject;

/** Tap on a meal in Today or Diary: delete it if this app wrote it, otherwise explain where to delete it. */
final class MealDelete {
    private MealDelete() {}

    static void confirm(Fragment fragment, MealEntry meal, String screen, Runnable onDeleted) {
        View root = fragment.getView();
        if (root == null) return;
        final Context ctx = fragment.requireContext();
        if (!meal.mine) {
            Snackbar.make(root, R.string.meal_not_mine, Snackbar.LENGTH_LONG).show();
            return;
        }
        String name = meal.name.isEmpty() ? ctx.getString(R.string.meal_unnamed) : meal.name;
        new MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.meal_delete_title)
                .setMessage(ctx.getString(R.string.meal_delete_message, name, HealthFormat.kcal(ctx, meal.kcal)))
                .setPositiveButton(R.string.meal_delete_ok, (dialog, which) -> delete(fragment, meal, screen, onDeleted))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void delete(Fragment fragment, MealEntry meal, String screen, Runnable onDeleted) {
        final Context app = fragment.requireContext().getApplicationContext();
        HealthConnectRepository.deleteMeal(app, meal.id, error -> {
            JSONObject e = ExperimentLog.event("meal_deleted");
            ExperimentLog.put(e, "hc_record_id", meal.id);
            ExperimentLog.put(e, "screen", screen);
            ExperimentLog.put(e, "kcal", meal.kcal);
            ExperimentLog.put(e, "error", error);
            ExperimentLog.append(app, e);
            View root = fragment.getView();
            if (!fragment.isAdded() || root == null) return;
            if (error != null) {
                Snackbar.make(root, app.getString(R.string.meal_delete_failed, error), Snackbar.LENGTH_LONG).show();
                return;
            }
            Snackbar.make(root, R.string.meal_deleted, Snackbar.LENGTH_SHORT).show();
            onDeleted.run();
        });
    }
}
