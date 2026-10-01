package com.example.identify;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.identify.learning.CorrectionRepository;
import com.example.identify.learning.EmbeddingCache;

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
            deleteOrphanImages(repo);
        });
    }

    /** Deletes photos older than 24 h that never got saved to the database. */
    private void deleteOrphanImages(CorrectionRepository repo) {
        File dir = new File(getFilesDir(), "images");
        File[] files = dir.listFiles();
        if (files == null) return;
        Set<String> keep = new HashSet<>(repo.getAllImagePathsSync());
        long cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
        for (File f : files) {
            if (!keep.contains(f.getAbsolutePath()) && f.lastModified() < cutoff) {
                if (!f.delete()) Log.w(Config.LOG_TAG, "could not delete orphan " + f);
            }
        }
    }

    public static App get() { return instance; }
    public static EmbeddingCache embeddingCache() { return EMBEDDING_CACHE; }
    public static void runOnModelThread(Runnable r) { MODEL_EXECUTOR.execute(r); }
    public static void runOnDbThread(Runnable r) { DB_EXECUTOR.execute(r); }
    public static void runOnIoThread(Runnable r) { IO_EXECUTOR.execute(r); }
    public static void runOnMainThread(Runnable r) { MAIN_HANDLER.post(r); }
}
