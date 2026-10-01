package com.example.identify;

public final class Config {
    private Config() {}

    public static final String MODEL_FILE_NAME  = "LFM2.5-VL-1.6B-Q4_0.gguf";
    public static final String MMPROJ_FILE_NAME = "mmproj-LFM2.5-VL-1.6b-Q8_0.gguf";
    public static final String MODEL_URL  =
            "https://huggingface.co/LiquidAI/LFM2.5-VL-1.6B-GGUF/resolve/main/LFM2.5-VL-1.6B-Q4_0.gguf";
    public static final String MMPROJ_URL =
            "https://huggingface.co/LiquidAI/LFM2.5-VL-1.6B-GGUF/resolve/main/mmproj-LFM2.5-VL-1.6b-Q8_0.gguf";
    public static final long MODEL_MIN_BYTES     = 650_000_000L;
    public static final long MMPROJ_MIN_BYTES    = 540_000_000L;
    public static final long EXPECTED_TOTAL_BYTES = 1_279_000_000L;
    public static final long REQUIRED_FREE_BYTES = 1_500_000_000L;
    /**
     * Embeddings from a different model/projector are not comparable. The "emb2" suffix marks embeddings
     * mean-pooled from the shared single-tile inference encoding (older rows used a separate 512x512 crop).
     */
    public static final String MODEL_ID = "LFM2.5-VL-1.6B-Q4_0+mmproj-Q8_0+emb2";

    public static final int   N_CTX          = 4096;
    public static final int   N_THREADS      = 4;    // default; adjustable in Settings
    public static final int   MAX_NEW_TOKENS = 128;
    public static final float TEMPERATURE    = 0.1f;
    public static final float MIN_P          = 0.15f;
    public static final float REPEAT_PENALTY = 1.05f;
    public static final int   TOP_K          = 50;

    public static final float DEFAULT_KNN_THRESHOLD = 0.92f;
    public static final int   FEWSHOT_BY_SIMILARITY = 3;
    public static final int   FEWSHOT_MAX           = 5;

    /** Longest side of the private photo copy kept for history (and for any later fine-tuning). */
    public static final int MODEL_IMAGE_MAX_DIM = 1024;
    /**
     * Longest side of the image handed to the model. LFM2-VL splits anything larger than about 724x724
     * into 512x512 tiles plus a thumbnail, and each tile costs a full vision encoder pass on the CPU.
     * 512 keeps every photo on a single tile.
     */
    public static final int INFER_IMAGE_MAX_DIM = 512;

    /** Cap on vision tokens per image (LFM2-VL default 256). Fewer tokens encode faster with less detail. */
    public static final int DEFAULT_IMAGE_MAX_TOKENS = 256;
    public static final int MIN_IMAGE_MAX_TOKENS     = 64;
    public static final int MAX_IMAGE_MAX_TOKENS     = 256;
    public static final int MIN_THREADS = 1;
    public static final int MAX_THREADS = 8;

    /** Nearest stored photos written to the experiment log for every run. */
    public static final int KNN_TOP_LOGGED = 5;
    public static final long TELEMETRY_INTERVAL_MS = 500;
    public static final String EXPERIMENT_DIR      = "experiments";
    public static final String EXPERIMENT_LOG_FILE = "experiment_log.jsonl";
    public static final String EXP_TAG = "IdentifyExp";

    /** Logcat tag for Health Connect. */
    public static final String HEALTH_TAG = "IdentifyHealth";
    /** Daily step goal until the user can change it (later phase). */
    public static final long DEFAULT_STEP_GOAL = 10_000L;

    public static final String SOURCE_MODEL  = "MODEL";
    public static final String SOURCE_MEMORY = "MEMORY";

    public static final String LOG_TAG = "Identify";
}
