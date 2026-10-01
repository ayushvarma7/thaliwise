package com.example.identify.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AnswerParserCuisineTest {

    @Test
    public void threeLineAnswer() {
        AnswerParser.ParsedAnswer a = AnswerParser.parse(
                "Label: Chicken tikka masala\nCuisine: Indian\nDescription: Chicken in a spiced tomato cream sauce.");
        assertEquals("Chicken tikka masala", a.label);
        assertEquals("Indian", a.cuisine);
        assertEquals("Chicken in a spiced tomato cream sauce.", a.description);
    }

    @Test
    public void markdownCuisine() {
        AnswerParser.ParsedAnswer a = AnswerParser.parse("**Label:** Pho\n**Cuisine:** \"Vietnamese\".");
        assertEquals("Pho", a.label);
        assertEquals("Vietnamese", a.cuisine);
        assertEquals("", a.description);
    }

    @Test
    public void missingCuisineIsEmpty() {
        assertEquals("", AnswerParser.parse("Label: Banana\nDescription: A fruit.").cuisine);
        assertEquals("", AnswerParser.parse(null).cuisine);
        assertEquals("", AnswerParser.parse("mouse").cuisine);
    }
}
