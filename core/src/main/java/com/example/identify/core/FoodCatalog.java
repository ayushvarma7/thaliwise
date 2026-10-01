package com.example.identify.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses the nutrition table shipped in app/src/main/assets/foods.txt.
 * One food per line, 12 fields separated by ';':
 * id;name;brand;cuisine;aliases;serving;grams;kcal;protein_g;carbs_g;fat_g;source
 * Aliases are separated by '|'. Blank lines and lines starting with '#' are skipped.
 */
public final class FoodCatalog {
    private FoodCatalog() {}

    public static final int FIELDS = 12;

    public static List<FoodItem> parse(String text) {
        List<FoodItem> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
            int n = i + 1;
            String[] f = line.split(";", -1);
            if (f.length != FIELDS) {
                throw new IllegalArgumentException("line " + n + ": expected " + FIELDS + " fields, found " + f.length);
            }
            String id = f[0].trim();
            if (id.isEmpty() || !ids.add(id)) {
                throw new IllegalArgumentException("line " + n + ": empty or duplicate id '" + id + "'");
            }
            String name = f[1].trim();
            if (name.isEmpty()) throw new IllegalArgumentException("line " + n + ": empty name");
            List<String> aliases = new ArrayList<>();
            for (String a : f[4].split("\\|")) {
                if (!a.trim().isEmpty()) aliases.add(a.trim());
            }
            out.add(new FoodItem(id, name, f[2].trim(), f[3].trim(), aliases, f[5].trim(),
                    number(f[6], n), number(f[7], n), number(f[8], n), number(f[9], n), number(f[10], n),
                    f[11].trim()));
        }
        return out;
    }

    private static double number(String s, int line) {
        double v;
        try {
            v = Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("line " + line + ": not a number '" + s + "'");
        }
        if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) {
            throw new IllegalArgumentException("line " + line + ": number out of range '" + s + "'");
        }
        return v;
    }
}
