# STEP 10.5: Screens (Today, Diary, Snap, Result) and navigation

Goal: the storyboard in `docs/USER_STORIES.md` section 5. Stories US-2.1, US-2.3, US-3.1 to US-3.5, US-4.1 to US-4.5, US-5.1, US-5.2.

- Bottom tabs: Today (start), Diary, Settings. Snap meal opens the capture screen; Identify opens the result; Done returns to a fresh Today.
- Result screen: dish name, cuisine chip, a big kcal card with Log meal and Different food; a "No food recognized" card with Name the food and Retake; after logging, a "Logged" card with today's totals, Done, and Undo. Logging also teaches the memory: the guessed food is saved as an accept, any other food as a correction (once per photo).
- Today: greeting, goal line, calorie ring (eaten / budget, kcal left or over), burned, steps bar, today's meals, and a Snap meal button. Diary: last 7 days grouped by day. Tapping a meal this app logged offers Delete.

EDIT `app/src/main/res/values/strings.xml`
Find:
```xml
    <string name="title_camera">Identify</string>
```
Replace with:
```xml
    <string name="title_camera">Snap meal</string>
```

EDIT `app/src/main/res/values/strings.xml`
Find:
```xml
    <string name="identify">Identify</string>
    <string name="empty_hint">Take or pick a photo to identify.</string>
```
Replace with:
```xml
    <string name="identify">Identify food</string>
    <string name="empty_hint">Take or pick a photo of your meal.</string>
```

EDIT `app/src/main/res/values/strings.xml`
Find:
```xml
</resources>
```
Replace with:
```xml

    <string name="title_today">Today</string>
    <string name="title_diary">Diary</string>
    <string name="today_greeting">Hi %1$s</string>
    <string name="today_greeting_plain">Hi there</string>
    <string name="today_goal_line">Goal: %1$s</string>
    <string name="today_connect_needed">Connect Health Connect to see your calories and steps here.</string>
    <string name="today_connect_button">Connect</string>
    <string name="today_calories_title">Calories eaten</string>
    <string name="today_of_budget">of %1$s</string>
    <string name="today_remaining">%1$s kcal left</string>
    <string name="today_over">%1$s kcal over budget</string>
    <string name="today_burned">Burned %1$s kcal (%2$s active)</string>
    <string name="today_steps_title">Steps</string>
    <string name="today_steps">%1$s / %2$s</string>
    <string name="today_steps_to_go">%1$s steps to go</string>
    <string name="today_steps_done">Step goal reached</string>
    <string name="today_meals_title">Meals today</string>
    <string name="today_meals_empty">No meals logged yet. Snap your first meal.</string>
    <string name="snap_meal">Snap meal</string>
    <string name="snap_title">Snap your meal</string>
    <string name="snap_subtitle">Take a photo or pick one from your gallery. The app reads it on your phone.</string>
    <string name="goal_lose">Lose weight</string>
    <string name="goal_maintain">Maintain my weight</string>
    <string name="goal_gain">Build muscle</string>
    <string name="goal_eat_healthier">Eat healthier</string>
    <string name="goal_track">Just track what I eat</string>
    <string name="diary_subtitle">Meals from Health Connect, last 7 days. Tap a meal you logged here to delete it.</string>
    <string name="diary_empty">No meals in the last 7 days.</string>
    <string name="diary_day_total">%1$s kcal</string>
    <string name="day_today">Today</string>
    <string name="day_yesterday">Yesterday</string>
    <string name="meal_unnamed">Meal</string>
    <string name="meal_kcal_format">%1$s kcal</string>
    <string name="meal_from_other_app">from another app</string>
    <string name="meal_meta_format">%1$s, %2$s</string>
    <string name="meal_delete_title">Delete this meal?</string>
    <string name="meal_delete_message">%1$s, %2$s kcal. It will be removed from Health Connect.</string>
    <string name="meal_delete_ok">Delete</string>
    <string name="meal_deleted">Meal deleted.</string>
    <string name="meal_delete_failed">Could not delete the meal: %1$s</string>
    <string name="meal_not_mine">Logged by another app. Delete it in Health Connect.</string>
    <string name="other_food_button">Different food</string>
    <string name="no_food_text">No food recognized. The model saw: %1$s</string>
    <string name="name_food_button">Name the food</string>
    <string name="retake_button">Retake</string>
    <string name="done_button">Done</string>
    <string name="kcal_unit_per_serving">kcal per %1$s</string>
    <string name="food_match_format">%1$s, %2$s</string>
    <string name="result_error">Something went wrong: %1$s</string>
</resources>
```

EDIT `app/src/main/java/com/example/identify/ui/HealthFormat.java`
Find:
```java
import com.example.identify.core.Meals;
```
Replace with:
```java
import com.example.identify.core.Meals;
import com.example.identify.core.UserProfile;
```

EDIT `app/src/main/java/com/example/identify/ui/HealthFormat.java`
Find:
```java
    static String slot(Context ctx, Meals.Slot slot) {
```
Replace with:
```java
    static String goal(Context ctx, UserProfile.Goal goal) {
        switch (goal) {
            case LOSE: return ctx.getString(R.string.goal_lose);
            case MAINTAIN: return ctx.getString(R.string.goal_maintain);
            case GAIN: return ctx.getString(R.string.goal_gain);
            case EAT_HEALTHIER: return ctx.getString(R.string.goal_eat_healthier);
            default: return ctx.getString(R.string.goal_track);
        }
    }

    static String slot(Context ctx, Meals.Slot slot) {
```

