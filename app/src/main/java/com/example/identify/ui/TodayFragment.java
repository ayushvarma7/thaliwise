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
