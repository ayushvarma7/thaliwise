# STEP 10.6: Onboarding (user profiling) and the profile in Settings

Goal: `docs/USER_STORIES.md` section 3 and stories US-1.1 to US-1.7. A six-step onboarding runs on first launch, before the main screens: (1) welcome and name, (2) age, sex, units, height, weight, activity, (3) goal and reasons, (4) favorite cuisines, (5) diet and what to eat more of, (6) the plan: suggested calorie budget and step goal (editable), optional Health Connect connect. Every answer is optional. Start saves the profile, budget, step goal, and units, sets the onboarded flag, and logs a `profile_saved` event. Settings shows the profile with Edit profile (reopens onboarding with the answers filled in) and a link to the identification history.

Design notes:
- The same activity serves first run and Edit profile: `AppPrefs.isOnboarded()` false means first run. First run fills the defaults from `AppPrefs.getProfile()` (activity LIGHT, goal TRACK) and opens MainActivity at the end; Edit profile fills the saved answers and just closes.
- The plan fields take the suggestion when they are empty or still hold the last suggestion written into them; a value the user typed is kept. "Use suggested" puts the suggestion back.
- Chips are added from string arrays and do not save their own state (their ids are generated); the activity saves the step, units, sex, and chip choices itself. Text fields and radio buttons restore themselves, so the first `showStep` runs in `onPostCreate`, after that restore.
- `profile_saved` records whether a name was given, not the name.

EDIT `app/src/main/res/values/strings.xml`
Find:
```xml
</resources>
```
Replace with:
```xml

    <string name="onboarding_step">Step %1$s of %2$s</string>
    <string name="onboarding_next">Next</string>
    <string name="onboarding_back">Back</string>
    <string name="onboarding_start">Start</string>
    <string name="onboarding_save">Save</string>
    <string name="onboarding_welcome_title">Welcome</string>
    <string name="onboarding_welcome_body">Snap a photo of your meal. The app names the dish and its cuisine, finds the calories, and logs the meal in Health Connect next to your steps and calories burned.</string>
    <string name="onboarding_welcome_private">Everything runs on your phone. Your photos, meals, and answers are never uploaded.</string>
    <string name="onboarding_name_hint">What should we call you?</string>
    <string name="onboarding_skip_note">Six short steps. You can skip any question and change your answers later in Settings.</string>
    <string name="onboarding_body_title">About you</string>
    <string name="onboarding_body_why">Your body size and activity set a daily calorie budget that fits you.</string>
    <string name="onboarding_age_hint">Age</string>
    <string name="onboarding_sex_label">Sex (used only by the calorie formula)</string>
    <string name="sex_female">Female</string>
    <string name="sex_male">Male</string>
    <string name="sex_unspecified">Prefer not to say</string>
    <string name="onboarding_units_label">Units</string>
    <string name="units_us">US (ft, lb)</string>
    <string name="units_metric">Metric (cm, kg)</string>
    <string name="onboarding_feet_hint">Height (ft)</string>
    <string name="onboarding_inches_hint">Inches</string>
    <string name="onboarding_cm_hint">Height (cm)</string>
    <string name="onboarding_weight_lb_hint">Weight (lb)</string>
    <string name="onboarding_weight_kg_hint">Weight (kg)</string>
    <string name="onboarding_activity_label">How active are you on a normal day?</string>
    <string name="activity_sedentary">Mostly sitting (desk work, little walking)</string>
    <string name="activity_light">Lightly active (some walking every day)</string>
    <string name="activity_moderate">Moderately active (on my feet a lot, or exercise 3 to 5 days a week)</string>
    <string name="activity_active">Very active (physical work or hard exercise most days)</string>
    <string name="age_error">Enter an age between %1$s and %2$s.</string>
    <string name="height_error_us">Enter a height between 4 ft and 7 ft 6 in.</string>
    <string name="height_error_metric">Enter a height between %1$s and %2$s cm.</string>
    <string name="weight_error_us">Enter a weight between 67 and 661 lb.</string>
    <string name="weight_error_metric">Enter a weight between %1$s and %2$s kg.</string>
    <string name="onboarding_why_title">Why are you here?</string>
    <string name="onboarding_goal_label">Your main goal</string>
    <string name="onboarding_reasons_label">What brings you to the app? Pick any.</string>
    <string name="onboarding_food_title">Food you love</string>
    <string name="onboarding_food_why">Pick the cuisines you eat most. When a photo could be one of several dishes, the app leans toward these.</string>
    <string name="onboarding_diet_title">How you eat</string>
    <string name="onboarding_diet_label">Your diet</string>
    <string name="onboarding_eat_more_label">What do you want to eat more (or less) of?</string>
    <string name="diet_none">No restrictions</string>
    <string name="onboarding_plan_title">Your plan</string>
    <string name="plan_suggested">Suggested for you: %1$s kcal and %2$s steps a day.</string>
    <string name="plan_reason_default">Add your age, height, and weight in step 2 for a personal budget. Until then the app uses %1$s kcal.</string>
    <string name="plan_reason_body">Your body and activity use about %1$s kcal a day (Mifflin-St Jeor formula).</string>
    <string name="plan_reason_lose">Minus 500 kcal a day to lose about 0.5 kg (1 lb) a week.</string>
    <string name="plan_reason_gain">Plus 300 kcal a day to build muscle.</string>
    <string name="plan_reason_floor">Never below %1$s kcal a day.</string>
    <string name="plan_reason_steps">The step goal follows your activity level.</string>
    <string name="plan_budget_hint">Daily calorie budget (kcal)</string>
    <string name="plan_steps_hint">Daily step goal</string>
    <string name="plan_budget_error">Enter a budget between %1$s and %2$s kcal.</string>
    <string name="plan_steps_error">Enter a step goal between %1$s and %2$s.</string>
    <string name="plan_use_suggested">Use suggested</string>
    <string name="onboarding_health_why">Connect Health Connect to see your steps and calories burned here, and to log meals. You can also do this later in Settings.</string>
    <string name="onboarding_health_connected">Health Connect is connected.</string>
    <string name="onboarding_plan_private">Your answers are stored only on this phone. Edit them any time in Settings.</string>

    <string name="profile_title">Your profile</string>
    <string name="profile_no_name">No name given</string>
    <string name="profile_body_line">%1$s, age %2$s, %3$s</string>
    <string name="profile_body_missing">%1$s. Age, height, and weight not given.</string>
    <string name="profile_size_us">%1$s ft %2$s in, %3$s lb</string>
    <string name="profile_size_metric">%1$s cm, %2$s kg</string>
    <string name="profile_goal_line">Goal: %1$s</string>
    <string name="profile_plan_line">Plan: %1$s kcal and %2$s steps a day</string>
    <string name="profile_cuisines_line">Cuisines: %1$s</string>
    <string name="profile_diet_line">Diet: %1$s</string>
    <string name="profile_eat_more_line">Eat more of: %1$s</string>
    <string name="profile_reasons_line">Why: %1$s</string>
    <string name="profile_none">none</string>
    <string name="edit_profile">Edit profile</string>
    <string name="history_button">Identification history</string>
</resources>
```