REPLACE `app/src/main/res/navigation/nav_graph.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_graph"
    app:startDestination="@id/todayFragment">

    <fragment
        android:id="@+id/todayFragment"
        android:name="com.example.identify.ui.TodayFragment"
        android:label="@string/title_today">
        <action
            android:id="@+id/action_today_to_camera"
            app:destination="@id/cameraFragment" />
    </fragment>

    <fragment
        android:id="@+id/cameraFragment"
        android:name="com.example.identify.ui.CameraFragment"
        android:label="@string/title_camera">
        <action
            android:id="@+id/action_camera_to_result"
            app:destination="@id/resultFragment" />
    </fragment>

    <fragment
        android:id="@+id/resultFragment"
        android:name="com.example.identify.ui.ResultFragment"
        android:label="@string/title_result">
        <argument
            android:name="imagePath"
            app:argType="string" />
        <action
            android:id="@+id/action_result_to_today"
            app:destination="@id/todayFragment"
            app:popUpTo="@id/todayFragment"
            app:popUpToInclusive="true" />
    </fragment>

    <fragment
        android:id="@+id/diaryFragment"
        android:name="com.example.identify.ui.DiaryFragment"
        android:label="@string/title_diary" />

    <fragment
        android:id="@+id/historyFragment"
        android:name="com.example.identify.ui.HistoryFragment"
        android:label="@string/title_history" />

    <fragment
        android:id="@+id/settingsFragment"
        android:name="com.example.identify.ui.SettingsFragment"
        android:label="@string/title_settings" />
</navigation>
```

REPLACE `app/src/main/res/menu/bottom_nav_menu.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@+id/todayFragment"    android:icon="@android:drawable/ic_menu_today"        android:title="@string/title_today" />
    <item android:id="@+id/diaryFragment"    android:icon="@android:drawable/ic_menu_agenda"       android:title="@string/title_diary" />
    <item android:id="@+id/settingsFragment" android:icon="@android:drawable/ic_menu_preferences"  android:title="@string/title_settings" />
</menu>
```

EDIT `app/src/main/java/com/example/identify/MainActivity.java`
Find:
```java
        topLevel.add(R.id.cameraFragment);
        topLevel.add(R.id.historyFragment);
        topLevel.add(R.id.settingsFragment);
```
Replace with:
```java
        topLevel.add(R.id.todayFragment);
        topLevel.add(R.id.diaryFragment);
        topLevel.add(R.id.settingsFragment);
```

CREATE `app/src/main/res/layout/item_meal.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:foreground="?attr/selectableItemBackground"
    android:gravity="center_vertical"
    android:minHeight="56dp"
    android:orientation="horizontal"
    android:paddingVertical="8dp">

    <TextView
        android:id="@+id/meal_time"
        android:layout_width="72dp"
        android:layout_height="wrap_content"
        android:textAppearance="?attr/textAppearanceBodyMedium" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/meal_name"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:ellipsize="end"
            android:maxLines="2"
            android:textAppearance="?attr/textAppearanceTitleMedium" />

        <TextView
            android:id="@+id/meal_meta"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceBodySmall" />
    </LinearLayout>

    <TextView
        android:id="@+id/meal_kcal"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="8dp"
        android:textAppearance="?attr/textAppearanceTitleMedium"
        android:textStyle="bold" />
</LinearLayout>
```

CREATE `app/src/main/res/layout/item_day_header.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingTop="16dp"
    android:paddingBottom="4dp">

    <TextView
        android:id="@+id/day_label"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:textAppearance="?attr/textAppearanceTitleSmall"
        android:textColor="?attr/colorPrimary" />

    <TextView
        android:id="@+id/day_total"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textAppearance="?attr/textAppearanceBodyMedium" />
</LinearLayout>
```

CREATE `app/src/main/java/com/example/identify/ui/MealAdapter.java`
```java
package com.example.identify.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.identify.R;
import com.example.identify.core.Diary;
import com.example.identify.core.MealEntry;
import com.example.identify.databinding.ItemDayHeaderBinding;
import com.example.identify.databinding.ItemMealBinding;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/** Day headers and meal rows for the Today and Diary lists. */
final class MealAdapter extends ListAdapter<Diary.Row, RecyclerView.ViewHolder> {

    interface OnMealClick {
        void onClick(MealEntry meal);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_MEAL = 1;

    private static final DiffUtil.ItemCallback<Diary.Row> DIFF = new DiffUtil.ItemCallback<Diary.Row>() {
        @Override
        public boolean areItemsTheSame(@NonNull Diary.Row a, @NonNull Diary.Row b) {
            if (a.header != b.header) return false;
            return a.header ? a.date.equals(b.date) : a.meal.id.equals(b.meal.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Diary.Row a, @NonNull Diary.Row b) {
            if (a.header) return Double.compare(a.totalKcal, b.totalKcal) == 0 && a.mealCount == b.mealCount;
            return a.meal.name.equals(b.meal.name)
                    && Double.compare(a.meal.kcal, b.meal.kcal) == 0
                    && a.meal.startMillis == b.meal.startMillis;
        }
    };

    private final OnMealClick onClick;

    MealAdapter(OnMealClick onClick) {
        super(DIFF);
        this.onClick = onClick;
    }

    static final class HeaderVH extends RecyclerView.ViewHolder {
        final ItemDayHeaderBinding b;

        HeaderVH(ItemDayHeaderBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    static final class MealVH extends RecyclerView.ViewHolder {
        final ItemMealBinding b;

        MealVH(ItemMealBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).header ? TYPE_HEADER : TYPE_MEAL;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) return new HeaderVH(ItemDayHeaderBinding.inflate(inflater, parent, false));
        return new MealVH(ItemMealBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Diary.Row row = getItem(position);
        Context ctx = holder.itemView.getContext();
        if (holder instanceof HeaderVH) {
            HeaderVH h = (HeaderVH) holder;
            h.b.dayLabel.setText(dayLabel(ctx, row.date));
            h.b.dayTotal.setText(ctx.getString(R.string.diary_day_total, HealthFormat.kcal(ctx, row.totalKcal)));
            return;
        }
        MealVH m = (MealVH) holder;
        MealEntry e = row.meal;
        m.b.mealTime.setText(Instant.ofEpochMilli(e.startMillis).atZone(ZoneId.systemDefault()).toLocalTime()
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)));
        m.b.mealName.setText(e.name.isEmpty() ? ctx.getString(R.string.meal_unnamed) : e.name);
        String slot = e.slot == null ? "" : HealthFormat.slot(ctx, e.slot);
        String meta;
        if (e.mine) {
            meta = slot;
        } else if (slot.isEmpty()) {
            meta = ctx.getString(R.string.meal_from_other_app);
        } else {
            meta = ctx.getString(R.string.meal_meta_format, slot, ctx.getString(R.string.meal_from_other_app));
        }
        m.b.mealMeta.setText(meta);
        m.b.mealMeta.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);
        m.b.mealKcal.setText(ctx.getString(R.string.meal_kcal_format, HealthFormat.kcal(ctx, e.kcal)));
        m.itemView.setOnClickListener(v -> onClick.onClick(e));
    }

    static String dayLabel(Context ctx, LocalDate day) {
        LocalDate today = LocalDate.now();
        if (day.equals(today)) return ctx.getString(R.string.day_today);
        if (day.equals(today.minusDays(1))) return ctx.getString(R.string.day_yesterday);
        return day.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()));
    }
}
```

