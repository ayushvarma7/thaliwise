package com.example.identify.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.identify.AppPrefs;
import com.example.identify.Config;
import com.example.identify.R;
import com.example.identify.databinding.FragmentSettingsBinding;
import com.example.identify.model.ModelDownloader;
import com.example.identify.model.VlmEngine;
import com.example.identify.util.ExperimentLog;
import com.example.identify.util.Telemetry;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;

public class SettingsFragment extends Fragment {

    private static final int SLIDER_MIN = 80;
    private static final int SLIDER_MAX = 99;

    private FragmentSettingsBinding binding;
    private SettingsViewModel vm;
    private AppPrefs prefs;
    private ModelDownloader.Progress lastProgress;
    private int total;
    private int corrections;
    private int memoryHits;
    private int memoryHitsCorrected;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = new ViewModelProvider(this).get(SettingsViewModel.class);
        prefs = AppPrefs.get(requireContext());

        binding.downloadProgress.setMax(1000);

        binding.memorySwitch.setChecked(prefs.isMemoryEnabled());
        binding.memorySwitch.setOnCheckedChangeListener((button, checked) -> {
            prefs.setMemoryEnabled(checked);
            if (button.isPressed()) vm.logSettingChange("memory_enabled", checked);
        });

        // Integer slider range on purpose: float step sizes crash Slider.
        int sliderValue = Math.max(SLIDER_MIN, Math.min(SLIDER_MAX, Math.round(prefs.getKnnThreshold() * 100)));
        binding.thresholdSlider.setValue(sliderValue);
        updateThresholdLabel(sliderValue / 100f);
        binding.thresholdSlider.addOnChangeListener((slider, value, fromUser) -> {
            float threshold = value / 100f;
            prefs.setKnnThreshold(threshold);
            updateThresholdLabel(threshold);
        });
        binding.thresholdSlider.addOnSliderTouchListener(logOnRelease("knn_threshold", 0.01f));

        int threads = prefs.getThreads();
        binding.threadsSlider.setValue(threads);
        binding.threadsLabel.setText(getString(R.string.threads_label, threads));
        binding.threadsSlider.addOnChangeListener((slider, value, fromUser) -> {
            prefs.setThreads((int) value);
            binding.threadsLabel.setText(getString(R.string.threads_label, (int) value));
        });
        binding.threadsSlider.addOnSliderTouchListener(logOnRelease("n_threads", 1f));

        int tokens = prefs.getImageMaxTokens();
        binding.imageTokensSlider.setValue(snapTokens(tokens));
        binding.imageTokensLabel.setText(getString(R.string.image_tokens_label, snapTokens(tokens)));
        binding.imageTokensSlider.addOnChangeListener((slider, value, fromUser) -> {
            prefs.setImageMaxTokens((int) value);
            binding.imageTokensLabel.setText(getString(R.string.image_tokens_label, (int) value));
        });
        binding.imageTokensSlider.addOnSliderTouchListener(logOnRelease("image_max_tokens", 1f));

