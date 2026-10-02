# STEP 11.2: Coach rules in :core (walk math, tips, meal and day rules)

Goal: the rules behind every coach message, as pure Java with tests (US-8.1 to US-8.5). The coach returns tips as data (a kind plus numbers); the app turns them into sentences with string resources in step 11.3, so wording and translations stay out of `:core`.

Rules for a meal (`Coach.forMeal`), checked in this order, at most 3 tips:

| Order | Tip | When |
|---|---|---|
| 1 | `DIET_CONFLICT` (diet, tag) | the food usually contains something a chosen diet avoids (DietRules); one per diet, a tag only once |
| 2 | `WALK` (steps, goal, meal kcal, minutes, walk steps, walk kcal) | steps are known, below the goal, and the meal is at least 400 kcal or takes the day over budget |
| 3 | `OVER_BUDGET` (kcal over, budget) | eaten today is known and eaten plus this meal is above the budget |
| 4 | `PROTEIN_GOOD` (grams) | "More protein" chosen and the meal has at least 25 g |
| 5 | `VEGGIE_GOOD` | "More vegetables" chosen and the food is tagged veg |
| 6 | `FRIED_NOTE` | "Fewer fried foods" chosen and the food is tagged fried |
| 7 | `SWEET_NOTE` | "Less sugar" chosen and the food is tagged sweet |
| 8 | `PORTION_NOTE` (half portion kcal) | "Smaller portions" chosen and the meal is at least 700 kcal |
| 9 | `BUDGET_LEFT` (kcal left) | goal is Build muscle, eaten is known, not over budget, and it is 5 PM or later |

Walk numbers: moderate walking, 3.5 MET and 100 steps a minute; kcal per minute = 3.5 x 3.5 x weight kg / 200 (ACSM), 70 kg when the weight is unknown. The meal walk is the time to finish the step goal, rounded up to 5 minutes and kept between 10 and 30 minutes.

Rules for the Today screen (`Coach.forDay`), the first that applies, at most 1 tip: `DAY_OVER_BUDGET` (eaten above budget; with a 30 minute walk), `DAY_GOAL_REACHED` (steps at or above the goal), `DAY_STEPS_LEFT` (5 PM or later and steps below the goal), `DAY_ON_TRACK` (something eaten and still under budget), otherwise none.