CREATE `app/src/main/java/com/example/identify/ui/MealDelete.java`
```java
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
```

CREATE `app/src/main/res/layout/fragment_today.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingStart="16dp"
            android:paddingTop="8dp"
            android:paddingEnd="16dp"
            android:paddingBottom="96dp">

            <TextView
                android:id="@+id/greeting_text"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textAppearance="?attr/textAppearanceHeadlineSmall" />

            <TextView
                android:id="@+id/date_text"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textAppearance="?attr/textAppearanceBodyMedium" />

            <TextView
                android:id="@+id/goal_text"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="2dp"
                android:textAppearance="?attr/textAppearanceBodySmall"
                android:textColor="?attr/colorPrimary" />

            <com.google.android.material.card.MaterialCardView
                android:id="@+id/connect_card"
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
                        android:text="@string/today_connect_needed"
                        android:textAppearance="?attr/textAppearanceBodyLarge"
                        android:textColor="?attr/colorOnSecondaryContainer" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/connect_button"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:text="@string/today_connect_button" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                app:cardBackgroundColor="?attr/colorPrimaryContainer"
                app:strokeWidth="0dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:baselineAligned="false"
                    android:gravity="center_vertical"
                    android:orientation="horizontal"
                    android:padding="16dp">

                    <FrameLayout
                        android:layout_width="140dp"
                        android:layout_height="140dp">

                        <com.google.android.material.progressindicator.CircularProgressIndicator
                            android:id="@+id/kcal_ring"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_gravity="center"
                            android:indeterminate="false"
                            app:indicatorSize="136dp"
                            app:trackCornerRadius="6dp"
                            app:trackThickness="12dp" />

                        <LinearLayout
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_gravity="center"
                            android:gravity="center"
                            android:orientation="vertical">

                            <TextView
                                android:id="@+id/kcal_eaten_text"
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:textAppearance="?attr/textAppearanceHeadlineSmall"
                                android:textColor="?attr/colorOnPrimaryContainer"
                                android:textStyle="bold" />

                            <TextView
                                android:id="@+id/kcal_budget_text"
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:textAppearance="?attr/textAppearanceBodySmall"
                                android:textColor="?attr/colorOnPrimaryContainer" />
                        </LinearLayout>
                    </FrameLayout>

                    <LinearLayout
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="16dp"
                        android:layout_weight="1"
                        android:orientation="vertical">

                        <TextView
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:text="@string/today_calories_title"
                            android:textAppearance="?attr/textAppearanceTitleMedium"
                            android:textColor="?attr/colorOnPrimaryContainer" />

                        <TextView
                            android:id="@+id/kcal_remaining_text"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:layout_marginTop="4dp"
                            android:textAppearance="?attr/textAppearanceBodyLarge"
                            android:textStyle="bold" />

                        <TextView
                            android:id="@+id/burned_text"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:layout_marginTop="8dp"
                            android:textAppearance="?attr/textAppearanceBodyMedium"
                            android:textColor="?attr/colorOnPrimaryContainer" />
                    </LinearLayout>
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal">

                        <TextView
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="@string/today_steps_title"
                            android:textAppearance="?attr/textAppearanceTitleMedium" />

                        <TextView
                            android:id="@+id/steps_text"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:textAppearance="?attr/textAppearanceBodyLarge"
                            android:textStyle="bold" />
                    </LinearLayout>

                    <com.google.android.material.progressindicator.LinearProgressIndicator
                        android:id="@+id/steps_progress"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:indeterminate="false"
                        app:indicatorColor="@color/steps_bar"
                        app:trackCornerRadius="5dp"
                        app:trackThickness="10dp" />

                    <TextView
                        android:id="@+id/steps_hint"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="4dp"
                        android:textAppearance="?attr/textAppearanceBodySmall" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="24dp"
                android:text="@string/today_meals_title"
                android:textAppearance="?attr/textAppearanceTitleMedium" />

            <TextView
                android:id="@+id/meals_empty_text"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:text="@string/today_meals_empty"
                android:textAppearance="?attr/textAppearanceBodyMedium"
                android:visibility="gone" />

            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/meals_list"
                android:layout_width="match_parent"
                android:layout_height="wrap_content" />
        </LinearLayout>
    </androidx.core.widget.NestedScrollView>

    <com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
        android:id="@+id/snap_button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_margin="16dp"
        android:text="@string/snap_meal"
        app:icon="@android:drawable/ic_menu_camera" />
</FrameLayout>
```

