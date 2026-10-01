package com.example.identify.ui;

import android.os.Bundle;
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
import com.example.identify.Config;
import com.example.identify.R;
import com.example.identify.databinding.FragmentResultBinding;

import java.io.File;

public class ResultFragment extends Fragment {

    private static final String KEY_DETAILS_OPEN = "detailsOpen";

    private FragmentResultBinding binding;
    private ResultViewModel vm;
    private boolean detailsOpen;

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
        if (savedInstanceState != null) detailsOpen = savedInstanceState.getBoolean(KEY_DETAILS_OPEN);
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
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_DETAILS_OPEN, detailsOpen);
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
    }

    private void hideCorrectionViews() {
        binding.correctionInputLayout.setVisibility(View.GONE);
        binding.saveCorrectionButton.setVisibility(View.GONE);
    }
}