Worked example (the user's original scenario): Ayush, 77 kg, goal 10,000 steps, 3,200 steps so far, 1,500 kcal eaten of a 2,000 budget, a Double Cheeseburger (450 kcal, 25 g protein, meat, dairy, gluten). One tip: `WALK` with 3,200 of 10,000 steps, 450 kcal, 30 minutes, 3,000 steps, 141 kcal (6,800 steps to go is 70 minutes, capped at 30; 30 x 4.716 = 141.5, rounds to 141). On screen (step 11.3): "Ayush, you are at 3,200 of 10,000 steps and this meal is about 450 kcal. A 30 minute walk (about 3,000 steps) burns about 141 kcal."

CREATE `core/src/main/java/com/example/identify/core/WalkMath.java`
```java
package com.example.identify.core;

/** Walking numbers for coach tips: moderate walking at 3.5 MET and about 100 steps a minute. Estimates. */
public final class WalkMath {
    private WalkMath() {}

    public static final double WALK_MET = 3.5;
    public static final int STEPS_PER_MINUTE = 100;
    public static final double DEFAULT_WEIGHT_KG = 70;

    /** ACSM: kcal per minute = MET x 3.5 x kg / 200. A weight of 0 (not given) uses 70 kg. */
    public static double kcalPerMinute(double weightKg) {
        double kg = weightKg > 0 ? weightKg : DEFAULT_WEIGHT_KG;
        return WALK_MET * 3.5 * kg / 200.0;
    }

    public static long kcalForMinutes(long minutes, double weightKg) {
        return Math.round(minutes * kcalPerMinute(weightKg));
    }

    /** Minutes to walk that many steps, rounded up to a multiple of 5 (at least 5); 0 for no steps. */
    public static long minutesForSteps(long steps) {
        if (steps <= 0) return 0;
        long minutes = (steps + STEPS_PER_MINUTE - 1) / STEPS_PER_MINUTE;
        return Math.max(5, (minutes + 4) / 5 * 5);
    }
}
```

CREATE `core/src/main/java/com/example/identify/core/Tip.java`
```java
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
```

CREATE `core/src/main/java/com/example/identify/core/Coach.java`
```java
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
```

CREATE `core/src/test/java/com/example/identify/core/WalkMathTest.java`
```java
package com.example.identify.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WalkMathTest {

    @Test
    public void kcalPerMinuteFromWeight() {
        // 3.5 * 3.5 * 70 / 200 = 4.2875
        assertEquals(4.2875, WalkMath.kcalPerMinute(70), 1e-9);
        assertEquals(4.2875, WalkMath.kcalPerMinute(0), 1e-9);
        // 3.5 * 3.5 * 77 / 200 = 4.71625
        assertEquals(4.71625, WalkMath.kcalPerMinute(77), 1e-9);
    }

    @Test
    public void kcalForMinutesRounds() {
        assertEquals(141, WalkMath.kcalForMinutes(30, 77));   // 141.49
        assertEquals(129, WalkMath.kcalForMinutes(30, 0));    // 128.6
    }

    @Test
    public void minutesRoundUpToFive() {
        assertEquals(0, WalkMath.minutesForSteps(0));
        assertEquals(5, WalkMath.minutesForSteps(1));
        assertEquals(5, WalkMath.minutesForSteps(500));
        assertEquals(10, WalkMath.minutesForSteps(501));
        assertEquals(70, WalkMath.minutesForSteps(6800));
        assertEquals(30, WalkMath.minutesForSteps(3000));
    }
}
```

CREATE `core/src/test/java/com/example/identify/core/CoachTest.java`
```java
package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class CoachTest {

    private static Set<String> set(String... s) {
        return new HashSet<>(Arrays.asList(s));
    }

    private static UserProfile profile(double kg, UserProfile.Goal goal, Set<String> diet, Set<String> eatMore) {
        return new UserProfile("Ayush", 28, UserProfile.Sex.MALE, 178, kg, UserProfile.Activity.LIGHT, goal,
                Collections.emptySet(), Collections.emptySet(), diet, eatMore);
    }

    private static UserProfile plain() {
        return profile(77, UserProfile.Goal.LOSE, Collections.emptySet(), Collections.emptySet());
    }

    private static final Coach.Meal DOUBLE_CHEESEBURGER =
            new Coach.Meal(450, 25, EnumSet.of(FoodTag.MEAT, FoodTag.DAIRY, FoodTag.GLUTEN));

    @Test
    public void ayushScenarioGivesOneWalkTip() {
        List<Tip> tips = Coach.forMeal(plain(), 2000, 10000, new Coach.Day(3200, 1500, 13), DOUBLE_CHEESEBURGER);
        assertEquals(1, tips.size());
        assertEquals("walk[3200, 10000, 450, 30, 3000, 141]", tips.get(0).toString());
    }

    @Test
    public void overBudgetAddsWalkThenBudgetTip() {
        List<Tip> tips = Coach.forMeal(plain(), 2000, 10000, new Coach.Day(9000, 1900, 13), DOUBLE_CHEESEBURGER);
        assertEquals(2, tips.size());
        // 1,000 steps to go is 10 minutes
        assertEquals("walk[9000, 10000, 450, 10, 1000, 47]", tips.get(0).toString());
        assertEquals("over_budget[350, 2000]", tips.get(1).toString());
    }

    @Test
    public void smallMealUnderBudgetSaysNothing() {
        Coach.Meal apple = new Coach.Meal(95, 0.5, EnumSet.noneOf(FoodTag.class));
        assertTrue(Coach.forMeal(plain(), 2000, 10000, new Coach.Day(3200, 500, 13), apple).isEmpty());
    }

    @Test
    public void stepGoalReachedMeansNoWalk() {
        List<Tip> tips = Coach.forMeal(plain(), 2000, 10000, new Coach.Day(12000, 1900, 13), DOUBLE_CHEESEBURGER);
        assertEquals(1, tips.size());
        assertEquals(Tip.Kind.OVER_BUDGET, tips.get(0).kind);
    }

    @Test
    public void unknownHealthSkipsNumberRules() {
        List<Tip> tips = Coach.forMeal(plain(), 2000, 10000, new Coach.Day(-1, Double.NaN, 13), DOUBLE_CHEESEBURGER);
        assertTrue(tips.isEmpty());
    }

    @Test
    public void dietConflictComesFirst() {
        UserProfile veg = profile(60, UserProfile.Goal.LOSE, set(DietRules.VEGETARIAN), Collections.emptySet());
        List<Tip> tips = Coach.forMeal(veg, 2000, 10000, new Coach.Day(3200, 1500, 13), DOUBLE_CHEESEBURGER);
        assertEquals(2, tips.size());
        assertEquals("diet_conflict[Vegetarian, meat]", tips.get(0).toString());
        assertEquals(Tip.Kind.WALK, tips.get(1).kind);
    }

    @Test
    public void eatMoreFeedback() {
        UserProfile p = profile(77, UserProfile.Goal.EAT_HEALTHIER, Collections.emptySet(),
                set(Coach.MORE_PROTEIN, Coach.FEWER_FRIED, Coach.LESS_SUGAR, Coach.MORE_VEGETABLES));
        Coach.Meal friedChicken = new Coach.Meal(380, 30, EnumSet.of(FoodTag.MEAT, FoodTag.FRIED));
        List<Tip> tips = Coach.forMeal(p, 2000, 10000, new Coach.Day(-1, Double.NaN, 13), friedChicken);
        assertEquals(2, tips.size());
        assertEquals("protein_good[30]", tips.get(0).toString());
        assertEquals(Tip.Kind.FRIED_NOTE, tips.get(1).kind);

        Coach.Meal salad = new Coach.Meal(150, 3, EnumSet.of(FoodTag.VEG));
        assertEquals(Tip.Kind.VEGGIE_GOOD,
                Coach.forMeal(p, 2000, 10000, new Coach.Day(-1, Double.NaN, 13), salad).get(0).kind);

        Coach.Meal jalebi = new Coach.Meal(300, 2, EnumSet.of(FoodTag.GLUTEN, FoodTag.FRIED, FoodTag.SWEET));
        List<Tip> sweet = Coach.forMeal(p, 2000, 10000, new Coach.Day(-1, Double.NaN, 13), jalebi);
        assertEquals(Tip.Kind.FRIED_NOTE, sweet.get(0).kind);
        assertEquals(Tip.Kind.SWEET_NOTE, sweet.get(1).kind);
    }

    @Test
    public void smallerPortionsSuggestsHalf() {
        UserProfile p = profile(77, UserProfile.Goal.LOSE, Collections.emptySet(), set(Coach.SMALLER_PORTIONS));
        Coach.Meal burrito = new Coach.Meal(1050, 56, EnumSet.of(FoodTag.MEAT));
        List<Tip> tips = Coach.forMeal(p, 2000, 10000, new Coach.Day(-1, Double.NaN, 13), burrito);
        assertEquals("portion_note[525]", tips.get(0).toString());
    }

    @Test
    public void buildMuscleEveningShowsBudgetLeft() {
        UserProfile diego = profile(80, UserProfile.Goal.GAIN, Collections.emptySet(), Collections.emptySet());
        Coach.Meal bowl = new Coach.Meal(300, 40, EnumSet.of(FoodTag.MEAT));
        List<Tip> tips = Coach.forMeal(diego, 3000, 10000, new Coach.Day(11000, 1800, 19), bowl);
        assertEquals(1, tips.size());
        assertEquals("budget_left[900]", tips.get(0).toString());
        assertTrue(Coach.forMeal(diego, 3000, 10000, new Coach.Day(11000, 1800, 12), bowl).isEmpty());
    }

    @Test
    public void atMostThreeTips() {
        UserProfile p = profile(77, UserProfile.Goal.LOSE, set(DietRules.VEGAN, DietRules.GLUTEN_FREE),
                set(Coach.MORE_PROTEIN));
        List<Tip> tips = Coach.forMeal(p, 2000, 10000, new Coach.Day(3200, 1900, 13), DOUBLE_CHEESEBURGER);
        assertEquals(Coach.MAX_MEAL_TIPS, tips.size());
        assertEquals("diet_conflict[Vegan, meat]", tips.get(0).toString());
        assertEquals("diet_conflict[Gluten-free, gluten]", tips.get(1).toString());
        assertEquals(Tip.Kind.WALK, tips.get(2).kind);
    }

    @Test
    public void dayTips() {
        UserProfile p = plain();
        assertEquals("day_over_budget[300, 30, 141]",
                Coach.forDay(p, 2000, 10000, new Coach.Day(4000, 2300, 20)).toString());
        assertEquals("day_goal_reached[10500]",
                Coach.forDay(p, 2000, 10000, new Coach.Day(10500, 1200, 20)).toString());
        assertEquals("day_steps_left[3000, 30]",
                Coach.forDay(p, 2000, 10000, new Coach.Day(7000, 1200, 18)).toString());
        assertEquals("day_on_track[800]",
                Coach.forDay(p, 2000, 10000, new Coach.Day(7000, 1200, 11)).toString());
        assertNull(Coach.forDay(p, 2000, 10000, new Coach.Day(-1, Double.NaN, 18)));
        assertNull(Coach.forDay(p, 2000, 10000, new Coach.Day(500, 0, 9)));
    }

    @Test
    public void eatMoreLabelsMatchTheOnboardingChips() throws IOException {
        String arrays = new String(Files.readAllBytes(new File("../app/src/main/res/values/arrays.xml").toPath()),
                StandardCharsets.UTF_8);
        for (String label : Arrays.asList(Coach.MORE_PROTEIN, Coach.MORE_VEGETABLES, Coach.LESS_SUGAR,
                Coach.FEWER_FRIED, Coach.SMALLER_PORTIONS)) {
            assertTrue(label, arrays.contains("<item>" + label + "</item>"));
        }
    }
}
```

## VERIFY 11.2

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew :core:test --console=plain
```
BUILD SUCCESSFUL; 21 result files in `core/build/test-results/test`, all with `failures="0" errors="0"`.

Commit subject: `Add coach rules: walk math, meal and day tips, all in :core with tests`.
