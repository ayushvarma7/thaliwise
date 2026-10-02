package com.example.identify.core;

/** Walking numbers for coach tips: moderate walking at 3.5 MET and about 100 steps a minute. Estimates. */
public final class WalkMath {
    private WalkMath() {}

    public static final double WALK_MET = 3.5;
    public static final int STEPS_PER_MINUTE = 100;
    public static final double DEFAULT_WEIGHT_KG = 70;

    /** ACSM: kcal per minute = MET x 3.5 x kg / 200. A weight of 0 (not given) uses 70 kg. */
    public static double kcalPerMinute(double weightKg) {
        double kg = weightKg > 0 ? weightKg : DEFAULT_WEIGHT_KG;
        return WALK_MET * 3.5 * kg / 200.0;
    }

    public static long kcalForMinutes(long minutes, double weightKg) {
        return Math.round(minutes * kcalPerMinute(weightKg));
    }

    /** Minutes to walk that many steps, rounded up to a multiple of 5 (at least 5); 0 for no steps. */
    public static long minutesForSteps(long steps) {
        if (steps <= 0) return 0;
        long minutes = (steps + STEPS_PER_MINUTE - 1) / STEPS_PER_MINUTE;
        return Math.max(5, (minutes + 4) / 5 * 5);
    }
}