CREATE `app/src/main/res/values/arrays.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Onboarding chips. The chosen labels are stored as they are; cuisines match the table's cuisine column by word. -->
    <string-array name="onboarding_reasons">
        <item>Understand what I eat</item>
        <item>I eat out a lot</item>
        <item>Cook more at home</item>
        <item>My doctor recommended it</item>
        <item>Training for a sport</item>
        <item>Curious about calories</item>
    </string-array>

    <string-array name="onboarding_cuisines">
        <item>Indian</item>
        <item>Mexican</item>
        <item>Chinese</item>
        <item>Japanese</item>
        <item>Korean</item>
        <item>Thai</item>
        <item>Vietnamese</item>
        <item>Italian</item>
        <item>Mediterranean</item>
        <item>Middle Eastern</item>
        <item>American</item>
        <item>Southern and BBQ</item>
        <item>Caribbean</item>
        <item>Latin American</item>
        <item>Ethiopian</item>
        <item>Hawaiian</item>
        <item>Fast food</item>
    </string-array>

    <string-array name="onboarding_diet">
        <item>Vegetarian</item>
        <item>Vegan</item>
        <item>Pescatarian</item>
        <item>Halal</item>
        <item>Kosher</item>
        <item>Gluten-free</item>
        <item>Dairy-free</item>
        <item>@string/diet_none</item>
    </string-array>

    <string-array name="onboarding_eat_more">
        <item>More protein</item>
        <item>More vegetables</item>
        <item>Less sugar</item>
        <item>Fewer fried foods</item>
        <item>Smaller portions</item>
        <item>More home cooking</item>
    </string-array>
</resources>
```

CREATE `app/src/main/res/layout/item_filter_chip.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- One multi-select onboarding chip. saveEnabled false: the activity saves the choices, ids are generated. -->
<com.google.android.material.chip.Chip xmlns:android="http://schemas.android.com/apk/res/android"
    style="@style/Widget.Material3.Chip.Filter"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:saveEnabled="false" />
```

