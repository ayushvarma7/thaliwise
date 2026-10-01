package com.example.identify.core;

/** One meal from Health Connect, as shown in the Today and Diary lists. */
public final class MealEntry {
    public final String id;          // Health Connect record id
    public final String name;        // may be empty when the writing app gave no name
    public final double kcal;        // NaN when the record has no energy
    public final long startMillis;
    public final Meals.Slot slot;    // null when the record has no meal type
    public final boolean mine;       // written by this app; only these can be deleted here

    public MealEntry(String id, String name, double kcal, long startMillis, Meals.Slot slot, boolean mine) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.kcal = kcal;
        this.startMillis = startMillis;
        this.slot = slot;
        this.mine = mine;
    }
}
