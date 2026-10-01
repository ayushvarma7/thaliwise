package com.example.identify.core;

import java.util.Locale;

/** Meal slot from the clock, and portion arithmetic for logging a meal. */
public final class Meals {
    private Meals() {}

    public enum Slot { BREAKFAST, LUNCH, DINNER, SNACK }

    /** 5 to 10 breakfast, 11 to 14 lunch, 17 to 21 dinner, anything else snack. */
    public static Slot slotForHour(int hour) {
        if (hour >= 5 && hour <= 10) return Slot.BREAKFAST;
        if (hour >= 11 && hour <= 14) return Slot.LUNCH;
        if (hour >= 17 && hour <= 21) return Slot.DINNER;
        return Slot.SNACK;
    }

    /** The portion slider runs 1 to 6 in whole steps: 0.5x to 3x servings. */
    public static double portionFromSlider(float value) {
        return value / 2.0;
    }

    public static long scaledKcal(FoodItem food, double portion) {
        return Math.round(food.kcal * portion);
    }

    /** One decimal place, for grams of protein, carbohydrate, and fat. */
    public static double scaled(double perServing, double portion) {
        return Math.round(perServing * portion * 10.0) / 10.0;
    }

    /** "1", "1.5", "0.5". */
    public static String formatPortion(double portion) {
        if (portion == Math.rint(portion)) return String.valueOf((long) portion);
        return String.format(Locale.ROOT, "%.1f", portion);
    }
}
