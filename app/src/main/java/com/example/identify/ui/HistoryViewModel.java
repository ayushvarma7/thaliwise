package com.example.identify.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.identify.core.SearchMatcher;
import com.example.identify.data.CorrectionEntity;
import com.example.identify.learning.CorrectionRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HistoryViewModel extends AndroidViewModel {

    private final LiveData<List<CorrectionEntity>> all;
    private final MutableLiveData<String> query = new MutableLiveData<>("");
    private final MediatorLiveData<List<CorrectionEntity>> filtered = new MediatorLiveData<>();

    public HistoryViewModel(@NonNull Application app) {
        super(app);
        all = CorrectionRepository.get(app).observeAll();
        filtered.addSource(all, list -> recompute());
        filtered.addSource(query, q -> recompute());
    }

    public void setQuery(String q) {
        if (!Objects.equals(q, query.getValue())) query.setValue(q);
    }

    public LiveData<List<CorrectionEntity>> getFiltered() { return filtered; }

    private void recompute() {
        List<CorrectionEntity> source = all.getValue();
        if (source == null) return;   // database not read yet
        String q = query.getValue();
        List<CorrectionEntity> out = new ArrayList<>();
        for (CorrectionEntity e : source) {
            if (SearchMatcher.matches(q, e.finalLabel, e.predictedLabel, e.userCorrection, e.predictedDescription)) {
                out.add(e);
            }
        }
        filtered.setValue(out);
    }
}
