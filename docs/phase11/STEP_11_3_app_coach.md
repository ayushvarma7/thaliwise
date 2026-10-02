# STEP 11.3: Coach in the app (result card, Today line, Settings switch, events)

Goal: show the coach tips from step 11.2 where the user decides (US-8.1 to US-8.6):

- **Result screen:** a "Coach" card under the kcal card, before logging, with up to 3 tips for the guessed food at one serving. It reads today's Health Connect numbers once per photo. Dismiss hides it for this photo. Logging is never blocked or delayed.
- **Today:** one coach line under the goal line. "Hide for today" hides it until tomorrow.
- **Settings:** a "Coach tips" switch in the profile section (on by default). Turning it off hides both.
- **Experiment log:** `nudge_shown` and `nudge_dismissed` (screen, run_id, food_id, tip kinds, and every tip with its numbers). `meal_logged` gains `coach_tips`: the kinds that were on screen when the user logged.

Wording rules (US-8.6, checked in step 11.4): tips state facts and options, never shame. No "bad", "guilt", "cheat", "junk", "unhealthy", "should", "must", or "don't". Diet tips say "usually contains", because the table describes typical recipes.

EDIT `app/src/main/res/values/strings.xml`
Find:
```xml
</resources>
```
Replace with:
```xml

    <string name="coach_title">Coach</string>
    <string name="coach_note">Estimates from typical recipes and servings.</string>
    <string name="coach_dismiss">Dismiss</string>
    <string name="coach_dismiss_today">Hide for today</string>
    <string name="coach_switch">Coach tips on Today and after identifying a meal</string>
    <string name="tip_diet_conflict">Usually contains %1$s, and your profile says %2$s.</string>
    <string name="tip_walk_named">%1$s, you are at %2$s of %3$s steps and this meal is about %4$s kcal. A %5$s minute walk (about %6$s steps) burns about %7$s kcal.</string>
    <string name="tip_walk">You are at %1$s of %2$s steps and this meal is about %3$s kcal. A %4$s minute walk (about %5$s steps) burns about %6$s kcal.</string>
    <string name="tip_over_budget">This meal takes you about %1$s kcal over your %2$s kcal budget for today.</string>
    <string name="tip_protein_good">Good for your protein goal: about %1$s g of protein.</string>
    <string name="tip_veggie_good">Nice, this one comes with vegetables.</string>
    <string name="tip_fried_note">This one is usually fried. A grilled or baked version is a lighter swap next time.</string>
    <string name="tip_sweet_note">This one is usually high in added sugar.</string>
    <string name="tip_portion_note">A half portion is about %1$s kcal. You can pick the portion when you log.</string>
    <string name="tip_budget_left">After this meal you still have about %1$s kcal to reach today\'s goal.</string>
    <string name="tip_day_over_budget">You are about %1$s kcal over today\'s budget. A %2$s minute walk burns about %3$s kcal.</string>
    <string name="tip_day_goal_reached">Step goal reached with %1$s steps. Nice work.</string>
    <string name="tip_day_steps_left_named">%1$s, %2$s steps to go today. That is about a %3$s minute walk.</string>
    <string name="tip_day_steps_left">%1$s steps to go today. That is about a %2$s minute walk.</string>
    <string name="tip_day_on_track">On track: about %1$s kcal left in today\'s budget.</string>
    <string name="tag_meat">meat</string>
    <string name="tag_pork">pork</string>
    <string name="tag_fish">fish</string>
    <string name="tag_shellfish">shellfish</string>
    <string name="tag_egg">egg</string>
    <string name="tag_dairy">dairy</string>
    <string name="tag_gluten">gluten</string>
    <string name="tag_alcohol">alcohol</string>
</resources>
```

