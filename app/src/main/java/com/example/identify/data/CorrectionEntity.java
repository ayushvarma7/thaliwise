package com.example.identify.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "corrections")
public class CorrectionEntity {

    /** Row id. */
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Absolute path of the private copy in filesDir/images/. */
    public String imagePath;

    /** Raw model text; null for memory hits. */
    public String vlmRawOutput;

    /** Label shown to the user (model label or memory label). */
    public String predictedLabel;

    /** Description shown (empty for memory hits). */
    public String predictedDescription;

    /** Config.SOURCE_MODEL or Config.SOURCE_MEMORY. */
    public String source;

    /** Top kNN similarity at identify time, -1f if the cache was empty. */
    public float nearestScore;

    /** Typed correction; null when accepted. */
    public String userCorrection;

    /** True = thumbs up, false = corrected. */
    public boolean accepted;

    /** accepted ? predictedLabel : userCorrection. */
    public String finalLabel;

    /** EmbeddingCodec.toBytes(float[]). */
    public byte[] embedding;

    /** Float count of the embedding. */
    public int embeddingDim;

    /** Config.MODEL_ID. */
    public String modelId;

    /** Identify time shown to the user. */
    public long latencyMs;

    /** System.currentTimeMillis() at save. */
    public long timestampMillis;

    public CorrectionEntity() {}
}
