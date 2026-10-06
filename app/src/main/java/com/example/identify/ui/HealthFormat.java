package com.example.identify.ui;

import android.content.Context;

import com.example.identify.R;
import com.example.identify.core.Meals;
import com.example.identify.core.UserProfile;

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

    static String goal(Context ctx, UserProfile.Goal goal) {
        switch (goal) {
            case LOSE: return ctx.getString(R.string.goal_lose);
            case MAINTAIN: return ctx.getString(R.string.goal_maintain);
            case GAIN: return ctx.getString(R.string.goal_gain);
            case EAT_HEALTHIER: return ctx.getString(R.string.goal_eat_healthier);
            default: return ctx.getString(R.string.goal_track);
        }
    }

    /** "1,234 steps", or "no data" without a unit. */
    static String stepsWithUnit(Context ctx, long v) {
        return v < 0 ? ctx.getString(R.string.health_no_data) : ctx.getString(R.string.steps_with_unit, steps(ctx, v));
    }

    /** "1,234 kcal", or "no data" without a unit. */
    static String kcalWithUnit(Context ctx, double v) {
        return Double.isNaN(v) ? ctx.getString(R.string.health_no_data) : ctx.getString(R.string.kcal_with_unit, kcal(ctx, v));
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