CREATE `app/src/main/res/layout/activity_onboarding.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- fitsSystemWindows: targetSdk 35 draws edge-to-edge on Android 15+, so the root pads itself for the system bars. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fitsSystemWindows="true"
    android:orientation="vertical">

    <com.google.android.material.progressindicator.LinearProgressIndicator
        android:id="@+id/onboarding_progress"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:layout_marginTop="16dp"
        android:layout_marginEnd="16dp"
        android:indeterminate="false"
        app:trackCornerRadius="4dp"
        app:trackThickness="8dp" />

    <TextView
        android:id="@+id/step_text"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:layout_marginTop="8dp"
        android:layout_marginEnd="16dp"
        android:textAppearance="?attr/textAppearanceLabelLarge"
        android:textColor="?attr/colorPrimary" />

    <androidx.core.widget.NestedScrollView
        android:id="@+id/scroll"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <!-- 1 Welcome -->
            <LinearLayout
                android:id="@+id/page_welcome"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical">

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/onboarding_welcome_title"
                    android:textAppearance="?attr/textAppearanceHeadlineMedium"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:text="@string/onboarding_welcome_body"
                    android:textAppearance="?attr/textAppearanceBodyLarge" />

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:text="@string/onboarding_welcome_private"
                    android:textAppearance="?attr/textAppearanceBodyMedium"
                    android:textColor="?attr/colorPrimary" />

                <com.google.android.material.textfield.TextInputLayout
                    android:id="@+id/name_layout"
                    style="@style/Widget.Material3.TextInputLayout.OutlinedBox"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="24dp"
                    android:hint="@string/onboarding_name_hint">

                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/name_input"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:autofillHints="personGivenName"
                        android:inputType="textPersonName|textCapWords"
                        android:maxLength="40"
                        android:maxLines="1" />
                </com.google.android.material.textfield.TextInputLayout>

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:text="@string/onboarding_skip_note"
                    android:textAppearance="?attr/textAppearanceBodySmall" />
            </LinearLayout>

            <!-- 2 About you -->
            <LinearLayout
                android:id="@+id/page_body"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:visibility="gone">

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/onboarding_body_title"
                    android:textAppearance="?attr/textAppearanceHeadlineSmall" />

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/onboarding_body_why"
                    android:textAppearance="?attr/textAppearanceBodyMedium" />

                <com.google.android.material.textfield.TextInputLayout
                    android:id="@+id/age_layout"
                    style="@style/Widget.Material3.TextInputLayout.OutlinedBox"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:hint="@string/onboarding_age_hint">

                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/age_input"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:importantForAutofill="no"
                        android:inputType="number"
                        android:maxLength="3"
                        android:maxLines="1" />
                </com.google.android.material.textfield.TextInputLayout>

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:text="@string/onboarding_sex_label"
                    android:textAppearance="?attr/textAppearanceTitleSmall" />

                <com.google.android.material.button.MaterialButtonToggleGroup
                    android:id="@+id/sex_toggle"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    app:singleSelection="true">

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/sex_female"
                        style="@style/Widget.Material3.Button.OutlinedButton"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:saveEnabled="false"
                        android:text="@string/sex_female" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/sex_male"
                        style="@style/Widget.Material3.Button.OutlinedButton"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:saveEnabled="false"
                        android:text="@string/sex_male" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/sex_unspecified"
                        style="@style/Widget.Material3.Button.OutlinedButton"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:saveEnabled="false"
                        android:text="@string/sex_unspecified" />
                </com.google.android.material.button.MaterialButtonToggleGroup>

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:text="@string/onboarding_units_label"
                    android:textAppearance="?attr/textAppearanceTitleSmall" />

                <com.google.android.material.button.MaterialButtonToggleGroup
                    android:id="@+id/units_toggle"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    app:selectionRequired="true"
                    app:singleSelection="true">

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/units_us"
                        style="@style/Widget.Material3.Button.OutlinedButton"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:saveEnabled="false"
                        android:text="@string/units_us" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/units_metric"
                        style="@style/Widget.Material3.Button.OutlinedButton"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:saveEnabled="false"
                        android:text="@string/units_metric" />
                </com.google.android.material.button.MaterialButtonToggleGroup>

                <LinearLayout
                    android:id="@+id/height_us_row"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:baselineAligned="false"
                    android:orientation="horizontal">

                    <com.google.android.material.textfield.TextInputLayout
                        android:id="@+id/height_feet_layout"
                        style="@style/Widget.Material3.TextInputLayout.OutlinedBox"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_marginEnd="8dp"
                        android:layout_weight="1"
                        android:hint="@string/onboarding_feet_hint">

                        <com.google.android.material.textfield.TextInputEditText
                            android:id="@+id/height_feet_input"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:importantForAutofill="no"
                            android:inputType="number"
                            android:maxLength="1"
                            android:maxLines="1" />
                    </com.google.android.material.textfield.TextInputLayout>

                    <com.google.android.material.textfield.TextInputLayout
                        android:id="@+id/height_inches_layout"
                        style="@style/Widget.Material3.TextInputLayout.OutlinedBox"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:hint="@string/onboarding_inches_hint">

                        <com.google.android.material.textfield.TextInputEditText
                            android:id="@+id/height_inches_input"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:importantForAutofill="no"
                            android:inputType="number"
                            android:maxLength="2"
                            android:maxLines="1" />
                    </com.google.android.material.textfield.TextInputLayout>
                </LinearLayout>

                <com.google.android.material.textfield.TextInputLayout
                    android:id="@+id/height_cm_layout"
                    style="@style/Widget.Material3.TextInputLayout.OutlinedBox"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:hint="@string/onboarding_cm_hint"
                    android:visibility="gone">

                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/height_cm_input"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:importantForAutofill="no"
                        android:inputType="number"
                        android:maxLength="3"
                        android:maxLines="1" />
                </com.google.android.material.textfield.TextInputLayout>

                <com.google.android.material.textfield.TextInputLayout
                    android:id="@+id/weight_layout"
                    style="@style/Widget.Material3.TextInputLayout.OutlinedBox"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:hint="@string/onboarding_weight_lb_hint">

                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/weight_input"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:importantForAutofill="no"
                        android:inputType="numberDecimal"
                        android:maxLength="5"
                        android:maxLines="1" />
                </com.google.android.material.textfield.TextInputLayout>

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:text="@string/onboarding_activity_label"
                    android:textAppearance="?attr/textAppearanceTitleSmall" />

                <RadioGroup
                    android:id="@+id/activity_group"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content">

                    <RadioButton
                        android:id="@+id/activity_sedentary"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="@string/activity_sedentary" />

                    <RadioButton
                        android:id="@+id/activity_light"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="@string/activity_light" />

                    <RadioButton
                        android:id="@+id/activity_moderate"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="@string/activity_moderate" />

                    <RadioButton
                        android:id="@+id/activity_active"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="@string/activity_active" />
                </RadioGroup>
            </LinearLayout>

            <!-- 3 Why you are here -->
            <LinearLayout
                android:id="@+id/page_why"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:visibility="gone">

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/onboarding_why_title"
                    android:textAppearance="?attr/textAppearanceHeadlineSmall" />

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:text="@string/onboarding_goal_label"
                    android:textAppearance="?attr/textAppearanceTitleSmall" />

                <RadioGroup
                    android:id="@+id/goal_group"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content">

                    <RadioButton
                        android:id="@+id/goal_lose"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="@string/goal_lose" />

                    <RadioButton
                        android:id="@+id/goal_maintain"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="@string/goal_maintain" />

                    <RadioButton
                        android:id="@+id/goal_gain"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="@string/goal_gain" />

                    <RadioButton
                        android:id="@+id/goal_eat_healthier"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="@string/goal_eat_healthier" />

                    <RadioButton
                        android:id="@+id/goal_track"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="@string/goal_track" />
                </RadioGroup>

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:text="@string/onboarding_reasons_label"
                    android:textAppearance="?attr/textAppearanceTitleSmall" />

                <com.google.android.material.chip.ChipGroup
                    android:id="@+id/reasons_group"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp" />
            </LinearLayout>

            <!-- 4 Food you love -->
            <LinearLayout
                android:id="@+id/page_food"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:visibility="gone">

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/onboarding_food_title"
                    android:textAppearance="?attr/textAppearanceHeadlineSmall" />

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/onboarding_food_why"
                    android:textAppearance="?attr/textAppearanceBodyMedium" />

                <com.google.android.material.chip.ChipGroup
                    android:id="@+id/cuisines_group"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp" />
            </LinearLayout>

            <!-- 5 How you eat -->
            <LinearLayout
                android:id="@+id/page_diet"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:visibility="gone">

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/onboarding_diet_title"
                    android:textAppearance="?attr/textAppearanceHeadlineSmall" />

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:text="@string/onboarding_diet_label"
                    android:textAppearance="?attr/textAppearanceTitleSmall" />

                <com.google.android.material.chip.ChipGroup
                    android:id="@+id/diet_group"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp" />

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:text="@string/onboarding_eat_more_label"
                    android:textAppearance="?attr/textAppearanceTitleSmall" />

                <com.google.android.material.chip.ChipGroup
                    android:id="@+id/eat_more_group"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp" />
            </LinearLayout>

            <!-- 6 Your plan -->
            <LinearLayout
                android:id="@+id/page_plan"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:visibility="gone">

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/onboarding_plan_title"
                    android:textAppearance="?attr/textAppearanceHeadlineSmall" />

                <TextView
                    android:id="@+id/plan_reason_text"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:textAppearance="?attr/textAppearanceBodyLarge" />

                <com.google.android.material.textfield.TextInputLayout
                    android:id="@+id/budget_layout"
                    style="@style/Widget.Material3.TextInputLayout.OutlinedBox"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:hint="@string/plan_budget_hint">

                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/budget_input"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:importantForAutofill="no"
                        android:inputType="number"
                        android:maxLength="4"
                        android:maxLines="1" />
                </com.google.android.material.textfield.TextInputLayout>

                <com.google.android.material.textfield.TextInputLayout
                    android:id="@+id/steps_layout"
                    style="@style/Widget.Material3.TextInputLayout.OutlinedBox"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:hint="@string/plan_steps_hint">

                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/steps_input"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:importantForAutofill="no"
                        android:inputType="number"
                        android:maxLength="5"
                        android:maxLines="1" />
                </com.google.android.material.textfield.TextInputLayout>

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/use_suggested_button"
                    style="@style/Widget.Material3.Button.TextButton"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/plan_use_suggested" />

                <com.google.android.material.card.MaterialCardView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    app:cardBackgroundColor="?attr/colorSecondaryContainer"
                    app:strokeWidth="0dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical"
                        android:padding="16dp">

                        <TextView
                            android:id="@+id/health_status_text"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:textAppearance="?attr/textAppearanceBodyMedium"
                            android:textColor="?attr/colorOnSecondaryContainer" />

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/connect_health_button"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_marginTop="8dp"
                            android:text="@string/health_connect_button" />
                    </LinearLayout>
                </com.google.android.material.card.MaterialCardView>

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:text="@string/onboarding_plan_private"
                    android:textAppearance="?attr/textAppearanceBodySmall" />
            </LinearLayout>
        </LinearLayout>
    </androidx.core.widget.NestedScrollView>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:padding="16dp">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/back_button"
            style="@style/Widget.Material3.Button.OutlinedButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/onboarding_back" />

        <Space
            android:layout_width="0dp"
            android:layout_height="0dp"
            android:layout_weight="1" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/next_button"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/onboarding_next" />
    </LinearLayout>
</LinearLayout>
```

