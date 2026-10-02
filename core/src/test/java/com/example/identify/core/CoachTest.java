package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class CoachTest {

    private static Set<String> set(String... s) {
        return new HashSet<>(Arrays.asList(s));
    }

    private static UserProfile profile(double kg, UserProfile.Goal goal, Set<String> diet, Set<String> eatMore) {
        return new UserProfile("Ayush", 28, UserProfile.Sex.MALE, 178, kg, UserProfile.Activity.LIGHT, goal,
                Collections.emptySet(), Collections.emptySet(), diet, eatMore);
    }

    private static UserProfile plain() {
        return profile(77, UserProfile.Goal.LOSE, Collections.emptySet(), Collections.emptySet());
    }

    private static final Coach.Meal DOUBLE_CHEESEBURGER =
            new Coach.Meal(450, 25, EnumSet.of(FoodTag.MEAT, FoodTag.DAIRY, FoodTag.GLUTEN));

    @Test
    public void ayushScenarioGivesOneWalkTip() {
        List<Tip> tips = Coach.forMeal(plain(), 2000, 10000, new Coach.Day(3200, 1500, 13), DOUBLE_CHEESEBURGER);
        assertEquals(1, tips.size());
        assertEquals("walk[3200, 10000, 450, 30, 3000, 141]", tips.get(0).toString());
    }

    @Test
    public void overBudgetAddsWalkThenBudgetTip() {
        List<Tip> tips = Coach.forMeal(plain(), 2000, 10000, new Coach.Day(9000, 1900, 13), DOUBLE_CHEESEBURGER);
        assertEquals(2, tips.size());
        // 1,000 steps to go is 10 minutes
        assertEquals("walk[9000, 10000, 450, 10, 1000, 47]", tips.get(0).toString());
        assertEquals("over_budget[350, 2000]", tips.get(1).toString());
    }

    @Test
    public void smallMealUnderBudgetSaysNothing() {
        Coach.Meal apple = new Coach.Meal(95, 0.5, EnumSet.noneOf(FoodTag.class));
        assertTrue(Coach.forMeal(plain(), 2000, 10000, new Coach.Day(3200, 500, 13), apple).isEmpty());
    }

    @Test
    public void stepGoalReachedMeansNoWalk() {
        List<Tip> tips = Coach.forMeal(plain(), 2000, 10000, new Coach.Day(12000, 1900, 13), DOUBLE_CHEESEBURGER);
        assertEquals(1, tips.size());
        assertEquals(Tip.Kind.OVER_BUDGET, tips.get(0).kind);
    }

    @Test
    public void unknownHealthSkipsNumberRules() {
        List<Tip> tips = Coach.forMeal(plain(), 2000, 10000, new Coach.Day(-1, Double.NaN, 13), DOUBLE_CHEESEBURGER);
        assertTrue(tips.isEmpty());
    }

    @Test
    public void dietConflictComesFirst() {
        UserProfile veg = profile(60, UserProfile.Goal.LOSE, set(DietRules.VEGETARIAN), Collections.emptySet());
        List<Tip> tips = Coach.forMeal(veg, 2000, 10000, new Coach.Day(3200, 1500, 13), DOUBLE_CHEESEBURGER);
        assertEquals(2, tips.size());
        assertEquals("diet_conflict[Vegetarian, meat]", tips.get(0).toString());
        assertEquals(Tip.Kind.WALK, tips.get(1).kind);
    }

    @Test
    public void eatMoreFeedback() {
        UserProfile p = profile(77, UserProfile.Goal.EAT_HEALTHIER, Collections.emptySet(),
                set(Coach.MORE_PROTEIN, Coach.FEWER_FRIED, Coach.LESS_SUGAR, Coach.MORE_VEGETABLES));
        Coach.Meal friedChicken = new Coach.Meal(380, 30, EnumSet.of(FoodTag.MEAT, FoodTag.FRIED));
        List<Tip> tips = Coach.forMeal(p, 2000, 10000, new Coach.Day(-1, Double.NaN, 13), friedChicken);
        assertEquals(2, tips.size());
        assertEquals("protein_good[30]", tips.get(0).toString());
        assertEquals(Tip.Kind.FRIED_NOTE, tips.get(1).kind);

        Coach.Meal salad = new Coach.Meal(150, 3, EnumSet.of(FoodTag.VEG));
        assertEquals(Tip.Kind.VEGGIE_GOOD,
                Coach.forMeal(p, 2000, 10000, new Coach.Day(-1, Double.NaN, 13), salad).get(0).kind);

        Coach.Meal jalebi = new Coach.Meal(300, 2, EnumSet.of(FoodTag.GLUTEN, FoodTag.FRIED, FoodTag.SWEET));
        List<Tip> sweet = Coach.forMeal(p, 2000, 10000, new Coach.Day(-1, Double.NaN, 13), jalebi);
        assertEquals(Tip.Kind.FRIED_NOTE, sweet.get(0).kind);
        assertEquals(Tip.Kind.SWEET_NOTE, sweet.get(1).kind);
    }

    @Test
    public void smallerPortionsSuggestsHalf() {
        UserProfile p = profile(77, UserProfile.Goal.LOSE, Collections.emptySet(), set(Coach.SMALLER_PORTIONS));
        Coach.Meal burrito = new Coach.Meal(1050, 56, EnumSet.of(FoodTag.MEAT));
        List<Tip> tips = Coach.forMeal(p, 2000, 10000, new Coach.Day(-1, Double.NaN, 13), burrito);
        assertEquals("portion_note[525]", tips.get(0).toString());
    }

    @Test
    public void buildMuscleEveningShowsBudgetLeft() {
        UserProfile diego = profile(80, UserProfile.Goal.GAIN, Collections.emptySet(), Collections.emptySet());
        Coach.Meal bowl = new Coach.Meal(300, 40, EnumSet.of(FoodTag.MEAT));
        List<Tip> tips = Coach.forMeal(diego, 3000, 10000, new Coach.Day(11000, 1800, 19), bowl);
        assertEquals(1, tips.size());
        assertEquals("budget_left[900]", tips.get(0).toString());
        assertTrue(Coach.forMeal(diego, 3000, 10000, new Coach.Day(11000, 1800, 12), bowl).isEmpty());
    }

    @Test
    public void atMostThreeTips() {
        UserProfile p = profile(77, UserProfile.Goal.LOSE, set(DietRules.VEGAN, DietRules.GLUTEN_FREE),
                set(Coach.MORE_PROTEIN));
        List<Tip> tips = Coach.forMeal(p, 2000, 10000, new Coach.Day(3200, 1900, 13), DOUBLE_CHEESEBURGER);
        assertEquals(Coach.MAX_MEAL_TIPS, tips.size());
        assertEquals("diet_conflict[Vegan, meat]", tips.get(0).toString());
        assertEquals("diet_conflict[Gluten-free, gluten]", tips.get(1).toString());
        assertEquals(Tip.Kind.WALK, tips.get(2).kind);
    }

    @Test
    public void dayTips() {
        UserProfile p = plain();
        assertEquals("day_over_budget[300, 30, 141]",
                Coach.forDay(p, 2000, 10000, new Coach.Day(4000, 2300, 20)).toString());
        assertEquals("day_goal_reached[10500]",
                Coach.forDay(p, 2000, 10000, new Coach.Day(10500, 1200, 20)).toString());
        assertEquals("day_steps_left[3000, 30]",
                Coach.forDay(p, 2000, 10000, new Coach.Day(7000, 1200, 18)).toString());
        assertEquals("day_on_track[800]",
                Coach.forDay(p, 2000, 10000, new Coach.Day(7000, 1200, 11)).toString());
        assertNull(Coach.forDay(p, 2000, 10000, new Coach.Day(-1, Double.NaN, 18)));
        assertNull(Coach.forDay(p, 2000, 10000, new Coach.Day(500, 0, 9)));
    }

    @Test
    public void eatMoreLabelsMatchTheOnboardingChips() throws IOException {
        String arrays = new String(Files.readAllBytes(new File("../app/src/main/res/values/arrays.xml").toPath()),
                StandardCharsets.UTF_8);
        for (String label : Arrays.asList(Coach.MORE_PROTEIN, Coach.MORE_VEGETABLES, Coach.LESS_SUGAR,
                Coach.FEWER_FRIED, Coach.SMALLER_PORTIONS)) {
            assertTrue(label, arrays.contains("<item>" + label + "</item>"));
        }
    }
}
