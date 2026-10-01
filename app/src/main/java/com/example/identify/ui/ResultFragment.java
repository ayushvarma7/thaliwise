package com.example.identify.ui;

import android.content.Context;
import android.health.connect.HealthPermissions;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
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

public class ResultFragment extends Fragment {

    private static final String KEY_DETAILS_OPEN = "detailsOpen";
    private static final String KEY_MEAL_RECORD_ID = "mealRecordId";
    private static final String KEY_MEAL_SUMMARY = "mealSummary";

    private FragmentResultBinding binding;
    private ResultViewModel vm;
    private boolean detailsOpen;
    /** Health Connect id of the meal logged from this screen, kept so Undo can delete it. */
    private String loggedRecordId;
    private String loggedSummary;

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
        vm.getSaved().observe(getViewLifecycleOwner(), saved -> {
            if (!Boolean.TRUE.equals(saved)) return;
            vm.consumeSaved();
            NavController nav = NavHostFragment.findNavController(this);
            NavDestination current = nav.getCurrentDestination();
            if (current != null && current.getId() == R.id.resultFragment) {
                nav.navigate(R.id.action_result_to_history);
            }
        });

        binding.acceptButton.setOnClickListener(v -> {
            binding.acceptButton.setEnabled(false);
            binding.saveCorrectionButton.setEnabled(false);
            vm.accept();
        });
        binding.correctButton.setOnClickListener(v -> {
            binding.correctionInputLayout.setVisibility(View.VISIBLE);
            binding.saveCorrectionButton.setVisibility(View.VISIBLE);
            binding.correctionInput.requestFocus();
        });
        binding.saveCorrectionButton.setOnClickListener(v -> {
            Editable editable = binding.correctionInput.getText();
            String text = editable == null ? "" : editable.toString();
            if (text.trim().isEmpty()) {
                binding.correctionInputLayout.setError(getString(R.string.correction_empty));
                return;
            }
            binding.correctionInputLayout.setError(null);
            binding.saveCorrectionButton.setEnabled(false);
            binding.acceptButton.setEnabled(false);
            vm.submitCorrection(text);
        });
        binding.runModelButton.setOnClickListener(v -> vm.runModelAnyway());
        binding.retryButton.setOnClickListener(v -> vm.retry());
        binding.detailsButton.setOnClickListener(v -> {
            detailsOpen = !detailsOpen;
            renderResult();
        });
        binding.logMealButton.setOnClickListener(v -> onLogMealClicked());
        binding.undoMealButton.setOnClickListener(v -> onUndoMealClicked());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_DETAILS_OPEN, detailsOpen);
        outState.putString(KEY_MEAL_RECORD_ID, loggedRecordId);
        outState.putString(KEY_MEAL_SUMMARY, loggedSummary);
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
        keepScreenOn(stage == ResultViewModel.Stage.LOADING_MODEL
                || stage == ResultViewModel.Stage.EMBEDDING
                || stage == ResultViewModel.Stage.GENERATING);
        switch (stage) {
            case IDLE:
            case LOADING_MODEL:
            case EMBEDDING:
            case GENERATING:
                binding.progressContainer.setVisibility(View.VISIBLE);
                binding.progressText.setText(stage == ResultViewModel.Stage.EMBEDDING
                        ? R.string.stage_embedding
                        : stage == ResultViewModel.Stage.GENERATING
                                ? R.string.stage_generating
                                : R.string.stage_loading_model);
                binding.resultCard.setVisibility(View.GONE);
                binding.actionRow.setVisibility(View.GONE);
                binding.sourceText.setVisibility(View.GONE);
                binding.runModelButton.setVisibility(View.GONE);
                binding.retryButton.setVisibility(View.GONE);
                binding.detailsButton.setVisibility(View.GONE);
                binding.detailsText.setVisibility(View.GONE);
                binding.foodCard.setVisibility(View.GONE);
                hideCorrectionViews();
                break;
            case DONE:
                binding.progressContainer.setVisibility(View.GONE);
                binding.retryButton.setVisibility(View.GONE);
                binding.resultCard.setVisibility(View.VISIBLE);
                binding.actionRow.setVisibility(View.VISIBLE);
                binding.sourceText.setVisibility(View.VISIBLE);
                renderResult();
                break;
            case ERROR:
                binding.progressContainer.setVisibility(View.GONE);
                binding.actionRow.setVisibility(View.GONE);
                binding.sourceText.setVisibility(View.GONE);
                binding.runModelButton.setVisibility(View.GONE);
                binding.detailsButton.setVisibility(View.GONE);
                binding.detailsText.setVisibility(View.GONE);
                binding.foodCard.setVisibility(View.GONE);
                hideCorrectionViews();
                binding.resultCard.setVisibility(View.VISIBLE);
                binding.labelText.setText("Error");
                String msg = vm.getError().getValue();
                binding.descriptionText.setText(msg == null ? "" : msg);
                binding.descriptionText.setVisibility(View.VISIBLE);
                binding.retryButton.setVisibility(View.VISIBLE);
                break;
        }
    }

    /** Writes the current result into the card. Only while DONE, so an error message is never overwritten. */
    private void renderResult() {
        if (binding == null || vm.getStage().getValue() != ResultViewModel.Stage.DONE) return;
        ResultViewModel.IdentifyResult r = vm.getResult().getValue();
        if (r == null) return;
        binding.labelText.setText(r.label);
        binding.descriptionText.setText(r.description);
        binding.descriptionText.setVisibility(r.description == null || r.description.isEmpty() ? View.GONE : View.VISIBLE);
        boolean fromMemory = Config.SOURCE_MEMORY.equals(r.source);
        binding.sourceText.setText(fromMemory
                ? getString(R.string.source_memory, r.latencyMs / 1000f, r.nearestScore)
                : getString(R.string.source_model, r.latencyMs / 1000f));
        binding.runModelButton.setVisibility(fromMemory ? View.VISIBLE : View.GONE);
        boolean hasDetails = r.details != null && !r.details.isEmpty();
        binding.detailsButton.setVisibility(hasDetails ? View.VISIBLE : View.GONE);
        binding.detailsButton.setText(detailsOpen ? R.string.hide_details : R.string.show_details);
        binding.detailsText.setText(r.details);
        binding.detailsText.setVisibility(hasDetails && detailsOpen ? View.VISIBLE : View.GONE);
        renderFood(r);
    }

    /** The food card: shown when the label matches the nutrition table, or after a meal was logged here. */
    private void renderFood(ResultViewModel.IdentifyResult r) {
        if (loggedRecordId != null) {
            binding.foodCard.setVisibility(View.VISIBLE);
            binding.foodText.setText(loggedSummary);
            binding.logMealButton.setVisibility(View.GONE);
            binding.undoMealButton.setVisibility(View.VISIBLE);
            binding.undoMealButton.setEnabled(true);
            return;
        }
        List<FoodMatcher.Match> matches = FoodMatcher.match(r.label, FoodRepository.foods(requireContext()), 1);
        if (matches.isEmpty()) {
            binding.foodCard.setVisibility(View.GONE);
            return;
        }
        FoodItem top = matches.get(0).item;
        binding.foodCard.setVisibility(View.VISIBLE);
        binding.foodText.setText(getString(R.string.food_detected, top.displayName(),
                HealthFormat.kcal(requireContext(), top.kcal), top.serving));
        binding.foodStatusText.setVisibility(View.GONE);
        binding.logMealButton.setVisibility(View.VISIBLE);
        binding.logMealButton.setEnabled(true);
        binding.undoMealButton.setVisibility(View.GONE);
    }

    private void onLogMealClicked() {
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
        LogMealDialog.show(this, r.label,
                (food, portion, kcal, kcalEdited, query) -> logMeal(r, food, portion, kcal, kcalEdited, query));
    }

    /** Writes the meal to Health Connect, logs a meal_logged event, then shows today's totals. */
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
        binding.foodStatusText.setVisibility(View.VISIBLE);
        binding.foodStatusText.setText(R.string.meal_logging);
        HealthConnectRepository.insertMeal(app, food.name, kcal, protein, carbs, fat, slot, (recordId, error) -> {
            JSONObject e = ExperimentLog.event("meal_logged");
            ExperimentLog.put(e, "run_id", r.runId);
            ExperimentLog.put(e, "model_label", r.label);
            ExperimentLog.put(e, "query", query);
            ExperimentLog.put(e, "food_id", food.id);
            ExperimentLog.put(e, "food_name", food.displayName());
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
                binding.foodStatusText.setVisibility(View.GONE);
                Snackbar.make(binding.getRoot(), getString(R.string.meal_log_failed, error), Snackbar.LENGTH_LONG).show();
                return;
            }
            loggedRecordId = recordId;
            loggedSummary = getString(R.string.meal_logged, HealthFormat.kcal(app, kcal), food.displayName(),
                    HealthFormat.slot(app, slot));
            renderFood(r);
            showToday();
        });
    }

    /** Today's eaten, burned, and step totals under the logged meal. */
    private void showToday() {
        final Context app = requireContext().getApplicationContext();
        final long goal = AppPrefs.get(app).getStepGoal();
        HealthConnectRepository.readToday(app, (today, error) -> {
            if (binding == null || !isAdded() || today == null) return;
            binding.foodStatusText.setVisibility(View.VISIBLE);
            binding.foodStatusText.setText(getString(R.string.meal_today_summary,
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
            Snackbar.make(binding.getRoot(), R.string.meal_undone, Snackbar.LENGTH_LONG).show();
            ResultViewModel.IdentifyResult r = vm.getResult().getValue();
            if (r != null) renderFood(r);
        });
    }

    private void hideCorrectionViews() {
        binding.correctionInputLayout.setVisibility(View.GONE);
        binding.saveCorrectionButton.setVisibility(View.GONE);
    }
}