CREATE `app/src/main/java/com/example/identify/ui/TodayFragment.java`
```java
package com.example.identify.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.identify.AppPrefs;
import com.example.identify.R;
import com.example.identify.core.DailyHealth;
import com.example.identify.core.Diary;
import com.example.identify.core.UserProfile;
import com.example.identify.databinding.FragmentTodayBinding;
import com.example.identify.health.HealthConnectRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Home screen: calories eaten against the budget, burned, steps against the goal, and today's meals. */
public class TodayFragment extends Fragment {

    private static final int SCALE = 1000;

    private FragmentTodayBinding binding;
    private MealAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTodayBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        adapter = new MealAdapter(meal -> MealDelete.confirm(this, meal, "today", this::refresh));
        binding.mealsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.mealsList.setNestedScrollingEnabled(false);
        binding.mealsList.setAdapter(adapter);
        binding.kcalRing.setMax(SCALE);
        binding.stepsProgress.setMax(SCALE);
        binding.snapButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_today_to_camera));
        binding.connectButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.settingsFragment));
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void refresh() {
        if (binding == null || !isAdded()) return;
        final Context ctx = requireContext();
        AppPrefs prefs = AppPrefs.get(ctx);
        UserProfile profile = prefs.getProfile();
        binding.greetingText.setText(profile.name.isEmpty()
                ? getString(R.string.today_greeting_plain)
                : getString(R.string.today_greeting, profile.name));
        binding.dateText.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())));
        binding.goalText.setText(getString(R.string.today_goal_line, HealthFormat.goal(ctx, profile.goal)));

        final long budget = prefs.getCalorieBudget();
        final long goal = prefs.getStepGoal();
        boolean connected = HealthConnectRepository.isAvailable(ctx)
                && HealthConnectRepository.missingPermissions(ctx).size() < HealthConnectRepository.PERMISSIONS.length;
        binding.connectCard.setVisibility(connected ? View.GONE : View.VISIBLE);
        renderCalories(Double.NaN, budget);
        renderSteps(DailyHealth.UNKNOWN_STEPS, goal);
        binding.burnedText.setText(getString(R.string.today_burned,
                HealthFormat.kcal(ctx, Double.NaN), HealthFormat.kcal(ctx, Double.NaN)));
        if (!connected) {
            adapter.submitList(null);
            binding.mealsEmptyText.setVisibility(View.VISIBLE);
            return;
        }

        HealthConnectRepository.readToday(ctx, (today, error) -> {
            if (binding == null || !isAdded() || today == null) return;
            renderCalories(today.eatenKcal, budget);
            renderSteps(today.steps, goal);
            binding.burnedText.setText(getString(R.string.today_burned,
                    HealthFormat.kcal(ctx, today.burnedKcal), HealthFormat.kcal(ctx, today.activeKcal)));
        });
        final ZoneId zone = ZoneId.systemDefault();
        Instant start = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        HealthConnectRepository.readMeals(ctx, start, Instant.now(), (meals, error) -> {
            if (binding == null || !isAdded()) return;
            adapter.submitList(Diary.mealRows(meals, zone));
            binding.mealsEmptyText.setVisibility(meals.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void renderCalories(double eatenKcal, long budget) {
        Context ctx = requireContext();
        double eaten = Double.isNaN(eatenKcal) ? 0 : eatenKcal;
        boolean over = eaten > budget;
        int progress = budget <= 0 ? 0 : (int) Math.min(SCALE, Math.round(eaten * SCALE / budget));
        binding.kcalRing.setProgressCompat(progress, true);
        binding.kcalRing.setIndicatorColor(ContextCompat.getColor(ctx, over ? R.color.kcal_over : R.color.kcal_ok));
        binding.kcalEatenText.setText(HealthFormat.kcal(ctx, eaten));
        binding.kcalBudgetText.setText(getString(R.string.today_of_budget, HealthFormat.kcal(ctx, budget)));
        binding.kcalRemainingText.setText(over
                ? getString(R.string.today_over, HealthFormat.kcal(ctx, eaten - budget))
                : getString(R.string.today_remaining, HealthFormat.kcal(ctx, budget - eaten)));
        binding.kcalRemainingText.setTextColor(ContextCompat.getColor(ctx, over ? R.color.kcal_over : R.color.kcal_ok));
    }

    private void renderSteps(long steps, long goal) {
        Context ctx = requireContext();
        binding.stepsText.setText(getString(R.string.today_steps, HealthFormat.steps(ctx, steps), HealthFormat.steps(ctx, goal)));
        if (steps < 0 || goal <= 0) {
            binding.stepsProgress.setProgressCompat(0, false);
            binding.stepsHint.setVisibility(View.GONE);
            return;
        }
        binding.stepsProgress.setProgressCompat((int) Math.min(SCALE, steps * SCALE / goal), true);
        long left = new DailyHealth(steps, 0, 0, 0).stepsRemaining(goal);
        binding.stepsHint.setVisibility(View.VISIBLE);
        binding.stepsHint.setText(left > 0
                ? getString(R.string.today_steps_to_go, HealthFormat.steps(ctx, left))
                : getString(R.string.today_steps_done));
    }
}
```

CREATE `app/src/main/res/layout/fragment_diary.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:paddingStart="16dp"
    android:paddingTop="8dp"
    android:paddingEnd="16dp">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/diary_subtitle"
        android:textAppearance="?attr/textAppearanceBodyMedium" />

    <TextView
        android:id="@+id/diary_empty_text"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:text="@string/diary_empty"
        android:textAppearance="?attr/textAppearanceBodyLarge"
        android:visibility="gone" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/diary_list"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />
</LinearLayout>
```

CREATE `app/src/main/java/com/example/identify/ui/DiaryFragment.java`
```java
package com.example.identify.ui;

import android.content.Context;
import android.health.connect.HealthPermissions;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.identify.R;
import com.example.identify.core.Diary;
import com.example.identify.databinding.FragmentDiaryBinding;
import com.example.identify.health.HealthConnectRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** The last 7 days of meals from Health Connect, grouped by day with daily totals. */
public class DiaryFragment extends Fragment {

    private static final int DAYS = 7;

    private FragmentDiaryBinding binding;
    private MealAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDiaryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        adapter = new MealAdapter(meal -> MealDelete.confirm(this, meal, "diary", this::refresh));
        binding.diaryList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.diaryList.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void refresh() {
        if (binding == null || !isAdded()) return;
        Context ctx = requireContext();
        if (!HealthConnectRepository.isAvailable(ctx)
                || !HealthConnectRepository.isGranted(ctx, HealthPermissions.READ_NUTRITION)) {
            adapter.submitList(null);
            binding.diaryEmptyText.setVisibility(View.VISIBLE);
            binding.diaryEmptyText.setText(R.string.today_connect_needed);
            return;
        }
        final ZoneId zone = ZoneId.systemDefault();
        Instant from = LocalDate.now(zone).minusDays(DAYS - 1).atStartOfDay(zone).toInstant();
        HealthConnectRepository.readMeals(ctx, from, Instant.now(), (meals, error) -> {
            if (binding == null || !isAdded()) return;
            adapter.submitList(Diary.rows(meals, zone));
            if (error != null) {
                binding.diaryEmptyText.setVisibility(View.VISIBLE);
                binding.diaryEmptyText.setText(getString(R.string.health_read_failed, error));
            } else {
                binding.diaryEmptyText.setVisibility(meals.isEmpty() ? View.VISIBLE : View.GONE);
                binding.diaryEmptyText.setText(R.string.diary_empty);
            }
        });
    }
}
```

