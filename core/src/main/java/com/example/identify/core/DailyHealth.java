package com.example.identify.core;

/** Today's totals from Health Connect. Unknown values: steps UNKNOWN_STEPS, energies NaN. */
public final class DailyHealth {

    public static final long UNKNOWN_STEPS = -1L;

    public final long steps;
    public final double activeKcal;
    public final double burnedKcal;
    public final double eatenKcal;

    public DailyHealth(long steps, double activeKcal, double burnedKcal, double eatenKcal) {
        this.steps = steps;
        this.activeKcal = activeKcal;
        this.burnedKcal = burnedKcal;
        this.eatenKcal = eatenKcal;
    }

    /** Steps still needed to reach the goal: 0 when reached, UNKNOWN_STEPS when steps or goal are unknown. */
    public long stepsRemaining(long goal) {
        if (steps < 0 || goal <= 0) return UNKNOWN_STEPS;
        return Math.max(0L, goal - steps);
    }

    /** Progress toward the goal from 0 to 100, or -1 when unknown. */
    public int goalPercent(long goal) {
        if (steps < 0 || goal <= 0) return -1;
        return (int) Math.min(100L, steps * 100L / goal);
    }

    /** Eaten minus burned in kcal, NaN unless both are known. Positive means more eaten than burned. */
    public double netKcal() {
        if (Double.isNaN(eatenKcal) || Double.isNaN(burnedKcal)) return Double.NaN;
        return eatenKcal - burnedKcal;
    }
}
