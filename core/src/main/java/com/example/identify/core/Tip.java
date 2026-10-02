package com.example.identify.core;

import java.util.Arrays;
import java.util.Locale;

/** One coach message as data. The app writes the sentence; n(i) holds the numbers listed for each kind. */
public final class Tip {

    public enum Kind {
        /** diet and tag are set; no numbers. */
        DIET_CONFLICT,
        /** steps today, step goal, meal kcal, walk minutes, walk steps, walk kcal. */
        WALK,
        /** kcal over the budget after this meal, budget. */
        OVER_BUDGET,
        /** protein grams in the meal. */
        PROTEIN_GOOD,
        /** no numbers. */
        VEGGIE_GOOD,
        /** no numbers. */
        FRIED_NOTE,
        /** no numbers. */
        SWEET_NOTE,
        /** kcal of a half portion. */
        PORTION_NOTE,
        /** kcal left in the budget after this meal. */
        BUDGET_LEFT,
        /** kcal over today, walk minutes, walk kcal. */
        DAY_OVER_BUDGET,
        /** steps today. */
        DAY_GOAL_REACHED,
        /** steps left today, walk minutes. */
        DAY_STEPS_LEFT,
        /** kcal left today. */
        DAY_ON_TRACK
    }

    public final Kind kind;
    /** DIET_CONFLICT only, otherwise "". */
    public final String diet;
    /** DIET_CONFLICT only, otherwise null. */
    public final FoodTag tag;
    private final long[] numbers;

    private Tip(Kind kind, String diet, FoodTag tag, long[] numbers) {
        this.kind = kind;
        this.diet = diet;
        this.tag = tag;
        this.numbers = numbers;
    }

    public static Tip of(Kind kind, long... numbers) {
        return new Tip(kind, "", null, numbers.clone());
    }

    public static Tip diet(String diet, FoodTag tag) {
        return new Tip(Kind.DIET_CONFLICT, diet, tag, new long[0]);
    }

    public long n(int i) {
        return numbers[i];
    }

    public int count() {
        return numbers.length;
    }

    /** For the experiment log and test messages, for example "walk[3200, 10000, 450, 30, 3000, 141]". */
    @Override
    public String toString() {
        String k = kind.name().toLowerCase(Locale.ROOT);
        if (kind == Kind.DIET_CONFLICT) return k + "[" + diet + ", " + tag.key() + "]";
        return k + Arrays.toString(numbers);
    }
}
