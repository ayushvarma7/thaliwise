package com.example.identify.ui;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.identify.R;
import com.example.identify.core.FoodItem;
import com.example.identify.core.FoodMatcher;
import com.example.identify.core.Meals;
import com.example.identify.databinding.DialogLogMealBinding;
import com.example.identify.health.FoodRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Picks the food (table rows matching the text), the portion, and the calories to log. Calories start at
 * the table value for the portion; the user may edit them. Nothing is written here: the listener does that.
 */
final class LogMealDialog {

    interface Listener {
        void onLog(FoodItem food, double portion, double kcal, boolean kcalEdited, String query);
    }

    static final int MAX_CHOICES = 5;
    static final double MAX_KCAL = 5000;

    private final Context ctx;
    private final Listener listener;
    private final DialogLogMealBinding b;
    private final List<FoodItem> foods;
    private final List<FoodItem> shown = new ArrayList<>();
    private final List<Integer> radioIds = new ArrayList<>();
    private FoodItem selected;
    private double portion = 1.0;
    private long suggestedKcal;

    private LogMealDialog(Context ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
        this.b = DialogLogMealBinding.inflate(LayoutInflater.from(ctx));
        this.foods = FoodRepository.foods(ctx);
    }

    static void show(Fragment fragment, String initialQuery, Listener listener) {
        new LogMealDialog(fragment.requireContext(), listener).open(initialQuery);
    }

    private void open(String initialQuery) {
        Meals.Slot slot = Meals.slotForHour(LocalTime.now().getHour());
        b.mealSlotText.setText(ctx.getString(R.string.meal_slot_format, HealthFormat.slot(ctx, slot)));
        b.mealPortionSlider.setLabelFormatter(v -> Meals.formatPortion(Meals.portionFromSlider(v)) + "x");
        b.mealPortionSlider.addOnChangeListener((slider, value, fromUser) -> {
            portion = Meals.portionFromSlider(value);
            refreshValues();
        });
        b.mealChoices.setOnCheckedChangeListener((group, checkedId) -> {
            int i = radioIds.indexOf(checkedId);
            selected = i >= 0 ? shown.get(i) : null;
            refreshValues();
        });
        b.mealSearchInput.setText(initialQuery);
        b.mealSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                refreshChoices(s.toString());
            }
        });
        refreshChoices(initialQuery);

        AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.meal_dialog_title)
                .setView(b.getRoot())
                .setPositiveButton(R.string.meal_log_confirm, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        // Validate before closing: a positive button set in the builder would always dismiss.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (confirm()) dialog.dismiss();
        }));
        dialog.show();
    }

    private void refreshChoices(String query) {
        b.mealChoices.removeAllViews();
        shown.clear();
        radioIds.clear();
        for (FoodMatcher.Match m : FoodMatcher.match(query, foods, MAX_CHOICES)) {
            MaterialRadioButton rb = new MaterialRadioButton(ctx);
            int id = View.generateViewId();
            rb.setId(id);
            rb.setText(ctx.getString(R.string.meal_choice_format, m.item.displayName(), m.item.serving,
                    HealthFormat.kcal(ctx, m.item.kcal)));
            b.mealChoices.addView(rb);
            shown.add(m.item);
            radioIds.add(id);
        }
        b.mealNoMatchText.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        if (shown.isEmpty()) {
            b.mealChoices.clearCheck();
            selected = null;
            refreshValues();
        } else {
            b.mealChoices.check(radioIds.get(0));   // fires the listener, which selects and refreshes
        }
    }

    private void refreshValues() {
        if (selected == null) {
            b.mealPortionLabel.setText(null);
            b.mealKcalInput.setText(null);
            b.mealSourceText.setText(null);
            return;
        }
        b.mealPortionLabel.setText(ctx.getString(R.string.meal_portion_label, Meals.formatPortion(portion), selected.serving));
        suggestedKcal = Meals.scaledKcal(selected, portion);
        b.mealKcalInput.setText(String.valueOf(suggestedKcal));
        b.mealKcalLayout.setError(null);
        b.mealSourceText.setText(ctx.getString(R.string.meal_source_format, selected.source));
    }

    private boolean confirm() {
        if (selected == null) {
            b.mealNoMatchText.setVisibility(View.VISIBLE);
            return false;
        }
        double kcal;
        try {
            kcal = Double.parseDouble(String.valueOf(b.mealKcalInput.getText()).trim());
        } catch (NumberFormatException e) {
            kcal = -1;
        }
        if (!(kcal >= 1 && kcal <= MAX_KCAL)) {
            b.mealKcalLayout.setError(ctx.getString(R.string.meal_kcal_invalid));
            return false;
        }
        String query = String.valueOf(b.mealSearchInput.getText()).trim();
        listener.onLog(selected, portion, kcal, Math.round(kcal) != suggestedKcal, query);
        return true;
    }
}
