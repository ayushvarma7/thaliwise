package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

public class FoodMatcherCuisineTest {

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
    public void dishesAcrossCuisines() {
        assertEquals("chicken_tikka_masala", top("Chicken tikka masala"));
        assertEquals("masala_dosa", top("Masala dosa"));
        assertEquals("tacos_carne_asada", top("Carne asada tacos"));
        assertEquals("tacos_al_pastor", top("Tacos al pastor"));
        assertEquals("pho", top("Pho"));
        assertEquals("pad_thai", top("Pad thai"));
        assertEquals("bibimbap", top("Bibimbap"));
        assertEquals("pita", top("Pita bread"));
        assertEquals("shawarma", top("Chicken shawarma"));
        assertEquals("general_tso_chicken", top("General Tso's chicken"));
        assertEquals("bbq_ribs", top("BBQ ribs"));
        assertEquals("ramen_restaurant", top("Ramen"));
    }

    @Test
    public void chainItemsNeedTheirName() {
        assertEquals("bk_whopper", top("Whopper"));
        assertEquals("orange_chicken", top("Orange chicken"));
        assertEquals("panda_orange_chicken", top("Panda Express orange chicken"));
    }

    @Test
    public void cuisineAndFavoritesBreakTies() {
        // "spring rolls" matches the Chinese fried rolls and the Vietnamese fresh rolls equally by name
        // ("fresh" is a stop word), so the cuisine decides.
        List<FoodMatcher.Match> chinese = FoodMatcher.match("spring rolls", "Chinese", Collections.emptySet(), foods, 2);
        assertEquals("spring_roll_fried", chinese.get(0).item.id);
        List<FoodMatcher.Match> fav = FoodMatcher.match("spring rolls", null, Collections.singleton("Chinese"), foods, 2);
        assertEquals("spring_roll_fried", fav.get(0).item.id);
        List<FoodMatcher.Match> viet = FoodMatcher.match("spring rolls", "Vietnamese", Collections.emptySet(), foods, 2);
        assertEquals("fresh_spring_rolls", viet.get(0).item.id);
    }

    @Test
    public void cuisineNeverCreatesAMatch() {
        assertTrue(FoodMatcher.match("Not food", "Indian", Collections.singleton("Indian"), foods, 5).isEmpty());
        assertTrue(FoodMatcher.match("computer mouse", "American", Collections.emptySet(), foods, 5).isEmpty());
    }
}
