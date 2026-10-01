package com.example.identify.learning;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.identify.data.AppDatabase;
import com.example.identify.data.CorrectionDao;
import com.example.identify.data.CorrectionEntity;

import java.util.List;

/** Wraps the DAO. Every *Sync method must run off the main thread (use App.runOnDbThread). */
public final class CorrectionRepository {

    private static volatile CorrectionRepository instance;

    private final CorrectionDao dao;

    private CorrectionRepository(Context ctx) {
        dao = AppDatabase.get(ctx).correctionDao();
    }

    public static CorrectionRepository get(Context ctx) {
        if (instance == null) {
            synchronized (CorrectionRepository.class) {
                if (instance == null) instance = new CorrectionRepository(ctx.getApplicationContext());
            }
        }
        return instance;
    }

    public long insertSync(CorrectionEntity e) { return dao.insert(e); }

    public List<CorrectionEntity> getAllSync() { return dao.getAllSync(); }

    public List<String> getAllImagePathsSync() { return dao.getAllImagePathsSync(); }

    public void deleteAllSync() { dao.deleteAll(); }

    public LiveData<List<CorrectionEntity>> observeAll() { return dao.observeAll(); }

    public LiveData<Integer> observeCount() { return dao.observeCount(); }

    public LiveData<Integer> observeCorrectionCount() { return dao.observeCorrectionCount(); }

    public LiveData<Integer> observeMemoryHitCount() { return dao.observeMemoryHitCount(); }

    public LiveData<Integer> observeMemoryHitCorrectedCount() { return dao.observeMemoryHitCorrectedCount(); }
}
