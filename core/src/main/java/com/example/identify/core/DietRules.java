package com.example.identify.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Which food tags each onboarding diet avoids. The labels are exactly the items of the onboarding_diet
 * array in app/src/main/res/values/arrays.xml (a test checks this). Kosher covers pork and shellfish only,
 * not meat with dairy or certification; Halal covers pork and alcohol only.
 */
public final class DietRules {
    private DietRules() {}

    public static final String VEGETARIAN = "Vegetarian";
    public static final String VEGAN = "Vegan";
    public static final String PESCATARIAN = "Pescatarian";
    public static final String HALAL = "Halal";
    public static final String KOSHER = "Kosher";
    public static final String GLUTEN_FREE = "Gluten-free";
    public static final String DAIRY_FREE = "Dairy-free";

    /** The diets that avoid something, in the order onboarding shows them. "No restrictions" avoids nothing. */
    public static final List<String> DIETS = Collections.unmodifiableList(
            Arrays.asList(VEGETARIAN, VEGAN, PESCATARIAN, HALAL, KOSHER, GLUTEN_FREE, DAIRY_FREE));

    /** One reported conflict: the diet and the first avoided tag the food has. */
    public static final class Conflict {
        public final String diet;
        public final FoodTag tag;

        Conflict(String diet, FoodTag tag) {
            this.diet = diet;
            this.tag = tag;
        }
    }

    /** The avoided tags, iterating in FoodTag order; empty for unknown labels and "No restrictions". */
    public static Set<FoodTag> avoided(String diet) {
        switch (diet) {
            case VEGETARIAN: return EnumSet.of(FoodTag.MEAT, FoodTag.PORK, FoodTag.FISH, FoodTag.SHELLFISH);
            case VEGAN: return EnumSet.of(FoodTag.MEAT, FoodTag.PORK, FoodTag.FISH, FoodTag.SHELLFISH,
                    FoodTag.EGG, FoodTag.DAIRY);
            case PESCATARIAN: return EnumSet.of(FoodTag.MEAT, FoodTag.PORK);
            case HALAL: return EnumSet.of(FoodTag.PORK, FoodTag.ALCOHOL);
            case KOSHER: return EnumSet.of(FoodTag.PORK, FoodTag.SHELLFISH);
            case GLUTEN_FREE: return EnumSet.of(FoodTag.GLUTEN);
            case DAIRY_FREE: return EnumSet.of(FoodTag.DAIRY);
            default: return EnumSet.noneOf(FoodTag.class);
        }
    }

    /**
     * At most one conflict per chosen diet (its first avoided tag in FoodTag order), in DIETS order. A tag
     * already reported for an earlier diet is not repeated, so Vegetarian plus Vegan gives one "meat".
     */
    public static List<Conflict> conflicts(Set<String> diets, Set<FoodTag> tags) {
        List<Conflict> out = new ArrayList<>();
        Set<FoodTag> reported = EnumSet.noneOf(FoodTag.class);
        for (String diet : DIETS) {
            if (!diets.contains(diet)) continue;
            for (FoodTag t : avoided(diet)) {
                if (tags.contains(t)) {
                    if (reported.add(t)) out.add(new Conflict(diet, t));
                    break;
                }
            }
        }
        return out;
    }
}
