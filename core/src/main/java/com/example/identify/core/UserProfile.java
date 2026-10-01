package com.example.identify.core;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** What the user told the app during onboarding. Body values are metric; 0 means not given. */
public final class UserProfile {

    public enum Sex { FEMALE, MALE, UNSPECIFIED }

    public enum Activity { SEDENTARY, LIGHT, MODERATE, ACTIVE }

    public enum Goal { LOSE, MAINTAIN, GAIN, EAT_HEALTHIER, TRACK }

    public final String name;
    public final int ageYears;
    public final Sex sex;
    public final double heightCm;
    public final double weightKg;
    public final Activity activity;
    public final Goal goal;
    public final Set<String> reasons;
    public final Set<String> cuisines;
    public final Set<String> diet;
    public final Set<String> eatMore;

    public UserProfile(String name, int ageYears, Sex sex, double heightCm, double weightKg, Activity activity,
                       Goal goal, Set<String> reasons, Set<String> cuisines, Set<String> diet, Set<String> eatMore) {
        this.name = name == null ? "" : name.trim();
        this.ageYears = ageYears;
        this.sex = sex == null ? Sex.UNSPECIFIED : sex;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.activity = activity == null ? Activity.LIGHT : activity;
        this.goal = goal == null ? Goal.TRACK : goal;
        this.reasons = copy(reasons);
        this.cuisines = copy(cuisines);
        this.diet = copy(diet);
        this.eatMore = copy(eatMore);
    }

    /** Age, height, and weight all given, so a personal budget can be computed. */
    public boolean hasBody() {
        return ageYears > 0 && heightCm > 0 && weightKg > 0;
    }

    private static Set<String> copy(Set<String> s) {
        return s == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(s));
    }
}
