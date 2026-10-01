package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

public class PromptBuilderTest {

    @Test
    public void emptyListGivesBasePrompt() {
        assertEquals(PromptBuilder.SYSTEM_BASE, PromptBuilder.systemPrompt(Collections.emptyList()));
    }

    @Test
    public void oneExampleIsAppended() {
        String p = PromptBuilder.systemPrompt(Collections.singletonList(
                new CorrectionExample("Golden Retriever", "Labradoodle", null, 1)));
        assertTrue(p.endsWith("\n- You said \"Golden Retriever\". Correct answer: \"Labradoodle\"."));
        assertTrue(p.contains(PromptBuilder.CORRECTIONS_HEADER));
    }

    @Test
    public void sanitizeRemovesNewlinesAndQuotes() {
        assertEquals("a b c", PromptBuilder.sanitize("a\nb \"c\""));
    }

    @Test
    public void sanitizeTruncatesLongText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) sb.append('x');
        assertTrue(PromptBuilder.sanitize(sb.toString()).length() <= 80);
    }
}
