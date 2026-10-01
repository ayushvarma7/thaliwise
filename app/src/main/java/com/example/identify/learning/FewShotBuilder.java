package com.example.identify.learning;

import com.example.identify.Config;
import com.example.identify.core.CorrectionExample;
import com.example.identify.core.FewShotSelector;
import com.example.identify.core.PromptBuilder;

import java.util.ArrayList;
import java.util.List;

public final class FewShotBuilder {
    private FewShotBuilder() {}

    /** The past corrections that go into the prompt: most similar first, then most recent. */
    public static List<CorrectionExample> selectExamples(float[] query, List<EmbeddingCache.Entry> entries) {
        List<CorrectionExample> pool = new ArrayList<>();
        for (EmbeddingCache.Entry e : entries) {
            if (!e.accepted && e.userCorrection != null && !e.userCorrection.trim().isEmpty()) {
                pool.add(new CorrectionExample(e.predictedLabel, e.userCorrection, e.embedding, e.timestampMillis));
            }
        }
        return FewShotSelector.select(query, pool, Config.FEWSHOT_BY_SIMILARITY, Config.FEWSHOT_MAX);
    }

    public static String buildSystemPrompt(float[] query, List<EmbeddingCache.Entry> entries) {
        return PromptBuilder.systemPrompt(selectExamples(query, entries));
    }
}