REPLACE `app/src/main/res/layout/fragment_camera.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/snap_title"
        android:textAppearance="?attr/textAppearanceHeadlineSmall" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:text="@string/snap_subtitle"
        android:textAppearance="?attr/textAppearanceBodyMedium" />

    <com.google.android.material.card.MaterialCardView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_marginTop="16dp"
        android:layout_weight="1"
        app:cardBackgroundColor="?attr/colorSurfaceVariant"
        app:strokeWidth="0dp">

        <FrameLayout
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <ImageView
                android:id="@+id/preview_image"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:importantForAccessibility="no"
                android:scaleType="centerCrop" />

            <TextView
                android:id="@+id/empty_hint"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_gravity="center"
                android:gravity="center"
                android:padding="24dp"
                android:text="@string/empty_hint"
                android:textAppearance="?attr/textAppearanceBodyLarge" />

            <com.google.android.material.progressindicator.CircularProgressIndicator
                android:id="@+id/prepare_progress"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_gravity="center"
                android:indeterminate="true"
                android:visibility="gone" />
        </FrameLayout>
    </com.google.android.material.card.MaterialCardView>

    <TextView
        android:id="@+id/model_status_text"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:text="@string/model_missing_hint"
        android:textAppearance="?attr/textAppearanceBodyMedium"
        android:visibility="gone" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/open_settings_button"
        style="@style/Widget.Material3.Button.TextButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/open_settings"
        android:visibility="gone" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:orientation="horizontal">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/take_photo_button"
            android:layout_width="0dp"
            android:layout_height="56dp"
            android:layout_marginEnd="8dp"
            android:layout_weight="1"
            android:text="@string/take_photo"
            app:icon="@android:drawable/ic_menu_camera" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/pick_photo_button"
            style="@style/Widget.Material3.Button.OutlinedButton"
            android:layout_width="0dp"
            android:layout_height="56dp"
            android:layout_weight="1"
            android:text="@string/pick_photo"
            app:icon="@android:drawable/ic_menu_gallery" />
    </LinearLayout>

    <com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
        android:id="@+id/identify_fab"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="end"
        android:layout_marginTop="12dp"
        android:text="@string/identify"
        android:visibility="gone" />
</LinearLayout>
```

REPLACE `app/src/main/res/layout/fragment_result.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.core.widget.NestedScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <com.google.android.material.imageview.ShapeableImageView
            android:id="@+id/result_image"
            android:layout_width="match_parent"
            android:layout_height="240dp"
            android:importantForAccessibility="no"
            android:scaleType="centerCrop"
            app:shapeAppearanceOverlay="@style/ShapeAppearance.Identify.Rounded" />

        <LinearLayout
            android:id="@+id/progress_container"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <com.google.android.material.progressindicator.CircularProgressIndicator
                android:id="@+id/result_progress"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:indeterminate="true" />

            <TextView
                android:id="@+id/progress_text"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="12dp"
                android:layout_weight="1"
                android:textAppearance="?attr/textAppearanceBodyLarge" />
        </LinearLayout>

        <LinearLayout
            android:id="@+id/result_block"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:orientation="vertical"
            android:visibility="gone">

            <TextView
                android:id="@+id/label_text"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textAppearance="?attr/textAppearanceHeadlineMedium"
                android:textStyle="bold" />

            <com.google.android.material.chip.Chip
                android:id="@+id/cuisine_chip"
                style="@style/Widget.Material3.Chip.Assist"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:clickable="false"
                android:visibility="gone" />

            <TextView
                android:id="@+id/description_text"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:textAppearance="?attr/textAppearanceBodyLarge" />

            <TextView
                android:id="@+id/source_text"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:textAppearance="?attr/textAppearanceBodySmall" />
        </LinearLayout>

        <com.google.android.material.card.MaterialCardView
            android:id="@+id/kcal_card"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:visibility="gone"
            app:cardBackgroundColor="?attr/colorPrimaryContainer"
            app:strokeWidth="0dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:gravity="bottom"
                    android:orientation="horizontal">

                    <TextView
                        android:id="@+id/kcal_value_text"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:textAppearance="?attr/textAppearanceDisplaySmall"
                        android:textColor="?attr/colorOnPrimaryContainer"
                        android:textStyle="bold" />

                    <TextView
                        android:id="@+id/kcal_unit_text"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="8dp"
                        android:layout_marginBottom="6dp"
                        android:textAppearance="?attr/textAppearanceTitleMedium"
                        android:textColor="?attr/colorOnPrimaryContainer" />
                </LinearLayout>

                <TextView
                    android:id="@+id/food_match_text"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:textAppearance="?attr/textAppearanceBodyMedium"
                    android:textColor="?attr/colorOnPrimaryContainer" />

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:orientation="horizontal">

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/log_meal_button"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/log_meal_button" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/other_food_button"
                        style="@style/Widget.Material3.Button.TextButton"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="8dp"
                        android:text="@string/other_food_button" />
                </LinearLayout>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <com.google.android.material.card.MaterialCardView
            android:id="@+id/no_food_card"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:visibility="gone">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:id="@+id/no_food_text"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:textAppearance="?attr/textAppearanceBodyLarge" />

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:orientation="horizontal">

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/name_food_button"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/name_food_button" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/retake_button"
                        style="@style/Widget.Material3.Button.OutlinedButton"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="8dp"
                        android:text="@string/retake_button" />
                </LinearLayout>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <com.google.android.material.card.MaterialCardView
            android:id="@+id/logged_card"
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
                    android:id="@+id/logged_text"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:textAppearance="?attr/textAppearanceTitleMedium"
                    android:textColor="?attr/colorOnSecondaryContainer" />

                <TextView
                    android:id="@+id/today_summary_text"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:textAppearance="?attr/textAppearanceBodyMedium"
                    android:textColor="?attr/colorOnSecondaryContainer"
                    android:visibility="gone" />

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:orientation="horizontal">

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/done_button"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/done_button" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/undo_meal_button"
                        style="@style/Widget.Material3.Button.OutlinedButton"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="8dp"
                        android:text="@string/meal_undo" />
                </LinearLayout>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <TextView
            android:id="@+id/error_text"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:textAppearance="?attr/textAppearanceBodyLarge"
            android:visibility="gone" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/retry_button"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="@string/retry"
            android:visibility="gone" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/run_model_button"
            style="@style/Widget.Material3.Button.TextButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="@string/run_model_anyway"
            android:visibility="gone" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/details_button"
            style="@style/Widget.Material3.Button.TextButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/show_details"
            android:visibility="gone" />

        <TextView
            android:id="@+id/details_text"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceBodySmall"
            android:textIsSelectable="true"
            android:visibility="gone" />
    </LinearLayout>
</androidx.core.widget.NestedScrollView>
```

