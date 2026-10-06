package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

public class AnswerFormatTest {

    @Test
    public void grammarForcesTheThreeLinesInOrder() {
        String g = PromptBuilder.ANSWER_GRAMMAR;
        int label = g.indexOf("root ::= \"Label: \"");
        int cuisine = g.indexOf("\"\\nCuisine: \"");
        int description = g.indexOf("\"\\nDescription: \"");
        assertTrue(label == 0);
        assertTrue(cuisine > label);
        assertTrue(description > cuisine);
        assertTrue(g.contains("label ::= [^\\n<>]{1," + AnswerParser.MAX_LABEL_CHARS + "}"));
    }

    @Test
    public void aGrammarShapedReplyParses() {
        AnswerParser.ParsedAnswer a = AnswerParser.parse(
                "Label: Vegetable rotini pasta\nCuisine: Italian\nDescription: Spiral pasta with mixed vegetables.");
        assertEquals("Vegetable rotini pasta", a.label);
        assertEquals("Italian", a.cuisine);
        assertEquals("Spiral pasta with mixed vegetables.", a.description);
    }

    @Test
    public void thePromptNamesNoExampleDishesAndOffersUnknownFood() {
        String p = PromptBuilder.SYSTEM_BASE.toLowerCase(Locale.ROOT);
        for (String example : Arrays.asList("tikka", "tacos", "pad thai", "pizza", "big mac", "doritos", "diet coke")) {
            assertFalse(example, p.contains(example));
        }
        assertTrue(PromptBuilder.SYSTEM_BASE.contains("Unknown food"));
    }

    @Test
    public void unknownFoodMatchesNoTableRow() throws IOException {
        assertTrue(FoodMatcher.match("Unknown food", FoodCatalogTest.shipped(), 1).isEmpty());
    }

    @Test
    public void nonAnswerCorrectionsAreNeverExamples() {
        float[] e = {1f, 0f};
        List<CorrectionExample> pool = Arrays.asList(
                new CorrectionExample("Unknown food", "Snickers bar", e, 3),
                new CorrectionExample("Not food", "Pasta", e, 2),
                new CorrectionExample("Chicken curry", "Butter chicken", e, 1));
        List<CorrectionExample> picked = FewShotSelector.select(e, pool, 3, 5);
        assertEquals(1, picked.size());
        assertEquals("Butter chicken", picked.get(0).correctLabel);
    }
}
