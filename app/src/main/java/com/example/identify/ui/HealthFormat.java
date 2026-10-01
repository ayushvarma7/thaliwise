package com.example.identify.ui;

import android.content.Context;

import com.example.identify.R;
import com.example.identify.core.Meals;

import java.util.Locale;

/** Number and label formatting shared by the Settings and Result screens. Unknown values show "no data". */
final class HealthFormat {
    private HealthFormat() {}

    static String steps(Context ctx, long v) {
        return v < 0 ? ctx.getString(R.string.health_no_data) : String.format(Locale.getDefault(), "%,d", v);
    }

    static String kcal(Context ctx, double v) {
        return Double.isNaN(v) ? ctx.getString(R.string.health_no_data) : String.format(Locale.getDefault(), "%,.0f", v);
    }

    static String slot(Context ctx, Meals.Slot slot) {
        switch (slot) {
            case BREAKFAST: return ctx.getString(R.string.meal_slot_breakfast);
            case LUNCH: return ctx.getString(R.string.meal_slot_lunch);
            case DINNER: return ctx.getString(R.string.meal_slot_dinner);
            default: return ctx.getString(R.string.meal_slot_snack);
        }
    }
}
