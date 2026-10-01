package com.example.identify.ui;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.identify.App;
import com.example.identify.Config;
import com.example.identify.learning.CorrectionRepository;
import com.example.identify.model.ModelDownloader;
import com.example.identify.model.VlmEngine;

import java.io.File;

public class SettingsViewModel extends AndroidViewModel {

    private static final long POLL_INTERVAL_MS = 1000;

    private final MutableLiveData<ModelDownloader.Progress> progress = new MutableLiveData<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LiveData<Integer> count;
    private final LiveData<Integer> correctionCount;
    private final LiveData<Integer> memoryHitCount;
    private final LiveData<Integer> memoryHitCorrectedCount;
    private boolean polling = false;

    // The download status query is small, so it runs on the main thread.
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            progress.setValue(ModelDownloader.queryProgress(getApplication()));
            if (polling) handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    public SettingsViewModel(@NonNull Application app) {
        super(app);
        CorrectionRepository repo = CorrectionRepository.get(app);
        count = repo.observeCount();
        correctionCount = repo.observeCorrectionCount();
        memoryHitCount = repo.observeMemoryHitCount();
        memoryHitCorrectedCount = repo.observeMemoryHitCorrectedCount();
    }

    public LiveData<ModelDownloader.Progress> getProgress() { return progress; }
    public LiveData<Integer> getCount() { return count; }
    public LiveData<Integer> getCorrectionCount() { return correctionCount; }
    public LiveData<Integer> getMemoryHitCount() { return memoryHitCount; }
    public LiveData<Integer> getMemoryHitCorrectedCount() { return memoryHitCorrectedCount; }

    public void startPolling() {
        if (polling) return;
        polling = true;
        handler.post(poll);
    }

    public void stopPolling() {
        polling = false;
        handler.removeCallbacks(poll);
    }

    /** False when there is not enough free space; nothing is enqueued then. */
    public boolean startDownload() {
        if (!ModelDownloader.hasEnoughSpace(getApplication())) return false;
        ModelDownloader.enqueue(getApplication());
        return true;
    }

    public void unloadModel(Runnable onDone) {
        App.runOnModelThread(() -> {
            VlmEngine.get().unload();
            App.runOnMainThread(onDone);
        });
    }

    public void deleteModel(Runnable onDone) {
        final Application app = getApplication();
        App.runOnModelThread(() -> {
            VlmEngine.get().unload();
            ModelDownloader.deleteModelFiles(app);
            App.runOnMainThread(onDone);
        });
    }

    public void clearAll(Runnable onDone) {
        final Application app = getApplication();
        App.runOnDbThread(() -> {
            CorrectionRepository.get(app).deleteAllSync();
            File[] files = new File(app.getFilesDir(), "images").listFiles();
            if (files != null) {
                for (File f : files) {
                    if (!f.delete()) Log.w(Config.LOG_TAG, "could not delete " + f);
                }
            }
            App.embeddingCache().clear();
            App.runOnMainThread(onDone);
        });
    }

    @Override
    protected void onCleared() {
        stopPolling();
    }
}
