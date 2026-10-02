package com.example.identify.core;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class FoodMatcherSnackTest {

    private static String top(String text) throws IOException {
        List<FoodMatcher.Match> m = FoodMatcher.match(text, FoodCatalogTest.shipped(), 1);
        return m.isEmpty() ? "" : m.get(0).item.id;
    }

    @Test
    public void productNamesBeatGenericFoods() throws IOException {
        assertEquals("reeses_cups", top("Reese's Peanut Butter Cups"));
        assertEquals("snickers", top("Snickers bar"));
        assertEquals("doritos_nacho", top("Doritos Nacho Cheese"));
        assertEquals("lays_classic", top("Lay's Classic potato chips"));
        assertEquals("mms_peanut", top("Peanut M&M's"));
    }

    @Test
    public void genericFoodsStillWin() throws IOException {
        assertEquals("pbj_sandwich", top("Peanut butter and jelly sandwich"));
        assertEquals("peanut_butter", top("Peanut butter"));
        assertEquals("potato_chips", top("potato chips"));
    }

    @Test
    public void freeTextWordsAreIgnored() {
        assertEquals(Arrays.asList("dorito", "nacho", "cheese"),
                FoodMatcher.tokens("The food in this photo is Doritos Nacho Cheese"));
    }
}