CREATE `app/src/main/java/com/example/identify/OnboardingActivity.java`
```java
package com.example.identify;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.identify.core.ProfileMath;
import com.example.identify.core.UserProfile;
import com.example.identify.databinding.ActivityOnboardingBinding;
import com.example.identify.databinding.ItemFilterChipBinding;
import com.example.identify.health.HealthConnectRepository;
import com.example.identify.util.ExperimentLog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Six-step user profile (docs/USER_STORIES.md section 3): welcome and name, body and activity, goal and
 * reasons, favorite cuisines, diet and eat-more, then the plan. Runs on first launch and from Settings >
 * Edit profile. Every answer is optional; Start saves the profile, budget, step goal, and units.
 */
public class OnboardingActivity extends AppCompatActivity {

    static final int STEPS = 6;
    private static final int STEP_BODY = 1;
    private static final int STEP_PLAN = 5;
    static final long MIN_BUDGET = 1000;
    static final long MAX_BUDGET = 6000;
    static final long MIN_STEP_GOAL = 1000;
    static final long MAX_STEP_GOAL = 50000;

    private static final String KEY_STEP = "step";
    private static final String KEY_US_UNITS = "usUnits";
    private static final String KEY_SEX = "sex";
    private static final String KEY_REASONS = "reasons";
    private static final String KEY_CUISINES = "cuisines";
    private static final String KEY_DIET = "diet";
    private static final String KEY_EAT_MORE = "eatMore";
    private static final String KEY_FILLED_BUDGET = "filledBudget";
    private static final String KEY_FILLED_STEPS = "filledSteps";
    private static final String KEY_STARTED_AT = "startedAt";

    private ActivityOnboardingBinding b;
    private AppPrefs prefs;
    /** True when opened from Settings after the first run: fields start from the saved answers and plan. */
    private boolean editing;
    private int step;
    private boolean usUnits;
    /** The suggestion last written into each plan field; a different value there was typed by the user. */
    private long filledBudget = -1;
    private long filledSteps = -1;
    private long startedAt;
    private View[] pages;
    private OnBackPressedCallback backCallback;
    private ActivityResultLauncher<String[]> healthLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        prefs = AppPrefs.get(this);
        editing = prefs.isOnboarded();
        pages = new View[]{b.pageWelcome, b.pageBody, b.pageWhy, b.pageFood, b.pageDiet, b.pagePlan};
        b.onboardingProgress.setMax(STEPS);

        healthLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            HealthConnectRepository.logPermissionResult(this, result);
            renderHealth();
        });

        addChips(b.reasonsGroup, R.array.onboarding_reasons);
        addChips(b.cuisinesGroup, R.array.onboarding_cuisines);
        addChips(b.dietGroup, R.array.onboarding_diet);
        addChips(b.eatMoreGroup, R.array.onboarding_eat_more);

        if (savedInstanceState == null) {
            step = 0;
            usUnits = prefs.usesUsUnits();
            startedAt = SystemClock.elapsedRealtime();
            fill(prefs.getProfile());
        } else {
            // Text fields and radio buttons restore themselves after onCreate; the rest is restored here.
            step = savedInstanceState.getInt(KEY_STEP);
            usUnits = savedInstanceState.getBoolean(KEY_US_UNITS);
            checkSex(sexOr(savedInstanceState.getString(KEY_SEX)));
            checkChips(b.reasonsGroup, setOf(savedInstanceState.getStringArrayList(KEY_REASONS)));
            checkChips(b.cuisinesGroup, setOf(savedInstanceState.getStringArrayList(KEY_CUISINES)));
            checkChips(b.dietGroup, setOf(savedInstanceState.getStringArrayList(KEY_DIET)));
            checkChips(b.eatMoreGroup, setOf(savedInstanceState.getStringArrayList(KEY_EAT_MORE)));
            filledBudget = savedInstanceState.getLong(KEY_FILLED_BUDGET, -1);
            filledSteps = savedInstanceState.getLong(KEY_FILLED_STEPS, -1);
            startedAt = savedInstanceState.getLong(KEY_STARTED_AT, SystemClock.elapsedRealtime());
        }
        b.unitsToggle.check(usUnits ? R.id.units_us : R.id.units_metric);
        showUnits();

        b.unitsToggle.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            boolean us = id == R.id.units_us;
            if (us == usUnits) return;
            double cm = heightCm();
            double kg = weightKg();
            usUnits = us;
            showUnits();
            writeHeight(cm);
            writeWeight(kg);
        });
        makeExclusive(b.dietGroup, getString(R.string.diet_none));
        b.useSuggestedButton.setOnClickListener(v -> {
            UserProfile p = draft();
            filledBudget = ProfileMath.calorieBudget(p);
            filledSteps = ProfileMath.stepGoal(p.activity);
            b.budgetInput.setText(String.valueOf(filledBudget));
            b.stepsInput.setText(String.valueOf(filledSteps));
            b.budgetLayout.setError(null);
            b.stepsLayout.setError(null);
        });
        b.connectHealthButton.setOnClickListener(v -> healthLauncher.launch(HealthConnectRepository.PERMISSIONS));
        b.backButton.setOnClickListener(v -> back());
        b.nextButton.setOnClickListener(v -> next());
        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                back();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        showStep(step);   // after the text fields restored, so the plan sees the restored answers
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(KEY_STEP, step);
        out.putBoolean(KEY_US_UNITS, usUnits);
        out.putString(KEY_SEX, sex().name());
        out.putStringArrayList(KEY_REASONS, new ArrayList<>(checked(b.reasonsGroup)));
        out.putStringArrayList(KEY_CUISINES, new ArrayList<>(checked(b.cuisinesGroup)));
        out.putStringArrayList(KEY_DIET, new ArrayList<>(checked(b.dietGroup)));
        out.putStringArrayList(KEY_EAT_MORE, new ArrayList<>(checked(b.eatMoreGroup)));
        out.putLong(KEY_FILLED_BUDGET, filledBudget);
        out.putLong(KEY_FILLED_STEPS, filledSteps);
        out.putLong(KEY_STARTED_AT, startedAt);
    }

    // ---------------------------------------------------------------- steps

    private void showStep(int s) {
        step = s;
        for (int i = 0; i < pages.length; i++) pages[i].setVisibility(i == s ? View.VISIBLE : View.GONE);
        b.onboardingProgress.setProgressCompat(s + 1, true);
        b.stepText.setText(getString(R.string.onboarding_step, String.valueOf(s + 1), String.valueOf(STEPS)));
        b.backButton.setVisibility(s == 0 ? View.INVISIBLE : View.VISIBLE);
        b.nextButton.setText(s == STEP_PLAN
                ? (editing ? R.string.onboarding_save : R.string.onboarding_start)
                : R.string.onboarding_next);
        backCallback.setEnabled(s > 0);
        b.scroll.scrollTo(0, 0);
        if (s == STEP_PLAN) renderPlan();
    }

    private void next() {
        hideKeyboard();
        if (step == STEP_BODY && !validateBody()) return;
        if (step == STEP_PLAN) {
            save();
            return;
        }
        showStep(step + 1);
    }

    private void back() {
        hideKeyboard();
        if (step > 0) showStep(step - 1);
    }

    /** Empty fields are allowed (US-1.2); a value that was typed must be in range. */
    private boolean validateBody() {
        b.ageLayout.setError(null);
        b.heightFeetLayout.setError(null);
        b.heightCmLayout.setError(null);
        b.weightLayout.setError(null);
        boolean ok = true;
        double age = number(b.ageInput);
        if (age != 0 && (age != Math.floor(age) || !ProfileMath.validAge((int) age))) {
            b.ageLayout.setError(getString(R.string.age_error,
                    String.valueOf(ProfileMath.MIN_AGE), String.valueOf(ProfileMath.MAX_AGE)));
            ok = false;
        }
        double cm = heightCm();
        if (cm != 0 && !ProfileMath.validHeightCm(cm)) {
            if (usUnits) {
                b.heightFeetLayout.setError(getString(R.string.height_error_us));
            } else {
                b.heightCmLayout.setError(getString(R.string.height_error_metric,
                        num(ProfileMath.MIN_HEIGHT_CM), num(ProfileMath.MAX_HEIGHT_CM)));
            }
            ok = false;
        }
        double kg = weightKg();
        if (kg != 0 && !ProfileMath.validWeightKg(kg)) {
            b.weightLayout.setError(usUnits
                    ? getString(R.string.weight_error_us)
                    : getString(R.string.weight_error_metric,
                            num(ProfileMath.MIN_WEIGHT_KG), num(ProfileMath.MAX_WEIGHT_KG)));
            ok = false;
        }
        return ok;
    }

    // ---------------------------------------------------------------- plan

    /** Suggested budget and step goal with a short why; fills the fields unless the user typed a value. */
    private void renderPlan() {
        UserProfile p = draft();
        long budget = ProfileMath.calorieBudget(p);
        long steps = ProfileMath.stepGoal(p.activity);
        long typedBudget = parseLong(text(b.budgetInput));
        if (typedBudget == 0 || typedBudget == filledBudget) {
            b.budgetInput.setText(String.valueOf(budget));
            filledBudget = budget;
        }
        long typedSteps = parseLong(text(b.stepsInput));
        if (typedSteps == 0 || typedSteps == filledSteps) {
            b.stepsInput.setText(String.valueOf(steps));
            filledSteps = steps;
        }
        b.planReasonText.setText(planReason(p, budget, steps));
        renderHealth();
    }

    private String planReason(UserProfile p, long budget, long steps) {
        StringBuilder s = new StringBuilder(getString(R.string.plan_suggested, grouped(budget), grouped(steps)));
        s.append('\n');
        if (!p.hasBody()) {
            s.append(getString(R.string.plan_reason_default, grouped(ProfileMath.DEFAULT_BUDGET)));
        } else {
            double maintain = ProfileMath.restingKcal(p) * ProfileMath.activityFactor(p.activity);
            s.append(getString(R.string.plan_reason_body, grouped(Math.round(maintain / 50.0) * 50)));
            double adjusted = maintain;
            if (p.goal == UserProfile.Goal.LOSE) {
                s.append(' ').append(getString(R.string.plan_reason_lose));
                adjusted -= 500;
            } else if (p.goal == UserProfile.Goal.GAIN) {
                s.append(' ').append(getString(R.string.plan_reason_gain));
                adjusted += 300;
            }
            long floor = p.sex == UserProfile.Sex.MALE ? 1500 : 1200;
            if (adjusted < floor) s.append(' ').append(getString(R.string.plan_reason_floor, grouped(floor)));
        }
        s.append(' ').append(getString(R.string.plan_reason_steps));
        return s.toString();
    }

    private void renderHealth() {
        if (!HealthConnectRepository.isAvailable(this)) {
            b.healthStatusText.setText(R.string.health_unavailable);
            b.connectHealthButton.setVisibility(View.GONE);
            return;
        }
        boolean missing = !HealthConnectRepository.missingPermissions(this).isEmpty();
        b.healthStatusText.setText(missing ? R.string.onboarding_health_why : R.string.onboarding_health_connected);
        b.connectHealthButton.setVisibility(missing ? View.VISIBLE : View.GONE);
    }

    /** Validates the plan, saves everything, logs profile_saved, and opens Today on the first run. */
    private void save() {
        UserProfile p = draft();
        long suggestedBudget = ProfileMath.calorieBudget(p);
        long suggestedSteps = ProfileMath.stepGoal(p.activity);
        long budget = parseLong(text(b.budgetInput));
        long steps = parseLong(text(b.stepsInput));
        if (budget == 0) budget = suggestedBudget;
        if (steps == 0) steps = suggestedSteps;
        b.budgetLayout.setError(null);
        b.stepsLayout.setError(null);
        boolean ok = true;
        if (budget < MIN_BUDGET || budget > MAX_BUDGET) {
            b.budgetLayout.setError(getString(R.string.plan_budget_error, grouped(MIN_BUDGET), grouped(MAX_BUDGET)));
            ok = false;
        }
        if (steps < MIN_STEP_GOAL || steps > MAX_STEP_GOAL) {
            b.stepsLayout.setError(getString(R.string.plan_steps_error, grouped(MIN_STEP_GOAL), grouped(MAX_STEP_GOAL)));
            ok = false;
        }
        if (!ok) return;

        prefs.saveProfile(p);
        prefs.setCalorieBudget(budget);
        prefs.setStepGoal(steps);
        prefs.setUsUnits(usUnits);
        prefs.setOnboarded(true);
        logSaved(p, budget, suggestedBudget, steps, suggestedSteps);
        if (!editing) startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void logSaved(UserProfile p, long budget, long suggestedBudget, long steps, long suggestedSteps) {
        JSONObject e = ExperimentLog.event("profile_saved");
        ExperimentLog.put(e, "mode", editing ? "edit" : "first_run");
        ExperimentLog.put(e, "has_name", !p.name.isEmpty());
        ExperimentLog.put(e, "age", p.ageYears);
        ExperimentLog.put(e, "sex", p.sex.name().toLowerCase(Locale.ROOT));
        ExperimentLog.put(e, "height_cm", p.heightCm);
        ExperimentLog.put(e, "weight_kg", p.weightKg);
        ExperimentLog.put(e, "activity", p.activity.name().toLowerCase(Locale.ROOT));
        ExperimentLog.put(e, "goal", p.goal.name().toLowerCase(Locale.ROOT));
        ExperimentLog.put(e, "reasons", new JSONArray(p.reasons));
        ExperimentLog.put(e, "cuisines", new JSONArray(p.cuisines));
        ExperimentLog.put(e, "diet", new JSONArray(p.diet));
        ExperimentLog.put(e, "eat_more", new JSONArray(p.eatMore));
        ExperimentLog.put(e, "us_units", usUnits);
        ExperimentLog.put(e, "kcal_budget", budget);
        ExperimentLog.put(e, "kcal_budget_suggested", suggestedBudget);
        ExperimentLog.put(e, "kcal_budget_edited", budget != suggestedBudget);
        ExperimentLog.put(e, "step_goal", steps);
        ExperimentLog.put(e, "step_goal_suggested", suggestedSteps);
        ExperimentLog.put(e, "step_goal_edited", steps != suggestedSteps);
        int granted = HealthConnectRepository.isAvailable(this)
                ? HealthConnectRepository.PERMISSIONS.length - HealthConnectRepository.missingPermissions(this).size()
                : 0;
        ExperimentLog.put(e, "health_permissions_granted", granted);
        ExperimentLog.put(e, "ms_in_onboarding", SystemClock.elapsedRealtime() - startedAt);
        ExperimentLog.append(this, e);
    }

    // ---------------------------------------------------------------- reading and writing the form

    /** The answers on screen as a profile. Out-of-range or unparsable body values count as not given. */
    private UserProfile draft() {
        double age = number(b.ageInput);
        double cm = heightCm();
        double kg = weightKg();
        return new UserProfile(text(b.nameInput),
                age == Math.floor(age) && ProfileMath.validAge((int) age) ? (int) age : 0,
                sex(),
                ProfileMath.validHeightCm(cm) ? cm : 0,
                ProfileMath.validWeightKg(kg) ? kg : 0,
                activity(), goal(),
                checked(b.reasonsGroup), checked(b.cuisinesGroup), checked(b.dietGroup), checked(b.eatMoreGroup));
    }

    private void fill(UserProfile p) {
        b.nameInput.setText(p.name);
        b.ageInput.setText(p.ageYears > 0 ? String.valueOf(p.ageYears) : "");
        checkSex(p.sex);
        writeHeight(p.heightCm);
        writeWeight(p.weightKg);
        b.activityGroup.check(activityId(p.activity));
        b.goalGroup.check(goalId(p.goal));
        checkChips(b.reasonsGroup, p.reasons);
        checkChips(b.cuisinesGroup, p.cuisines);
        checkChips(b.dietGroup, p.diet);
        checkChips(b.eatMoreGroup, p.eatMore);
        if (editing) {
            b.budgetInput.setText(String.valueOf(prefs.getCalorieBudget()));
            b.stepsInput.setText(String.valueOf(prefs.getStepGoal()));
        }
    }

    private void showUnits() {
        b.heightUsRow.setVisibility(usUnits ? View.VISIBLE : View.GONE);
        b.heightCmLayout.setVisibility(usUnits ? View.GONE : View.VISIBLE);
        b.weightLayout.setHint(usUnits ? R.string.onboarding_weight_lb_hint : R.string.onboarding_weight_kg_hint);
        b.heightFeetLayout.setError(null);
        b.heightCmLayout.setError(null);
        b.weightLayout.setError(null);
    }

    /** Height in cm from the visible fields: 0 when empty, -1 when unparsable. */
    private double heightCm() {
        if (!usUnits) return number(b.heightCmInput);
        double feet = number(b.heightFeetInput);
        double inches = number(b.heightInchesInput);
        if (feet < 0 || inches < 0) return -1;
        if (feet == 0 && inches == 0) return 0;
        return ProfileMath.feetInchesToCm((int) feet, inches);
    }

    /** Weight in kg from the visible field: 0 when empty, -1 when unparsable. */
    private double weightKg() {
        double v = number(b.weightInput);
        if (v <= 0 || !usUnits) return v;
        return ProfileMath.lbToKg(v);
    }

    private void writeHeight(double cm) {
        b.heightFeetInput.setText("");
        b.heightInchesInput.setText("");
        b.heightCmInput.setText("");
        if (cm <= 0) return;
        if (usUnits) {
            int[] fi = ProfileMath.cmToFeetInches(cm);
            b.heightFeetInput.setText(String.valueOf(fi[0]));
            b.heightInchesInput.setText(String.valueOf(fi[1]));
        } else {
            b.heightCmInput.setText(String.valueOf(Math.round(cm)));
        }
    }

    private void writeWeight(double kg) {
        b.weightInput.setText(kg <= 0 ? "" : num(usUnits ? ProfileMath.kgToLb(kg) : kg));
    }

    private UserProfile.Sex sex() {
        int id = b.sexToggle.getCheckedButtonId();
        if (id == R.id.sex_female) return UserProfile.Sex.FEMALE;
        if (id == R.id.sex_male) return UserProfile.Sex.MALE;
        return UserProfile.Sex.UNSPECIFIED;
    }

    private void checkSex(UserProfile.Sex sex) {
        if (sex == UserProfile.Sex.FEMALE) b.sexToggle.check(R.id.sex_female);
        else if (sex == UserProfile.Sex.MALE) b.sexToggle.check(R.id.sex_male);
        else if (editing) b.sexToggle.check(R.id.sex_unspecified);
        else b.sexToggle.clearChecked();
    }

    private static UserProfile.Sex sexOr(String name) {
        if (name == null) return UserProfile.Sex.UNSPECIFIED;
        try {
            return UserProfile.Sex.valueOf(name);
        } catch (IllegalArgumentException e) {
            return UserProfile.Sex.UNSPECIFIED;
        }
    }

    private UserProfile.Activity activity() {
        int id = b.activityGroup.getCheckedRadioButtonId();
        if (id == R.id.activity_sedentary) return UserProfile.Activity.SEDENTARY;
        if (id == R.id.activity_moderate) return UserProfile.Activity.MODERATE;
        if (id == R.id.activity_active) return UserProfile.Activity.ACTIVE;
        return UserProfile.Activity.LIGHT;
    }

    private static int activityId(UserProfile.Activity a) {
        switch (a) {
            case SEDENTARY: return R.id.activity_sedentary;
            case MODERATE: return R.id.activity_moderate;
            case ACTIVE: return R.id.activity_active;
            default: return R.id.activity_light;
        }
    }

    private UserProfile.Goal goal() {
        int id = b.goalGroup.getCheckedRadioButtonId();
        if (id == R.id.goal_lose) return UserProfile.Goal.LOSE;
        if (id == R.id.goal_maintain) return UserProfile.Goal.MAINTAIN;
        if (id == R.id.goal_gain) return UserProfile.Goal.GAIN;
        if (id == R.id.goal_eat_healthier) return UserProfile.Goal.EAT_HEALTHIER;
        return UserProfile.Goal.TRACK;
    }

    private static int goalId(UserProfile.Goal g) {
        switch (g) {
            case LOSE: return R.id.goal_lose;
            case MAINTAIN: return R.id.goal_maintain;
            case GAIN: return R.id.goal_gain;
            case EAT_HEALTHIER: return R.id.goal_eat_healthier;
            default: return R.id.goal_track;
        }
    }

    // ---------------------------------------------------------------- chips

    private void addChips(ChipGroup group, int arrayRes) {
        for (String label : getResources().getStringArray(arrayRes)) {
            Chip chip = ItemFilterChipBinding.inflate(getLayoutInflater(), group, false).getRoot();
            chip.setText(label);
            group.addView(chip);
        }
    }

    private static Set<String> checked(ChipGroup group) {
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i < group.getChildCount(); i++) {
            Chip c = (Chip) group.getChildAt(i);
            if (c.isChecked()) out.add(c.getText().toString());
        }
        return out;
    }

    private static void checkChips(ChipGroup group, Set<String> values) {
        for (int i = 0; i < group.getChildCount(); i++) {
            Chip c = (Chip) group.getChildAt(i);
            c.setChecked(values.contains(c.getText().toString()));
        }
    }

    /** Checking the exclusive chip clears the others; checking any other chip clears it (US-1.5). */
    private static void makeExclusive(ChipGroup group, String exclusive) {
        for (int i = 0; i < group.getChildCount(); i++) {
            Chip chip = (Chip) group.getChildAt(i);
            chip.setOnCheckedChangeListener((button, isChecked) -> {
                if (!isChecked) return;
                boolean isExclusive = exclusive.contentEquals(button.getText());
                for (int j = 0; j < group.getChildCount(); j++) {
                    Chip other = (Chip) group.getChildAt(j);
                    if (other == button) continue;
                    if (isExclusive || exclusive.contentEquals(other.getText())) other.setChecked(false);
                }
            });
        }
    }

    private static Set<String> setOf(List<String> list) {
        return list == null ? new LinkedHashSet<>() : new LinkedHashSet<>(list);
    }

    // ---------------------------------------------------------------- small helpers

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null) return;
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        focus.clearFocus();
    }

    private static String text(EditText e) {
        Editable s = e.getText();
        return s == null ? "" : s.toString().trim();
    }

    /** 0 for an empty field, -1 when the text is not a number. */
    private static double number(EditText e) {
        String t = text(e).replace(',', '.');
        if (t.isEmpty()) return 0;
        try {
            double v = Double.parseDouble(t);
            return v < 0 ? -1 : v;
        } catch (NumberFormatException x) {
            return -1;
        }
    }

    /** 0 for an empty field, -1 when the text is not a whole number. */
    private static long parseLong(String t) {
        if (t.isEmpty()) return 0;
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException x) {
            return -1;
        }
    }

    /** A whole number when the value is whole, otherwise one decimal (170, 77.1). */
    private static String num(double v) {
        long r = Math.round(v);
        return Math.abs(v - r) < 0.05 ? String.valueOf(r) : String.format(Locale.US, "%.1f", v);
    }

    private static String grouped(long v) {
        return String.format(Locale.getDefault(), "%,d", v);
    }
}
```

