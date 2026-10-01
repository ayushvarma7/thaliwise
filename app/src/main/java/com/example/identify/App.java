package com.example.identify;

import android.app.ActivityManager;
import android.app.Application;
import android.app.ApplicationExitInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.identify.learning.CorrectionRepository;
import com.example.identify.learning.EmbeddingCache;
import com.example.identify.model.ModelDownloader;
import com.example.identify.util.ExperimentLog;
import com.example.identify.util.Telemetry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App extends Application {

    private static App instance;

    // Model ops are strictly sequential. 8 MB stack for native code.
    private static final ExecutorService MODEL_EXECUTOR = Executors.newSingleThreadExecutor(
            r -> new Thread(null, r, "model-thread", 8L * 1024 * 1024));
    private static final ExecutorService DB_EXECUTOR = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "db-thread"));
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "io-thread"));
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final EmbeddingCache EMBEDDING_CACHE = new EmbeddingCache();

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        DB_EXECUTOR.execute(() -> {
            CorrectionRepository repo = CorrectionRepository.get(this);
            EMBEDDING_CACHE.loadIfNeeded(repo);
            Log.i(Config.LOG_TAG, "embedding cache loaded entries=" + EMBEDDING_CACHE.size());
            deleteOrphanImages(repo);
        });
        IO_EXECUTOR.execute(this::logAppStart);
    }

    /** Deletes photos older than 24 h that never got saved to the database. */
    private void deleteOrphanImages(CorrectionRepository repo) {
        File dir = new File(getFilesDir(), "images");
        File[] files = dir.listFiles();
        if (files == null) return;
        Set<String> keep = new HashSet<>(repo.getAllImagePathsSync());
        long cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
        int deleted = 0;
        for (File f : files) {
            if (!keep.contains(f.getAbsolutePath()) && f.lastModified() < cutoff) {
                if (f.delete()) deleted++;
                else Log.w(Config.LOG_TAG, "could not delete orphan " + f);
            }
        }
        if (deleted > 0) Log.i(Config.LOG_TAG, "deleted orphan images count=" + deleted);
    }

    /** Device facts and why the previous process ended (low memory, crash, user, ...). */
    private void logAppStart() {
        JSONObject e = ExperimentLog.event("app_start");
        ExperimentLog.put(e, "model_id", Config.MODEL_ID);
        ExperimentLog.put(e, "model_files_ready", ModelDownloader.filesReady(this));
        ExperimentLog.put(e, "device", Telemetry.deviceJson(this));
        JSONArray exits = new JSONArray();
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am != null) {
            List<ApplicationExitInfo> infos = am.getHistoricalProcessExitReasons(getPackageName(), 0, 3);
            for (ApplicationExitInfo info : infos) {
                JSONObject x = new JSONObject();
                ExperimentLog.put(x, "reason", exitReasonName(info.getReason()));
                ExperimentLog.put(x, "description", info.getDescription());
                ExperimentLog.put(x, "timestamp", info.getTimestamp());
                ExperimentLog.put(x, "importance", info.getImportance());
                ExperimentLog.put(x, "pss_mb", info.getPss() / 1024);
                ExperimentLog.put(x, "rss_mb", info.getRss() / 1024);
                ExperimentLog.put(x, "status", info.getStatus());
                exits.put(x);
            }
            if (!infos.isEmpty()) {
                ApplicationExitInfo last = infos.get(0);
                Log.i(Config.LOG_TAG, "previous process exit reason=" + exitReasonName(last.getReason())
                        + " description=" + last.getDescription() + " rss_mb=" + last.getRss() / 1024);
            }
        }
        ExperimentLog.put(e, "previous_exits", exits);
        ExperimentLog.append(this, e);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Telemetry.ProcStatus ps = Telemetry.procStatus();
        Log.w(Config.LOG_TAG, "trim memory level=" + level + " rss_mb=" + ps.rssKb / 1024);
        JSONObject e = ExperimentLog.event("trim_memory");
        ExperimentLog.put(e, "level", level);
        ExperimentLog.put(e, "rss_mb", ps.rssKb / 1024);
        ExperimentLog.append(this, e);
    }

    private static String exitReasonName(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_EXIT_SELF: return "exit_self";
            case ApplicationExitInfo.REASON_SIGNALED: return "signaled";
            case ApplicationExitInfo.REASON_LOW_MEMORY: return "low_memory";
            case ApplicationExitInfo.REASON_CRASH: return "crash";
            case ApplicationExitInfo.REASON_CRASH_NATIVE: return "crash_native";
            case ApplicationExitInfo.REASON_ANR: return "anr";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE: return "initialization_failure";
            case ApplicationExitInfo.REASON_PERMISSION_CHANGE: return "permission_change";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE: return "excessive_resource_usage";
            case ApplicationExitInfo.REASON_USER_REQUESTED: return "user_requested";
            case ApplicationExitInfo.REASON_USER_STOPPED: return "user_stopped";
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED: return "dependency_died";
            case ApplicationExitInfo.REASON_OTHER: return "other";
            case ApplicationExitInfo.REASON_FREEZER: return "freezer";
            case ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE: return "package_state_change";
            case ApplicationExitInfo.REASON_PACKAGE_UPDATED: return "package_updated";
            default: return "unknown_" + reason;
        }
    }

    public static App get() { return instance; }
    public static EmbeddingCache embeddingCache() { return EMBEDDING_CACHE; }
    public static void runOnModelThread(Runnable r) { MODEL_EXECUTOR.execute(r); }
    public static void runOnDbThread(Runnable r) { DB_EXECUTOR.execute(r); }
    public static void runOnIoThread(Runnable r) { IO_EXECUTOR.execute(r); }
    public static void runOnMainThread(Runnable r) { MAIN_HANDLER.post(r); }
}
