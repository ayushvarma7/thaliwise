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