EDIT `app/src/main/AndroidManifest.xml`
Find:
```xml
        <!-- Privacy policy. Health Connect opens it from its permission screen. -->
```
Replace with:
```xml
        <!-- First-launch user profile (six steps); Settings > Edit profile opens it again. -->
        <activity
            android:name=".OnboardingActivity"
            android:exported="false"
            android:windowSoftInputMode="adjustResize" />

        <!-- Privacy policy. Health Connect opens it from its permission screen. -->
```

EDIT `app/src/main/java/com/example/identify/MainActivity.java`
Find:
```java
import android.os.Bundle;
```
Replace with:
```java
import android.content.Intent;
import android.os.Bundle;
```

EDIT `app/src/main/java/com/example/identify/MainActivity.java`
Find:
```java
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
```
Replace with:
```java
        super.onCreate(savedInstanceState);
        if (!AppPrefs.get(this).isOnboarded()) {
            // First launch: the profile comes first (US-1.1). Onboarding opens this activity again at Start.
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
```

EDIT `app/src/main/res/layout/fragment_settings.xml`
Find:
```xml
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/settings_model_title"
            android:textAppearance="?attr/textAppearanceTitleMedium" />
```
Replace with:
```xml
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/profile_title"
            android:textAppearance="?attr/textAppearanceTitleMedium" />

        <TextView
            android:id="@+id/profile_summary_text"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textAppearance="?attr/textAppearanceBodyMedium" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:orientation="horizontal">

            <com.google.android.material.button.MaterialButton
                android:id="@+id/edit_profile_button"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/edit_profile" />

            <com.google.android.material.button.MaterialButton
                android:id="@+id/history_button"
                style="@style/Widget.Material3.Button.TextButton"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:text="@string/history_button" />
        </LinearLayout>

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="@string/settings_model_title"
            android:textAppearance="?attr/textAppearanceTitleMedium" />
```

