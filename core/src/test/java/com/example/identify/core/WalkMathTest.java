package com.example.identify.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WalkMathTest {

    @Test
    public void kcalPerMinuteFromWeight() {
        // 3.5 * 3.5 * 70 / 200 = 4.2875
        assertEquals(4.2875, WalkMath.kcalPerMinute(70), 1e-9);
        assertEquals(4.2875, WalkMath.kcalPerMinute(0), 1e-9);
        // 3.5 * 3.5 * 77 / 200 = 4.71625
        assertEquals(4.71625, WalkMath.kcalPerMinute(77), 1e-9);
    }

    @Test
    public void kcalForMinutesRounds() {
        assertEquals(141, WalkMath.kcalForMinutes(30, 77));   // 141.49
        assertEquals(129, WalkMath.kcalForMinutes(30, 0));    // 128.6
    }

    @Test
    public void minutesRoundUpToFive() {
        assertEquals(0, WalkMath.minutesForSteps(0));
        assertEquals(5, WalkMath.minutesForSteps(1));
        assertEquals(5, WalkMath.minutesForSteps(500));
        assertEquals(10, WalkMath.minutesForSteps(501));
        assertEquals(70, WalkMath.minutesForSteps(6800));
        assertEquals(30, WalkMath.minutesForSteps(3000));
    }
}
