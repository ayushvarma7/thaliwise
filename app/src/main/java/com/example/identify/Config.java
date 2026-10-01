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
    /** Embeddings from a different model/projector are not comparable. */
    public static final String MODEL_ID = "LFM2.5-VL-1.6B-Q4_0+mmproj-Q8_0";

    public static final int   N_CTX          = 4096;
    public static final int   N_THREADS      = 4;
    public static final int   MAX_NEW_TOKENS = 128;
    public static final float TEMPERATURE    = 0.1f;
    public static final float MIN_P          = 0.15f;
    public static final float REPEAT_PENALTY = 1.05f;
    public static final int   TOP_K          = 50;

    public static final float DEFAULT_KNN_THRESHOLD = 0.92f;
    public static final int   FEWSHOT_BY_SIMILARITY = 3;
    public static final int   FEWSHOT_MAX           = 5;

    public static final int MODEL_IMAGE_MAX_DIM = 1024;
    public static final int EMBED_IMAGE_DIM     = 512;

    public static final String SOURCE_MODEL  = "MODEL";
    public static final String SOURCE_MEMORY = "MEMORY";

    public static final String LOG_TAG = "Identify";
}
