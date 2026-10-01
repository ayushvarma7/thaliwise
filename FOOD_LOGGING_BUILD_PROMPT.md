# BUILD SPEC: IdentifyVLM Phase 9, food logging

You are extending an existing, working Android app. This document is the single source of truth for this phase. Follow it literally and in order. Where it gives exact code, use that code. Where it says EDIT, the Find text is copied from the current files and must match exactly once. Do not improvise features.

Goal of this phase, and nothing more: when a photo is identified as a food, the result screen offers to log it. The app looks the food up in a nutrition table shipped inside the app, the user picks the food and portion and can edit the calories, and the app writes one `NutritionRecord` into Health Connect. It then shows today's eaten, burned, and step totals. An Undo button deletes the record again. Nudges and notifications are Phase 10 (Section 13). Do not build them now.

The AI model only names the food. It never invents calorie numbers; calories always come from the table, and the user confirms them.

---

## 0. RULES FOR YOU (THE IMPLEMENTING AGENT)

1. Project root: `/Users/ayush/Downloads/CLAUDE/IdentifyVLM`. Work only inside it. Use absolute paths.
2. Before editing any file, read it. Edit only the regions this document names. Keep everything else byte for byte.
3. Java 17 only. Zero Kotlin files. No coroutines, Flow, Compose, or RxJava.
4. Do NOT add any dependency. Health Connect is the platform API in `android.health.connect` (Android 14+). The nutrition table is a plain text asset parsed by pure Java in `:core`. No `androidx.health`, no JSON library in `:core`.
5. Do not touch native code (`app/src/main/cpp/`), the model, the prompts, the sampling settings, `third_party/`, or the Room database schema (no new tables, no version bump).
6. Never write the em-dash character (U+2014) or the en-dash character (U+2013) in any file.
7. No `TODO`, no `FIXME`, no stubs, no placeholder code.
8. Never write test or fake records into the user's Health Connect yourself. Never grant permissions with adb. The user logs a real meal on the phone in Step 9.6.
9. Build commands always set these two variables inline:
   ```bash
   cd /Users/ayush/Downloads/CLAUDE/IdentifyVLM
   JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew <tasks>
   ```
10. adb is `/Users/ayush/Library/Android/sdk/platform-tools/adb`. Use the full path. Use `adb install -r` only; never uninstall (that deletes the downloaded model and the experiment log).
11. After each step's VERIFY passes, append one line to `PROGRESS.md` under `## Phase 9: Food logging` (format `- 9.N <name>: DONE - <verify result>`) and commit (Rule 13). On a failure: read the whole error, fix the cause, rerun, at most 5 attempts per error, then write `BLOCKERS.md` and STOP.
12. How to apply this document: every `CREATE \`path\`` is followed by one fenced block that is the complete file. Every `EDIT \`path\`` is followed by a `Find:` block and a `Replace with:` block; the Find text must occur exactly once in the file before the edit. Blocks are applied in the order written.
13. Commit after every step with a descriptive multi-paragraph message: a subject line, then what changed per file and why, then how it was verified, then the trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Never `git push`.

### 0.1 STOP conditions

- `git status --short` lists anything other than this file before Step 9.0's commit.
- The same build error 5 times.
- The meal write fails on the phone with the same error after the fixes in Section 12.

---

## 1. FACTS ABOUT THE CURRENT STATE (verified, do not re-derive)

- Last commit before this phase: `bba18cd Phase 8: Health Connect connection (docs and wrap-up)`.
- `minSdk = 34`, `compileSdk = 35`, `targetSdk = 35`. Test phone: Pixel 8, Android 17, serial `48071VDJH00284`. All 5 Health Connect permissions, including `WRITE_NUTRITION`, are already granted to the app on that phone.
- Existing classes used here: `com.example.identify.core.DailyHealth`, `com.example.identify.health.HealthConnectRepository` (has `PERMISSIONS`, `isAvailable`, `isGranted`, `readToday`, `manager`), `com.example.identify.util.ExperimentLog` (`event`, `put`, `append`), `com.example.identify.AppPrefs` (`getStepGoal`), `com.example.identify.ui.ResultViewModel.IdentifyResult` (fields `label`, `runId`).
- Health Connect write API (checked against `.toolchain/android-sdk/platforms/android-35/android.jar`):
  - `new NutritionRecord.Builder(Metadata, Instant start, Instant end)` with `setEnergy(Energy)`, `setProtein(Mass)`, `setTotalCarbohydrate(Mass)`, `setTotalFat(Mass)`, `setMealName(String)`, `setMealType(int)`, `setStartZoneOffset(ZoneOffset)`, `setEndZoneOffset(ZoneOffset)`.
  - `new Metadata.Builder().setClientRecordId(String).setRecordingMethod(Metadata.RECORDING_METHOD_MANUAL_ENTRY).build()`.
  - `MealType.MEAL_TYPE_BREAKFAST / LUNCH / DINNER / SNACK`. `Mass.fromGrams(double)`. `Energy.fromCalories(double)` takes small calories, so kcal x 1000 (Phase 8 confirmed reads divide by 1000 and give correct kcal).
  - `HealthConnectManager.insertRecords(List<Record>, Executor, OutcomeReceiver<InsertRecordsResponse, HealthConnectException>)`; the new id is `response.getRecords().get(0).getMetadata().getId()`.
  - `HealthConnectManager.deleteRecords(List<RecordIdFilter>, Executor, OutcomeReceiver<Void, HealthConnectException>)` with `RecordIdFilter.fromId(NutritionRecord.class, id)`.
