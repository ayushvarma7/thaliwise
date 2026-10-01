package com.example.identify.ui;

import android.app.Application;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.identify.App;
import com.example.identify.AppPrefs;
import com.example.identify.Config;
import com.example.identify.core.AnswerParser;
import com.example.identify.core.EmbeddingCodec;
import com.example.identify.core.PromptBuilder;
import com.example.identify.data.CorrectionEntity;
import com.example.identify.learning.CorrectionRepository;
import com.example.identify.learning.EmbeddingCache;
import com.example.identify.learning.FewShotBuilder;
import com.example.identify.learning.KnnClassifier;
import com.example.identify.model.VlmEngine;
import com.example.identify.util.ImageUtil;

import java.io.File;
import java.util.List;

public class ResultViewModel extends AndroidViewModel {

    public enum Stage { IDLE, LOADING_MODEL, EMBEDDING, GENERATING, DONE, ERROR }

    public static final class IdentifyResult {
        public final String label;
        public final String description;
        public final String rawOutput;     // null for memory hits
        public final String source;        // Config.SOURCE_MODEL or Config.SOURCE_MEMORY
        public final float nearestScore;   // -1 if cache empty
        public final long latencyMs;
        public final float[] embedding;

        IdentifyResult(String label, String description, String rawOutput, String source,
                       float nearestScore, long latencyMs, float[] embedding) {
            this.label = label; this.description = description; this.rawOutput = rawOutput;
            this.source = source; this.nearestScore = nearestScore; this.latencyMs = latencyMs;
            this.embedding = embedding;
        }
    }

    private final MutableLiveData<Stage> stage = new MutableLiveData<>(Stage.IDLE);
    private final MutableLiveData<IdentifyResult> result = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saved = new MutableLiveData<>(false);
    private String imagePath;
    private boolean saving = false;

    public ResultViewModel(@NonNull Application app) { super(app); }

    public LiveData<Stage> getStage() { return stage; }
    public LiveData<IdentifyResult> getResult() { return result; }
    public LiveData<String> getError() { return error; }
    public LiveData<Boolean> getSaved() { return saved; }

    /** Starts once per ViewModel. Ignored after rotation. */
    public void start(String path) {
        if (stage.getValue() != Stage.IDLE) return;
        imagePath = path;
        run(false, null);
    }

    public void retry() {
        if (imagePath != null) run(false, null);
    }

    /** Memory hit shown, user wants the full model. Reuses the embedding. */
    public void runModelAnyway() {
        IdentifyResult r = result.getValue();
        if (r == null || imagePath == null) return;
        run(true, r.embedding);
    }

    private void run(final boolean forceModel, final float[] knownEmbedding) {
        stage.setValue(Stage.LOADING_MODEL);
        error.setValue(null);
        final Application app = getApplication();
        final String path = imagePath;
        App.runOnModelThread(() -> {
            long t0 = SystemClock.elapsedRealtime();
            try {
                VlmEngine engine = VlmEngine.get();
                engine.ensureLoaded(app);
                long tLoad = SystemClock.elapsedRealtime();

                final float[] emb;
                if (knownEmbedding != null) {
                    emb = knownEmbedding;
                } else {
                    App.runOnMainThread(() -> stage.setValue(Stage.EMBEDDING));
                    File embedInput = ImageUtil.prepareForEmbedding(app, path);
                    emb = engine.getImageEmbedding(embedInput.getAbsolutePath());
                }
                long tEmbed = SystemClock.elapsedRealtime();

                EmbeddingCache cache = App.embeddingCache();
                cache.loadIfNeeded(CorrectionRepository.get(app));
                List<EmbeddingCache.Entry> entries = cache.snapshot();
                KnnClassifier.Hit hit = KnnClassifier.nearest(emb, entries);
                final float nearestScore = hit == null ? -1f : hit.score;
                AppPrefs prefs = AppPrefs.get(app);
                float threshold = prefs.getKnnThreshold();

                if (!forceModel && prefs.isMemoryEnabled() && hit != null && hit.score >= threshold) {
                    long total = SystemClock.elapsedRealtime() - t0;
                    Log.i(Config.LOG_TAG, "source=MEMORY load_ms=" + (tLoad - t0)
                            + " embed_ms=" + (tEmbed - tLoad) + " total_ms=" + total
                            + " score=" + hit.score + " threshold=" + threshold + " cache=" + entries.size());
                    IdentifyResult r = new IdentifyResult(hit.label, "", null, Config.SOURCE_MEMORY,
                            nearestScore, total, emb);
                    App.runOnMainThread(() -> { result.setValue(r); stage.setValue(Stage.DONE); });
                    return;
                }

                App.runOnMainThread(() -> stage.setValue(Stage.GENERATING));
                String system = FewShotBuilder.buildSystemPrompt(emb, entries);
                String raw = engine.generateWithImage(path, system, PromptBuilder.USER_PROMPT);
                AnswerParser.ParsedAnswer parsed = AnswerParser.parse(raw);
                long total = SystemClock.elapsedRealtime() - t0;
                Log.i(Config.LOG_TAG, "source=MODEL load_ms=" + (tLoad - t0)
                        + " embed_ms=" + (tEmbed - tLoad)
                        + " gen_ms=" + (SystemClock.elapsedRealtime() - tEmbed)
                        + " total_ms=" + total + " nearest=" + nearestScore + " cache=" + entries.size()
                        + " raw=" + raw.replace('\n', ' '));
                IdentifyResult r = new IdentifyResult(parsed.label, parsed.description, raw,
                        Config.SOURCE_MODEL, nearestScore, total, emb);
                App.runOnMainThread(() -> { result.setValue(r); stage.setValue(Stage.DONE); });
            } catch (Throwable t) {
                Log.e(Config.LOG_TAG, "identify failed", t);
                String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                App.runOnMainThread(() -> { error.setValue(msg); stage.setValue(Stage.ERROR); });
            }
        });
    }

    public void accept() { save(true, null); }

    /** Caller has already rejected empty text. Same text as the label counts as accept. */
    public void submitCorrection(String text) {
        IdentifyResult r = result.getValue();
        String t = text.trim();
        if (r != null && t.equalsIgnoreCase(r.label)) { save(true, null); return; }
        save(false, t);
    }

    private void save(boolean accepted, String correction) {
        IdentifyResult r = result.getValue();
        if (r == null || saving) return;
        saving = true;
        CorrectionEntity e = new CorrectionEntity();
        e.imagePath = imagePath;
        e.vlmRawOutput = r.rawOutput;
        e.predictedLabel = r.label;
        e.predictedDescription = r.description;
        e.source = r.source;
        e.nearestScore = r.nearestScore;
        e.userCorrection = accepted ? null : correction;
        e.accepted = accepted;
        e.finalLabel = accepted ? r.label : correction;
        e.embedding = EmbeddingCodec.toBytes(r.embedding);
        e.embeddingDim = r.embedding.length;
        e.modelId = Config.MODEL_ID;
        e.latencyMs = r.latencyMs;
        e.timestampMillis = System.currentTimeMillis();
        final Application app = getApplication();
        App.runOnDbThread(() -> {
            e.id = CorrectionRepository.get(app).insertSync(e);
            App.embeddingCache().add(e);
            App.runOnMainThread(() -> { saving = false; saved.setValue(true); });
        });
    }

    public void consumeSaved() { saved.setValue(false); }
}
