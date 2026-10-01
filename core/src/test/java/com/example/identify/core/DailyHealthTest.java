package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DailyHealthTest {

    @Test
    public void remainingAndPercent() {
        DailyHealth h = new DailyHealth(3200, 150, 1800, 2400);
        assertEquals(6800, h.stepsRemaining(10_000));
        assertEquals(32, h.goalPercent(10_000));
    }

    @Test
    public void goalReachedClampsToZeroAndHundred() {
        DailyHealth h = new DailyHealth(12_500, 300, 2200, 1900);
        assertEquals(0, h.stepsRemaining(10_000));
        assertEquals(100, h.goalPercent(10_000));
    }

    @Test
    public void unknownStepsOrGoal() {
        DailyHealth h = new DailyHealth(DailyHealth.UNKNOWN_STEPS, Double.NaN, Double.NaN, Double.NaN);
        assertEquals(DailyHealth.UNKNOWN_STEPS, h.stepsRemaining(10_000));
        assertEquals(-1, h.goalPercent(10_000));
        DailyHealth known = new DailyHealth(500, 0, 0, 0);
        assertEquals(DailyHealth.UNKNOWN_STEPS, known.stepsRemaining(0));
        assertEquals(-1, known.goalPercent(0));
    }

    @Test
    public void netKcal() {
        assertEquals(600.0, new DailyHealth(0, 0, 1800, 2400).netKcal(), 1e-9);
        assertTrue(Double.isNaN(new DailyHealth(0, 0, Double.NaN, 2400).netKcal()));
        assertTrue(Double.isNaN(new DailyHealth(0, 0, 1800, Double.NaN).netKcal()));
    }
}