- Nutrition values in the shipped table are approximate reference values for one serving (McDonald's US published figures and USDA FoodData Central typical servings, compiled for this prototype). They are not lab values. The dialog says so and the user can edit calories before logging.

---

## 2. FILES IN THIS PHASE (complete list)

Create:
```
app/src/main/assets/foods.txt
core/src/main/java/com/example/identify/core/FoodItem.java
core/src/main/java/com/example/identify/core/FoodCatalog.java
core/src/main/java/com/example/identify/core/FoodMatcher.java
core/src/main/java/com/example/identify/core/Meals.java
core/src/test/java/com/example/identify/core/FoodCatalogTest.java
core/src/test/java/com/example/identify/core/FoodMatcherTest.java
core/src/test/java/com/example/identify/core/MealsTest.java
app/src/main/java/com/example/identify/health/FoodRepository.java
app/src/main/java/com/example/identify/ui/HealthFormat.java
app/src/main/java/com/example/identify/ui/LogMealDialog.java
app/src/main/res/layout/dialog_log_meal.xml
```
Modify:
```
app/src/main/java/com/example/identify/health/HealthConnectRepository.java
app/src/main/java/com/example/identify/ui/SettingsFragment.java
app/src/main/java/com/example/identify/ui/ResultFragment.java
app/src/main/res/layout/fragment_result.xml
app/src/main/res/values/strings.xml
README.md
PROGRESS.md
```

---

## 3. STEP 9.0: PREFLIGHT

```bash
cd /Users/ayush/Downloads/CLAUDE/IdentifyVLM
git status --short
git log --oneline | head -n 1
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew :core:test :app:assembleDebug --console=plain -q
```
VERIFY: status lists only `FOOD_LOGGING_BUILD_PROMPT.md` (untracked); last commit is `bba18cd`; build exits 0. Add the heading `## Phase 9: Food logging` and the 9.0 line to `PROGRESS.md`, then commit this document and `PROGRESS.md` together.

---

## 4. STEP 9.1: NUTRITION TABLE AND PARSER (:core)

CREATE `app/src/main/assets/foods.txt`
```text
# IdentifyVLM nutrition table. One food per line, 11 fields separated by ';':
# id;name;brand;aliases;serving;grams;kcal;protein_g;carbs_g;fat_g;source
# Aliases are separated by '|'. Values are for one serving and are approximate reference values.
mcd_double_cheeseburger;Double Cheeseburger;McDonald's;double cheeseburger|double cheese burger;sandwich;165;450;25;34;24;McDonald's US published nutrition (approximate)
mcd_cheeseburger;Cheeseburger;McDonald's;cheeseburger|cheese burger;sandwich;114;300;15;32;13;McDonald's US published nutrition (approximate)
mcd_hamburger;Hamburger;McDonald's;hamburger;sandwich;100;250;12;31;9;McDonald's US published nutrition (approximate)
mcd_big_mac;Big Mac;McDonald's;big mac;sandwich;219;590;25;46;34;McDonald's US published nutrition (approximate)
mcd_quarter_pounder_cheese;Quarter Pounder with Cheese;McDonald's;quarter pounder|quarter pounder with cheese;sandwich;202;520;30;42;26;McDonald's US published nutrition (approximate)
mcd_mcchicken;McChicken;McDonald's;mcchicken|mc chicken;sandwich;147;400;14;39;21;McDonald's US published nutrition (approximate)
mcd_nuggets_10;Chicken McNuggets, 10 piece;McDonald's;chicken mcnuggets|mcnuggets|chicken nuggets|nuggets;10 pieces;162;410;23;26;24;McDonald's US published nutrition (approximate)
mcd_filet_o_fish;Filet-O-Fish;McDonald's;filet o fish|fillet o fish|fish sandwich;sandwich;142;390;16;39;19;McDonald's US published nutrition (approximate)
mcd_fries_medium;French Fries, medium;McDonald's;mcdonalds fries|french fries|fries;medium serving;111;320;5;43;15;McDonald's US published nutrition (approximate)
mcd_egg_mcmuffin;Egg McMuffin;McDonald's;egg mcmuffin|mcmuffin;sandwich;137;310;17;30;13;McDonald's US published nutrition (approximate)
mcd_coke_medium;Coca-Cola, medium;McDonald's;coke|coca cola;medium cup;621;200;0;55;0;McDonald's US published nutrition (approximate)
mcd_vanilla_cone;Vanilla Cone;McDonald's;vanilla cone|ice cream cone|soft serve;cone;90;200;5;33;5;McDonald's US published nutrition (approximate)
mcd_mcflurry_oreo;McFlurry with Oreo Cookies;McDonald's;mcflurry|oreo mcflurry;regular cup;285;510;12;80;16;McDonald's US published nutrition (approximate)
burger_generic;Hamburger (restaurant);;burger|hamburger|beef burger;burger;220;550;30;40;30;Typical restaurant serving (approximate)
cheeseburger_generic;Cheeseburger (restaurant);;cheeseburger|cheese burger;burger;230;600;32;40;34;Typical restaurant serving (approximate)
banana;Banana;;banana;medium banana;118;105;1.3;27;0.4;USDA FoodData Central typical serving (approximate)
apple;Apple;;apple|red apple|green apple;medium apple;182;95;0.5;25;0.3;USDA FoodData Central typical serving (approximate)
orange;Orange;;orange|navel orange;medium orange;131;62;1.2;15.4;0.2;USDA FoodData Central typical serving (approximate)
grapes;Grapes;;grapes|grape;cup;151;104;1.1;27;0.2;USDA FoodData Central typical serving (approximate)
strawberries;Strawberries;;strawberries|strawberry;cup;152;49;1;12;0.5;USDA FoodData Central typical serving (approximate)
avocado;Avocado;;avocado;half avocado;100;160;2;8.5;14.7;USDA FoodData Central typical serving (approximate)
egg_boiled;Boiled egg;;boiled egg|hard boiled egg|egg;large egg;50;78;6.3;0.6;5.3;USDA FoodData Central typical serving (approximate)
egg_fried;Fried egg;;fried egg|sunny side up egg;large egg;46;90;6.3;0.4;6.8;USDA FoodData Central typical serving (approximate)
scrambled_eggs;Scrambled eggs;;scrambled eggs|scrambled egg|omelette|omelet;2 eggs;122;200;13.5;2;15;USDA FoodData Central typical serving (approximate)
bread_white;White bread;;bread|white bread|toast;slice;25;67;2;12.7;0.8;USDA FoodData Central typical serving (approximate)
bagel;Bagel;;bagel|plain bagel;medium bagel;105;270;10;53;1.5;USDA FoodData Central typical serving (approximate)
rice_white;White rice;;rice|white rice|steamed rice;cup cooked;158;205;4.3;44.5;0.4;USDA FoodData Central typical serving (approximate)
pasta;Pasta;;pasta|spaghetti|penne|noodles;cup cooked;140;220;8;43;1.3;USDA FoodData Central typical serving (approximate)
spaghetti_meat_sauce;Spaghetti with meat sauce;;spaghetti bolognese|spaghetti with meat sauce|bolognese;cup;250;330;16;40;11;USDA FoodData Central typical serving (approximate)
pizza_cheese;Cheese pizza;;pizza|cheese pizza|margherita pizza;slice (14 inch);107;285;12;36;10;USDA FoodData Central typical serving (approximate)
pizza_pepperoni;Pepperoni pizza;;pepperoni pizza;slice (14 inch);107;313;13;35;13;USDA FoodData Central typical serving (approximate)
burrito;Burrito, beef and bean;;burrito|beef burrito|bean burrito;burrito;220;480;20;58;18;Typical restaurant serving (approximate)
taco;Taco, beef, crunchy;;taco|beef taco;taco;78;170;8;13;10;Typical restaurant serving (approximate)
hot_dog;Hot dog with bun;;hot dog|hotdog;hot dog;98;290;11;24;17;USDA FoodData Central typical serving (approximate)
chicken_breast;Grilled chicken breast;;chicken breast|grilled chicken|chicken;breast cooked;120;198;37;0;4.3;USDA FoodData Central typical serving (approximate)
fried_chicken;Fried chicken drumstick;;fried chicken|chicken drumstick|drumstick|chicken leg;drumstick;75;195;15;6;12;USDA FoodData Central typical serving (approximate)
salmon;Salmon fillet;;salmon|salmon fillet|grilled salmon;fillet cooked;150;309;33;0;18.6;USDA FoodData Central typical serving (approximate)
steak;Sirloin steak;;steak|sirloin|beef steak;steak cooked;150;360;42;0;21;USDA FoodData Central typical serving (approximate)
caesar_salad;Caesar salad;;caesar salad;side salad with dressing;150;190;5;8;16;Typical restaurant serving (approximate)
green_salad;Green salad, no dressing;;salad|green salad|garden salad|lettuce;bowl;100;20;1.5;3.5;0.2;USDA FoodData Central typical serving (approximate)
california_roll;California roll;;sushi|california roll|sushi roll|maki;6 pieces;180;255;9;38;7;Typical restaurant serving (approximate)
ramen;Instant ramen;;ramen|instant noodles|cup noodles;pack prepared;300;380;8;52;14;Typical package label (approximate)
fries_generic;French fries;;french fries|fries;medium serving;117;365;4;48;17;USDA FoodData Central typical serving (approximate)
potato_chips;Potato chips;;potato chips|chips|crisps;small bag (1 oz);28;152;2;15;10;USDA FoodData Central typical serving (approximate)
chocolate_bar;Milk chocolate bar;;chocolate|chocolate bar|candy bar;bar;43;220;3;26;13;Typical package label (approximate)
cookie_chocolate_chip;Chocolate chip cookie;;cookie|chocolate chip cookie;large cookie;40;195;2;26;10;USDA FoodData Central typical serving (approximate)
donut_glazed;Glazed donut;;donut|doughnut|glazed donut;donut;52;240;4;33;11;Typical restaurant serving (approximate)
croissant;Butter croissant;;croissant;croissant;57;231;4.7;26;12;USDA FoodData Central typical serving (approximate)
pancakes;Pancakes;;pancakes|pancake;3 medium, no syrup;114;260;7;33;11;USDA FoodData Central typical serving (approximate)
oatmeal;Oatmeal;;oatmeal|porridge|oats;cup cooked;234;166;6;28;3.6;USDA FoodData Central typical serving (approximate)
greek_yogurt;Greek yogurt, plain nonfat;;yogurt|greek yogurt|yoghurt;container;170;100;17;6;0.7;USDA FoodData Central typical serving (approximate)
milk;Milk, 2 percent;;milk|glass of milk;cup;244;122;8;12;4.8;USDA FoodData Central typical serving (approximate)
coffee_black;Coffee, black;;coffee|black coffee|espresso;cup;240;2;0.3;0;0;USDA FoodData Central typical serving (approximate)
latte;Latte, 2 percent milk;;latte|caffe latte|cappuccino;16 oz cup;473;190;13;19;7;Typical cafe serving (approximate)
orange_juice;Orange juice;;orange juice|juice;cup;248;112;1.7;26;0.5;USDA FoodData Central typical serving (approximate)
beer;Beer;;beer|lager;12 oz can;355;153;1.6;12.6;0;USDA FoodData Central typical serving (approximate)
wine_red;Red wine;;wine|red wine;5 oz glass;147;125;0.1;3.8;0;USDA FoodData Central typical serving (approximate)
cola;Cola;;cola|soda|coke|pepsi;12 oz can;368;140;0;39;0;Typical package label (approximate)
ice_cream_vanilla;Vanilla ice cream;;ice cream|vanilla ice cream;half cup;66;137;2.3;16;7.3;USDA FoodData Central typical serving (approximate)
peanut_butter;Peanut butter;;peanut butter;2 tbsp;32;190;7;7;16;USDA FoodData Central typical serving (approximate)
almonds;Almonds;;almonds|almond|nuts;1 oz handful;28;164;6;6;14;USDA FoodData Central typical serving (approximate)
pbj_sandwich;Peanut butter and jelly sandwich;;pb and j|pbj|peanut butter sandwich|peanut butter and jelly sandwich;sandwich;93;380;13;48;17;Typical home recipe (approximate)
grilled_cheese;Grilled cheese sandwich;;grilled cheese|cheese sandwich;sandwich;130;400;15;30;24;Typical home recipe (approximate)
turkey_sandwich;Turkey sandwich;;turkey sandwich|sandwich|sub;sandwich;230;350;24;36;12;Typical deli serving (approximate)
mac_and_cheese;Macaroni and cheese;;mac and cheese|macaroni and cheese|macaroni;cup;200;350;13;44;14;Typical package label (approximate)
chicken_curry_rice;Chicken curry with rice;;chicken curry|curry|curry and rice;plate;400;600;30;70;20;Typical restaurant serving (approximate)
butter_chicken;Butter chicken;;butter chicken|chicken tikka masala|tikka masala;cup;240;440;30;14;30;Typical restaurant serving (approximate)
naan;Naan;;naan|naan bread;piece;90;262;8.7;45;5.1;USDA FoodData Central typical serving (approximate)
samosa;Samosa, potato;;samosa;piece;100;260;5;30;13;Typical restaurant serving (approximate)
biryani;Chicken biryani;;biryani|chicken biryani;cup;200;350;18;42;12;Typical restaurant serving (approximate)
dal;Dal (lentil curry);;dal|daal|dhal|lentil curry|lentils;cup;200;230;12;30;7;Typical home recipe (approximate)
fried_rice;Fried rice;;fried rice;cup;137;238;5.5;45;4;USDA FoodData Central typical serving (approximate)
pad_thai;Pad thai;;pad thai;plate;300;550;22;70;20;Typical restaurant serving (approximate)
hummus;Hummus;;hummus;2 tbsp;30;50;2.4;4.3;2.9;USDA FoodData Central typical serving (approximate)
```

CREATE `core/src/main/java/com/example/identify/core/FoodItem.java`
```java
package com.example.identify.core;

import java.util.Collections;
import java.util.List;

/** One row of the nutrition table. All amounts are for one serving. */
public final class FoodItem {
    public final String id;
    public final String name;
    public final String brand;           // empty for generic foods
    public final List<String> aliases;   // names a model or a user may use for this food
    public final String serving;         // for example "sandwich" or "cup cooked"
    public final double servingGrams;
    public final double kcal;
    public final double proteinG;
    public final double carbsG;
    public final double fatG;
    public final String source;

    public FoodItem(String id, String name, String brand, List<String> aliases, String serving,
                    double servingGrams, double kcal, double proteinG, double carbsG, double fatG, String source) {
        this.id = id;
        this.name = name;
        this.brand = brand;
        this.aliases = Collections.unmodifiableList(aliases);
        this.serving = serving;
        this.servingGrams = servingGrams;
        this.kcal = kcal;
        this.proteinG = proteinG;
        this.carbsG = carbsG;
        this.fatG = fatG;
        this.source = source;
    }

    /** "Big Mac (McDonald's)" for branded foods, the plain name otherwise. */
    public String displayName() {
        return brand.isEmpty() ? name : name + " (" + brand + ")";
    }
}
```

CREATE `core/src/main/java/com/example/identify/core/FoodCatalog.java`
```java
package com.example.identify.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses the nutrition table shipped in app/src/main/assets/foods.txt.
 * One food per line, 11 fields separated by ';':
 * id;name;brand;aliases;serving;grams;kcal;protein_g;carbs_g;fat_g;source
 * Aliases are separated by '|'. Blank lines and lines starting with '#' are skipped.
 */
public final class FoodCatalog {
    private FoodCatalog() {}

    public static final int FIELDS = 11;

    public static List<FoodItem> parse(String text) {
        List<FoodItem> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
            int n = i + 1;
            String[] f = line.split(";", -1);
            if (f.length != FIELDS) {
                throw new IllegalArgumentException("line " + n + ": expected " + FIELDS + " fields, found " + f.length);
            }
            String id = f[0].trim();
            if (id.isEmpty() || !ids.add(id)) {
                throw new IllegalArgumentException("line " + n + ": empty or duplicate id '" + id + "'");
            }
            String name = f[1].trim();
            if (name.isEmpty()) throw new IllegalArgumentException("line " + n + ": empty name");
            List<String> aliases = new ArrayList<>();
            for (String a : f[3].split("\\|")) {
                if (!a.trim().isEmpty()) aliases.add(a.trim());
            }
            out.add(new FoodItem(id, name, f[2].trim(), aliases, f[4].trim(),
                    number(f[5], n), number(f[6], n), number(f[7], n), number(f[8], n), number(f[9], n),
                    f[10].trim()));
        }
        return out;
    }

    private static double number(String s, int line) {
        double v;
        try {
            v = Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("line " + line + ": not a number '" + s + "'");
        }
        if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) {
            throw new IllegalArgumentException("line " + line + ": number out of range '" + s + "'");
        }
        return v;
    }
}
```

CREATE `core/src/test/java/com/example/identify/core/FoodCatalogTest.java`
```java
package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.Test;

public class FoodCatalogTest {

    /** The table shipped in the app. Gradle runs :core tests with the core/ folder as working directory. */
    static List<FoodItem> shipped() throws IOException {
        File f = new File("../app/src/main/assets/foods.txt");
        return FoodCatalog.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void parsesFieldsSkippingCommentsAndBlanks() {
        String text = "# header\n\nbanana;Banana;;banana|bananas;medium banana;118;105;1.3;27;0.4;USDA\n"
                + "mcd_big_mac;Big Mac;McDonald's;big mac;sandwich;219;590;25;46;34;McDonald's\n";
        List<FoodItem> foods = FoodCatalog.parse(text);
        assertEquals(2, foods.size());
        FoodItem b = foods.get(0);
        assertEquals("banana", b.id);
        assertEquals("", b.brand);
        assertEquals(2, b.aliases.size());
        assertEquals(105, b.kcal, 0);
        assertEquals("Banana", b.displayName());
        assertEquals("Big Mac (McDonald's)", foods.get(1).displayName());
    }

    @Test
    public void wrongFieldCountNamesTheLine() {
        try {
            FoodCatalog.parse("# x\nok;Ok;;ok;s;1;1;1;1;1;src\nbad;Bad;;bad\n");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("line 3"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateIdRejected() {
        FoodCatalog.parse("a;A;;a;s;1;1;1;1;1;src\na;B;;b;s;1;1;1;1;1;src\n");
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeNumberRejected() {
        FoodCatalog.parse("a;A;;a;s;1;-5;1;1;1;src\n");
    }

    @Test
    public void shippedTableIsValid() throws IOException {
        List<FoodItem> foods = shipped();
        assertTrue("table too small: " + foods.size(), foods.size() >= 70);
        for (FoodItem f : foods) {
            assertTrue(f.id + " needs an alias", !f.aliases.isEmpty());
            assertTrue(f.id + " kcal", f.kcal > 0 && f.kcal < 2000);
            assertTrue(f.id + " source", !f.source.isEmpty());
        }
    }
}
```

VERIFY 9.1:
```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew :core:test --console=plain -q
grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' core/build/test-results/test/TEST-com.example.identify.core.FoodCatalogTest.xml | head -n 1
```
Exit 0, FoodCatalogTest `tests="5" ... failures="0" errors="0"`.

---

## 5. STEP 9.2: MATCHING AND MEAL MATH (:core)

CREATE `core/src/main/java/com/example/identify/core/FoodMatcher.java`
```java
package com.example.identify.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Finds the nutrition table rows that a free-text food name (a model label or user text) refers to. */
public final class FoodMatcher {
    private FoodMatcher() {}

    /** Matches scoring below this are dropped. */
    public static final double MIN_SCORE = 0.5;

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "of", "with", "and", "on", "in", "some", "my", "fresh", "homemade",
            "food", "meal", "dish", "plate", "bowl", "slice", "piece", "serving", "cup", "glass",
            "side", "one", "two", "small", "medium", "large"));

    public static final class Match {
        public final FoodItem item;
        public final double score;

        Match(FoodItem item, double score) {
            this.item = item;
            this.score = score;
        }
    }

    /**
     * Up to max foods, best first. Each food scores by its best name or alias; naming the brand adds 0.2,
     * and a branded food loses 0.05 when the text does not name the brand, so "cheeseburger" prefers the
     * generic row while "McDonald's cheeseburger" prefers the branded one.
     */
    public static List<Match> match(String text, List<FoodItem> foods, int max) {
        List<Match> out = new ArrayList<>();
        Set<String> query = new HashSet<>(tokens(text));
        if (query.isEmpty() || max <= 0) return out;
        for (FoodItem f : foods) {
            double best = score(tokens(f.name), query);
            for (String alias : f.aliases) best = Math.max(best, score(tokens(alias), query));
            if (best <= 0) continue;
            List<String> brand = tokens(f.brand);
            if (!brand.isEmpty()) {
                boolean named = false;
                for (String t : brand) {
                    if (query.contains(t)) named = true;
                }
                best += named ? 0.2 : -0.05;
            }
            if (best >= MIN_SCORE) out.add(new Match(f, best));
        }
        out.sort((a, b) -> {
            int c = Double.compare(b.score, a.score);
            return c != 0 ? c : a.item.name.compareTo(b.item.name);
        });
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** 0 when no word is shared. A name whose every word appears in the query gets a 0.2 bonus. */
    static double score(List<String> name, Set<String> query) {
        if (name.isEmpty()) return 0;
        Set<String> words = new HashSet<>(name);
        int hits = 0;
        for (String t : words) {
            if (query.contains(t)) hits++;
        }
        if (hits == 0) return 0;
        double recall = (double) hits / words.size();
        double precision = (double) hits / query.size();
        double s = 0.7 * recall + 0.3 * precision;
        if (hits == words.size()) s += 0.2;
        return s;
    }

    /** Lowercase words without punctuation, apostrophes, stop words, or a plural s. */
    public static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        String t = text.toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replace("’", "")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        if (t.isEmpty()) return out;
        for (String w : t.split(" ")) {
            String s = stem(w);
            if (!STOPWORDS.contains(s)) out.add(s);
        }
        return out;
    }

    /** Drops one plural s ("bananas" to "banana"). Applied to both sides, so odd stems still match. */
    static String stem(String w) {
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) return w.substring(0, w.length() - 1);
        return w;
    }
}
```

CREATE `core/src/main/java/com/example/identify/core/Meals.java`
```java
package com.example.identify.core;

import java.util.Locale;

/** Meal slot from the clock, and portion arithmetic for logging a meal. */
public final class Meals {
    private Meals() {}

    public enum Slot { BREAKFAST, LUNCH, DINNER, SNACK }

    /** 5 to 10 breakfast, 11 to 14 lunch, 17 to 21 dinner, anything else snack. */
    public static Slot slotForHour(int hour) {
        if (hour >= 5 && hour <= 10) return Slot.BREAKFAST;
        if (hour >= 11 && hour <= 14) return Slot.LUNCH;
        if (hour >= 17 && hour <= 21) return Slot.DINNER;
        return Slot.SNACK;
    }

    /** The portion slider runs 1 to 6 in whole steps: 0.5x to 3x servings. */
    public static double portionFromSlider(float value) {
        return value / 2.0;
    }

    public static long scaledKcal(FoodItem food, double portion) {
        return Math.round(food.kcal * portion);
    }

    /** One decimal place, for grams of protein, carbohydrate, and fat. */
    public static double scaled(double perServing, double portion) {
        return Math.round(perServing * portion * 10.0) / 10.0;
    }

    /** "1", "1.5", "0.5". */
    public static String formatPortion(double portion) {
        if (portion == Math.rint(portion)) return String.valueOf((long) portion);
        return String.format(Locale.ROOT, "%.1f", portion);
    }
}
```

CREATE `core/src/test/java/com/example/identify/core/FoodMatcherTest.java`
```java
package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

public class FoodMatcherTest {

    private static List<FoodItem> foods;

    @BeforeClass
    public static void load() throws IOException {
        foods = FoodCatalogTest.shipped();
    }

    private static String top(String text) {
        List<FoodMatcher.Match> m = FoodMatcher.match(text, foods, 5);
        return m.isEmpty() ? null : m.get(0).item.id;
    }

    @Test
    public void doubleCheeseburgerFindsTheBrandedRow() {
        assertEquals("mcd_double_cheeseburger", top("Double cheeseburger"));
    }

    @Test
    public void brandNamedInTextWins() {
        assertEquals("mcd_big_mac", top("McDonald's Big Mac"));
        assertEquals("mcd_cheeseburger", top("McDonalds cheeseburger"));
    }

    @Test
    public void unbrandedTextPrefersGenericRow() {
        assertEquals("cheeseburger_generic", top("cheeseburger"));
        assertEquals("fries_generic", top("French fries"));
    }

    @Test
    public void pluralsCaseAndStopWords() {
        assertEquals("banana", top("Bananas"));
        assertEquals("pizza_pepperoni", top("Slice of pepperoni pizza"));
        assertEquals("coffee_black", top("A cup of coffee"));
    }

    @Test
    public void nonFoodHasNoMatch() {
        assertTrue(FoodMatcher.match("computer mouse", foods, 5).isEmpty());
        assertTrue(FoodMatcher.match("Golden Retriever", foods, 5).isEmpty());
        assertTrue(FoodMatcher.match("", foods, 5).isEmpty());
        assertTrue(FoodMatcher.match(null, foods, 5).isEmpty());
    }

    @Test
    public void maxIsRespectedAndSorted() {
        List<FoodMatcher.Match> m = FoodMatcher.match("fried chicken", foods, 2);
        assertEquals(2, m.size());
        assertEquals("fried_chicken", m.get(0).item.id);
        assertTrue(m.get(0).score >= m.get(1).score);
    }

    @Test
    public void tokensNormalize() {
        assertEquals(Arrays.asList("mcdonald", "frie"), FoodMatcher.tokens("McDonald's Fries!"));
    }
}
```

CREATE `core/src/test/java/com/example/identify/core/MealsTest.java`
```java
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
```

VERIFY 9.2:
```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew :core:test --console=plain -q
ls core/build/test-results/test/ | grep -c "^TEST-"
grep -rn "import android" core/src && echo "FAIL: android import in core" || echo "OK: core is android-free"
```
Exit 0, count `13`, `OK: core is android-free`.

---

## 6. STEP 9.3: HEALTH CONNECT WRITE AND DELETE, FOOD TABLE LOADER (:app)

CREATE `app/src/main/java/com/example/identify/health/FoodRepository.java`
```java
package com.example.identify.health;

import android.content.Context;
import android.util.Log;

import com.example.identify.Config;
import com.example.identify.core.FoodCatalog;
import com.example.identify.core.FoodItem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/** The nutrition table from assets/foods.txt, read once and kept in memory (74 rows). */
public final class FoodRepository {
    private FoodRepository() {}

    public static final String ASSET = "foods.txt";

    private static volatile List<FoodItem> foods;

    /** The table, or an empty list if the asset cannot be read (logged). */
    public static List<FoodItem> foods(Context ctx) {
        List<FoodItem> f = foods;
        if (f != null) return f;
        synchronized (FoodRepository.class) {
            if (foods == null) {
                try (InputStream in = ctx.getApplicationContext().getAssets().open(ASSET)) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] chunk = new byte[8192];
                    int n;
                    while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
                    foods = Collections.unmodifiableList(
                            FoodCatalog.parse(new String(buf.toByteArray(), StandardCharsets.UTF_8)));
                    Log.i(Config.HEALTH_TAG, "food table loaded items=" + foods.size());
                } catch (IOException | IllegalArgumentException e) {
                    Log.e(Config.HEALTH_TAG, "cannot load " + ASSET, e);
                    return Collections.emptyList();
                }
            }
            return foods;
        }
    }
}
```

EDIT `app/src/main/java/com/example/identify/health/HealthConnectRepository.java`
Find:
```java
import android.health.connect.HealthPermissions;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.ActiveCaloriesBurnedRecord;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.NutritionRecord;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.datatypes.TotalCaloriesBurnedRecord;
import android.health.connect.datatypes.units.Energy;
```
Replace with:
```java
import android.health.connect.HealthPermissions;
import android.health.connect.InsertRecordsResponse;
import android.health.connect.RecordIdFilter;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.ActiveCaloriesBurnedRecord;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.MealType;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.NutritionRecord;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.datatypes.TotalCaloriesBurnedRecord;
import android.health.connect.datatypes.units.Energy;
import android.health.connect.datatypes.units.Mass;
```

EDIT `app/src/main/java/com/example/identify/health/HealthConnectRepository.java`
Find:
```java
import com.example.identify.core.DailyHealth;
```
Replace with:
```java
import com.example.identify.core.DailyHealth;
import com.example.identify.core.Meals;
```

EDIT `app/src/main/java/com/example/identify/health/HealthConnectRepository.java`
Find:
```java
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
```
Replace with:
```java
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
```

EDIT `app/src/main/java/com/example/identify/health/HealthConnectRepository.java`
Find:
```java
    /** Health Connect energy is in small calories; the app shows kilocalories. */
```
Replace with:
```java
    /** recordId is the Health Connect id of the new record; on failure it is null and error says why. Main thread. */
    public interface WriteCallback {
        void onResult(String recordId, String error);
    }

    /** error is null on success. Main thread. */
    public interface DeleteCallback {
        void onResult(String error);
    }

    public static int mealType(Meals.Slot slot) {
        switch (slot) {
            case BREAKFAST: return MealType.MEAL_TYPE_BREAKFAST;
            case LUNCH: return MealType.MEAL_TYPE_LUNCH;
            case DINNER: return MealType.MEAL_TYPE_DINNER;
            default: return MealType.MEAL_TYPE_SNACK;
        }
    }

    /**
     * Writes one meal as a NutritionRecord covering the last minute (the record type has a start and an
     * end). Energy goes in as small calories, the same unit the reads divide by 1000. NaN macros are skipped.
     */
    public static void insertMeal(Context ctx, String mealName, double kcal, double proteinG, double carbsG,
                                  double fatG, Meals.Slot slot, WriteCallback cb) {
        final Context app = ctx.getApplicationContext();
        final HealthConnectManager hc = manager(app);
        if (hc == null) {
            cb.onResult(null, "Health Connect is not available on this device");
            return;
        }
        if (!isGranted(app, HealthPermissions.WRITE_NUTRITION)) {
            cb.onResult(null, "nutrition write permission not granted");
            return;
        }
        Instant end = Instant.now();
        Instant start = end.minusSeconds(60);
        ZoneOffset offset = ZoneId.systemDefault().getRules().getOffset(end);
        Metadata metadata = new Metadata.Builder()
                .setClientRecordId("identifyvlm-meal-" + UUID.randomUUID())
                .setRecordingMethod(Metadata.RECORDING_METHOD_MANUAL_ENTRY)
                .build();
        NutritionRecord.Builder b = new NutritionRecord.Builder(metadata, start, end)
                .setStartZoneOffset(offset)
                .setEndZoneOffset(offset)
                .setMealName(mealName)
                .setMealType(mealType(slot))
                .setEnergy(Energy.fromCalories(kcal * 1000.0));
        if (!Double.isNaN(proteinG)) b.setProtein(Mass.fromGrams(proteinG));
        if (!Double.isNaN(carbsG)) b.setTotalCarbohydrate(Mass.fromGrams(carbsG));
        if (!Double.isNaN(fatG)) b.setTotalFat(Mass.fromGrams(fatG));
        List<Record> records = Collections.<Record>singletonList(b.build());
        Executor main = ContextCompat.getMainExecutor(app);
        try {
            hc.insertRecords(records, main, new OutcomeReceiver<InsertRecordsResponse, HealthConnectException>() {
                @Override
                public void onResult(InsertRecordsResponse response) {
                    List<Record> saved = response.getRecords();
                    String id = saved.isEmpty() ? null : saved.get(0).getMetadata().getId();
                    Log.i(Config.HEALTH_TAG, "meal written id=" + id + " kcal=" + kcal + " name=" + mealName);
                    cb.onResult(id, id == null ? "Health Connect returned no record" : null);
                }

                @Override
                public void onError(HealthConnectException e) {
                    Log.w(Config.HEALTH_TAG, "meal write failed", e);
                    cb.onResult(null, e.getMessage() == null ? "error " + e.getErrorCode() : e.getMessage());
                }
            });
        } catch (RuntimeException e) {   // SecurityException if access was removed a moment ago
            Log.w(Config.HEALTH_TAG, "meal write failed", e);
            main.execute(() -> cb.onResult(null, e.toString()));
        }
    }

    /** Deletes a meal this app wrote, by its Health Connect id. */
    public static void deleteMeal(Context ctx, String recordId, DeleteCallback cb) {
        final Context app = ctx.getApplicationContext();
        final HealthConnectManager hc = manager(app);
        if (hc == null) {
            cb.onResult("Health Connect is not available on this device");
            return;
        }
        Executor main = ContextCompat.getMainExecutor(app);
        List<RecordIdFilter> ids = Collections.singletonList(RecordIdFilter.fromId(NutritionRecord.class, recordId));
        try {
            hc.deleteRecords(ids, main, new OutcomeReceiver<Void, HealthConnectException>() {
                @Override
                public void onResult(Void unused) {
                    Log.i(Config.HEALTH_TAG, "meal deleted id=" + recordId);
                    cb.onResult(null);
                }

                @Override
                public void onError(HealthConnectException e) {
                    Log.w(Config.HEALTH_TAG, "meal delete failed", e);
                    cb.onResult(e.getMessage() == null ? "error " + e.getErrorCode() : e.getMessage());
                }
            });
        } catch (RuntimeException e) {
            Log.w(Config.HEALTH_TAG, "meal delete failed", e);
            main.execute(() -> cb.onResult(e.toString()));
        }
    }

    /** Health Connect energy is in small calories; the app shows kilocalories. */
```

VERIFY 9.3: `./gradlew :app:compileDebugJavaWithJavac` (with the variables from Rule 9) exits 0.

---

## 7. STEP 9.4: UI (STRINGS, LAYOUTS, DIALOG, RESULT SCREEN)

EDIT `app/src/main/res/values/strings.xml`
Find:
```xml
</resources>
```
Replace with:
```xml

    <string name="food_detected">Looks like food: %1$s, about %2$s kcal per %3$s.</string>
    <string name="log_meal_button">Log meal</string>
    <string name="meal_dialog_title">Log meal in Health Connect</string>
    <string name="meal_search_hint">Food</string>
    <string name="meal_no_match">No matching food in the table. Try another name.</string>
    <string name="meal_choice_format">%1$s, %2$s, %3$s kcal</string>
    <string name="meal_portion_label">Portion: %1$s x %2$s</string>
    <string name="meal_kcal_hint">Calories (kcal)</string>
    <string name="meal_kcal_invalid">Enter calories between 1 and 5000.</string>
    <string name="meal_slot_format">Meal type: %1$s (from the time of day)</string>
    <string name="meal_slot_breakfast">breakfast</string>
    <string name="meal_slot_lunch">lunch</string>
    <string name="meal_slot_dinner">dinner</string>
    <string name="meal_slot_snack">snack</string>
    <string name="meal_source_format">Values: %1$s. They are estimates, so edit the calories if needed.</string>
    <string name="meal_log_confirm">Log</string>
    <string name="meal_need_permission">Allow Health Connect in Settings to log meals.</string>
    <string name="meal_open_settings">Settings</string>
    <string name="meal_logging">Logging meal</string>
    <string name="meal_logged">Logged %1$s kcal of %2$s as %3$s.</string>
    <string name="meal_log_failed">Could not log the meal: %1$s</string>
    <string name="meal_undo">Undo</string>
    <string name="meal_undone">Meal removed from Health Connect.</string>
    <string name="meal_undo_failed">Could not remove the meal: %1$s</string>
    <string name="meal_today_summary">Today: %1$s kcal eaten, %2$s kcal burned, %3$s of %4$s steps</string>
</resources>
```

CREATE `app/src/main/res/layout/dialog_log_meal.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingHorizontal="24dp"
        android:paddingTop="8dp"
        android:paddingBottom="8dp">

        <com.google.android.material.textfield.TextInputLayout
            android:id="@+id/meal_search_layout"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:hint="@string/meal_search_hint">

            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/meal_search_input"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:inputType="text"
                android:maxLines="1" />
        </com.google.android.material.textfield.TextInputLayout>

        <TextView
            android:id="@+id/meal_no_match_text"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="@string/meal_no_match"
            android:textAppearance="?attr/textAppearanceBodyMedium"
            android:visibility="gone" />

        <RadioGroup
            android:id="@+id/meal_choices"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:orientation="vertical" />

        <TextView
            android:id="@+id/meal_portion_label"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:textAppearance="?attr/textAppearanceBodyMedium" />

        <com.google.android.material.slider.Slider
            android:id="@+id/meal_portion_slider"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:stepSize="1"
            android:value="2"
            android:valueFrom="1"
            android:valueTo="6" />

        <com.google.android.material.textfield.TextInputLayout
            android:id="@+id/meal_kcal_layout"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:hint="@string/meal_kcal_hint">

            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/meal_kcal_input"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:inputType="numberDecimal"
                android:maxLines="1" />
        </com.google.android.material.textfield.TextInputLayout>

        <TextView
            android:id="@+id/meal_slot_text"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:textAppearance="?attr/textAppearanceBodySmall" />

        <TextView
            android:id="@+id/meal_source_text"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textAppearance="?attr/textAppearanceBodySmall" />
    </LinearLayout>
</ScrollView>
```

EDIT `app/src/main/res/layout/fragment_result.xml`
Find:
```xml
        <LinearLayout
            android:id="@+id/action_row"
```
Replace with:
```xml
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/food_card"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:visibility="gone">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:id="@+id/food_text"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:textAppearance="?attr/textAppearanceBodyLarge" />

                <TextView
                    android:id="@+id/food_status_text"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:textAppearance="?attr/textAppearanceBodyMedium"
                    android:visibility="gone" />

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:orientation="horizontal">

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/log_meal_button"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/log_meal_button" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/undo_meal_button"
                        style="@style/Widget.Material3.Button.OutlinedButton"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="8dp"
                        android:text="@string/meal_undo"
                        android:visibility="gone" />
                </LinearLayout>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <LinearLayout
            android:id="@+id/action_row"
```

CREATE `app/src/main/java/com/example/identify/ui/HealthFormat.java`
```java
package com.example.identify.ui;

import android.content.Context;

import com.example.identify.R;
import com.example.identify.core.Meals;

import java.util.Locale;

/** Number and label formatting shared by the Settings and Result screens. Unknown values show "no data". */
final class HealthFormat {
    private HealthFormat() {}

    static String steps(Context ctx, long v) {
        return v < 0 ? ctx.getString(R.string.health_no_data) : String.format(Locale.getDefault(), "%,d", v);
    }

    static String kcal(Context ctx, double v) {
        return Double.isNaN(v) ? ctx.getString(R.string.health_no_data) : String.format(Locale.getDefault(), "%,.0f", v);
    }

    static String slot(Context ctx, Meals.Slot slot) {
        switch (slot) {
            case BREAKFAST: return ctx.getString(R.string.meal_slot_breakfast);
            case LUNCH: return ctx.getString(R.string.meal_slot_lunch);
            case DINNER: return ctx.getString(R.string.meal_slot_dinner);
            default: return ctx.getString(R.string.meal_slot_snack);
        }
    }
}
```

EDIT `app/src/main/java/com/example/identify/ui/SettingsFragment.java`
Find:
```java
    private String formatSteps(long v) {
        return v < 0 ? getString(R.string.health_no_data) : String.format(Locale.getDefault(), "%,d", v);
    }

    private String formatKcal(double v) {
        return Double.isNaN(v) ? getString(R.string.health_no_data) : String.format(Locale.getDefault(), "%,.0f", v);
    }
```
Replace with:
```java
    private String formatSteps(long v) {
        return HealthFormat.steps(requireContext(), v);
    }

    private String formatKcal(double v) {
        return HealthFormat.kcal(requireContext(), v);
    }
```

EDIT `app/src/main/java/com/example/identify/ui/SettingsFragment.java` (the Locale import is now unused)
Find:
```java
import com.google.android.material.snackbar.Snackbar;

import java.util.Locale;
```
Replace with:
```java
import com.google.android.material.snackbar.Snackbar;
```

CREATE `app/src/main/java/com/example/identify/ui/LogMealDialog.java`
```java
package com.example.identify.ui;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.identify.R;
import com.example.identify.core.FoodItem;
import com.example.identify.core.FoodMatcher;
import com.example.identify.core.Meals;
import com.example.identify.databinding.DialogLogMealBinding;
import com.example.identify.health.FoodRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Picks the food (table rows matching the text), the portion, and the calories to log. Calories start at
 * the table value for the portion; the user may edit them. Nothing is written here: the listener does that.
 */
final class LogMealDialog {

    interface Listener {
        void onLog(FoodItem food, double portion, double kcal, boolean kcalEdited, String query);
    }

    static final int MAX_CHOICES = 5;
    static final double MAX_KCAL = 5000;

    private final Context ctx;
    private final Listener listener;
    private final DialogLogMealBinding b;
    private final List<FoodItem> foods;
    private final List<FoodItem> shown = new ArrayList<>();
    private final List<Integer> radioIds = new ArrayList<>();
    private FoodItem selected;
    private double portion = 1.0;
    private long suggestedKcal;

    private LogMealDialog(Context ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
        this.b = DialogLogMealBinding.inflate(LayoutInflater.from(ctx));
        this.foods = FoodRepository.foods(ctx);
    }

    static void show(Fragment fragment, String initialQuery, Listener listener) {
        new LogMealDialog(fragment.requireContext(), listener).open(initialQuery);
    }

    private void open(String initialQuery) {
        Meals.Slot slot = Meals.slotForHour(LocalTime.now().getHour());
        b.mealSlotText.setText(ctx.getString(R.string.meal_slot_format, HealthFormat.slot(ctx, slot)));
        b.mealPortionSlider.setLabelFormatter(v -> Meals.formatPortion(Meals.portionFromSlider(v)) + "x");
        b.mealPortionSlider.addOnChangeListener((slider, value, fromUser) -> {
            portion = Meals.portionFromSlider(value);
            refreshValues();
        });
        b.mealChoices.setOnCheckedChangeListener((group, checkedId) -> {
            int i = radioIds.indexOf(checkedId);
            selected = i >= 0 ? shown.get(i) : null;
            refreshValues();
        });
        b.mealSearchInput.setText(initialQuery);
        b.mealSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                refreshChoices(s.toString());
            }
        });
        refreshChoices(initialQuery);

        AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.meal_dialog_title)
                .setView(b.getRoot())
                .setPositiveButton(R.string.meal_log_confirm, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        // Validate before closing: a positive button set in the builder would always dismiss.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (confirm()) dialog.dismiss();
        }));
        dialog.show();
    }

    private void refreshChoices(String query) {
        b.mealChoices.removeAllViews();
        shown.clear();
        radioIds.clear();
        for (FoodMatcher.Match m : FoodMatcher.match(query, foods, MAX_CHOICES)) {
            MaterialRadioButton rb = new MaterialRadioButton(ctx);
            int id = View.generateViewId();
            rb.setId(id);
            rb.setText(ctx.getString(R.string.meal_choice_format, m.item.displayName(), m.item.serving,
                    HealthFormat.kcal(ctx, m.item.kcal)));
            b.mealChoices.addView(rb);
            shown.add(m.item);
            radioIds.add(id);
        }
        b.mealNoMatchText.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        if (shown.isEmpty()) {
            b.mealChoices.clearCheck();
            selected = null;
            refreshValues();
        } else {
            b.mealChoices.check(radioIds.get(0));   // fires the listener, which selects and refreshes
        }
    }

    private void refreshValues() {
        if (selected == null) {
            b.mealPortionLabel.setText(null);
            b.mealKcalInput.setText(null);
            b.mealSourceText.setText(null);
            return;
        }
        b.mealPortionLabel.setText(ctx.getString(R.string.meal_portion_label, Meals.formatPortion(portion), selected.serving));
        suggestedKcal = Meals.scaledKcal(selected, portion);
        b.mealKcalInput.setText(String.valueOf(suggestedKcal));
        b.mealKcalLayout.setError(null);
        b.mealSourceText.setText(ctx.getString(R.string.meal_source_format, selected.source));
    }

    private boolean confirm() {
        if (selected == null) {
            b.mealNoMatchText.setVisibility(View.VISIBLE);
            return false;
        }
        double kcal;
        try {
            kcal = Double.parseDouble(String.valueOf(b.mealKcalInput.getText()).trim());
        } catch (NumberFormatException e) {
            kcal = -1;
        }
        if (!(kcal >= 1 && kcal <= MAX_KCAL)) {
            b.mealKcalLayout.setError(ctx.getString(R.string.meal_kcal_invalid));
            return false;
        }
        String query = String.valueOf(b.mealSearchInput.getText()).trim();
        listener.onLog(selected, portion, kcal, Math.round(kcal) != suggestedKcal, query);
        return true;
    }
}
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
import android.os.Bundle;
import android.text.Editable;
```
Replace with:
```java
import android.content.Context;
import android.health.connect.HealthPermissions;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
import com.bumptech.glide.Glide;
import com.example.identify.Config;
import com.example.identify.R;
import com.example.identify.databinding.FragmentResultBinding;

import java.io.File;
```
Replace with:
```java
import com.bumptech.glide.Glide;
import com.example.identify.AppPrefs;
import com.example.identify.Config;
import com.example.identify.R;
import com.example.identify.core.FoodItem;
import com.example.identify.core.FoodMatcher;
import com.example.identify.core.Meals;
import com.example.identify.databinding.FragmentResultBinding;
import com.example.identify.health.FoodRepository;
import com.example.identify.health.HealthConnectRepository;
import com.example.identify.util.ExperimentLog;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONObject;

import java.io.File;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
    private static final String KEY_DETAILS_OPEN = "detailsOpen";

    private FragmentResultBinding binding;
    private ResultViewModel vm;
    private boolean detailsOpen;
```
Replace with:
```java
    private static final String KEY_DETAILS_OPEN = "detailsOpen";
    private static final String KEY_MEAL_RECORD_ID = "mealRecordId";
    private static final String KEY_MEAL_SUMMARY = "mealSummary";

    private FragmentResultBinding binding;
    private ResultViewModel vm;
    private boolean detailsOpen;
    /** Health Connect id of the meal logged from this screen, kept so Undo can delete it. */
    private String loggedRecordId;
    private String loggedSummary;
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
        if (savedInstanceState != null) detailsOpen = savedInstanceState.getBoolean(KEY_DETAILS_OPEN);
```
Replace with:
```java
        if (savedInstanceState != null) {
            detailsOpen = savedInstanceState.getBoolean(KEY_DETAILS_OPEN);
            loggedRecordId = savedInstanceState.getString(KEY_MEAL_RECORD_ID);
            loggedSummary = savedInstanceState.getString(KEY_MEAL_SUMMARY);
        }
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
        binding.detailsButton.setOnClickListener(v -> {
            detailsOpen = !detailsOpen;
            renderResult();
        });
    }
```
Replace with:
```java
        binding.detailsButton.setOnClickListener(v -> {
            detailsOpen = !detailsOpen;
            renderResult();
        });
        binding.logMealButton.setOnClickListener(v -> onLogMealClicked());
        binding.undoMealButton.setOnClickListener(v -> onUndoMealClicked());
    }
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
        outState.putBoolean(KEY_DETAILS_OPEN, detailsOpen);
```
Replace with:
```java
        outState.putBoolean(KEY_DETAILS_OPEN, detailsOpen);
        outState.putString(KEY_MEAL_RECORD_ID, loggedRecordId);
        outState.putString(KEY_MEAL_SUMMARY, loggedSummary);
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
                binding.detailsButton.setVisibility(View.GONE);
                binding.detailsText.setVisibility(View.GONE);
                hideCorrectionViews();
                break;
            case DONE:
```
Replace with:
```java
                binding.detailsButton.setVisibility(View.GONE);
                binding.detailsText.setVisibility(View.GONE);
                binding.foodCard.setVisibility(View.GONE);
                hideCorrectionViews();
                break;
            case DONE:
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
                binding.detailsText.setVisibility(View.GONE);
                hideCorrectionViews();
                binding.resultCard.setVisibility(View.VISIBLE);
```
Replace with:
```java
                binding.detailsText.setVisibility(View.GONE);
                binding.foodCard.setVisibility(View.GONE);
                hideCorrectionViews();
                binding.resultCard.setVisibility(View.VISIBLE);
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
        binding.detailsText.setVisibility(hasDetails && detailsOpen ? View.VISIBLE : View.GONE);
    }
```
Replace with:
```java
        binding.detailsText.setVisibility(hasDetails && detailsOpen ? View.VISIBLE : View.GONE);
        renderFood(r);
    }
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
    private void hideCorrectionViews() {
```
Replace with:
```java
    /** The food card: shown when the label matches the nutrition table, or after a meal was logged here. */
    private void renderFood(ResultViewModel.IdentifyResult r) {
        if (loggedRecordId != null) {
            binding.foodCard.setVisibility(View.VISIBLE);
            binding.foodText.setText(loggedSummary);
            binding.logMealButton.setVisibility(View.GONE);
            binding.undoMealButton.setVisibility(View.VISIBLE);
            binding.undoMealButton.setEnabled(true);
            return;
        }
        List<FoodMatcher.Match> matches = FoodMatcher.match(r.label, FoodRepository.foods(requireContext()), 1);
        if (matches.isEmpty()) {
            binding.foodCard.setVisibility(View.GONE);
            return;
        }
        FoodItem top = matches.get(0).item;
        binding.foodCard.setVisibility(View.VISIBLE);
        binding.foodText.setText(getString(R.string.food_detected, top.displayName(),
                HealthFormat.kcal(requireContext(), top.kcal), top.serving));
        binding.foodStatusText.setVisibility(View.GONE);
        binding.logMealButton.setVisibility(View.VISIBLE);
        binding.logMealButton.setEnabled(true);
        binding.undoMealButton.setVisibility(View.GONE);
    }

    private void onLogMealClicked() {
        ResultViewModel.IdentifyResult r = vm.getResult().getValue();
        if (r == null || binding == null) return;
        Context ctx = requireContext();
        if (!HealthConnectRepository.isAvailable(ctx)
                || !HealthConnectRepository.isGranted(ctx, HealthPermissions.WRITE_NUTRITION)) {
            Snackbar.make(binding.getRoot(), R.string.meal_need_permission, Snackbar.LENGTH_LONG)
                    .setAction(R.string.meal_open_settings,
                            v -> NavHostFragment.findNavController(this).navigate(R.id.settingsFragment))
                    .show();
            return;
        }
        LogMealDialog.show(this, r.label,
                (food, portion, kcal, kcalEdited, query) -> logMeal(r, food, portion, kcal, kcalEdited, query));
    }

    /** Writes the meal to Health Connect, logs a meal_logged event, then shows today's totals. */
    private void logMeal(ResultViewModel.IdentifyResult r, FoodItem food, double portion, double kcal,
                         boolean kcalEdited, String query) {
        if (binding == null) return;
        final Context app = requireContext().getApplicationContext();
        final Meals.Slot slot = Meals.slotForHour(LocalTime.now().getHour());
        final double protein = Meals.scaled(food.proteinG, portion);
        final double carbs = Meals.scaled(food.carbsG, portion);
        final double fat = Meals.scaled(food.fatG, portion);
        final long t0 = SystemClock.elapsedRealtime();
        binding.logMealButton.setEnabled(false);
        binding.foodStatusText.setVisibility(View.VISIBLE);
        binding.foodStatusText.setText(R.string.meal_logging);
        HealthConnectRepository.insertMeal(app, food.name, kcal, protein, carbs, fat, slot, (recordId, error) -> {
            JSONObject e = ExperimentLog.event("meal_logged");
            ExperimentLog.put(e, "run_id", r.runId);
            ExperimentLog.put(e, "model_label", r.label);
            ExperimentLog.put(e, "query", query);
            ExperimentLog.put(e, "food_id", food.id);
            ExperimentLog.put(e, "food_name", food.displayName());
            ExperimentLog.put(e, "portion", portion);
            ExperimentLog.put(e, "kcal_logged", kcal);
            ExperimentLog.put(e, "kcal_table", food.kcal * portion);
            ExperimentLog.put(e, "kcal_edited", kcalEdited);
            ExperimentLog.put(e, "protein_g", protein);
            ExperimentLog.put(e, "carbs_g", carbs);
            ExperimentLog.put(e, "fat_g", fat);
            ExperimentLog.put(e, "meal_slot", slot.name().toLowerCase(Locale.ROOT));
            ExperimentLog.put(e, "hc_record_id", recordId);
            ExperimentLog.put(e, "ms", SystemClock.elapsedRealtime() - t0);
            ExperimentLog.put(e, "error", error);
            ExperimentLog.append(app, e);
            if (binding == null || !isAdded()) return;
            if (error != null) {
                binding.logMealButton.setEnabled(true);
                binding.foodStatusText.setVisibility(View.GONE);
                Snackbar.make(binding.getRoot(), getString(R.string.meal_log_failed, error), Snackbar.LENGTH_LONG).show();
                return;
            }
            loggedRecordId = recordId;
            loggedSummary = getString(R.string.meal_logged, HealthFormat.kcal(app, kcal), food.displayName(),
                    HealthFormat.slot(app, slot));
            renderFood(r);
            showToday();
        });
    }

    /** Today's eaten, burned, and step totals under the logged meal. */
    private void showToday() {
        final Context app = requireContext().getApplicationContext();
        final long goal = AppPrefs.get(app).getStepGoal();
        HealthConnectRepository.readToday(app, (today, error) -> {
            if (binding == null || !isAdded() || today == null) return;
            binding.foodStatusText.setVisibility(View.VISIBLE);
            binding.foodStatusText.setText(getString(R.string.meal_today_summary,
                    HealthFormat.kcal(app, today.eatenKcal), HealthFormat.kcal(app, today.burnedKcal),
                    HealthFormat.steps(app, today.steps), HealthFormat.steps(app, goal)));
        });
    }

    private void onUndoMealClicked() {
        if (loggedRecordId == null || binding == null) return;
        final String id = loggedRecordId;
        final Context app = requireContext().getApplicationContext();
        binding.undoMealButton.setEnabled(false);
        HealthConnectRepository.deleteMeal(app, id, error -> {
            JSONObject e = ExperimentLog.event("meal_undone");
            ExperimentLog.put(e, "hc_record_id", id);
            ExperimentLog.put(e, "error", error);
            ExperimentLog.append(app, e);
            if (binding == null || !isAdded()) return;
            if (error != null) {
                binding.undoMealButton.setEnabled(true);
                Snackbar.make(binding.getRoot(), getString(R.string.meal_undo_failed, error), Snackbar.LENGTH_LONG).show();
                return;
            }
            loggedRecordId = null;
            loggedSummary = null;
            Snackbar.make(binding.getRoot(), R.string.meal_undone, Snackbar.LENGTH_LONG).show();
            ResultViewModel.IdentifyResult r = vm.getResult().getValue();
            if (r != null) renderFood(r);
        });
    }

    private void hideCorrectionViews() {
```

VERIFY 9.4:
```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew :core:test :app:assembleDebug :app:lintDebug --console=plain
tail -n 1 app/build/reports/lint-results-debug.txt
```
BUILD SUCCESSFUL and lint `0 errors`. If lint reports `NewApi` for `setRecordingMethod`, remove the `.setRecordingMethod(Metadata.RECORDING_METHOD_MANUAL_ENTRY)` line (it is optional metadata) and rebuild.

---

## 8. STEP 9.5: COMPLIANCE CHECKS

```bash
cd /Users/ayush/Downloads/CLAUDE/IdentifyVLM
cat > .toolchain/phase9_checks.sh <<'EOF'
#!/bin/bash
cd /Users/ayush/Downloads/CLAUDE/IdentifyVLM
echo "## 1 kt files (must be empty)"; find app/src core/src -name "*.kt"
echo "## 2 no androidx.health library (must be empty)"; grep -rn "androidx.health\|connect-client" app core --include=*.kts --include=*.java
echo "## 3 network only in ModelDownloader (must list only ModelDownloader.java)"; grep -rln "DownloadManager\|java.net\|HttpURLConnection\|okhttp\|retrofit" app/src/main/java
echo "## 4 no android imports in core (must be empty)"; grep -rn "import android" core/src
echo "## 5 forbidden words (must be empty)"; grep -rni "reinforcement\|coroutine\|StateFlow\|kotlinx\|compose" app/src core/src
echo "## 6 no em or en dash (must be empty)"; grep -rlI $'\xe2\x80\x94\|\xe2\x80\x93' --exclude-dir=third_party --exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle --exclude-dir=.cxx --exclude-dir=.toolchain .
echo "## 7 no camera or storage permission (must be empty)"; grep -n "CAMERA\|READ_EXTERNAL\|WRITE_EXTERNAL\|READ_MEDIA" app/src/main/AndroidManifest.xml
echo "## 8 no TODO (must be empty)"; grep -rn "TODO\|FIXME\|implement later\|not implemented" app/src core/src
echo "## 9 food table rows (must print 74)"; grep -vc "^#" app/src/main/assets/foods.txt
echo "## 10 no new permission (must print 5)"; grep -c "android.permission.health" app/src/main/AndroidManifest.xml
echo "## 11 database version unchanged (must print version = 1)"; grep -o "version = [0-9]*" app/src/main/java/com/example/identify/data/AppDatabase.java
EOF
bash .toolchain/phase9_checks.sh
```
Every check must give the stated output.

---

## 9. STEP 9.6: INSTALL AND TEST ON THE PHONE

```bash
ADB=/Users/ayush/Library/Android/sdk/platform-tools/adb
"$ADB" devices
"$ADB" install -r /Users/ayush/Downloads/CLAUDE/IdentifyVLM/app/build/outputs/apk/debug/app-debug.apk
"$ADB" shell am start -n com.example.identify/.MainActivity
sleep 3
"$ADB" logcat -d -b crash | grep -A3 "Process: com.example.identify" | tail -n 10
```
VERIFY: `Success`, no new crash for `com.example.identify`.

Send the user exactly these instructions:

1. Take or pick a photo of a food (a real meal, or a food picture on another screen) and tap Identify.
2. Below the answer, a card should say "Looks like food: ..." with calories. If the model named something that is not food, there is no card; try another photo.
3. Tap "Log meal". Check the food choice (type another name in the Food box if the guess is wrong), set the portion, check the calories, and tap "Log".
4. The card should say "Logged ... kcal of ... as <meal type>." and then show today's eaten, burned, and step totals.
5. Optional: tap "Undo" to remove the meal again, or keep it. Then tell me the food, the kcal, and whether you kept it.

Then run:
```bash
ADB=/Users/ayush/Library/Android/sdk/platform-tools/adb
"$ADB" shell grep "meal_\|health_read" /sdcard/Android/data/com.example.identify/files/experiments/experiment_log.jsonl | tail -n 4
"$ADB" logcat -d -s IdentifyHealth:I | tail -n 10
"$ADB" logcat -d -b crash | grep -A3 "Process: com.example.identify" | tail -n 10
```
VERIFY: a `meal_logged` line with `"error":null` and a non-null `hc_record_id`; the logcat line `meal written id=...`; if the meal was kept, the last `health_read` has `eaten_kcal` at least the logged kcal; if Undo was used, a `meal_undone` line with `"error":null`; no crash.

---

## 10. STEP 9.7: DOCS AND FINAL COMMIT

Append to `README.md`:
```
## 12. Food logging

When the identified label matches the nutrition table shipped in `app/src/main/assets/foods.txt` (74 foods: McDonald's US menu items and common foods), the result screen shows "Looks like food" with the calories for one serving. "Log meal" opens a dialog to pick the food (type another name if the guess is wrong), set the portion (0.5x to 3x), and check or edit the calories. "Log" writes one NutritionRecord into Health Connect (calories, protein, carbohydrate, fat, meal name, and a meal type from the time of day), then shows today's eaten, burned, and step totals. "Undo" deletes that record again.

The model only names the food; calories come from the table and the user confirms them. Table values are approximate reference values for one serving (McDonald's US published figures and USDA FoodData Central typical servings, compiled for this prototype), not measurements of the actual plate. Matching (`FoodMatcher` in `:core`) compares words of the label with each food's names and aliases, ignores plurals and filler words, and prefers branded rows only when the brand is named.

Experiment log events: `meal_logged` (run_id, model label, query, food, portion, table kcal, logged kcal, whether the user edited it, macros, meal slot, Health Connect record id, error) and `meal_undone`.
```

Append the 9.6 and 9.7 lines to `PROGRESS.md`, then commit with a descriptive message (Rule 13).

Final message to the user: what was built, test and lint results, the meal they logged and what Health Connect returned, and anything in `BLOCKERS.md`.

---

## 11. TROUBLESHOOTING

| Symptom | Fix |
|---|---|
| FoodCatalogTest cannot find `../app/src/main/assets/foods.txt` | The test must run from `core/` (Gradle default). Do not move the file. |
| A table line fails to parse | The message names the line. Each line needs exactly 11 `;`-separated fields; numbers use `.` as decimal point. |
| No food card for an obvious food | Add the model's word as an alias on the right row in `foods.txt` and add a FoodMatcherTest case for it. |
| `meal_logged` error mentions permission | The user removed WRITE_NUTRITION. Settings > Health Connect > Connect. |
| Health Connect rejects the record with an invalid argument | Check that start is before end (Section 6 uses a 60 second interval) and kcal is between 1 and 5000. |
| Lint `NewApi` on `setRecordingMethod` | Remove that one builder call (optional metadata). |
| Dialog closes without logging | The positive button listener must be attached in `setOnShowListener` (Section 7), not in the builder. |

---

## 12. LATER PHASES (do NOT implement now)

- Phase 10, nudges: after a logged meal and a few times a day, compare eaten against burned kcal and steps against the goal, and show a personal message, for example "Ayush, you are at 3,200 of 10,000 steps and this double cheeseburger is about 450 kcal. A 25 minute walk would burn about 150 kcal." Needs a user name and step goal setting, `POST_NOTIFICATIONS`, and `READ_HEALTH_DATA_IN_BACKGROUND` for checks while the app is closed.
- Phase 11, faster capture: a quick-capture entry point (launcher shortcut or widget). Background photos are not allowed by Android.
