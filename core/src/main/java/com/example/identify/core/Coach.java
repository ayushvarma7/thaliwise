package com.example.identify.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Coach tips (docs/USER_STORIES.md E8) from the profile, today's Health Connect numbers, and one meal.
 * Tips inform and never block logging. Unknown numbers (no Health Connect data) simply skip the rules that
 * need them. Pure Java, no Android.
 */
public final class Coach {
    private Coach() {}

    public static final int MAX_MEAL_TIPS = 3;
    /** A meal at least this big gets a walk tip while the step goal is not reached. */
    public static final double WALK_MEAL_KCAL = 400;
    public static final long MIN_WALK_MINUTES = 10;
    public static final long MAX_WALK_MINUTES = 30;
    public static final double GOOD_PROTEIN_G = 25;
    public static final double BIG_PORTION_KCAL = 700;
    /** From this hour on, tips talk about the rest of the day. */
    public static final int EVENING_HOUR = 17;

    // "Eat more of" labels, exactly the items of onboarding_eat_more in res/values/arrays.xml.
    public static final String MORE_PROTEIN = "More protein";
    public static final String MORE_VEGETABLES = "More vegetables";
    public static final String LESS_SUGAR = "Less sugar";
    public static final String FEWER_FRIED = "Fewer fried foods";
    public static final String SMALLER_PORTIONS = "Smaller portions";

    /** Today so far. Unknown values: steps below 0, eatenKcal NaN. hour is 0 to 23, local time. */
    public static final class Day {
        public final long steps;
        public final double eatenKcal;
        public final int hour;

        public Day(long steps, double eatenKcal, int hour) {
            this.steps = steps;
            this.eatenKcal = eatenKcal;
            this.hour = hour;
        }
    }

    /** One meal at the portion being considered. tags may be empty (unknown food). */
    public static final class Meal {
        public final double kcal;
        public final double proteinG;
        public final Set<FoodTag> tags;

        public Meal(double kcal, double proteinG, Set<FoodTag> tags) {
            this.kcal = kcal;
            this.proteinG = proteinG;
            this.tags = tags == null ? Collections.emptySet() : tags;
        }
    }

    /** Tips for the result screen before logging, most important first, at most MAX_MEAL_TIPS. */
    public static List<Tip> forMeal(UserProfile p, long budget, long stepGoal, Day day, Meal meal) {
        List<Tip> tips = new ArrayList<>();
        for (DietRules.Conflict c : DietRules.conflicts(p.diet, meal.tags)) {
            tips.add(Tip.diet(c.diet, c.tag));
        }
        boolean eatenKnown = !Double.isNaN(day.eatenKcal);
        double after = eatenKnown ? day.eatenKcal + meal.kcal : Double.NaN;
        boolean over = eatenKnown && after > budget;
        if (day.steps >= 0 && stepGoal > 0 && day.steps < stepGoal && (over || meal.kcal >= WALK_MEAL_KCAL)) {
            long minutes = Math.min(MAX_WALK_MINUTES,
                    Math.max(MIN_WALK_MINUTES, WalkMath.minutesForSteps(stepGoal - day.steps)));
            tips.add(Tip.of(Tip.Kind.WALK, day.steps, stepGoal, Math.round(meal.kcal), minutes,
                    minutes * WalkMath.STEPS_PER_MINUTE, WalkMath.kcalForMinutes(minutes, p.weightKg)));
        }
        if (over) tips.add(Tip.of(Tip.Kind.OVER_BUDGET, Math.round(after - budget), budget));
        Set<String> more = p.eatMore;
        if (more.contains(MORE_PROTEIN) && meal.proteinG >= GOOD_PROTEIN_G) {
            tips.add(Tip.of(Tip.Kind.PROTEIN_GOOD, Math.round(meal.proteinG)));
        }
        if (more.contains(MORE_VEGETABLES) && meal.tags.contains(FoodTag.VEG)) tips.add(Tip.of(Tip.Kind.VEGGIE_GOOD));
        if (more.contains(FEWER_FRIED) && meal.tags.contains(FoodTag.FRIED)) tips.add(Tip.of(Tip.Kind.FRIED_NOTE));
        if (more.contains(LESS_SUGAR) && meal.tags.contains(FoodTag.SWEET)) tips.add(Tip.of(Tip.Kind.SWEET_NOTE));
        if (more.contains(SMALLER_PORTIONS) && meal.kcal >= BIG_PORTION_KCAL) {
            tips.add(Tip.of(Tip.Kind.PORTION_NOTE, Math.round(meal.kcal / 2)));
        }
        if (p.goal == UserProfile.Goal.GAIN && eatenKnown && !over && day.hour >= EVENING_HOUR) {
            tips.add(Tip.of(Tip.Kind.BUDGET_LEFT, Math.round(budget - after)));
        }
        return tips.size() > MAX_MEAL_TIPS ? new ArrayList<>(tips.subList(0, MAX_MEAL_TIPS)) : tips;
    }

    /** One tip for the Today screen, or null when there is nothing useful to say. */
    public static Tip forDay(UserProfile p, long budget, long stepGoal, Day day) {
        boolean eatenKnown = !Double.isNaN(day.eatenKcal);
        if (eatenKnown && day.eatenKcal > budget) {
            return Tip.of(Tip.Kind.DAY_OVER_BUDGET, Math.round(day.eatenKcal - budget), MAX_WALK_MINUTES,
                    WalkMath.kcalForMinutes(MAX_WALK_MINUTES, p.weightKg));
        }
        if (day.steps >= 0 && stepGoal > 0) {
            if (day.steps >= stepGoal) return Tip.of(Tip.Kind.DAY_GOAL_REACHED, day.steps);
            if (day.hour >= EVENING_HOUR) {
                long left = stepGoal - day.steps;
                return Tip.of(Tip.Kind.DAY_STEPS_LEFT, left, WalkMath.minutesForSteps(left));
            }
        }
        if (eatenKnown && day.eatenKcal > 0) return Tip.of(Tip.Kind.DAY_ON_TRACK, Math.round(budget - day.eatenKcal));
        return null;
    }
}
