package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class DiaryTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");

    private static long at(int day, int hour) {
        return ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, NY).toInstant().toEpochMilli();
    }

    private static MealEntry m(String id, double kcal, long t) {
        return new MealEntry(id, id, kcal, t, Meals.Slot.LUNCH, true);
    }

    @Test
    public void groupsByLocalDayNewestFirst() {
        List<MealEntry> meals = Arrays.asList(
                m("breakfast1", 300, at(1, 8)),
                m("dinner2", 700, at(2, 19)),
                m("lunch1", 500, at(1, 12)),
                m("late1", 200, at(1, 23)));
        List<Diary.Row> rows = Diary.rows(meals, NY);
        assertEquals(6, rows.size());
        assertTrue(rows.get(0).header);
        assertEquals(LocalDate.of(2026, 10, 2), rows.get(0).date);
        assertEquals(700, rows.get(0).totalKcal, 0);
        assertEquals(1, rows.get(0).mealCount);
        assertEquals("dinner2", rows.get(1).meal.id);
        assertTrue(rows.get(2).header);
        assertEquals(1000, rows.get(2).totalKcal, 0);
        assertEquals(3, rows.get(2).mealCount);
        assertEquals("late1", rows.get(3).meal.id);
        assertEquals("lunch1", rows.get(4).meal.id);
        assertEquals("breakfast1", rows.get(5).meal.id);
    }

    @Test
    public void unknownCaloriesAreSkippedInTotals() {
        List<MealEntry> meals = Arrays.asList(m("a", Double.NaN, at(1, 8)), m("b", 250, at(1, 9)));
        assertEquals(250, Diary.totalKcal(meals), 0);
        assertEquals(250, Diary.rows(meals, NY).get(0).totalKcal, 0);
    }

    @Test
    public void mealRowsHaveNoHeaders() {
        List<Diary.Row> rows = Diary.mealRows(Arrays.asList(m("a", 1, at(1, 8)), m("b", 2, at(1, 9))), NY);
        assertEquals(2, rows.size());
        assertFalse(rows.get(0).header);
        assertEquals("b", rows.get(0).meal.id);
        assertTrue(Diary.rows(Arrays.asList(), NY).isEmpty());
    }
}