REPLACE `app/src/main/java/com/example/identify/health/FoodRepository.java`
```java
package com.example.identify.health;

import android.content.Context;
import android.util.Log;

import com.example.identify.Config;
import com.example.identify.core.FoodCatalog;
import com.example.identify.core.FoodItem;
import com.example.identify.core.FoodTag;
import com.example.identify.core.FoodTags;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The nutrition table (assets/foods.txt, 300 rows) and its diet tags (assets/food_tags.txt), each read
 * once and kept in memory. A file that cannot be read gives an empty result (logged) and is retried later.
 */
public final class FoodRepository {
    private FoodRepository() {}

    public static final String ASSET = "foods.txt";
    public static final String TAGS_ASSET = "food_tags.txt";

    private static volatile List<FoodItem> foods;
    private static volatile Map<String, Set<FoodTag>> tags;

    /** The table, or an empty list if the asset cannot be read (logged). */
    public static List<FoodItem> foods(Context ctx) {
        List<FoodItem> f = foods;
        if (f != null) return f;
        synchronized (FoodRepository.class) {
            if (foods == null) {
                try {
                    foods = Collections.unmodifiableList(FoodCatalog.parse(readAsset(ctx, ASSET)));
                    Log.i(Config.HEALTH_TAG, "food table loaded items=" + foods.size());
                } catch (IOException | IllegalArgumentException e) {
                    Log.e(Config.HEALTH_TAG, "cannot load " + ASSET, e);
                    return Collections.emptyList();
                }
            }
            return foods;
        }
    }

    /** What the food usually contains or is; empty for an unknown id or when the tag file cannot be read. */
    public static Set<FoodTag> tagsOf(Context ctx, String foodId) {
        Set<FoodTag> s = tags(ctx).get(foodId);
        return s == null ? Collections.emptySet() : s;
    }

    private static Map<String, Set<FoodTag>> tags(Context ctx) {
        Map<String, Set<FoodTag>> t = tags;
        if (t != null) return t;
        synchronized (FoodRepository.class) {
            if (tags == null) {
                try {
                    tags = Collections.unmodifiableMap(FoodTags.parse(readAsset(ctx, TAGS_ASSET)));
                    Log.i(Config.HEALTH_TAG, "food tags loaded items=" + tags.size());
                } catch (IOException | IllegalArgumentException e) {
                    Log.e(Config.HEALTH_TAG, "cannot load " + TAGS_ASSET, e);
                    return Collections.emptyMap();
                }
            }
            return tags;
        }
    }

    private static String readAsset(Context ctx, String name) throws IOException {
        try (InputStream in = ctx.getApplicationContext().getAssets().open(name)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
```

EDIT `app/src/main/java/com/example/identify/AppPrefs.java`
Find:
```java
    private static final String KEY_EAT_MORE = "profile_eat_more";
```
Replace with:
```java
    private static final String KEY_EAT_MORE = "profile_eat_more";
    private static final String KEY_COACH = "coach_enabled";
    private static final String KEY_COACH_DISMISSED_DAY = "coach_dismissed_day";
```

EDIT `app/src/main/java/com/example/identify/AppPrefs.java`
Find:
```java
    public void setUsUnits(boolean us) { prefs.edit().putBoolean(KEY_US_UNITS, us).apply(); }
```
Replace with:
```java
    public void setUsUnits(boolean us) { prefs.edit().putBoolean(KEY_US_UNITS, us).apply(); }

    /** Coach tips on Today and the result screen (US-8.6); on by default. */
    public boolean isCoachEnabled() { return prefs.getBoolean(KEY_COACH, true); }

    public void setCoachEnabled(boolean on) { prefs.edit().putBoolean(KEY_COACH, on).apply(); }

    /** The local day (epoch day) on which the Today tip was hidden; -1 when never. */
    public long getCoachDismissedDay() { return prefs.getLong(KEY_COACH_DISMISSED_DAY, -1L); }

    public void setCoachDismissedDay(long epochDay) {
        prefs.edit().putLong(KEY_COACH_DISMISSED_DAY, epochDay).apply();
    }
```