EDIT `app/src/main/java/com/example/identify/ui/SettingsFragment.java`
Find:
```java
import android.content.Context;
```
Replace with:
```java
import android.content.Context;
import android.content.Intent;
```

EDIT `app/src/main/java/com/example/identify/ui/SettingsFragment.java`
Find:
```java
import androidx.lifecycle.ViewModelProvider;
```
Replace with:
```java
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
```

EDIT `app/src/main/java/com/example/identify/ui/SettingsFragment.java`
Find:
```java
import com.example.identify.Config;
```
Replace with:
```java
import com.example.identify.Config;
import com.example.identify.OnboardingActivity;
```

EDIT `app/src/main/java/com/example/identify/ui/SettingsFragment.java`
Find:
```java
import com.example.identify.core.DailyHealth;
```
Replace with:
```java
import com.example.identify.core.DailyHealth;
import com.example.identify.core.ProfileMath;
import com.example.identify.core.UserProfile;
```

EDIT `app/src/main/java/com/example/identify/ui/SettingsFragment.java`
Find:
```java
import com.google.android.material.snackbar.Snackbar;
```
Replace with:
```java
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
```

EDIT `app/src/main/java/com/example/identify/ui/SettingsFragment.java`
Find:
```java
        binding.healthOpenButton.setOnClickListener(v -> openHealthConnect());
    }
```
Replace with:
```java
        binding.healthOpenButton.setOnClickListener(v -> openHealthConnect());

        binding.editProfileButton.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), OnboardingActivity.class)));
        binding.historyButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.historyFragment));
    }
```

