package com.example.identify.model;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.example.identify.AppPrefs;
import com.example.identify.Config;

import java.io.File;

/** The only class that touches the network, and only through the system DownloadManager. */
public final class ModelDownloader {
    private ModelDownloader() {}

    public static final class Progress {
        public static final int STATE_NONE = 0;
        public static final int STATE_RUNNING = 1;
        public static final int STATE_SUCCESS = 2;
        public static final int STATE_FAILED = 3;

        public int state;
        public long downloadedBytes;
        public long totalBytes;
        public int reason;
    }

    public static File modelDir(Context ctx) {
        File dir = new File(ctx.getExternalFilesDir(null), "models");
        if (!dir.exists() && !dir.mkdirs()) Log.w(Config.LOG_TAG, "could not create " + dir);
        return dir;
    }

    public static File modelFile(Context ctx) {
        return new File(modelDir(ctx), Config.MODEL_FILE_NAME);
    }

    public static File mmprojFile(Context ctx) {
        return new File(modelDir(ctx), Config.MMPROJ_FILE_NAME);
    }

    public static boolean filesReady(Context ctx) {
        return isReady(modelFile(ctx), Config.MODEL_MIN_BYTES)
                && isReady(mmprojFile(ctx), Config.MMPROJ_MIN_BYTES);
    }

    public static boolean hasEnoughSpace(Context ctx) {
        File dir = ctx.getExternalFilesDir(null);
        return dir != null && dir.getUsableSpace() >= Config.REQUIRED_FREE_BYTES;
    }

    /** Starts a download for each file that is not ready yet. */
    public static void enqueue(Context ctx) {
        DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        AppPrefs prefs = AppPrefs.get(ctx);
        File model = modelFile(ctx);
        if (!isReady(model, Config.MODEL_MIN_BYTES)) {
            long id = enqueueOne(ctx, dm, prefs.getModelDownloadId(), Config.MODEL_URL, Config.MODEL_FILE_NAME, model);
            prefs.setModelDownloadId(id);
        }
        File mmproj = mmprojFile(ctx);
        if (!isReady(mmproj, Config.MMPROJ_MIN_BYTES)) {
            long id = enqueueOne(ctx, dm, prefs.getMmprojDownloadId(), Config.MMPROJ_URL, Config.MMPROJ_FILE_NAME, mmproj);
            prefs.setMmprojDownloadId(id);
        }
    }

    private static long enqueueOne(Context ctx, DownloadManager dm, long previousId,
                                   String url, String fileName, File target) {
        // Cancel an older attempt for this file so two downloads never write the same path.
        if (previousId >= 0) dm.remove(previousId);
        // DownloadManager renames to name-1.gguf when the target already exists.
        if (target.exists() && !target.delete()) Log.w(Config.LOG_TAG, "could not delete " + target);
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url))
                .setTitle("IdentifyVLM model")
                .setDescription(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(ctx, null, "models/" + fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false);
        return dm.enqueue(req);
    }

    public static Progress queryProgress(Context ctx) {
        DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        AppPrefs prefs = AppPrefs.get(ctx);
        long[] ids = {prefs.getModelDownloadId(), prefs.getMmprojDownloadId()};
        File[] files = {modelFile(ctx), mmprojFile(ctx)};
        long[] minBytes = {Config.MODEL_MIN_BYTES, Config.MMPROJ_MIN_BYTES};

        boolean anyFailed = false;
        boolean anyRunning = false;
        boolean totalKnown = true;
        int failReason = 0;
        long downloaded = 0;
        long total = 0;

        for (int i = 0; i < ids.length; i++) {
            if (isReady(files[i], minBytes[i])) {
                downloaded += files[i].length();
                total += files[i].length();
                continue;
            }
            if (ids[i] < 0) {
                totalKnown = false;
                continue;
            }
            Cursor c = dm.query(new DownloadManager.Query().setFilterById(ids[i]));
            if (c == null) {
                totalKnown = false;
                continue;
            }
            try {
                if (!c.moveToFirst()) {
                    totalKnown = false;
                    continue;
                }
                int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                long soFar = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                long size = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                int reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                if (status == DownloadManager.STATUS_FAILED) {
                    anyFailed = true;
                    failReason = reason;
                } else if (status == DownloadManager.STATUS_PENDING
                        || status == DownloadManager.STATUS_RUNNING
                        || status == DownloadManager.STATUS_PAUSED) {
                    anyRunning = true;
                }
                if (soFar > 0) downloaded += soFar;
                if (size > 0) total += size; else totalKnown = false;
            } finally {
                c.close();
            }
        }

        Progress p = new Progress();
        if (anyFailed) {
            p.state = Progress.STATE_FAILED;
            p.reason = failReason;
        } else if (anyRunning) {
            p.state = Progress.STATE_RUNNING;
        } else if (filesReady(ctx)) {
            p.state = Progress.STATE_SUCCESS;
        } else {
            p.state = Progress.STATE_NONE;
        }
        p.totalBytes = totalKnown ? total : Config.EXPECTED_TOTAL_BYTES;
        p.downloadedBytes = Math.min(downloaded, p.totalBytes);
        return p;
    }

    public static void deleteModelFiles(Context ctx) {
        File[] files = {modelFile(ctx), mmprojFile(ctx)};
        for (File f : files) {
            if (f.exists() && !f.delete()) Log.w(Config.LOG_TAG, "could not delete " + f);
        }
    }

    private static boolean isReady(File f, long minBytes) {
        return f.exists() && f.length() >= minBytes;
    }
}
