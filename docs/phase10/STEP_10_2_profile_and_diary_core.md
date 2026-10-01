# STEP 10.2: Profile math and diary grouping (:core)

Goal: pure-Java pieces behind onboarding (US-1.2 to US-1.6) and the Today/Diary lists (US-4.4, US-5.1), with tests.

- `UserProfile`: what the user answered. Metric body values; 0 means not given.
- `ProfileMath`: Mifflin-St Jeor resting energy, activity factors, goal adjustment, step goal, unit conversions.
  - Resting energy (kcal/day) = 10 x kg + 6.25 x cm - 5 x age + s, with s = +5 (male), -161 (female), -78 (prefer not to say, the midpoint).
  - Activity factor: mostly sitting 1.2, lightly active 1.375, moderately active 1.55, very active 1.725.
  - Goal: lose -500 kcal, build muscle +300 kcal, otherwise no change. Floor 1,500 kcal for men and 1,200 otherwise. Rounded to the nearest 50. Missing body values give 2,000.
  - Step goal: 6,000 / 8,000 / 10,000 / 12,000 by activity.
- `MealEntry`, `Diary`: meals read from Health Connect, grouped newest first with one header per local day.

CREATE `core/src/main/java/com/example/identify/core/UserProfile.java`
```java
package com.example.identify.core;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** What the user told the app during onboarding. Body values are metric; 0 means not given. */
public final class UserProfile {

    public enum Sex { FEMALE, MALE, UNSPECIFIED }

    public enum Activity { SEDENTARY, LIGHT, MODERATE, ACTIVE }

    public enum Goal { LOSE, MAINTAIN, GAIN, EAT_HEALTHIER, TRACK }

    public final String name;
    public final int ageYears;
    public final Sex sex;
    public final double heightCm;
    public final double weightKg;
    public final Activity activity;
    public final Goal goal;
    public final Set<String> reasons;
    public final Set<String> cuisines;
    public final Set<String> diet;
    public final Set<String> eatMore;

    public UserProfile(String name, int ageYears, Sex sex, double heightCm, double weightKg, Activity activity,
                       Goal goal, Set<String> reasons, Set<String> cuisines, Set<String> diet, Set<String> eatMore) {
        this.name = name == null ? "" : name.trim();
        this.ageYears = ageYears;
        this.sex = sex == null ? Sex.UNSPECIFIED : sex;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.activity = activity == null ? Activity.LIGHT : activity;
        this.goal = goal == null ? Goal.TRACK : goal;
        this.reasons = copy(reasons);
        this.cuisines = copy(cuisines);
        this.diet = copy(diet);
        this.eatMore = copy(eatMore);
    }

    /** Age, height, and weight all given, so a personal budget can be computed. */
    public boolean hasBody() {
        return ageYears > 0 && heightCm > 0 && weightKg > 0;
    }

    private static Set<String> copy(Set<String> s) {
        return s == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(s));
    }
}
```

