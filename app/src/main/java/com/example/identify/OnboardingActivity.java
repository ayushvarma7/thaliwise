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
