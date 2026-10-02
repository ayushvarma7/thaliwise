package com.example.identify.ui;

import android.content.Context;

import com.example.identify.R;
import com.example.identify.core.FoodTag;
import com.example.identify.core.Tip;

import java.util.ArrayList;
import java.util.List;

/** Sentences for coach tips (strings tip_*). Numbers use the same grouping as the rest of the app. */
final class TipFormat {
    private TipFormat() {}

    /** Several tips as one block, separated by blank lines. */
    static String join(Context ctx, List<Tip> tips, String name) {
        List<String> parts = new ArrayList<>();
        for (Tip t : tips) parts.add(text(ctx, t, name));
        return String.join("\n\n", parts);
    }

    /** One tip. The name, when given, opens the walk and steps-left tips ("Ayush, ..."). */
    static String text(Context ctx, Tip t, String name) {
        boolean named = name != null && !name.isEmpty();
        switch (t.kind) {
            case DIET_CONFLICT:
                return ctx.getString(R.string.tip_diet_conflict, tagName(ctx, t.tag), t.diet);
            case WALK:
                return named
                        ? ctx.getString(R.string.tip_walk_named, name, steps(ctx, t.n(0)), steps(ctx, t.n(1)),
                                kcal(ctx, t.n(2)), String.valueOf(t.n(3)), steps(ctx, t.n(4)), kcal(ctx, t.n(5)))
                        : ctx.getString(R.string.tip_walk, steps(ctx, t.n(0)), steps(ctx, t.n(1)),
                                kcal(ctx, t.n(2)), String.valueOf(t.n(3)), steps(ctx, t.n(4)), kcal(ctx, t.n(5)));
            case OVER_BUDGET:
                return ctx.getString(R.string.tip_over_budget, kcal(ctx, t.n(0)), kcal(ctx, t.n(1)));
            case PROTEIN_GOOD:
                return ctx.getString(R.string.tip_protein_good, String.valueOf(t.n(0)));
            case VEGGIE_GOOD:
                return ctx.getString(R.string.tip_veggie_good);
            case FRIED_NOTE:
                return ctx.getString(R.string.tip_fried_note);
            case SWEET_NOTE:
                return ctx.getString(R.string.tip_sweet_note);
            case PORTION_NOTE:
                return ctx.getString(R.string.tip_portion_note, kcal(ctx, t.n(0)));
            case BUDGET_LEFT:
                return ctx.getString(R.string.tip_budget_left, kcal(ctx, t.n(0)));
            case DAY_OVER_BUDGET:
                return ctx.getString(R.string.tip_day_over_budget, kcal(ctx, t.n(0)), String.valueOf(t.n(1)),
                        kcal(ctx, t.n(2)));
            case DAY_GOAL_REACHED:
                return ctx.getString(R.string.tip_day_goal_reached, steps(ctx, t.n(0)));
            case DAY_STEPS_LEFT:
                return named
                        ? ctx.getString(R.string.tip_day_steps_left_named, name, steps(ctx, t.n(0)),
                                String.valueOf(t.n(1)))
                        : ctx.getString(R.string.tip_day_steps_left, steps(ctx, t.n(0)), String.valueOf(t.n(1)));
            default:
                return ctx.getString(R.string.tip_day_on_track, kcal(ctx, t.n(0)));
        }
    }

    /** The word for a tag a diet avoids; fried, sweet, and veg never appear in diet tips. */
    static String tagName(Context ctx, FoodTag tag) {
        switch (tag) {
            case MEAT: return ctx.getString(R.string.tag_meat);
            case PORK: return ctx.getString(R.string.tag_pork);
            case FISH: return ctx.getString(R.string.tag_fish);
            case SHELLFISH: return ctx.getString(R.string.tag_shellfish);
            case EGG: return ctx.getString(R.string.tag_egg);
            case DAIRY: return ctx.getString(R.string.tag_dairy);
            case GLUTEN: return ctx.getString(R.string.tag_gluten);
            case ALCOHOL: return ctx.getString(R.string.tag_alcohol);
            default: return tag.key();
        }
    }

    private static String steps(Context ctx, long v) {
        return HealthFormat.steps(ctx, v);
    }

    private static String kcal(Context ctx, long v) {
        return HealthFormat.kcal(ctx, v);
    }
}
