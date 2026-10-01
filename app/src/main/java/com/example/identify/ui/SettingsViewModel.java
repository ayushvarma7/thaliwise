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
import com.example.identify.util.ExperimentLog;
import com.example.identify.util.Telemetry;

import org.json.JSONObject;

import java.io.File;

public class SettingsViewModel extends AndroidViewModel {

    private static final long POLL_INTERVAL_MS = 1000;

    private final MutableLiveData<ModelDownloader.Progress> progress = new MutableLiveData<>();
    private final MutableLiveData<Telemetry.Live> live = new MutableLiveData<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LiveData<Integer> count;
    private final LiveData<Integer> correctionCount;
    private final LiveData<Integer> memoryHitCount;
    private final LiveData<Integer> memoryHitCorrectedCount;
    private boolean polling = false;
    private int lastDownloadState = -1;

    // The download status query and the /proc reads are small, so they run on the main thread.
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            ModelDownloader.Progress p = ModelDownloader.queryProgress(getApplication());
            if (p.state != lastDownloadState) {
                if (lastDownloadState != -1) logDownloadState(p);
                lastDownloadState = p.state;
            }
            progress.setValue(p);
            Telemetry.Live previous = live.getValue();
            live.setValue(Telemetry.live(getApplication(), previous == null ? null : previous.mark));
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
    public LiveData<Telemetry.Live> getLive() { return live; }
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
        Application app = getApplication();
        boolean enough = ModelDownloader.hasEnoughSpace(app);
        JSONObject e = ExperimentLog.event("download_start");
        ExperimentLog.put(e, "enough_space", enough);
        File dir = app.getExternalFilesDir(null);
        ExperimentLog.put(e, "usable_mb", dir == null ? null : dir.getUsableSpace() / (1024 * 1024));
        ExperimentLog.append(app, e);
        Log.i(Config.LOG_TAG, "download start requested enough_space=" + enough);
        if (!enough) return false;
        ModelDownloader.enqueue(app);
        return true;
    }

    public void unloadModel(Runnable onDone) {
        final Application app = getApplication();
        App.runOnModelThread(() -> {
            VlmEngine.get().unload();
            ExperimentLog.append(app, memoryEvent("model_unload"));
            App.runOnMainThread(onDone);
        });
    }

    public void deleteModel(Runnable onDone) {
        final Application app = getApplication();
        App.runOnModelThread(() -> {
            VlmEngine.get().unload();
            ModelDownloader.deleteModelFiles(app);
            ExperimentLog.append(app, ExperimentLog.event("model_delete"));
            Log.i(Config.LOG_TAG, "model files deleted");
            App.runOnMainThread(onDone);
        });
    }

    /** Deletes history, photos, embeddings, and the experiment log. The model files stay. */
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
            Log.i(Config.LOG_TAG, "history, photos, embeddings, and experiment log cleared");
            ExperimentLog.delete(app, () -> App.runOnMainThread(onDone));
        });
    }

    /** Records a user change of a setting, for comparing runs before and after. */
    public void logSettingChange(String key, Object value) {
        JSONObject e = ExperimentLog.event("setting_change");
        ExperimentLog.put(e, "key", key);
        ExperimentLog.put(e, "value", value);
        ExperimentLog.append(getApplication(), e);
        Log.i(Config.LOG_TAG, "setting " + key + "=" + value);
    }

    private JSONObject memoryEvent(String type) {
        JSONObject e = ExperimentLog.event(type);
        Telemetry.ProcStatus ps = Telemetry.procStatus();
        ExperimentLog.put(e, "rss_mb", ps.rssKb / 1024);
        ExperimentLog.put(e, "vm_hwm_mb", ps.hwmKb / 1024);
        Log.i(Config.LOG_TAG, type + " rss_mb=" + ps.rssKb / 1024);
        return e;
    }

    private void logDownloadState(ModelDownloader.Progress p) {
        JSONObject e = ExperimentLog.event("download_state");
        ExperimentLog.put(e, "state", p.state);
        ExperimentLog.put(e, "downloaded_bytes", p.downloadedBytes);
        ExperimentLog.put(e, "total_bytes", p.totalBytes);
        ExperimentLog.put(e, "reason", p.reason);
        ExperimentLog.append(getApplication(), e);
        Log.i(Config.LOG_TAG, "download state=" + p.state + " bytes=" + p.downloadedBytes + "/" + p.totalBytes
                + " reason=" + p.reason);
    }

    @Override
    protected void onCleared() {
        stopPolling();
    }
}