CREATE `core/src/main/java/com/example/identify/core/ProfileMath.java`
```java
package com.example.identify.core;

/** Daily calorie budget and step goal from a profile, plus unit conversions for the onboarding form. */
public final class ProfileMath {
    private ProfileMath() {}

    public static final long DEFAULT_BUDGET = 2000;
    public static final int MIN_AGE = 13;
    public static final int MAX_AGE = 100;
    public static final double MIN_HEIGHT_CM = 120;
    public static final double MAX_HEIGHT_CM = 230;
    public static final double MIN_WEIGHT_KG = 30;
    public static final double MAX_WEIGHT_KG = 300;

    /** Mifflin-St Jeor resting energy in kcal per day. "Prefer not to say" uses the midpoint of the two constants. */
    public static double restingKcal(UserProfile p) {
        double s;
        switch (p.sex) {
            case MALE: s = 5; break;
            case FEMALE: s = -161; break;
            default: s = -78; break;
        }
        return 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.ageYears + s;
    }

    public static double activityFactor(UserProfile.Activity a) {
        switch (a) {
            case SEDENTARY: return 1.2;
            case LIGHT: return 1.375;
            case MODERATE: return 1.55;
            default: return 1.725;
        }
    }

    /** Resting energy times activity, then the goal adjustment, a safety floor, and rounding to 50 kcal. */
    public static long calorieBudget(UserProfile p) {
        if (!p.hasBody()) return DEFAULT_BUDGET;
        double kcal = restingKcal(p) * activityFactor(p.activity);
        if (p.goal == UserProfile.Goal.LOSE) kcal -= 500;
        if (p.goal == UserProfile.Goal.GAIN) kcal += 300;
        double floor = p.sex == UserProfile.Sex.MALE ? 1500 : 1200;
        kcal = Math.max(kcal, floor);
        return Math.round(kcal / 50.0) * 50;
    }

    public static long stepGoal(UserProfile.Activity a) {
        switch (a) {
            case SEDENTARY: return 6000;
            case LIGHT: return 8000;
            case MODERATE: return 10000;
            default: return 12000;
        }
    }

    public static double lbToKg(double lb) {
        return lb * 0.45359237;
    }

    public static double kgToLb(double kg) {
        return kg / 0.45359237;
    }

    public static double feetInchesToCm(int feet, double inches) {
        return (feet * 12 + inches) * 2.54;
    }

    /** {feet, inches} with inches rounded to a whole number (5 ft 12 in becomes 6 ft 0 in). */
    public static int[] cmToFeetInches(double cm) {
        int totalInches = (int) Math.round(cm / 2.54);
        return new int[]{totalInches / 12, totalInches % 12};
    }

    public static boolean validAge(int age) {
        return age >= MIN_AGE && age <= MAX_AGE;
    }

    public static boolean validHeightCm(double cm) {
        return cm >= MIN_HEIGHT_CM && cm <= MAX_HEIGHT_CM;
    }

    public static boolean validWeightKg(double kg) {
        return kg >= MIN_WEIGHT_KG && kg <= MAX_WEIGHT_KG;
    }
}
```

CREATE `core/src/main/java/com/example/identify/core/MealEntry.java`
```java
package com.example.identify.core;

/** One meal from Health Connect, as shown in the Today and Diary lists. */
public final class MealEntry {
    public final String id;          // Health Connect record id
    public final String name;        // may be empty when the writing app gave no name
    public final double kcal;        // NaN when the record has no energy
    public final long startMillis;
    public final Meals.Slot slot;    // null when the record has no meal type
    public final boolean mine;       // written by this app; only these can be deleted here

    public MealEntry(String id, String name, double kcal, long startMillis, Meals.Slot slot, boolean mine) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.kcal = kcal;
        this.startMillis = startMillis;
        this.slot = slot;
        this.mine = mine;
    }
}
```

CREATE `core/src/main/java/com/example/identify/core/Diary.java`
```java
package com.example.identify.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Turns a flat list of meals into list rows: newest first, one header per local day with its total. */
public final class Diary {
    private Diary() {}

    public static final class Row {
        public final boolean header;
        public final LocalDate date;     // the day, for headers and meals
        public final double totalKcal;   // headers only
        public final int mealCount;      // headers only
        public final MealEntry meal;     // meal rows only

        private Row(boolean header, LocalDate date, double totalKcal, int mealCount, MealEntry meal) {
            this.header = header;
            this.date = date;
            this.totalKcal = totalKcal;
            this.mealCount = mealCount;
            this.meal = meal;
        }

        static Row header(LocalDate date, double totalKcal, int mealCount) {
            return new Row(true, date, totalKcal, mealCount, null);
        }

        static Row meal(LocalDate date, MealEntry meal) {
            return new Row(false, date, 0, 0, meal);
        }
    }

    /** Sum of known calories; NaN entries are skipped. */
    public static double totalKcal(List<MealEntry> meals) {
        double sum = 0;
        for (MealEntry m : meals) {
            if (!Double.isNaN(m.kcal)) sum += m.kcal;
        }
        return sum;
    }

    public static List<MealEntry> newestFirst(List<MealEntry> meals) {
        List<MealEntry> sorted = new ArrayList<>(meals);
        sorted.sort((a, b) -> Long.compare(b.startMillis, a.startMillis));
        return sorted;
    }

    /** Meal rows only, newest first (the Today list). */
    public static List<Row> mealRows(List<MealEntry> meals, ZoneId zone) {
        List<Row> rows = new ArrayList<>();
        for (MealEntry m : newestFirst(meals)) rows.add(Row.meal(day(m, zone), m));
        return rows;
    }

    /** Header plus meals for each local day, newest day first (the Diary list). */
    public static List<Row> rows(List<MealEntry> meals, ZoneId zone) {
        List<Row> rows = new ArrayList<>();
        List<MealEntry> sorted = newestFirst(meals);
        int i = 0;
        while (i < sorted.size()) {
            LocalDate d = day(sorted.get(i), zone);
            List<MealEntry> sameDay = new ArrayList<>();
            while (i < sorted.size() && day(sorted.get(i), zone).equals(d)) sameDay.add(sorted.get(i++));
            rows.add(Row.header(d, totalKcal(sameDay), sameDay.size()));
            for (MealEntry m : sameDay) rows.add(Row.meal(d, m));
        }
        return rows;
    }

    private static LocalDate day(MealEntry m, ZoneId zone) {
        return Instant.ofEpochMilli(m.startMillis).atZone(zone).toLocalDate();
    }
}
```

