package com.example.identify.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** How sure the model was about the label it generated, and which other label starts it considered. */
public final class GenerationAnalysis {
    private GenerationAnalysis() {}

    public static final int MAX_ALTERNATIVES = 4;

    public static final class Candidate {
        public final String text;
        public final double prob;
        public Candidate(String text, double prob) { this.text = text; this.prob = prob; }
    }

    /** One generated token: its text, the model probability of it, and the most likely tokens at that step. */
    public static final class Step {
        public final String text;
        public final double prob;
        public final List<Candidate> top;
        public Step(String text, double prob, List<Candidate> top) {
            this.text = text == null ? "" : text;
            this.prob = prob;
            this.top = top == null ? Collections.emptyList() : top;
        }
    }

    public static final class LabelStats {
        /** True when a "Label:" line was found; false means the first line was used, like AnswerParser. */
        public final boolean labelLineFound;
        /** Number of generated tokens that make up the label text. 0 when there is no label text. */
        public final int labelTokens;
        /** Product of the chosen-token probabilities over the label text, or NaN when there is no label text. */
        public final double confidence;
        public final double minTokenProb;
        public final double meanLogProb;
        /** Top candidates at the first label token, chosen token excluded. */
        public final List<Candidate> alternatives;

        LabelStats(boolean labelLineFound, int labelTokens, double confidence, double minTokenProb,
                   double meanLogProb, List<Candidate> alternatives) {
            this.labelLineFound = labelLineFound;
            this.labelTokens = labelTokens;
            this.confidence = confidence;
            this.minTokenProb = minTokenProb;
            this.meanLogProb = meanLogProb;
            this.alternatives = alternatives;
        }
    }

    public static LabelStats analyzeLabel(List<Step> steps) {
        StringBuilder sb = new StringBuilder();
        int[] start = new int[steps.size()];
        for (int i = 0; i < steps.size(); i++) {
            start[i] = sb.length();
            sb.append(steps.get(i).text);
        }
        String text = sb.toString();
        String lower = text.toLowerCase(Locale.ROOT);

        int marker = lower.indexOf("label:");
        boolean found = marker >= 0;
        int from = found ? marker + "label:".length() : 0;
        while (from < text.length() && isLeadingNoise(text.charAt(from))) from++;
        int to = text.indexOf('\n', from);
        if (to < 0) to = text.length();
        while (to > from && isTrailingNoise(text.charAt(to - 1))) to--;

        if (from >= to) {
            return new LabelStats(found, 0, Double.NaN, Double.NaN, Double.NaN, Collections.emptyList());
        }

        int first = -1;
        int count = 0;
        double product = 1.0;
        double min = Double.POSITIVE_INFINITY;
        double logSum = 0.0;
        for (int i = 0; i < steps.size(); i++) {
            int s = start[i];
            int e = s + steps.get(i).text.length();
            if (s < to && e > from) {
                double p = steps.get(i).prob;
                if (first < 0) first = i;
                count++;
                product *= p;
                min = Math.min(min, p);
                logSum += Math.log(Math.max(p, 1e-12));
            }
        }

        List<Candidate> alternatives = new ArrayList<>();
        if (first >= 0) {
            Step chosen = steps.get(first);
            for (Candidate c : chosen.top) {
                if (alternatives.size() >= MAX_ALTERNATIVES) break;
                if (!c.text.equals(chosen.text)) alternatives.add(c);
            }
        }
        return new LabelStats(found, count, product, min, logSum / count, alternatives);
    }

    private static boolean isLeadingNoise(char c) {
        return Character.isWhitespace(c) || c == '*' || c == '"' || c == '\'';
    }

    private static boolean isTrailingNoise(char c) {
        return Character.isWhitespace(c) || c == '*' || c == '"' || c == '\'' || c == '.';
    }
}
