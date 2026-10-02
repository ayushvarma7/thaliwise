package com.example.identify.core;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Parses assets/food_tags.txt: one "id;tag|tag" line per table food, "id;-" for none, # comments. */
public final class FoodTags {
    private FoodTags() {}

    public static final String NONE = "-";

    /** Food id to its tags (iterating in FoodTag order). Throws IllegalArgumentException naming the line. */
    public static Map<String, Set<FoodTag>> parse(String text) {
        Map<String, Set<FoodTag>> out = new LinkedHashMap<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split(";", -1);
            if (parts.length != 2 || parts[0].trim().isEmpty()) {
                throw new IllegalArgumentException("line " + (i + 1) + ": expected id;tags");
            }
            String id = parts[0].trim();
            if (out.containsKey(id)) {
                throw new IllegalArgumentException("line " + (i + 1) + ": duplicate id " + id);
            }
            Set<FoodTag> tags = EnumSet.noneOf(FoodTag.class);
            String value = parts[1].trim();
            if (!value.equals(NONE)) {
                for (String key : value.split("\\|", -1)) {
                    FoodTag tag = FoodTag.fromKey(key.trim());
                    if (tag == null) {
                        throw new IllegalArgumentException("line " + (i + 1) + ": unknown tag '" + key + "'");
                    }
                    tags.add(tag);
                }
            }
            out.put(id, Collections.unmodifiableSet(tags));
        }
        return out;
    }
}