CREATE `app/src/main/java/com/example/identify/ui/TipFormat.java`
```java
package com.example.identify.ui;

import android.content.Context;

import com.example.identify.R;
import com.example.identify.core.FoodTag;
import com.example.identify.core.Tip;

import java.util.ArrayList;
import java.util.List;

/** Sentences for coach tips (strings tip_*). Numbers use the same grouping as the rest of the app. */
final class TipFormat {
    private TipFormat() {}

    /** Several tips as one block, separated by blank lines. */
    static String join(Context ctx, List<Tip> tips, String name) {
        List<String> parts = new ArrayList<>();
        for (Tip t : tips) parts.add(text(ctx, t, name));
        return String.join("\n\n", parts);
    }

    /** One tip. The name, when given, opens the walk and steps-left tips ("Ayush, ..."). */
    static String text(Context ctx, Tip t, String name) {
        boolean named = name != null && !name.isEmpty();
        switch (t.kind) {
            case DIET_CONFLICT:
                return ctx.getString(R.string.tip_diet_conflict, tagName(ctx, t.tag), t.diet);
            case WALK:
                return named
                        ? ctx.getString(R.string.tip_walk_named, name, steps(ctx, t.n(0)), steps(ctx, t.n(1)),
                                kcal(ctx, t.n(2)), String.valueOf(t.n(3)), steps(ctx, t.n(4)), kcal(ctx, t.n(5)))
                        : ctx.getString(R.string.tip_walk, steps(ctx, t.n(0)), steps(ctx, t.n(1)),
                                kcal(ctx, t.n(2)), String.valueOf(t.n(3)), steps(ctx, t.n(4)), kcal(ctx, t.n(5)));
            case OVER_BUDGET:
                return ctx.getString(R.string.tip_over_budget, kcal(ctx, t.n(0)), kcal(ctx, t.n(1)));
            case PROTEIN_GOOD:
                return ctx.getString(R.string.tip_protein_good, String.valueOf(t.n(0)));
            case VEGGIE_GOOD:
                return ctx.getString(R.string.tip_veggie_good);
            case FRIED_NOTE:
                return ctx.getString(R.string.tip_fried_note);
            case SWEET_NOTE:
                return ctx.getString(R.string.tip_sweet_note);
            case PORTION_NOTE:
                return ctx.getString(R.string.tip_portion_note, kcal(ctx, t.n(0)));
            case BUDGET_LEFT:
                return ctx.getString(R.string.tip_budget_left, kcal(ctx, t.n(0)));
            case DAY_OVER_BUDGET:
                return ctx.getString(R.string.tip_day_over_budget, kcal(ctx, t.n(0)), String.valueOf(t.n(1)),
                        kcal(ctx, t.n(2)));
            case DAY_GOAL_REACHED:
                return ctx.getString(R.string.tip_day_goal_reached, steps(ctx, t.n(0)));
            case DAY_STEPS_LEFT:
                return named
                        ? ctx.getString(R.string.tip_day_steps_left_named, name, steps(ctx, t.n(0)),
                                String.valueOf(t.n(1)))
                        : ctx.getString(R.string.tip_day_steps_left, steps(ctx, t.n(0)), String.valueOf(t.n(1)));
            default:
                return ctx.getString(R.string.tip_day_on_track, kcal(ctx, t.n(0)));
        }
    }

    /** The word for a tag a diet avoids; fried, sweet, and veg never appear in diet tips. */
    static String tagName(Context ctx, FoodTag tag) {
        switch (tag) {
            case MEAT: return ctx.getString(R.string.tag_meat);
            case PORK: return ctx.getString(R.string.tag_pork);
            case FISH: return ctx.getString(R.string.tag_fish);
            case SHELLFISH: return ctx.getString(R.string.tag_shellfish);
            case EGG: return ctx.getString(R.string.tag_egg);
            case DAIRY: return ctx.getString(R.string.tag_dairy);
            case GLUTEN: return ctx.getString(R.string.tag_gluten);
            case ALCOHOL: return ctx.getString(R.string.tag_alcohol);
            default: return tag.key();
        }
    }

    private static String steps(Context ctx, long v) {
        return HealthFormat.steps(ctx, v);
    }

    private static String kcal(Context ctx, long v) {
        return HealthFormat.kcal(ctx, v);
    }
}
```

CREATE `app/src/main/java/com/example/identify/ui/CoachEvents.java`
```java
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
```

