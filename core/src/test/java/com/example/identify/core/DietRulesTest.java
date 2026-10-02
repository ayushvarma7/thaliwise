package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class DietRulesTest {

    private static Set<String> diets(String... d) {
        return new HashSet<>(Arrays.asList(d));
    }

    @Test
    public void vegetarianAvoidsMeatFirst() {
        List<DietRules.Conflict> c = DietRules.conflicts(diets(DietRules.VEGETARIAN),
                EnumSet.of(FoodTag.MEAT, FoodTag.PORK, FoodTag.DAIRY));
        assertEquals(1, c.size());
        assertEquals(DietRules.VEGETARIAN, c.get(0).diet);
        assertEquals(FoodTag.MEAT, c.get(0).tag);
    }

    @Test
    public void veganAvoidsDairyAndEgg() {
        assertEquals(FoodTag.EGG, DietRules.conflicts(diets(DietRules.VEGAN),
                EnumSet.of(FoodTag.EGG, FoodTag.DAIRY, FoodTag.GLUTEN)).get(0).tag);
        assertTrue(DietRules.conflicts(diets(DietRules.VEGAN), EnumSet.of(FoodTag.GLUTEN, FoodTag.VEG)).isEmpty());
    }

    @Test
    public void pescatarianHalalKosherGlutenDairy() {
        assertTrue(DietRules.conflicts(diets(DietRules.PESCATARIAN), EnumSet.of(FoodTag.FISH)).isEmpty());
        assertEquals(FoodTag.PORK, DietRules.conflicts(diets(DietRules.HALAL),
                EnumSet.of(FoodTag.MEAT, FoodTag.PORK)).get(0).tag);
        assertEquals(FoodTag.ALCOHOL, DietRules.conflicts(diets(DietRules.HALAL),
                EnumSet.of(FoodTag.GLUTEN, FoodTag.ALCOHOL)).get(0).tag);
        assertEquals(FoodTag.SHELLFISH, DietRules.conflicts(diets(DietRules.KOSHER),
                EnumSet.of(FoodTag.SHELLFISH, FoodTag.DAIRY)).get(0).tag);
        assertEquals(FoodTag.GLUTEN, DietRules.conflicts(diets(DietRules.GLUTEN_FREE),
                EnumSet.of(FoodTag.GLUTEN)).get(0).tag);
        assertEquals(FoodTag.DAIRY, DietRules.conflicts(diets(DietRules.DAIRY_FREE),
                EnumSet.of(FoodTag.DAIRY)).get(0).tag);
    }

    @Test
    public void noRestrictionsAndUnknownLabelsAvoidNothing() {
        assertTrue(DietRules.conflicts(diets("No restrictions"), EnumSet.allOf(FoodTag.class)).isEmpty());
        assertTrue(DietRules.conflicts(diets("Paleo"), EnumSet.allOf(FoodTag.class)).isEmpty());
        assertTrue(DietRules.conflicts(Collections.emptySet(), EnumSet.allOf(FoodTag.class)).isEmpty());
    }

    @Test
    public void sameTagIsReportedOnce() {
        List<DietRules.Conflict> c = DietRules.conflicts(diets(DietRules.VEGETARIAN, DietRules.VEGAN),
                EnumSet.of(FoodTag.MEAT));
        assertEquals(1, c.size());
        assertEquals(DietRules.VEGETARIAN, c.get(0).diet);
    }

    @Test
    public void severalDietsInOnboardingOrder() {
        List<DietRules.Conflict> c = DietRules.conflicts(diets(DietRules.GLUTEN_FREE, DietRules.VEGAN),
                EnumSet.of(FoodTag.DAIRY, FoodTag.GLUTEN));
        assertEquals(2, c.size());
        assertEquals(DietRules.VEGAN, c.get(0).diet);
        assertEquals(FoodTag.DAIRY, c.get(0).tag);
        assertEquals(DietRules.GLUTEN_FREE, c.get(1).diet);
    }

    @Test
    public void labelsMatchTheOnboardingChips() throws IOException {
        String arrays = new String(Files.readAllBytes(new File("../app/src/main/res/values/arrays.xml").toPath()),
                StandardCharsets.UTF_8);
        for (String d : DietRules.DIETS) {
            assertTrue(d, arrays.contains("<item>" + d + "</item>"));
        }
    }
}
