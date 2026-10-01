package com.example.identify.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Turns a flat list of meals into list rows: newest first, one header per local day with its total. */
public final class Diary {
    private Diary() {}

    public static final class Row {
        public final boolean header;
        public final LocalDate date;     // the day, for headers and meals
        public final double totalKcal;   // headers only
        public final int mealCount;      // headers only
        public final MealEntry meal;     // meal rows only

        private Row(boolean header, LocalDate date, double totalKcal, int mealCount, MealEntry meal) {
            this.header = header;
            this.date = date;
            this.totalKcal = totalKcal;
            this.mealCount = mealCount;
            this.meal = meal;
        }

        static Row header(LocalDate date, double totalKcal, int mealCount) {
            return new Row(true, date, totalKcal, mealCount, null);
        }

        static Row meal(LocalDate date, MealEntry meal) {
            return new Row(false, date, 0, 0, meal);
        }
    }

    /** Sum of known calories; NaN entries are skipped. */
    public static double totalKcal(List<MealEntry> meals) {
        double sum = 0;
        for (MealEntry m : meals) {
            if (!Double.isNaN(m.kcal)) sum += m.kcal;
        }
        return sum;
    }

    public static List<MealEntry> newestFirst(List<MealEntry> meals) {
        List<MealEntry> sorted = new ArrayList<>(meals);
        sorted.sort((a, b) -> Long.compare(b.startMillis, a.startMillis));
        return sorted;
    }

    /** Meal rows only, newest first (the Today list). */
    public static List<Row> mealRows(List<MealEntry> meals, ZoneId zone) {
        List<Row> rows = new ArrayList<>();
        for (MealEntry m : newestFirst(meals)) rows.add(Row.meal(day(m, zone), m));
        return rows;
    }

    /** Header plus meals for each local day, newest day first (the Diary list). */
    public static List<Row> rows(List<MealEntry> meals, ZoneId zone) {
        List<Row> rows = new ArrayList<>();
        List<MealEntry> sorted = newestFirst(meals);
        int i = 0;
        while (i < sorted.size()) {
            LocalDate d = day(sorted.get(i), zone);
            List<MealEntry> sameDay = new ArrayList<>();
            while (i < sorted.size() && day(sorted.get(i), zone).equals(d)) sameDay.add(sorted.get(i++));
            rows.add(Row.header(d, totalKcal(sameDay), sameDay.size()));
            for (MealEntry m : sameDay) rows.add(Row.meal(d, m));
        }
        return rows;
    }

    private static LocalDate day(MealEntry m, ZoneId zone) {
        return Instant.ofEpochMilli(m.startMillis).atZone(zone).toLocalDate();
    }
}