REPLACE `app/src/main/java/com/example/identify/ui/ResultFragment.java`
```java
package com.example.identify.ui;

import android.content.Context;
import android.health.connect.HealthPermissions;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.example.identify.AppPrefs;
import com.example.identify.Config;
import com.example.identify.R;
import com.example.identify.core.FoodItem;
import com.example.identify.core.FoodMatcher;
import com.example.identify.core.Meals;
import com.example.identify.databinding.FragmentResultBinding;
import com.example.identify.health.FoodRepository;
import com.example.identify.health.HealthConnectRepository;
import com.example.identify.util.ExperimentLog;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONObject;

import java.io.File;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

/**
 * One photo: the dish and its cuisine, the calories for one serving, and logging the meal. Logging also
 * teaches the memory once per photo: the guessed food is saved as an accept, any other food as a correction.
 */
public class ResultFragment extends Fragment {

    private static final String KEY_DETAILS_OPEN = "detailsOpen";
    private static final String KEY_MEAL_RECORD_ID = "mealRecordId";
    private static final String KEY_MEAL_SUMMARY = "mealSummary";
    private static final String KEY_FEEDBACK_SAVED = "feedbackSaved";

    private FragmentResultBinding binding;
    private ResultViewModel vm;
    private boolean detailsOpen;
    /** Health Connect id of the meal logged from this screen, kept so Undo can delete it. */
    private String loggedRecordId;
    private String loggedSummary;
    private boolean feedbackSaved;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentResultBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null) {
            detailsOpen = savedInstanceState.getBoolean(KEY_DETAILS_OPEN);
            loggedRecordId = savedInstanceState.getString(KEY_MEAL_RECORD_ID);
            loggedSummary = savedInstanceState.getString(KEY_MEAL_SUMMARY);
            feedbackSaved = savedInstanceState.getBoolean(KEY_FEEDBACK_SAVED);
        }
        vm = new ViewModelProvider(this).get(ResultViewModel.class);
        String path = requireArguments().getString("imagePath");
        if (path == null) {
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }

        Glide.with(this).load(new File(path)).into(binding.resultImage);
        vm.start(path);

        vm.getStage().observe(getViewLifecycleOwner(), this::renderStage);
        vm.getResult().observe(getViewLifecycleOwner(), r -> renderResult());
        // Feedback rows are saved quietly in the background; the user leaves with Done.
        vm.getSaved().observe(getViewLifecycleOwner(), saved -> {
            if (Boolean.TRUE.equals(saved)) vm.consumeSaved();
        });

        binding.logMealButton.setOnClickListener(v -> openMealDialog(currentLabel()));
        binding.otherFoodButton.setOnClickListener(v -> openMealDialog(""));
        binding.nameFoodButton.setOnClickListener(v -> openMealDialog(""));
        binding.retakeButton.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        binding.doneButton.setOnClickListener(v -> goToday());
        binding.undoMealButton.setOnClickListener(v -> onUndoMealClicked());
        binding.runModelButton.setOnClickListener(v -> vm.runModelAnyway());
        binding.retryButton.setOnClickListener(v -> vm.retry());
        binding.detailsButton.setOnClickListener(v -> {
            detailsOpen = !detailsOpen;
            renderResult();
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_DETAILS_OPEN, detailsOpen);
        outState.putString(KEY_MEAL_RECORD_ID, loggedRecordId);
        outState.putString(KEY_MEAL_SUMMARY, loggedSummary);
        outState.putBoolean(KEY_FEEDBACK_SAVED, feedbackSaved);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        keepScreenOn(false);
        binding = null;
    }

    /** A run takes many seconds; if the screen turns off, Android moves the work to slow background cores. */
    private void keepScreenOn(boolean on) {
        if (getActivity() == null) return;
        if (on) getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void renderStage(ResultViewModel.Stage stage) {
        if (binding == null || stage == null) return;
        boolean working = stage == ResultViewModel.Stage.LOADING_MODEL
                || stage == ResultViewModel.Stage.EMBEDDING
                || stage == ResultViewModel.Stage.GENERATING;
        keepScreenOn(working);
        boolean running = working || stage == ResultViewModel.Stage.IDLE;
        boolean done = stage == ResultViewModel.Stage.DONE;
        boolean failed = stage == ResultViewModel.Stage.ERROR;
        binding.progressContainer.setVisibility(running ? View.VISIBLE : View.GONE);
        if (running) {
            binding.progressText.setText(stage == ResultViewModel.Stage.EMBEDDING
                    ? R.string.stage_embedding
                    : stage == ResultViewModel.Stage.GENERATING
                            ? R.string.stage_generating
                            : R.string.stage_loading_model);
        }
        binding.resultBlock.setVisibility(done ? View.VISIBLE : View.GONE);
        binding.errorText.setVisibility(failed ? View.VISIBLE : View.GONE);
        binding.retryButton.setVisibility(failed ? View.VISIBLE : View.GONE);
        if (!done) {
            binding.kcalCard.setVisibility(View.GONE);
            binding.noFoodCard.setVisibility(View.GONE);
            binding.loggedCard.setVisibility(View.GONE);
            binding.runModelButton.setVisibility(View.GONE);
            binding.detailsButton.setVisibility(View.GONE);
            binding.detailsText.setVisibility(View.GONE);
        }
        if (failed) {
            String msg = vm.getError().getValue();
            binding.errorText.setText(getString(R.string.result_error, msg == null ? "" : msg));
        }
        if (done) renderResult();
    }

    /** Writes the current result into the screen. Only while DONE, so an error message is never overwritten. */
    private void renderResult() {
        if (binding == null || vm.getStage().getValue() != ResultViewModel.Stage.DONE) return;
        ResultViewModel.IdentifyResult r = vm.getResult().getValue();
        if (r == null) return;
        binding.labelText.setText(r.label);
        binding.cuisineChip.setVisibility(r.cuisine.isEmpty() ? View.GONE : View.VISIBLE);
        binding.cuisineChip.setText(r.cuisine);
        binding.descriptionText.setText(r.description);
        binding.descriptionText.setVisibility(r.description == null || r.description.isEmpty() ? View.GONE : View.VISIBLE);
        boolean fromMemory = Config.SOURCE_MEMORY.equals(r.source);
        binding.sourceText.setText(fromMemory
                ? getString(R.string.source_memory, r.latencyMs / 1000f, r.nearestScore)
                : getString(R.string.source_model, r.latencyMs / 1000f));
        binding.runModelButton.setVisibility(fromMemory && loggedRecordId == null ? View.VISIBLE : View.GONE);
        boolean hasDetails = r.details != null && !r.details.isEmpty();
        binding.detailsButton.setVisibility(hasDetails ? View.VISIBLE : View.GONE);
        binding.detailsButton.setText(detailsOpen ? R.string.hide_details : R.string.show_details);
        binding.detailsText.setText(r.details);
        binding.detailsText.setVisibility(hasDetails && detailsOpen ? View.VISIBLE : View.GONE);
        renderFood(r);
    }

    /** kcal card for a table match, "no food" card otherwise, or the "logged" card after logging. */
    private void renderFood(ResultViewModel.IdentifyResult r) {
        if (loggedRecordId != null) {
            binding.kcalCard.setVisibility(View.GONE);
            binding.noFoodCard.setVisibility(View.GONE);
            binding.loggedCard.setVisibility(View.VISIBLE);
            binding.loggedText.setText(loggedSummary);
            binding.undoMealButton.setEnabled(true);
            return;
        }
        binding.loggedCard.setVisibility(View.GONE);
        FoodItem top = topMatch(r);
        if (top == null) {
            binding.kcalCard.setVisibility(View.GONE);
            binding.noFoodCard.setVisibility(View.VISIBLE);
            binding.noFoodText.setText(getString(R.string.no_food_text, r.label));
            return;
        }
        Context ctx = requireContext();
        binding.noFoodCard.setVisibility(View.GONE);
        binding.kcalCard.setVisibility(View.VISIBLE);
        binding.kcalValueText.setText(HealthFormat.kcal(ctx, top.kcal));
        binding.kcalUnitText.setText(getString(R.string.kcal_unit_per_serving, top.serving));
        binding.foodMatchText.setText(getString(R.string.food_match_format, top.displayName(), top.cuisine));
        binding.logMealButton.setEnabled(true);
        binding.logMealButton.setText(R.string.log_meal_button);
    }

    /** Best table row for the model's label, using its cuisine and the user's favorite cuisines. */
    private FoodItem topMatch(ResultViewModel.IdentifyResult r) {
        Context ctx = requireContext();
        List<FoodMatcher.Match> m = FoodMatcher.match(r.label, r.cuisine, AppPrefs.get(ctx).getProfile().cuisines,
                FoodRepository.foods(ctx), 1);
        return m.isEmpty() ? null : m.get(0).item;
    }

    private String currentLabel() {
        ResultViewModel.IdentifyResult r = vm.getResult().getValue();
        return r == null ? "" : r.label;
    }

    private void openMealDialog(String query) {
        ResultViewModel.IdentifyResult r = vm.getResult().getValue();
        if (r == null || binding == null) return;
        Context ctx = requireContext();
        if (!HealthConnectRepository.isAvailable(ctx)
                || !HealthConnectRepository.isGranted(ctx, HealthPermissions.WRITE_NUTRITION)) {
            Snackbar.make(binding.getRoot(), R.string.meal_need_permission, Snackbar.LENGTH_LONG)
                    .setAction(R.string.meal_open_settings,
                            v -> NavHostFragment.findNavController(this).navigate(R.id.settingsFragment))
                    .show();
            return;
        }
        LogMealDialog.show(this, query,
                (food, portion, kcal, kcalEdited, q) -> logMeal(r, food, portion, kcal, kcalEdited, q));
    }

    /** Writes the meal to Health Connect, saves the feedback once, logs meal_logged, then shows today's totals. */
    private void logMeal(ResultViewModel.IdentifyResult r, FoodItem food, double portion, double kcal,
                         boolean kcalEdited, String query) {
        if (binding == null) return;
        final Context app = requireContext().getApplicationContext();
        final Meals.Slot slot = Meals.slotForHour(LocalTime.now().getHour());
        final double protein = Meals.scaled(food.proteinG, portion);
        final double carbs = Meals.scaled(food.carbsG, portion);
        final double fat = Meals.scaled(food.fatG, portion);
        final long t0 = SystemClock.elapsedRealtime();
        binding.logMealButton.setEnabled(false);
        binding.logMealButton.setText(R.string.meal_logging);
        HealthConnectRepository.insertMeal(app, food.name, kcal, protein, carbs, fat, slot, (recordId, error) -> {
            JSONObject e = ExperimentLog.event("meal_logged");
            ExperimentLog.put(e, "run_id", r.runId);
            ExperimentLog.put(e, "model_label", r.label);
            ExperimentLog.put(e, "model_cuisine", r.cuisine);
            ExperimentLog.put(e, "query", query);
            ExperimentLog.put(e, "food_id", food.id);
            ExperimentLog.put(e, "food_name", food.displayName());
            ExperimentLog.put(e, "food_cuisine", food.cuisine);
            ExperimentLog.put(e, "portion", portion);
            ExperimentLog.put(e, "kcal_logged", kcal);
            ExperimentLog.put(e, "kcal_table", food.kcal * portion);
            ExperimentLog.put(e, "kcal_edited", kcalEdited);
            ExperimentLog.put(e, "protein_g", protein);
            ExperimentLog.put(e, "carbs_g", carbs);
            ExperimentLog.put(e, "fat_g", fat);
            ExperimentLog.put(e, "meal_slot", slot.name().toLowerCase(Locale.ROOT));
            ExperimentLog.put(e, "hc_record_id", recordId);
            ExperimentLog.put(e, "ms", SystemClock.elapsedRealtime() - t0);
            ExperimentLog.put(e, "error", error);
            ExperimentLog.append(app, e);
            if (binding == null || !isAdded()) return;
            if (error != null) {
                binding.logMealButton.setEnabled(true);
                binding.logMealButton.setText(R.string.log_meal_button);
                Snackbar.make(binding.getRoot(), getString(R.string.meal_log_failed, error), Snackbar.LENGTH_LONG).show();
                return;
            }
            loggedRecordId = recordId;
            loggedSummary = getString(R.string.meal_logged, HealthFormat.kcal(app, kcal), food.displayName(),
                    HealthFormat.slot(app, slot));
            saveFeedback(r, food);
            renderResult();
            showToday();
        });
    }

    /** Once per photo: the guessed food is an accept, any other food a correction (US-3.5). */
    private void saveFeedback(ResultViewModel.IdentifyResult r, FoodItem food) {
        if (feedbackSaved) return;
        feedbackSaved = true;
        FoodItem guess = topMatch(r);
        if (guess != null && guess.id.equals(food.id)) vm.accept();
        else vm.submitCorrection(food.name);
    }

    /** Today's eaten, burned, and step totals under the logged meal. */
    private void showToday() {
        final Context app = requireContext().getApplicationContext();
        final long goal = AppPrefs.get(app).getStepGoal();
        HealthConnectRepository.readToday(app, (today, error) -> {
            if (binding == null || !isAdded() || today == null) return;
            binding.todaySummaryText.setVisibility(View.VISIBLE);
            binding.todaySummaryText.setText(getString(R.string.meal_today_summary,
                    HealthFormat.kcal(app, today.eatenKcal), HealthFormat.kcal(app, today.burnedKcal),
                    HealthFormat.steps(app, today.steps), HealthFormat.steps(app, goal)));
        });
    }

    private void onUndoMealClicked() {
        if (loggedRecordId == null || binding == null) return;
        final String id = loggedRecordId;
        final Context app = requireContext().getApplicationContext();
        binding.undoMealButton.setEnabled(false);
        HealthConnectRepository.deleteMeal(app, id, error -> {
            JSONObject e = ExperimentLog.event("meal_undone");
            ExperimentLog.put(e, "hc_record_id", id);
            ExperimentLog.put(e, "error", error);
            ExperimentLog.append(app, e);
            if (binding == null || !isAdded()) return;
            if (error != null) {
                binding.undoMealButton.setEnabled(true);
                Snackbar.make(binding.getRoot(), getString(R.string.meal_undo_failed, error), Snackbar.LENGTH_LONG).show();
                return;
            }
            loggedRecordId = null;
            loggedSummary = null;
            binding.todaySummaryText.setVisibility(View.GONE);
            Snackbar.make(binding.getRoot(), R.string.meal_undone, Snackbar.LENGTH_LONG).show();
            renderResult();
        });
    }

    private void goToday() {
        NavController nav = NavHostFragment.findNavController(this);
        NavDestination current = nav.getCurrentDestination();
        if (current != null && current.getId() == R.id.resultFragment) nav.navigate(R.id.action_result_to_today);
    }
}
```

