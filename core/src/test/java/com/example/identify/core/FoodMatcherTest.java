package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

public class FoodMatcherTest {

    private static List<FoodItem> foods;

    @BeforeClass
    public static void load() throws IOException {
        foods = FoodCatalogTest.shipped();
    }

    private static String top(String text) {
        List<FoodMatcher.Match> m = FoodMatcher.match(text, foods, 5);
        return m.isEmpty() ? null : m.get(0).item.id;
    }

    @Test
    public void doubleCheeseburgerFindsTheBrandedRow() {
        assertEquals("mcd_double_cheeseburger", top("Double cheeseburger"));
    }

    @Test
    public void brandNamedInTextWins() {
        assertEquals("mcd_big_mac", top("McDonald's Big Mac"));
        assertEquals("mcd_cheeseburger", top("McDonalds cheeseburger"));
    }

    @Test
    public void unbrandedTextPrefersGenericRow() {
        assertEquals("cheeseburger_generic", top("cheeseburger"));
        assertEquals("fries_generic", top("French fries"));
    }

    @Test
    public void pluralsCaseAndStopWords() {
        assertEquals("banana", top("Bananas"));
        assertEquals("pizza_pepperoni", top("Slice of pepperoni pizza"));
        assertEquals("coffee_black", top("A cup of coffee"));
    }

    @Test
    public void nonFoodHasNoMatch() {
        assertTrue(FoodMatcher.match("computer mouse", foods, 5).isEmpty());
        assertTrue(FoodMatcher.match("Golden Retriever", foods, 5).isEmpty());
        assertTrue(FoodMatcher.match("", foods, 5).isEmpty());
        assertTrue(FoodMatcher.match(null, foods, 5).isEmpty());
    }

    @Test
    public void maxIsRespectedAndSorted() {
        List<FoodMatcher.Match> m = FoodMatcher.match("fried chicken", foods, 2);
        assertEquals(2, m.size());
        assertEquals("fried_chicken", m.get(0).item.id);
        assertTrue(m.get(0).score >= m.get(1).score);
    }

    @Test
    public void tokensNormalize() {
        assertEquals(Arrays.asList("mcdonald", "frie"), FoodMatcher.tokens("McDonald's Fries!"));
    }
}