EDIT `app/src/main/java/com/example/identify/ui/SettingsFragment.java`
Find:
```java
        vm.startPolling();
        refreshUi();
        refreshHealth();
```
Replace with:
```java
        vm.startPolling();
        refreshUi();
        refreshHealth();
        refreshProfile();
```

EDIT `app/src/main/java/com/example/identify/ui/SettingsFragment.java`
Find:
```java
    /** Health Connect section: status line, buttons, and today's numbers. Reads once per call, not per poll. */
```
Replace with:
```java
    /** Profile section: the onboarding answers and the plan, in the units the user picked. */
    private void refreshProfile() {
        if (binding == null || !isAdded()) return;
        UserProfile p = prefs.getProfile();
        String name = p.name.isEmpty() ? getString(R.string.profile_no_name) : p.name;
        List<String> lines = new ArrayList<>();
        lines.add(p.hasBody()
                ? getString(R.string.profile_body_line, name, String.valueOf(p.ageYears), bodySize(p))
                : getString(R.string.profile_body_missing, name));
        lines.add(getString(R.string.profile_goal_line, HealthFormat.goal(requireContext(), p.goal)));
        lines.add(getString(R.string.profile_plan_line, formatKcal(prefs.getCalorieBudget()),
                formatSteps(prefs.getStepGoal())));
        lines.add(getString(R.string.profile_cuisines_line, joined(p.cuisines)));
        lines.add(getString(R.string.profile_diet_line, joined(p.diet)));
        lines.add(getString(R.string.profile_eat_more_line, joined(p.eatMore)));
        lines.add(getString(R.string.profile_reasons_line, joined(p.reasons)));
        binding.profileSummaryText.setText(String.join("\n", lines));
    }

    private String bodySize(UserProfile p) {
        if (prefs.usesUsUnits()) {
            int[] fi = ProfileMath.cmToFeetInches(p.heightCm);
            return getString(R.string.profile_size_us, String.valueOf(fi[0]), String.valueOf(fi[1]),
                    String.valueOf(Math.round(ProfileMath.kgToLb(p.weightKg))));
        }
        return getString(R.string.profile_size_metric, String.valueOf(Math.round(p.heightCm)),
                String.valueOf(Math.round(p.weightKg)));
    }

    /** Alphabetical, because the stored sets have no order. */
    private String joined(Set<String> values) {
        return values.isEmpty() ? getString(R.string.profile_none) : String.join(", ", new TreeSet<>(values));
    }

    /** Health Connect section: status line, buttons, and today's numbers. Reads once per call, not per poll. */
```

## VERIFY 10.6

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew :core:test :app:assembleDebug :app:lintDebug --console=plain
tail -n 1 app/build/reports/lint-results-debug.txt
```
BUILD SUCCESSFUL, lint `0 errors`, and no new warning in the files of this step.

Commit subject: `Add six-step onboarding that profiles the user, and show the profile in Settings`.
