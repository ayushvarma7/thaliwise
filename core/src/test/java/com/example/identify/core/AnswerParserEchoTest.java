package com.example.identify.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AnswerParserEchoTest {

    @Test
    public void copiedInstructionWordsAreRemoved() {
        AnswerParser.ParsedAnswer a = AnswerParser.parse(
                "Label: Pad thai, at most 6 words\nCuisine: Thai\nDescription: Stir-fried rice noodles.");
        assertEquals("Pad thai", a.label);
        assertEquals("Thai", a.cuisine);
        assertEquals("Stir-fried rice noodles.", a.description);
        assertEquals("Bibimbap", AnswerParser.parse("Label: Bibimbap (at most 6 words)").label);
        assertEquals("Pho", AnswerParser.parse("Label: Pho, max 6 words.").label);
    }

    @Test
    public void templateWordsMeanUnknown() {
        AnswerParser.ParsedAnswer a = AnswerParser.parse(
                "Label: <dish name>\nCuisine: <cuisine>\nDescription: <one sentence>");
        assertEquals("Unknown", a.label);
        assertEquals("", a.cuisine);
    }

    @Test
    public void freeTextUsesTheFirstSentenceWithoutTheOpening() {
        AnswerParser.ParsedAnswer a = AnswerParser.parse(
                "The food in this photo is Doritos Nacho Cheese. The bag shows the brand name.");
        assertEquals("Doritos Nacho Cheese", a.label);
        assertEquals("The bag shows the brand name.", a.description);
        assertEquals("bowl of pho with herbs", AnswerParser.parse("This image shows a bowl of pho with herbs.").label);
        assertEquals("burrito", AnswerParser.parse("It is a burrito").label);
    }

    @Test
    public void savedLabelsAreCleanedAndNormalLabelsKept() {
        assertEquals("Pad thai", AnswerParser.cleanLabel("Pad thai, at most 6 words"));
        assertEquals("", AnswerParser.cleanLabel(null));
        assertEquals("Pad see ew", AnswerParser.cleanLabel("Pad see ew"));
        assertEquals("Reese's Peanut Butter Cups", AnswerParser.cleanLabel("Reese's Peanut Butter Cups"));
        assertEquals("Not food", AnswerParser.cleanLabel("Not food"));
    }
}
