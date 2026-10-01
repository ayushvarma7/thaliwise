package com.example.identify.core;

import java.util.Collections;
import java.util.List;

/** One row of the nutrition table. All amounts are for one serving. */
public final class FoodItem {
    public final String id;
    public final String name;
    public final String brand;           // empty for generic foods
    public final List<String> aliases;   // names a model or a user may use for this food
    public final String serving;         // for example "sandwich" or "cup cooked"
    public final double servingGrams;
    public final double kcal;
    public final double proteinG;
    public final double carbsG;
    public final double fatG;
    public final String source;

    public FoodItem(String id, String name, String brand, List<String> aliases, String serving,
                    double servingGrams, double kcal, double proteinG, double carbsG, double fatG, String source) {
        this.id = id;
        this.name = name;
        this.brand = brand;
        this.aliases = Collections.unmodifiableList(aliases);
        this.serving = serving;
        this.servingGrams = servingGrams;
        this.kcal = kcal;
        this.proteinG = proteinG;
        this.carbsG = carbsG;
        this.fatG = fatG;
        this.source = source;
    }

    /** "Big Mac (McDonald's)" for branded foods, the plain name otherwise. */
    public String displayName() {
        return brand.isEmpty() ? name : name + " (" + brand + ")";
    }
}
