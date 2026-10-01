package com.example.identify.core;

import static org.junit.Assert.assertEquals;

import java.util.Collections;

import org.junit.Test;

public class MealsTest {

    @Test
    public void slotsByHour() {
        assertEquals(Meals.Slot.BREAKFAST, Meals.slotForHour(7));
        assertEquals(Meals.Slot.LUNCH, Meals.slotForHour(12));
        assertEquals(Meals.Slot.DINNER, Meals.slotForHour(19));
        assertEquals(Meals.Slot.SNACK, Meals.slotForHour(16));
        assertEquals(Meals.Slot.SNACK, Meals.slotForHour(23));
        assertEquals(Meals.Slot.SNACK, Meals.slotForHour(3));
    }

    @Test
    public void portionsAndScaling() {
        FoodItem f = new FoodItem("x", "X", "", Collections.singletonList("x"), "sandwich", 165, 450, 25, 34, 24, "src");
        assertEquals(1.5, Meals.portionFromSlider(3), 0);
        assertEquals(675, Meals.scaledKcal(f, 1.5));
        assertEquals(37.5, Meals.scaled(25, 1.5), 1e-9);
        assertEquals(12.5, Meals.scaled(25, 0.5), 1e-9);
    }

    @Test
    public void portionText() {
        assertEquals("1", Meals.formatPortion(1.0));
        assertEquals("1.5", Meals.formatPortion(1.5));
        assertEquals("0.5", Meals.formatPortion(0.5));
        assertEquals("3", Meals.formatPortion(3.0));
    }
}
