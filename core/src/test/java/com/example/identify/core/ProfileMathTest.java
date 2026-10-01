package com.example.identify.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

public class ProfileMathTest {

    private static UserProfile p(int age, UserProfile.Sex sex, double cm, double kg, UserProfile.Activity a, UserProfile.Goal g) {
        return new UserProfile("A", age, sex, cm, kg, a, g, Collections.emptySet(), Collections.emptySet(),
                Collections.emptySet(), Collections.emptySet());
    }

    @Test
    public void restingEnergyMifflinStJeor() {
        // 10*80 + 6.25*180 - 5*30 + 5 = 1780
        assertEquals(1780, ProfileMath.restingKcal(p(30, UserProfile.Sex.MALE, 180, 80, UserProfile.Activity.LIGHT, UserProfile.Goal.TRACK)), 1e-9);
        // 10*60 + 6.25*165 - 5*25 - 161 = 1345.25
        assertEquals(1345.25, ProfileMath.restingKcal(p(25, UserProfile.Sex.FEMALE, 165, 60, UserProfile.Activity.LIGHT, UserProfile.Goal.TRACK)), 1e-9);
        // midpoint constant -78
        assertEquals(1428.25, ProfileMath.restingKcal(p(25, UserProfile.Sex.UNSPECIFIED, 165, 60, UserProfile.Activity.LIGHT, UserProfile.Goal.TRACK)), 1e-9);
    }

    @Test
    public void budgetsByGoal() {
        // 1780 * 1.55 = 2759 -> lose -500 = 2259 -> 2250
        assertEquals(2250, ProfileMath.calorieBudget(p(30, UserProfile.Sex.MALE, 180, 80, UserProfile.Activity.MODERATE, UserProfile.Goal.LOSE)));
        // 2759 -> maintain -> 2750
        assertEquals(2750, ProfileMath.calorieBudget(p(30, UserProfile.Sex.MALE, 180, 80, UserProfile.Activity.MODERATE, UserProfile.Goal.MAINTAIN)));
        // 2759 + 300 = 3059 -> 3050
        assertEquals(3050, ProfileMath.calorieBudget(p(30, UserProfile.Sex.MALE, 180, 80, UserProfile.Activity.MODERATE, UserProfile.Goal.GAIN)));
        // 1345.25 * 1.375 = 1849.7 -> 1850
        assertEquals(1850, ProfileMath.calorieBudget(p(25, UserProfile.Sex.FEMALE, 165, 60, UserProfile.Activity.LIGHT, UserProfile.Goal.EAT_HEALTHIER)));
    }

    @Test
    public void floorAndDefault() {
        // 10*45 + 6.25*150 - 5*70 - 161 = 876.5; *1.2 = 1051.8; -500 -> floor 1200
        assertEquals(1200, ProfileMath.calorieBudget(p(70, UserProfile.Sex.FEMALE, 150, 45, UserProfile.Activity.SEDENTARY, UserProfile.Goal.LOSE)));
        assertEquals(ProfileMath.DEFAULT_BUDGET, ProfileMath.calorieBudget(p(0, UserProfile.Sex.MALE, 180, 80, UserProfile.Activity.LIGHT, UserProfile.Goal.LOSE)));
    }

    @Test
    public void stepGoals() {
        assertEquals(6000, ProfileMath.stepGoal(UserProfile.Activity.SEDENTARY));
        assertEquals(8000, ProfileMath.stepGoal(UserProfile.Activity.LIGHT));
        assertEquals(10000, ProfileMath.stepGoal(UserProfile.Activity.MODERATE));
        assertEquals(12000, ProfileMath.stepGoal(UserProfile.Activity.ACTIVE));
    }

    @Test
    public void unitsAndValidation() {
        assertEquals(81.65, ProfileMath.lbToKg(180), 0.01);
        assertEquals(180, ProfileMath.kgToLb(ProfileMath.lbToKg(180)), 1e-9);
        assertEquals(177.8, ProfileMath.feetInchesToCm(5, 10), 1e-9);
        assertArrayEquals(new int[]{5, 10}, ProfileMath.cmToFeetInches(177.8));
        assertArrayEquals(new int[]{6, 0}, ProfileMath.cmToFeetInches(182.6));
        assertTrue(ProfileMath.validAge(28));
        assertFalse(ProfileMath.validAge(8));
        assertTrue(ProfileMath.validHeightCm(170));
        assertFalse(ProfileMath.validHeightCm(80));
        assertTrue(ProfileMath.validWeightKg(70));
        assertFalse(ProfileMath.validWeightKg(500));
    }

    @Test
    public void profileDefaults() {
        UserProfile u = new UserProfile(null, 0, null, 0, 0, null, null, null, null, null, null);
        assertEquals("", u.name);
        assertEquals(UserProfile.Sex.UNSPECIFIED, u.sex);
        assertEquals(UserProfile.Activity.LIGHT, u.activity);
        assertEquals(UserProfile.Goal.TRACK, u.goal);
        assertTrue(u.cuisines.isEmpty());
        assertFalse(u.hasBody());
    }
}
