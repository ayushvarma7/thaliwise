package com.example.identify.core;

import java.util.Locale;

/**
 * What a typical serving of a table food usually contains or is (assets/food_tags.txt). "Usually", not
 * "always": the table describes common restaurant and home recipes, not the plate in the photo. The
 * declaration order is the order in which diet conflicts are reported.
 */
public enum FoodTag {
    MEAT, PORK, FISH, SHELLFISH, EGG, DAIRY, GLUTEN, ALCOHOL, FRIED, SWEET, VEG;

    /** The lowercase key used in food_tags.txt. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The tag for a file key, or null when the key is unknown. */
    public static FoodTag fromKey(String key) {
        for (FoodTag t : values()) {
            if (t.key().equals(key)) return t;
        }
        return null;
    }
}