        binding.downloadButton.setOnClickListener(v -> {
            if (!vm.startDownload()) {
                Snackbar.make(binding.getRoot(), R.string.status_no_space, Snackbar.LENGTH_LONG).show();
                return;
            }
            binding.downloadButton.setEnabled(false);   // the next refresh re-enables it if still needed
        });
        binding.unloadModelButton.setOnClickListener(v -> {
            binding.unloadModelButton.setEnabled(false);
            vm.unloadModel(() -> {
                if (binding == null) return;
                binding.unloadModelButton.setEnabled(true);
                refreshUi();
            });
        });
        binding.deleteModelButton.setOnClickListener(v -> {
            binding.deleteModelButton.setEnabled(false);
            vm.deleteModel(() -> {
                if (binding == null) return;
                binding.deleteModelButton.setEnabled(true);
                refreshUi();
            });
        });
        binding.clearHistoryButton.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clear_confirm_title)
                .setMessage(R.string.clear_confirm_message)
                .setPositiveButton(R.string.clear_confirm_ok, (dialog, which) -> vm.clearAll(this::refreshUi))
                .setNegativeButton(R.string.cancel, null)
                .show());

        vm.getProgress().observe(getViewLifecycleOwner(), p -> {
            lastProgress = p;
            refreshUi();
        });
        vm.getLive().observe(getViewLifecycleOwner(), this::renderLive);
        vm.getCount().observe(getViewLifecycleOwner(), n -> { total = n == null ? 0 : n; updateStats(); });
        vm.getCorrectionCount().observe(getViewLifecycleOwner(), n -> { corrections = n == null ? 0 : n; updateStats(); });
        vm.getMemoryHitCount().observe(getViewLifecycleOwner(), n -> { memoryHits = n == null ? 0 : n; updateStats(); });
        vm.getMemoryHitCorrectedCount().observe(getViewLifecycleOwner(),
                n -> { memoryHitsCorrected = n == null ? 0 : n; updateStats(); });
        updateStats();
    }

    @Override
    public void onResume() {
        super.onResume();
        vm.startPolling();
        refreshUi();
    }

    @Override
    public void onPause() {
        super.onPause();
        vm.stopPolling();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void refreshUi() {
        if (binding == null || !isAdded()) return;
        boolean ready = ModelDownloader.filesReady(requireContext());
        ModelDownloader.Progress p = lastProgress;
        boolean running = !ready && p != null && p.state == ModelDownloader.Progress.STATE_RUNNING;
        boolean loaded = VlmEngine.get().isLoaded();

        if (ready) {
            binding.modelStatusText.setVisibility(View.VISIBLE);
            binding.modelStatusText.setText(getString(R.string.status_ready) + " "
                    + getString(loaded ? R.string.status_loaded : R.string.status_not_loaded));
        } else if (running) {
            binding.modelStatusText.setVisibility(View.GONE);
        } else if (p != null && p.state == ModelDownloader.Progress.STATE_FAILED) {
            binding.modelStatusText.setVisibility(View.VISIBLE);
            binding.modelStatusText.setText(getString(R.string.status_failed, p.reason));
        } else {
            binding.modelStatusText.setVisibility(View.VISIBLE);
            binding.modelStatusText.setText(R.string.status_missing);
        }

        if (running) {
            long totalBytes = p.totalBytes > 0 ? p.totalBytes : Config.EXPECTED_TOTAL_BYTES;
            int permille = (int) Math.min(1000L, p.downloadedBytes * 1000L / totalBytes);
            binding.downloadProgress.setVisibility(View.VISIBLE);
            binding.downloadProgress.setProgressCompat(permille, true);
            binding.downloadProgressText.setVisibility(View.VISIBLE);
            binding.downloadProgressText.setText(getString(R.string.status_downloading,
                    (int) (p.downloadedBytes / 1_000_000), (int) (totalBytes / 1_000_000)));
        } else {
            binding.downloadProgress.setVisibility(View.GONE);
            binding.downloadProgressText.setVisibility(View.GONE);
        }

        boolean showDownload = !ready && !running;
        binding.downloadButton.setVisibility(showDownload ? View.VISIBLE : View.GONE);
        if (showDownload) binding.downloadButton.setEnabled(true);
        binding.unloadModelButton.setVisibility(loaded ? View.VISIBLE : View.GONE);
        binding.deleteModelButton.setVisibility(ready ? View.VISIBLE : View.GONE);

        String info = VlmEngine.get().getSystemInfo();
        binding.systemInfoText.setText(info == null || info.isEmpty()
                ? getString(R.string.system_info_unavailable)
                : info);
        binding.experimentLogText.setText(getString(R.string.experiment_log_format,
                (int) (ExperimentLog.sizeBytes(requireContext()) / 1024),
                ExperimentLog.file(requireContext()).getAbsolutePath()));
    }

    private void renderLive(Telemetry.Live live) {
        if (binding == null || live == null) return;
        VlmEngine engine = VlmEngine.get();
        String model = engine.isLoaded()
                ? getString(R.string.loaded_model_format, engine.getLoadedThreads(), engine.getLoadedImageMaxTokens())
                : getString(R.string.loaded_model_none);
        String battery = getString(live.charging ? R.string.live_battery_charging : R.string.live_battery_discharging,
                live.tempC);
        binding.liveTelemetryText.setText(getString(R.string.live_telemetry_format,
                (float) live.cores, (float) (100.0 * live.cores / live.nCpus), live.nCpus,
                (int) live.rssMb, (int) live.hwmMb, battery, live.thermal, model));
    }

    /** Logs one setting_change event when the user lets go of a slider, not on every step of a drag. */
    private Slider.OnSliderTouchListener logOnRelease(String key, float scale) {
        return new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {}

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                float v = slider.getValue() * scale;
                vm.logSettingChange(key, scale == 1f ? (Object) (int) v : (Object) (double) v);
            }
        };
    }

    private static int snapTokens(int tokens) {
        int min = Config.MIN_IMAGE_MAX_TOKENS;
        int step = 32;
        int snapped = min + Math.round((tokens - min) / (float) step) * step;
        return Math.max(min, Math.min(Config.MAX_IMAGE_MAX_TOKENS, snapped));
    }

    private void updateThresholdLabel(float threshold) {
        if (binding == null) return;
        binding.thresholdLabel.setText(getString(R.string.threshold_label, threshold));
    }

    private void updateStats() {
        if (binding == null) return;
        binding.statsText.setText(getString(R.string.stats_format, total, corrections, memoryHits, memoryHitsCorrected));
    }
}