EDIT `app/src/main/res/layout/fragment_result.xml`
Find:
```xml
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/no_food_card"
```
Replace with:
```xml
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/coach_card"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:visibility="gone"
            app:cardBackgroundColor="?attr/colorSecondaryContainer"
            app:strokeWidth="0dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/coach_title"
                    android:textAppearance="?attr/textAppearanceTitleSmall"
                    android:textColor="?attr/colorOnSecondaryContainer" />

                <TextView
                    android:id="@+id/coach_text"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:textAppearance="?attr/textAppearanceBodyMedium"
                    android:textColor="?attr/colorOnSecondaryContainer" />

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:text="@string/coach_note"
                    android:textAppearance="?attr/textAppearanceBodySmall"
                    android:textColor="?attr/colorOnSecondaryContainer" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/coach_dismiss_button"
                    style="@style/Widget.Material3.Button.TextButton"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/coach_dismiss" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <com.google.android.material.card.MaterialCardView
            android:id="@+id/no_food_card"
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
import com.example.identify.R;
import com.example.identify.core.FoodItem;
```
Replace with:
```java
import com.example.identify.R;
import com.example.identify.core.Coach;
import com.example.identify.core.DailyHealth;
import com.example.identify.core.FoodItem;
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
import com.example.identify.core.Meals;
```
Replace with:
```java
import com.example.identify.core.Meals;
import com.example.identify.core.Tip;
import com.example.identify.core.UserProfile;
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
import java.util.List;
```
Replace with:
```java
import java.util.ArrayList;
import java.util.List;
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
    private static final String KEY_FEEDBACK_SAVED = "feedbackSaved";
```
Replace with:
```java
    private static final String KEY_FEEDBACK_SAVED = "feedbackSaved";
    private static final String KEY_COACH_DISMISSED = "coachDismissed";
    private static final String KEY_COACH_LOGGED = "coachLogged";
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
    private boolean feedbackSaved;
```
Replace with:
```java
    private boolean feedbackSaved;
    /** Today's numbers for the coach card, read once per screen; null until that read finishes. */
    private DailyHealth coachToday;
    private boolean coachReadStarted;
    private boolean coachDismissed;
    /** True once nudge_shown was logged for this photo. */
    private boolean coachLogged;
    private List<Tip> coachTips = new ArrayList<>();
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
            feedbackSaved = savedInstanceState.getBoolean(KEY_FEEDBACK_SAVED);
```
Replace with:
```java
            feedbackSaved = savedInstanceState.getBoolean(KEY_FEEDBACK_SAVED);
            coachDismissed = savedInstanceState.getBoolean(KEY_COACH_DISMISSED);
            coachLogged = savedInstanceState.getBoolean(KEY_COACH_LOGGED);
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
        outState.putBoolean(KEY_FEEDBACK_SAVED, feedbackSaved);
```
Replace with:
```java
        outState.putBoolean(KEY_FEEDBACK_SAVED, feedbackSaved);
        outState.putBoolean(KEY_COACH_DISMISSED, coachDismissed);
        outState.putBoolean(KEY_COACH_LOGGED, coachLogged);
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
        binding.undoMealButton.setOnClickListener(v -> onUndoMealClicked());
```
Replace with:
```java
        binding.undoMealButton.setOnClickListener(v -> onUndoMealClicked());
        binding.coachDismissButton.setOnClickListener(v -> dismissCoach());
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
            binding.loggedCard.setVisibility(View.GONE);
            binding.runModelButton.setVisibility(View.GONE);
```
Replace with:
```java
            binding.loggedCard.setVisibility(View.GONE);
            binding.coachCard.setVisibility(View.GONE);
            binding.runModelButton.setVisibility(View.GONE);
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
            binding.loggedCard.setVisibility(View.VISIBLE);
```
Replace with:
```java
            binding.coachCard.setVisibility(View.GONE);
            binding.loggedCard.setVisibility(View.VISIBLE);
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
            binding.noFoodCard.setVisibility(View.VISIBLE);
```
Replace with:
```java
            binding.coachCard.setVisibility(View.GONE);
            binding.noFoodCard.setVisibility(View.VISIBLE);
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
        binding.logMealButton.setText(R.string.log_meal_button);
    }
```
Replace with:
```java
        binding.logMealButton.setText(R.string.log_meal_button);
        renderCoach(r, top);
    }

    /**
     * Coach tips for the guessed food at one serving, under the kcal card and before logging (US-8.1 to
     * US-8.4). Today's numbers are read once; without Health Connect only the diet and eat-more rules apply.
     */
    private void renderCoach(ResultViewModel.IdentifyResult r, FoodItem top) {
        Context ctx = requireContext();
        AppPrefs prefs = AppPrefs.get(ctx);
        if (coachDismissed || !prefs.isCoachEnabled()) {
            binding.coachCard.setVisibility(View.GONE);
            return;
        }
        if (coachToday == null) {
            binding.coachCard.setVisibility(View.GONE);
            if (coachReadStarted) return;
            coachReadStarted = true;
            HealthConnectRepository.readToday(ctx.getApplicationContext(), (today, error) -> {
                coachToday = today != null
                        ? today
                        : new DailyHealth(DailyHealth.UNKNOWN_STEPS, Double.NaN, Double.NaN, Double.NaN);
                renderResult();
            });
            return;
        }
        UserProfile p = prefs.getProfile();
        Coach.Day day = new Coach.Day(coachToday.steps, coachToday.eatenKcal, LocalTime.now().getHour());
        Coach.Meal meal = new Coach.Meal(top.kcal, top.proteinG, FoodRepository.tagsOf(ctx, top.id));
        coachTips = Coach.forMeal(p, prefs.getCalorieBudget(), prefs.getStepGoal(), day, meal);
        if (coachTips.isEmpty()) {
            binding.coachCard.setVisibility(View.GONE);
            return;
        }
        binding.coachCard.setVisibility(View.VISIBLE);
        binding.coachText.setText(TipFormat.join(ctx, coachTips, p.name));
        if (!coachLogged) {
            coachLogged = true;
            CoachEvents.shown(ctx, "result", r.runId, top.id, coachTips);
        }
    }

    private void dismissCoach() {
        coachDismissed = true;
        binding.coachCard.setVisibility(View.GONE);
        ResultViewModel.IdentifyResult r = vm.getResult().getValue();
        CoachEvents.dismissed(requireContext(), "result", r == null ? null : r.runId, coachTips);
    }
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
            ExperimentLog.put(e, "meal_slot", slot.name().toLowerCase(Locale.ROOT));
```
Replace with:
```java
            ExperimentLog.put(e, "meal_slot", slot.name().toLowerCase(Locale.ROOT));
            ExperimentLog.put(e, "coach_tips",
                    CoachEvents.kinds(coachLogged && !coachDismissed ? coachTips : new ArrayList<>()));
```