CREATE `core/src/test/java/com/example/identify/core/ProfileMathTest.java`
```java
package com.example.identify.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

public class ProfileMathTest {

    private static UserProfile p(int age, UserProfile.Sex sex, double cm, double kg, UserProfile.Activity a, UserProfile.Goal g) {
        return new UserProfile("A", age, sex, cm, kg, a, g, Collections.emptySet(), Collections.emptySet(),
                Collections.emptySet(), Collections.emptySet());
    }

    @Test
    public void restingEnergyMifflinStJeor() {
        // 10*80 + 6.25*180 - 5*30 + 5 = 1780
        assertEquals(1780, ProfileMath.restingKcal(p(30, UserProfile.Sex.MALE, 180, 80, UserProfile.Activity.LIGHT, UserProfile.Goal.TRACK)), 1e-9);
        // 10*60 + 6.25*165 - 5*25 - 161 = 1345.25
        assertEquals(1345.25, ProfileMath.restingKcal(p(25, UserProfile.Sex.FEMALE, 165, 60, UserProfile.Activity.LIGHT, UserProfile.Goal.TRACK)), 1e-9);
        // midpoint constant -78
        assertEquals(1428.25, ProfileMath.restingKcal(p(25, UserProfile.Sex.UNSPECIFIED, 165, 60, UserProfile.Activity.LIGHT, UserProfile.Goal.TRACK)), 1e-9);
    }

    @Test
    public void budgetsByGoal() {
        // 1780 * 1.55 = 2759 -> lose -500 = 2259 -> 2250
        assertEquals(2250, ProfileMath.calorieBudget(p(30, UserProfile.Sex.MALE, 180, 80, UserProfile.Activity.MODERATE, UserProfile.Goal.LOSE)));
        // 2759 -> maintain -> 2750
        assertEquals(2750, ProfileMath.calorieBudget(p(30, UserProfile.Sex.MALE, 180, 80, UserProfile.Activity.MODERATE, UserProfile.Goal.MAINTAIN)));
        // 2759 + 300 = 3059 -> 3050
        assertEquals(3050, ProfileMath.calorieBudget(p(30, UserProfile.Sex.MALE, 180, 80, UserProfile.Activity.MODERATE, UserProfile.Goal.GAIN)));
        // 1345.25 * 1.375 = 1849.7 -> 1850
        assertEquals(1850, ProfileMath.calorieBudget(p(25, UserProfile.Sex.FEMALE, 165, 60, UserProfile.Activity.LIGHT, UserProfile.Goal.EAT_HEALTHIER)));
    }

    @Test
    public void floorAndDefault() {
        // 10*45 + 6.25*150 - 5*70 - 161 = 876.5; *1.2 = 1051.8; -500 -> floor 1200
        assertEquals(1200, ProfileMath.calorieBudget(p(70, UserProfile.Sex.FEMALE, 150, 45, UserProfile.Activity.SEDENTARY, UserProfile.Goal.LOSE)));
        assertEquals(ProfileMath.DEFAULT_BUDGET, ProfileMath.calorieBudget(p(0, UserProfile.Sex.MALE, 180, 80, UserProfile.Activity.LIGHT, UserProfile.Goal.LOSE)));
    }

    @Test
    public void stepGoals() {
        assertEquals(6000, ProfileMath.stepGoal(UserProfile.Activity.SEDENTARY));
        assertEquals(8000, ProfileMath.stepGoal(UserProfile.Activity.LIGHT));
        assertEquals(10000, ProfileMath.stepGoal(UserProfile.Activity.MODERATE));
        assertEquals(12000, ProfileMath.stepGoal(UserProfile.Activity.ACTIVE));
    }

    @Test
    public void unitsAndValidation() {
        assertEquals(81.65, ProfileMath.lbToKg(180), 0.01);
        assertEquals(180, ProfileMath.kgToLb(ProfileMath.lbToKg(180)), 1e-9);
        assertEquals(177.8, ProfileMath.feetInchesToCm(5, 10), 1e-9);
        assertArrayEquals(new int[]{5, 10}, ProfileMath.cmToFeetInches(177.8));
        assertArrayEquals(new int[]{6, 0}, ProfileMath.cmToFeetInches(182.6));
        assertTrue(ProfileMath.validAge(28));
        assertFalse(ProfileMath.validAge(8));
        assertTrue(ProfileMath.validHeightCm(170));
        assertFalse(ProfileMath.validHeightCm(80));
        assertTrue(ProfileMath.validWeightKg(70));
        assertFalse(ProfileMath.validWeightKg(500));
    }

    @Test
    public void profileDefaults() {
        UserProfile u = new UserProfile(null, 0, null, 0, 0, null, null, null, null, null, null);
        assertEquals("", u.name);
        assertEquals(UserProfile.Sex.UNSPECIFIED, u.sex);
        assertEquals(UserProfile.Activity.LIGHT, u.activity);
        assertEquals(UserProfile.Goal.TRACK, u.goal);
        assertTrue(u.cuisines.isEmpty());
        assertFalse(u.hasBody());
    }
}
```

