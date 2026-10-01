package com.example.identify.core;

/** Daily calorie budget and step goal from a profile, plus unit conversions for the onboarding form. */
public final class ProfileMath {
    private ProfileMath() {}

    public static final long DEFAULT_BUDGET = 2000;
    public static final int MIN_AGE = 13;
    public static final int MAX_AGE = 100;
    public static final double MIN_HEIGHT_CM = 120;
    public static final double MAX_HEIGHT_CM = 230;
    public static final double MIN_WEIGHT_KG = 30;
    public static final double MAX_WEIGHT_KG = 300;

    /** Mifflin-St Jeor resting energy in kcal per day. "Prefer not to say" uses the midpoint of the two constants. */
    public static double restingKcal(UserProfile p) {
        double s;
        switch (p.sex) {
            case MALE: s = 5; break;
            case FEMALE: s = -161; break;
            default: s = -78; break;
        }
        return 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.ageYears + s;
    }

    public static double activityFactor(UserProfile.Activity a) {
        switch (a) {
            case SEDENTARY: return 1.2;
            case LIGHT: return 1.375;
            case MODERATE: return 1.55;
            default: return 1.725;
        }
    }

    /** Resting energy times activity, then the goal adjustment, a safety floor, and rounding to 50 kcal. */
    public static long calorieBudget(UserProfile p) {
        if (!p.hasBody()) return DEFAULT_BUDGET;
        double kcal = restingKcal(p) * activityFactor(p.activity);
        if (p.goal == UserProfile.Goal.LOSE) kcal -= 500;
        if (p.goal == UserProfile.Goal.GAIN) kcal += 300;
        double floor = p.sex == UserProfile.Sex.MALE ? 1500 : 1200;
        kcal = Math.max(kcal, floor);
        return Math.round(kcal / 50.0) * 50;
    }

    public static long stepGoal(UserProfile.Activity a) {
        switch (a) {
            case SEDENTARY: return 6000;
            case LIGHT: return 8000;
            case MODERATE: return 10000;
            default: return 12000;
        }
    }

    public static double lbToKg(double lb) {
        return lb * 0.45359237;
    }

    public static double kgToLb(double kg) {
        return kg / 0.45359237;
    }

    public static double feetInchesToCm(int feet, double inches) {
        return (feet * 12 + inches) * 2.54;
    }

    /** {feet, inches} with inches rounded to a whole number (5 ft 12 in becomes 6 ft 0 in). */
    public static int[] cmToFeetInches(double cm) {
        int totalInches = (int) Math.round(cm / 2.54);
        return new int[]{totalInches / 12, totalInches % 12};
    }

    public static boolean validAge(int age) {
        return age >= MIN_AGE && age <= MAX_AGE;
    }

    public static boolean validHeightCm(double cm) {
        return cm >= MIN_HEIGHT_CM && cm <= MAX_HEIGHT_CM;
    }

    public static boolean validWeightKg(double kg) {
        return kg >= MIN_WEIGHT_KG && kg <= MAX_WEIGHT_KG;
    }
}