Remove the strings and colors of the old Accept and Correct buttons (the new screen saves feedback when a meal is logged). Each Find keeps one neighboring line so the edit has context.

EDIT `app/src/main/res/values/strings.xml`
Find:
```xml
    <string name="stage_generating">Generating answer</string>
    <string name="accept">👍 Accept</string>
    <string name="correct">✏️ Correct</string>
    <string name="correction_hint">What is it actually?</string>
    <string name="save_correction">Save correction</string>
```
Replace with:
```xml
    <string name="stage_generating">Generating answer</string>
```

EDIT `app/src/main/res/values/strings.xml`
Find:
```xml
    <string name="source_memory">Identified from memory (%1$.1f s, similarity %2$.2f)</string>
    <string name="correction_empty">Type the correct name first.</string>
```
Replace with:
```xml
    <string name="source_memory">Identified from memory (%1$.1f s, similarity %2$.2f)</string>
```

EDIT `app/src/main/res/values/strings.xml`
Find:
```xml
    <string name="food_detected">Looks like food: %1$s, about %2$s kcal per %3$s.</string>
    <string name="log_meal_button">Log meal</string>
```
Replace with:
```xml
    <string name="log_meal_button">Log meal</string>
```

EDIT `app/src/main/res/values/colors.xml`
Find:
```xml
<resources>
    <color name="accept_green">#2E7D32</color>
    <color name="correct_amber">#FF8F00</color>

    <color name="brand_green">#2E7D32</color>
```
Replace with:
```xml
<resources>
    <color name="brand_green">#2E7D32</color>
```

## VERIFY 10.5

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew :core:test :app:assembleDebug :app:lintDebug --console=plain
tail -n 1 app/build/reports/lint-results-debug.txt
```
BUILD SUCCESSFUL, lint `0 errors`.

Commit subject: `Redesign around a Today dashboard: Today, Diary, Snap, and a meal-first result`.