EDIT `app/src/main/res/layout/fragment_today.xml`
Find:
```xml
            <com.google.android.material.card.MaterialCardView
                android:id="@+id/connect_card"
```
Replace with:
```xml
            <com.google.android.material.card.MaterialCardView
                android:id="@+id/today_coach_card"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:visibility="gone"
                app:cardBackgroundColor="?attr/colorSecondaryContainer"
                app:strokeWidth="0dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="@string/coach_title"
                        android:textAppearance="?attr/textAppearanceTitleSmall"
                        android:textColor="?attr/colorOnSecondaryContainer" />

                    <TextView
                        android:id="@+id/today_coach_text"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="4dp"
                        android:textAppearance="?attr/textAppearanceBodyLarge"
                        android:textColor="?attr/colorOnSecondaryContainer" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/today_coach_dismiss_button"
                        style="@style/Widget.Material3.Button.TextButton"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/coach_dismiss_today" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <com.google.android.material.card.MaterialCardView
                android:id="@+id/connect_card"
```

EDIT `app/src/main/java/com/example/identify/ui/TodayFragment.java`
Find:
```java
import com.example.identify.core.DailyHealth;
```
Replace with:
```java
import com.example.identify.core.Coach;
import com.example.identify.core.DailyHealth;
```

EDIT `app/src/main/java/com/example/identify/ui/TodayFragment.java`
Find:
```java
import com.example.identify.core.UserProfile;
```
Replace with:
```java
import com.example.identify.core.Tip;
import com.example.identify.core.UserProfile;
```

EDIT `app/src/main/java/com/example/identify/ui/TodayFragment.java`
Find:
```java
import java.time.LocalDate;
```
Replace with:
```java
import java.time.LocalDate;
import java.time.LocalTime;
```

EDIT `app/src/main/java/com/example/identify/ui/TodayFragment.java`
Find:
```java
import java.util.Locale;
```
Replace with:
```java
import java.util.Collections;
import java.util.Locale;
```

EDIT `app/src/main/java/com/example/identify/ui/TodayFragment.java`
Find:
```java
    private MealAdapter adapter;
```
Replace with:
```java
    private MealAdapter adapter;
    /** The day tip on screen, and the kind last written to the log, so a refresh does not log it twice. */
    private Tip dayTip;
    private Tip.Kind loggedDayTip;
```

