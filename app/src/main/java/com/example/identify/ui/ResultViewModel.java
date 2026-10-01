package com.example.identify.ui;

import android.app.Application;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.identify.App;
import com.example.identify.AppPrefs;
import com.example.identify.Config;
import com.example.identify.R;
import com.example.identify.core.AnswerParser;
import com.example.identify.core.CorrectionExample;
import com.example.identify.core.EmbeddingCodec;
import com.example.identify.core.GenerationAnalysis;
import com.example.identify.core.PromptBuilder;
import com.example.identify.data.CorrectionEntity;
import com.example.identify.learning.CorrectionRepository;
import com.example.identify.learning.EmbeddingCache;
import com.example.identify.learning.FewShotBuilder;
import com.example.identify.learning.KnnClassifier;
import com.example.identify.model.VlmEngine;
import com.example.identify.util.ExperimentLog;
import com.example.identify.util.ImageUtil;
import com.example.identify.util.Telemetry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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
        public final String runId;         // links the experiment log run to its feedback event
        public final String details;       // time, CPU, memory, confidence, and neighbor summary

        IdentifyResult(String label, String description, String rawOutput, String source,
                       float nearestScore, long latencyMs, float[] embedding, String runId, String details) {
            this.label = label; this.description = description; this.rawOutput = rawOutput;
            this.source = source; this.nearestScore = nearestScore; this.latencyMs = latencyMs;
            this.embedding = embedding; this.runId = runId; this.details = details;
        }
    }

    private final MutableLiveData<Stage> stage = new MutableLiveData<>(Stage.IDLE);
    private final MutableLiveData<IdentifyResult> result = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saved = new MutableLiveData<>(false);
    private String imagePath;
    private boolean saving = false;
    private long resultShownAtMs;

    public ResultViewModel(@NonNull Application app) { super(app); }

    public LiveData<Stage> getStage() { return stage; }
    public LiveData<IdentifyResult> getResult() { return result; }
    public LiveData<String> getError() { return error; }
    public LiveData<Boolean> getSaved() { return saved; }

    /** Starts once per ViewModel. Ignored after rotation. */
    public void start(String path) {
        if (stage.getValue() != Stage.IDLE) return;
        imagePath = path;
        run(false, null, "start");
    }

    public void retry() {
        if (imagePath != null) run(false, null, "retry");
    }

    /** Memory hit shown, user wants the full model. Reuses the embedding. */
    public void runModelAnyway() {
        IdentifyResult r = result.getValue();
        if (r == null || imagePath == null) return;
        run(true, r.embedding, "run_model_anyway");
    }

    private void run(final boolean forceModel, final float[] knownEmbedding, final String trigger) {
        stage.setValue(Stage.LOADING_MODEL);
        error.setValue(null);
        final Application app = getApplication();
        final String path = imagePath;
        final AppPrefs prefs = AppPrefs.get(app);
        final int threads = prefs.getThreads();
        final int imageTokens = prefs.getImageMaxTokens();
        final float threshold = prefs.getKnnThreshold();
        final boolean memoryEnabled = prefs.isMemoryEnabled();
        final String runId = UUID.randomUUID().toString();
        App.runOnModelThread(() -> identify(app, path, runId, trigger, forceModel, knownEmbedding,
                threads, imageTokens, threshold, memoryEnabled));
    }

    /** Runs on the model thread. Every outcome, including errors, becomes one "run" line in the experiment log. */
    private void identify(Application app, String path, String runId, String trigger, boolean forceModel,
                          float[] knownEmbedding, int threads, int imageTokens, float threshold,
                          boolean memoryEnabled) {
        JSONObject rec = ExperimentLog.event("run");
        put(rec, "run_id", runId);
        put(rec, "trigger", trigger);
        put(rec, "force_model", forceModel);
        put(rec, "image_file", new File(path).getName());
        put(rec, "app_version", appVersion(app));
        put(rec, "model_id", Config.MODEL_ID);
        put(rec, "config", configJson(threads, imageTokens, threshold, memoryEnabled));
        put(rec, "device", Telemetry.deviceJson(app));
        JSONArray phases = new JSONArray();
        put(rec, "phases", phases);

        Telemetry.Sampler sampler = Telemetry.Sampler.start(app, Config.TELEMETRY_INTERVAL_MS);
        Telemetry.Mark start = Telemetry.Mark.now();
        try {
            VlmEngine engine = VlmEngine.get();
            boolean loadedNow = engine.ensureLoaded(app, threads, imageTokens);
            Telemetry.Mark afterLoad = Telemetry.Mark.now();
            phases.put(Telemetry.phase("model_load", start, afterLoad));
            put(rec, "model_loaded_now", loadedNow);
            if (loadedNow) put(rec, "native_load", ExperimentLog.parse(engine.getLoadStatsJson()));

            File infer = ImageUtil.prepareForInference(app, path);
            Telemetry.Mark afterPrep = Telemetry.Mark.now();
            phases.put(Telemetry.phase("inference_image", afterLoad, afterPrep));
            put(rec, "inference_image_file", infer.getName());

            final float[] emb;
            Telemetry.Mark afterEmbed;
            if (knownEmbedding != null) {
                emb = knownEmbedding;
                afterEmbed = afterPrep;
                put(rec, "embedding_from_previous_run", true);
            } else {
                App.runOnMainThread(() -> stage.setValue(Stage.EMBEDDING));
                emb = engine.getImageEmbedding(infer.getAbsolutePath());
                afterEmbed = Telemetry.Mark.now();
                phases.put(Telemetry.phase("image_encode", afterPrep, afterEmbed));
                put(rec, "native_embed", ExperimentLog.parse(engine.getLastEmbedStatsJson()));
            }
            put(rec, "embedding_dim", emb.length);

            EmbeddingCache cache = App.embeddingCache();
            cache.loadIfNeeded(CorrectionRepository.get(app));
            List<EmbeddingCache.Entry> entries = cache.snapshot();
            List<KnnClassifier.Hit> neighbors = KnnClassifier.topK(emb, entries, Config.KNN_TOP_LOGGED);
            KnnClassifier.Hit hit = neighbors.isEmpty() ? null : neighbors.get(0);
            final float nearestScore = hit == null ? -1f : hit.score;
            Telemetry.Mark afterKnn = Telemetry.Mark.now();
            phases.put(Telemetry.phase("knn", afterEmbed, afterKnn));
            boolean memoryHit = !forceModel && memoryEnabled && hit != null && hit.score >= threshold;
            put(rec, "knn", knnJson(entries.size(), threshold, memoryEnabled, memoryHit, neighbors));

            long loadMs = afterLoad.wallMs - start.wallMs;
            long embedMs = afterEmbed.wallMs - afterLoad.wallMs;

            if (memoryHit) {
                long total = afterKnn.wallMs - start.wallMs;
                Telemetry.Report tele = sampler.stop();
                put(rec, "source", Config.SOURCE_MEMORY);
                put(rec, "label", hit.label);
                put(rec, "total_ms", total);
                put(rec, "telemetry", tele.json);
                Log.i(Config.LOG_TAG, "source=MEMORY run_id=" + runId + " load_ms=" + loadMs
                        + " embed_ms=" + embedMs + " total_ms=" + total
                        + " score=" + hit.score + " threshold=" + threshold + " cache=" + entries.size()
                        + " threads=" + threads + " image_tokens=" + imageTokens + telemetryLogFields(tele));
                String details = memoryDetails(app, total, loadMs, afterEmbed.wallMs - afterPrep.wallMs,
                        afterKnn.wallMs - afterEmbed.wallMs, tele, neighbors);
                ExperimentLog.append(app, rec);
                IdentifyResult r = new IdentifyResult(hit.label, "", null, Config.SOURCE_MEMORY,
                        nearestScore, total, emb, runId, details);
                App.runOnMainThread(() -> showResult(r));
                return;
            }

            App.runOnMainThread(() -> stage.setValue(Stage.GENERATING));
            List<CorrectionExample> shots = FewShotBuilder.selectExamples(emb, entries);
            String system = PromptBuilder.systemPrompt(shots);
            Telemetry.Mark afterPrompt = Telemetry.Mark.now();
            phases.put(Telemetry.phase("prompt_build", afterKnn, afterPrompt));
            put(rec, "few_shot", fewShotJson(shots));
            put(rec, "system_prompt", system);

            String raw = engine.generateWithImage(infer.getAbsolutePath(), system, PromptBuilder.USER_PROMPT);
            Telemetry.Mark afterGen = Telemetry.Mark.now();
            phases.put(Telemetry.phase("generate", afterPrompt, afterGen));
            JSONObject gen = ExperimentLog.parse(engine.getLastGenerateStatsJson());
            AnswerParser.ParsedAnswer parsed = AnswerParser.parse(raw);
            GenerationAnalysis.LabelStats ls = GenerationAnalysis.analyzeLabel(steps(gen));
            long total = afterGen.wallMs - start.wallMs;
            Telemetry.Report tele = sampler.stop();

            put(rec, "source", Config.SOURCE_MODEL);
            put(rec, "raw_output", raw);
            put(rec, "label", parsed.label);
            put(rec, "description", parsed.description);
            put(rec, "label_stats", labelStatsJson(ls));
            put(rec, "native_generate", gen);
            put(rec, "total_ms", total);
            put(rec, "telemetry", tele.json);
            Log.i(Config.LOG_TAG, "source=MODEL run_id=" + runId + " load_ms=" + loadMs
                    + " embed_ms=" + embedMs
                    + " gen_ms=" + (afterGen.wallMs - afterEmbed.wallMs)
                    + " total_ms=" + total + " nearest=" + nearestScore + " cache=" + entries.size()
                    + " threads=" + threads + " image_tokens=" + imageTokens
                    + " image_tokens_used=" + gen.optInt("n_image_tokens")
                    + " prefill_ms=" + Math.round(gen.optDouble("t_prefill_ms", 0))
                    + " ttft_ms=" + Math.round(gen.optDouble("t_ttft_ms", 0))
                    + " gen_tokens=" + gen.optInt("n_gen_tokens")
                    + " tok_s=" + gen.optDouble("gen_tokens_per_s", 0)
                    + " label_conf=" + String.format(Locale.US, "%.3f", ls.confidence)
                    + telemetryLogFields(tele)
                    + " raw=" + raw.replace('\n', ' '));
            String details = modelDetails(app, total, loadMs, afterEmbed.wallMs - afterPrep.wallMs, gen, tele, ls, neighbors);
            ExperimentLog.append(app, rec);
            IdentifyResult r = new IdentifyResult(parsed.label, parsed.description, raw,
                    Config.SOURCE_MODEL, nearestScore, total, emb, runId, details);
            App.runOnMainThread(() -> showResult(r));
        } catch (Throwable t) {
            Log.e(Config.LOG_TAG, "identify failed run_id=" + runId, t);
            String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            Telemetry.Report tele = sampler.stop();
            put(rec, "error", msg);
            put(rec, "error_class", t.getClass().getName());
            put(rec, "total_ms", Telemetry.Mark.now().wallMs - start.wallMs);
            put(rec, "telemetry", tele.json);
            ExperimentLog.append(app, rec);
            App.runOnMainThread(() -> { error.setValue(msg); stage.setValue(Stage.ERROR); });
        }
    }

    private void showResult(IdentifyResult r) {
        resultShownAtMs = SystemClock.elapsedRealtime();
        result.setValue(r);
        stage.setValue(Stage.DONE);
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
        final long timeToFeedbackMs = SystemClock.elapsedRealtime() - resultShownAtMs;
        App.runOnDbThread(() -> {
            e.id = CorrectionRepository.get(app).insertSync(e);
            App.embeddingCache().add(e);
            JSONObject fb = ExperimentLog.event("feedback");
            put(fb, "run_id", r.runId);
            put(fb, "db_id", e.id);
            put(fb, "image_file", new File(e.imagePath).getName());
            put(fb, "source", e.source);
            put(fb, "accepted", e.accepted);
            put(fb, "predicted_label", e.predictedLabel);
            put(fb, "user_correction", e.userCorrection);
            put(fb, "final_label", e.finalLabel);
            put(fb, "nearest_score", (double) e.nearestScore);
            put(fb, "latency_ms", e.latencyMs);
            put(fb, "time_to_feedback_ms", timeToFeedbackMs);
            ExperimentLog.append(app, fb);
            Log.i(Config.LOG_TAG, "feedback run_id=" + r.runId + " db_id=" + e.id + " accepted=" + e.accepted
                    + " final_label=" + e.finalLabel + " time_to_feedback_ms=" + timeToFeedbackMs);
            App.runOnMainThread(() -> { saving = false; saved.setValue(true); });
        });
    }

    public void consumeSaved() { saved.setValue(false); }

    // ---------------------------------------------------------------- experiment log pieces

    private static void put(JSONObject o, String key, Object value) { ExperimentLog.put(o, key, value); }

    private static JSONObject configJson(int threads, int imageTokens, float threshold, boolean memoryEnabled) {
        JSONObject c = new JSONObject();
        put(c, "n_threads", threads);
        put(c, "image_max_tokens", imageTokens);
        put(c, "n_ctx", Config.N_CTX);
        put(c, "max_new_tokens", Config.MAX_NEW_TOKENS);
        put(c, "temperature", (double) Config.TEMPERATURE);
        put(c, "min_p", (double) Config.MIN_P);
        put(c, "repeat_penalty", (double) Config.REPEAT_PENALTY);
        put(c, "top_k", Config.TOP_K);
        put(c, "knn_threshold", (double) threshold);
        put(c, "memory_enabled", memoryEnabled);
        put(c, "fewshot_by_similarity", Config.FEWSHOT_BY_SIMILARITY);
        put(c, "fewshot_max", Config.FEWSHOT_MAX);
        put(c, "stored_image_max_dim", Config.MODEL_IMAGE_MAX_DIM);
        put(c, "inference_image_max_dim", Config.INFER_IMAGE_MAX_DIM);
        return c;
    }

    private static JSONObject knnJson(int cacheSize, float threshold, boolean memoryEnabled, boolean memoryHit,
                                      List<KnnClassifier.Hit> neighbors) {
        JSONObject k = new JSONObject();
        put(k, "cache_size", cacheSize);
        put(k, "threshold", (double) threshold);
        put(k, "memory_enabled", memoryEnabled);
        put(k, "memory_hit", memoryHit);
        put(k, "nearest_score", neighbors.isEmpty() ? null : (double) neighbors.get(0).score);
        JSONArray arr = new JSONArray();
        for (KnnClassifier.Hit h : neighbors) {
            JSONObject o = new JSONObject();
            put(o, "label", h.label);
            put(o, "score", (double) h.score);
            put(o, "entry_id", h.entryId);
            put(o, "predicted_label", h.predictedLabel);
            put(o, "accepted", h.accepted);
            arr.put(o);
        }
        put(k, "neighbors", arr);
        return k;
    }

    private static JSONArray fewShotJson(List<CorrectionExample> shots) {
        JSONArray arr = new JSONArray();
        for (CorrectionExample c : shots) {
            JSONObject o = new JSONObject();
            put(o, "model_label", c.modelLabel);
            put(o, "correct_label", c.correctLabel);
            arr.put(o);
        }
        return arr;
    }

    private static JSONObject labelStatsJson(GenerationAnalysis.LabelStats ls) {
        JSONObject o = new JSONObject();
        put(o, "label_line_found", ls.labelLineFound);
        put(o, "label_tokens", ls.labelTokens);
        put(o, "confidence", ls.confidence);
        put(o, "min_token_prob", ls.minTokenProb);
        put(o, "mean_log_prob", ls.meanLogProb);
        JSONArray alt = new JSONArray();
        for (GenerationAnalysis.Candidate c : ls.alternatives) {
            JSONObject a = new JSONObject();
            put(a, "t", c.text);
            put(a, "p", c.prob);
            alt.put(a);
        }
        put(o, "alternatives", alt);
        return o;
    }

    /** The per-token data from the native generate stats. */
    private static List<GenerationAnalysis.Step> steps(JSONObject gen) {
        List<GenerationAnalysis.Step> out = new ArrayList<>();
        JSONArray tokens = gen.optJSONArray("tokens");
        if (tokens == null) return out;
        for (int i = 0; i < tokens.length(); i++) {
            JSONObject t = tokens.optJSONObject(i);
            if (t == null) continue;
            List<GenerationAnalysis.Candidate> top = new ArrayList<>();
            JSONArray arr = t.optJSONArray("top");
            if (arr != null) {
                for (int k = 0; k < arr.length(); k++) {
                    JSONObject c = arr.optJSONObject(k);
                    if (c != null) top.add(new GenerationAnalysis.Candidate(c.optString("t"), c.optDouble("p", 0)));
                }
            }
            out.add(new GenerationAnalysis.Step(t.optString("t"), t.optDouble("p", 0), top));
        }
        return out;
    }

    private static String telemetryLogFields(Telemetry.Report tele) {
        return " cpu_ms=" + tele.summary.cpuMs
                + " avg_cores=" + String.format(Locale.US, "%.2f", tele.summary.avgCores)
                + " peak_cores=" + String.format(Locale.US, "%.2f", tele.summary.peakCores)
                + " cores_by_type=[" + tele.clusterShares + "]"
                + " peak_rss_mb=" + tele.summary.peakRssKb / 1024
                + " temp_c=" + tele.tempEndC
                + " thermal=" + tele.thermalMax
                + " charging=" + tele.charging;
    }

    private static String appVersion(Application app) {
        try {
            PackageManager pm = app.getPackageManager();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return pm.getPackageInfo(app.getPackageName(), PackageManager.PackageInfoFlags.of(0)).versionName;
            }
            return pm.getPackageInfo(app.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }

    // ---------------------------------------------------------------- details text for the result screen

    private static String modelDetails(Application app, long totalMs, long loadMs, long encodeMs, JSONObject gen,
                                       Telemetry.Report tele, GenerationAnalysis.LabelStats ls,
                                       List<KnnClassifier.Hit> neighbors) {
        StringBuilder d = new StringBuilder();
        d.append(app.getString(R.string.details_time_model, totalMs / 1000f, loadMs / 1000f, encodeMs / 1000f,
                (float) gen.optDouble("t_prefill_ms", 0) / 1000f, (float) gen.optDouble("t_generate_ms", 0) / 1000f,
                (float) gen.optDouble("gen_tokens_per_s", 0)));
        d.append('\n').append(app.getString(R.string.details_tokens, gen.optInt("n_image_tokens"),
                gen.optInt("n_text_prompt_tokens"), gen.optInt("n_gen_tokens")));
        appendTelemetry(app, d, tele);
        if (!Double.isNaN(ls.confidence)) {
            d.append('\n').append(app.getString(R.string.details_confidence, (float) ls.confidence));
        }
        if (!ls.alternatives.isEmpty()) {
            StringBuilder alt = new StringBuilder();
            for (GenerationAnalysis.Candidate c : ls.alternatives) {
                if (alt.length() > 0) alt.append(", ");
                alt.append(displayToken(c.text)).append(' ').append(String.format(Locale.US, "%.2f", c.prob));
            }
            d.append('\n').append(app.getString(R.string.details_alternatives, alt.toString()));
        }
        appendNeighbors(app, d, neighbors);
        return d.toString();
    }

    private static String memoryDetails(Application app, long totalMs, long loadMs, long encodeMs, long knnMs,
                                        Telemetry.Report tele, List<KnnClassifier.Hit> neighbors) {
        StringBuilder d = new StringBuilder();
        d.append(app.getString(R.string.details_time_memory, totalMs / 1000f, loadMs / 1000f, encodeMs / 1000f,
                (int) knnMs));
        appendTelemetry(app, d, tele);
        appendNeighbors(app, d, neighbors);
        return d.toString();
    }

    private static void appendTelemetry(Application app, StringBuilder d, Telemetry.Report tele) {
        d.append('\n').append(app.getString(R.string.details_cpu, (float) tele.summary.avgCores,
                (float) (100.0 * tele.summary.avgCores / tele.nCpus), tele.nCpus,
                (float) tele.summary.peakCores, tele.summary.cpuMs / 1000f));
        if (!tele.clusterShares.isEmpty()) {
            d.append('\n').append(app.getString(R.string.details_clusters, tele.clusterShares));
        }
        String battery;
        if (tele.charging) {
            battery = app.getString(R.string.details_battery_charging, tele.tempEndC);
        } else if (!Double.isNaN(tele.summary.energyMah)) {
            battery = app.getString(R.string.details_battery_used, tele.tempEndC, (float) tele.summary.energyMah);
        } else {
            battery = app.getString(R.string.details_battery_plain, tele.tempEndC);
        }
        d.append('\n').append(app.getString(R.string.details_memory, (int) (tele.summary.peakRssKb / 1024),
                battery, tele.thermalMax));
    }

    private static void appendNeighbors(Application app, StringBuilder d, List<KnnClassifier.Hit> neighbors) {
        if (neighbors.isEmpty()) {
            d.append('\n').append(app.getString(R.string.details_neighbors_none));
            return;
        }
        StringBuilder n = new StringBuilder();
        for (KnnClassifier.Hit h : neighbors) {
            if (n.length() > 0) n.append(", ");
            n.append(h.label).append(' ').append(String.format(Locale.US, "%.2f", h.score));
        }
        d.append('\n').append(app.getString(R.string.details_neighbors, n.toString()));
    }

    private static String displayToken(String t) {
        String s = t.replace("\n", "\\n").trim();
        return s.isEmpty() ? "(space)" : s;
    }
}
