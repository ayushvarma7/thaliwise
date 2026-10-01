package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class GenerationAnalysisTest {

    private static GenerationAnalysis.Step step(String text, double p, GenerationAnalysis.Candidate... top) {
        return new GenerationAnalysis.Step(text, p, Arrays.asList(top));
    }

    private static GenerationAnalysis.Candidate c(String text, double p) {
        return new GenerationAnalysis.Candidate(text, p);
    }

    private static List<GenerationAnalysis.Step> wellFormed() {
        return Arrays.asList(
                step("Label", 0.99),
                step(":", 0.99),
                step(" Golden", 0.8, c(" Golden", 0.8), c(" Labrador", 0.15), c(" Dog", 0.03)),
                step(" Retriever", 0.5),
                step("\n", 0.9),
                step("Description", 0.9),
                step(":", 0.9),
                step(" A dog.", 0.7));
    }

    @Test
    public void confidenceIsProductOverLabelTokens() {
        GenerationAnalysis.LabelStats s = GenerationAnalysis.analyzeLabel(wellFormed());
        assertTrue(s.labelLineFound);
        assertEquals(2, s.labelTokens);
        assertEquals(0.4, s.confidence, 1e-9);
        assertEquals(0.5, s.minTokenProb, 1e-9);
        assertEquals((Math.log(0.8) + Math.log(0.5)) / 2, s.meanLogProb, 1e-9);
    }

    @Test
    public void alternativesExcludeChosenToken() {
        GenerationAnalysis.LabelStats s = GenerationAnalysis.analyzeLabel(wellFormed());
        assertEquals(2, s.alternatives.size());
        assertEquals(" Labrador", s.alternatives.get(0).text);
        assertEquals(0.15, s.alternatives.get(0).prob, 1e-9);
        assertEquals(" Dog", s.alternatives.get(1).text);
    }

    @Test
    public void markdownAndQuotesAreSkipped() {
        List<GenerationAnalysis.Step> steps = Arrays.asList(
                step("**", 0.9), step("Label", 0.9), step(":**", 0.9), step(" \"", 0.9),
                step("Echeveria", 0.6), step("\".", 0.9));
        GenerationAnalysis.LabelStats s = GenerationAnalysis.analyzeLabel(steps);
        assertTrue(s.labelLineFound);
        assertEquals(1, s.labelTokens);
        assertEquals(0.6, s.confidence, 1e-9);
    }

    @Test
    public void withoutLabelLineFirstLineIsUsed() {
        List<GenerationAnalysis.Step> steps = Arrays.asList(
                step("A", 0.5), step(" red", 0.5), step(" bike", 0.5), step(".", 0.9), step("\n", 0.9), step("More", 0.1));
        GenerationAnalysis.LabelStats s = GenerationAnalysis.analyzeLabel(steps);
        assertFalse(s.labelLineFound);
        assertEquals(3, s.labelTokens);
        assertEquals(0.125, s.confidence, 1e-9);
    }

    @Test
    public void emptyOutputGivesNaN() {
        GenerationAnalysis.LabelStats s = GenerationAnalysis.analyzeLabel(new ArrayList<>());
        assertEquals(0, s.labelTokens);
        assertTrue(Double.isNaN(s.confidence));
        assertTrue(s.alternatives.isEmpty());
        GenerationAnalysis.LabelStats blank = GenerationAnalysis.analyzeLabel(
                Collections.singletonList(step("Label: ", 0.9)));
        assertEquals(0, blank.labelTokens);
        assertTrue(Double.isNaN(blank.confidence));
    }
}