EDIT `app/src/main/java/com/example/identify/ui/TodayFragment.java`
Find:
```java
        binding.connectButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.settingsFragment));
```
Replace with:
```java
        binding.connectButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.settingsFragment));
        binding.todayCoachDismissButton.setOnClickListener(v -> dismissDayTip());
```

EDIT `app/src/main/java/com/example/identify/ui/TodayFragment.java`
Find:
```java
        if (!connected) {
            adapter.submitList(null);
```
Replace with:
```java
        if (!connected) {
            binding.todayCoachCard.setVisibility(View.GONE);
            adapter.submitList(null);
```

EDIT `app/src/main/java/com/example/identify/ui/TodayFragment.java`
Find:
```java
                    HealthFormat.kcal(ctx, today.burnedKcal), HealthFormat.kcal(ctx, today.activeKcal)));
        });
```
Replace with:
```java
                    HealthFormat.kcal(ctx, today.burnedKcal), HealthFormat.kcal(ctx, today.activeKcal)));
            renderDayTip(today, budget, goal);
        });
```

EDIT `app/src/main/java/com/example/identify/ui/TodayFragment.java`
Find:
```java
    private void renderCalories(double eatenKcal, long budget) {
```
Replace with:
```java
    /** One coach line for the day (US-8.5); hidden when coach tips are off, hidden today, or nothing applies. */
    private void renderDayTip(DailyHealth today, long budget, long goal) {
        Context ctx = requireContext();
        AppPrefs prefs = AppPrefs.get(ctx);
        UserProfile p = prefs.getProfile();
        boolean hiddenToday = prefs.getCoachDismissedDay() == LocalDate.now().toEpochDay();
        dayTip = prefs.isCoachEnabled() && !hiddenToday
                ? Coach.forDay(p, budget, goal, new Coach.Day(today.steps, today.eatenKcal, LocalTime.now().getHour()))
                : null;
        if (dayTip == null) {
            binding.todayCoachCard.setVisibility(View.GONE);
            return;
        }
        binding.todayCoachCard.setVisibility(View.VISIBLE);
        binding.todayCoachText.setText(TipFormat.text(ctx, dayTip, p.name));
        if (dayTip.kind != loggedDayTip) {
            loggedDayTip = dayTip.kind;
            CoachEvents.shown(ctx, "today", null, null, Collections.singletonList(dayTip));
        }
    }

    /** Hides the day tip until tomorrow. */
    private void dismissDayTip() {
        AppPrefs.get(requireContext()).setCoachDismissedDay(LocalDate.now().toEpochDay());
        binding.todayCoachCard.setVisibility(View.GONE);
        if (dayTip != null) {
            CoachEvents.dismissed(requireContext(), "today", null, Collections.singletonList(dayTip));
        }
    }

    private void renderCalories(double eatenKcal, long budget) {
```

EDIT `app/src/main/res/layout/fragment_settings.xml`
Find:
```xml
                android:text="@string/history_button" />
        </LinearLayout>
```
Replace with:
```xml
                android:text="@string/history_button" />
        </LinearLayout>

        <com.google.android.material.materialswitch.MaterialSwitch
            android:id="@+id/coach_switch"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="@string/coach_switch" />
```

EDIT `app/src/main/java/com/example/identify/ui/SettingsFragment.java`
Find:
```java
        binding.memorySwitch.setChecked(prefs.isMemoryEnabled());
```
Replace with:
```java
        binding.coachSwitch.setChecked(prefs.isCoachEnabled());
        binding.coachSwitch.setOnCheckedChangeListener((button, checked) -> {
            prefs.setCoachEnabled(checked);
            if (button.isPressed()) vm.logSettingChange("coach_enabled", checked);
        });

        binding.memorySwitch.setChecked(prefs.isMemoryEnabled());
```

## VERIFY 11.3

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew :core:test :app:assembleDebug :app:lintDebug --console=plain
tail -n 1 app/build/reports/lint-results-debug.txt
```
BUILD SUCCESSFUL, lint `0 errors`, and no new warning in the files of this step (compare the warning list with the one before the step).

Commit subject: `Show coach tips on the result screen and Today, with a Settings switch and events`.
