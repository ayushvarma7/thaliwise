package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AnswerParserTest {

    @Test
    public void parsesWellFormedAnswer() {
        AnswerParser.ParsedAnswer a = AnswerParser.parse("Label: Labradoodle\nDescription: A curly-coated dog.");
        assertEquals("Labradoodle", a.label);
        assertEquals("A curly-coated dog.", a.description);
    }

    @Test
    public void stripsMarkdownQuotesAndTrailingPeriod() {
        AnswerParser.ParsedAnswer a = AnswerParser.parse("**Label:** \"Echeveria elegans\".\n**Description:** A succulent.");
        assertEquals("Echeveria elegans", a.label);
        assertEquals("A succulent.", a.description);
    }

    @Test
    public void fallsBackToFirstLine() {
        AnswerParser.ParsedAnswer a = AnswerParser.parse("A red bicycle leaning on a wall.");
        assertEquals("A red bicycle leaning on a wall", a.label);
        assertEquals("", a.description);
    }

    @Test
    public void emptyAndNullGiveUnknown() {
        AnswerParser.ParsedAnswer empty = AnswerParser.parse("");
        assertEquals("Unknown", empty.label);
        assertEquals("", empty.description);
        AnswerParser.ParsedAnswer nul = AnswerParser.parse(null);
        assertEquals("Unknown", nul.label);
        assertEquals("", nul.description);
    }

    @Test
    public void longLabelIsTruncated() {
        StringBuilder sb = new StringBuilder("Label: ");
        for (int i = 0; i < 20; i++) sb.append("word ");
        AnswerParser.ParsedAnswer a = AnswerParser.parse(sb.toString());
        assertTrue(a.label.length() <= 60);
    }
}