CREATE `core/src/test/java/com/example/identify/core/DiaryTest.java`
```java
package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class DiaryTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");

    private static long at(int day, int hour) {
        return ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, NY).toInstant().toEpochMilli();
    }

    private static MealEntry m(String id, double kcal, long t) {
        return new MealEntry(id, id, kcal, t, Meals.Slot.LUNCH, true);
    }

    @Test
    public void groupsByLocalDayNewestFirst() {
        List<MealEntry> meals = Arrays.asList(
                m("breakfast1", 300, at(1, 8)),
                m("dinner2", 700, at(2, 19)),
                m("lunch1", 500, at(1, 12)),
                m("late1", 200, at(1, 23)));
        List<Diary.Row> rows = Diary.rows(meals, NY);
        assertEquals(6, rows.size());
        assertTrue(rows.get(0).header);
        assertEquals(LocalDate.of(2026, 10, 2), rows.get(0).date);
        assertEquals(700, rows.get(0).totalKcal, 0);
        assertEquals(1, rows.get(0).mealCount);
        assertEquals("dinner2", rows.get(1).meal.id);
        assertTrue(rows.get(2).header);
        assertEquals(1000, rows.get(2).totalKcal, 0);
        assertEquals(3, rows.get(2).mealCount);
        assertEquals("late1", rows.get(3).meal.id);
        assertEquals("lunch1", rows.get(4).meal.id);
        assertEquals("breakfast1", rows.get(5).meal.id);
    }

    @Test
    public void unknownCaloriesAreSkippedInTotals() {
        List<MealEntry> meals = Arrays.asList(m("a", Double.NaN, at(1, 8)), m("b", 250, at(1, 9)));
        assertEquals(250, Diary.totalKcal(meals), 0);
        assertEquals(250, Diary.rows(meals, NY).get(0).totalKcal, 0);
    }

    @Test
    public void mealRowsHaveNoHeaders() {
        List<Diary.Row> rows = Diary.mealRows(Arrays.asList(m("a", 1, at(1, 8)), m("b", 2, at(1, 9))), NY);
        assertEquals(2, rows.size());
        assertFalse(rows.get(0).header);
        assertEquals("b", rows.get(0).meal.id);
        assertTrue(Diary.rows(Arrays.asList(), NY).isEmpty());
    }
}
```

## VERIFY 10.2

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew :core:test --console=plain -q
ls core/build/test-results/test/ | grep -c "^TEST-"
grep -rn "import android" core/src && echo "FAIL" || echo "OK: core is android-free"
```
Exit 0, 17 test classes, core android-free.

Commit subject: `Add user profile math (calorie budget, step goal) and diary grouping`.
