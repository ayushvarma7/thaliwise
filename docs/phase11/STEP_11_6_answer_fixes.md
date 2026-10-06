# STEP 11.6: Answer fixes (no example dishes, forced three-line format, eaten 0 kcal)

Found in the Identification history and on the result screen while the README screenshots were taken (with the user's permission):

1. **Example-dish fallback.** A photo of a packaged snack came back as "Chicken tikka masala", the first example dish in the system prompt. A small model that cannot read a photo tends to repeat an example from its prompt. Fix: the prompt names no example dishes or products, and it gives an explicit way out: `Unknown food`. "Unknown food" matches no table row, so the result shows the "No food recognized" card with Name the food.
2. **Format drift.** The model still sometimes skips the Cuisine line and writes several sentences. Fix: llama.cpp's grammar sampler forces exactly `Label: ...`, `Cuisine: ...`, `Description: ...`. The grammar (GBNF) lives in `PromptBuilder.ANSWER_GRAMMAR` (tested in `:core`) and goes through JNI to `vlm_bridge.cpp`, which puts it first in the sampler chain. If llama.cpp cannot parse it, the bridge samples without it and logs that; it never crashes. The generation stats record `grammar` as `on`, `failed`, or `off`.
3. **"no data kcal".** With the nutrition permission granted and nothing logged yet today, the eaten total came back as "no data", and the Settings line read "no data kcal". Fix: an empty nutrition total with no error is 0 kcal (the coach budget rules then work from the first meal of the day), and the three status lines put the unit next to real numbers only.

Also: a correction made after an "Unknown food" or "Not food" answer never becomes a few-shot prompt example. "You said Unknown food. Correct answer: X" would teach the model to answer X whenever it is unsure. Such photos still teach the kNN memory, where the photo itself is compared.

EDIT `core/src/main/java/com/example/identify/core/PromptBuilder.java`
Find:
```java
            "You identify the food or drink in a photo. Name the specific dish the way people order it, for example chicken tikka masala, carne asada tacos, pad thai, pepperoni pizza, or a menu item such as a Big Mac.\n"
          + "If several foods are shown, name the main one. If there is no food or drink, use the label Not food.\n"
          + "For packaged food or drinks, use the product name on the package, for example Doritos Nacho Cheese or Diet Coke.\n"
```
Replace with:
```java
            "You identify the food or drink in a photo. Name the specific dish the way people order it, or the menu item if it comes from a restaurant chain.\n"
          + "If several foods are shown, name the main one. If there is no food or drink, use the label Not food. If you cannot tell what the food is, use the label Unknown food. Do not guess a dish that you do not see.\n"
          + "For packaged food or drinks, read the product name on the package and use it as the label.\n"
```

EDIT `core/src/main/java/com/example/identify/core/PromptBuilder.java`
Find:
```java
    public static final String USER_PROMPT = "What food is in this photo?";
```
Replace with:
```java
    public static final String USER_PROMPT = "What food is in this photo?";

    /**
     * GBNF for llama.cpp's grammar sampler: the reply is exactly the three lines, so the model cannot
     * answer in free text or skip the cuisine. Fields exclude line breaks and angle brackets, and the label
     * is at most AnswerParser.MAX_LABEL_CHARS characters.
     */
    public static final String ANSWER_GRAMMAR =
            "root ::= \"Label: \" label \"\\nCuisine: \" cuisine \"\\nDescription: \" description\n"
          + "label ::= [^\\n<>]{1,60}\n"
          + "cuisine ::= [^\\n<>]{1,30}\n"
          + "description ::= [^\\n<>]{1,200}\n";
```

EDIT `core/src/main/java/com/example/identify/core/FewShotSelector.java`
Find:
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
```
Replace with:
```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
```

EDIT `core/src/main/java/com/example/identify/core/FewShotSelector.java`
Find:
```java
    private FewShotSelector() {}
```
Replace with:
```java
    private FewShotSelector() {}

    /**
     * Model labels that name no dish. A correction after one of these would read "You said Unknown food.
     * Correct answer: X" and teach the model to answer X whenever it is unsure, so it is never an example.
     */
    static final Set<String> NON_ANSWERS = new HashSet<>(Arrays.asList("unknown food", "unknown", "not food"));
```

EDIT `core/src/main/java/com/example/identify/core/FewShotSelector.java`
Find:
```java
            if (e == null || isBlank(e.modelLabel) || isBlank(e.correctLabel)) continue;
```
Replace with:
```java
            if (e == null || isBlank(e.modelLabel) || isBlank(e.correctLabel)) continue;
            if (NON_ANSWERS.contains(norm(e.modelLabel))) continue;
```

CREATE `core/src/test/java/com/example/identify/core/AnswerFormatTest.java`
```java
package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

public class AnswerFormatTest {

    @Test
    public void grammarForcesTheThreeLinesInOrder() {
        String g = PromptBuilder.ANSWER_GRAMMAR;
        int label = g.indexOf("root ::= \"Label: \"");
        int cuisine = g.indexOf("\"\\nCuisine: \"");
        int description = g.indexOf("\"\\nDescription: \"");
        assertTrue(label == 0);
        assertTrue(cuisine > label);
        assertTrue(description > cuisine);
        assertTrue(g.contains("label ::= [^\\n<>]{1," + AnswerParser.MAX_LABEL_CHARS + "}"));
    }

    @Test
    public void aGrammarShapedReplyParses() {
        AnswerParser.ParsedAnswer a = AnswerParser.parse(
                "Label: Vegetable rotini pasta\nCuisine: Italian\nDescription: Spiral pasta with mixed vegetables.");
        assertEquals("Vegetable rotini pasta", a.label);
        assertEquals("Italian", a.cuisine);
        assertEquals("Spiral pasta with mixed vegetables.", a.description);
    }

    @Test
    public void thePromptNamesNoExampleDishesAndOffersUnknownFood() {
        String p = PromptBuilder.SYSTEM_BASE.toLowerCase(Locale.ROOT);
        for (String example : Arrays.asList("tikka", "tacos", "pad thai", "pizza", "big mac", "doritos", "diet coke")) {
            assertFalse(example, p.contains(example));
        }
        assertTrue(PromptBuilder.SYSTEM_BASE.contains("Unknown food"));
    }

    @Test
    public void unknownFoodMatchesNoTableRow() throws IOException {
        assertTrue(FoodMatcher.match("Unknown food", FoodCatalogTest.shipped(), 1).isEmpty());
    }

    @Test
    public void nonAnswerCorrectionsAreNeverExamples() {
        float[] e = {1f, 0f};
        List<CorrectionExample> pool = Arrays.asList(
                new CorrectionExample("Unknown food", "Snickers bar", e, 3),
                new CorrectionExample("Not food", "Pasta", e, 2),
                new CorrectionExample("Chicken curry", "Butter chicken", e, 1));
        List<CorrectionExample> picked = FewShotSelector.select(e, pool, 3, 5);
        assertEquals(1, picked.size());
        assertEquals("Butter chicken", picked.get(0).correctLabel);
    }
}
```

EDIT `app/src/main/java/com/example/identify/model/VlmEngine.java`
Find:
```java
import com.example.identify.Config;
```
Replace with:
```java
import com.example.identify.Config;
import com.example.identify.core.PromptBuilder;
```

EDIT `app/src/main/java/com/example/identify/model/VlmEngine.java`
Find:
```java
                Config.MAX_NEW_TOKENS, Config.TEMPERATURE, Config.MIN_P,
                Config.REPEAT_PENALTY, Config.TOP_K);
```
Replace with:
```java
                Config.MAX_NEW_TOKENS, Config.TEMPERATURE, Config.MIN_P,
                Config.REPEAT_PENALTY, Config.TOP_K,
                PromptBuilder.ANSWER_GRAMMAR.getBytes(StandardCharsets.UTF_8));
```

EDIT `app/src/main/java/com/example/identify/model/VlmEngine.java`
Find:
```java
            float temperature, float minP, float repeatPenalty, int topK);
```
Replace with:
```java
            float temperature, float minP, float repeatPenalty, int topK, byte[] grammarUtf8);
```

EDIT `app/src/main/cpp/vlm_bridge.cpp`
Find:
```cpp
        jfloat temperature, jfloat minP, jfloat repeatPenalty, jint topK) {
```
Replace with:
```cpp
        jfloat temperature, jfloat minP, jfloat repeatPenalty, jint topK, jbyteArray jGrammar) {
```

EDIT `app/src/main/cpp/vlm_bridge.cpp`
Find:
```cpp
    const std::string user      = from_utf8_bytes(env, jUser);
```
Replace with:
```cpp
    const std::string user      = from_utf8_bytes(env, jUser);
    const std::string grammar   = from_utf8_bytes(env, jGrammar);   // empty: no grammar
```

EDIT `app/src/main/cpp/vlm_bridge.cpp`
Find:
```cpp
    // order: penalties -> top_k -> min_p -> temp -> dist
    llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
```
Replace with:
```cpp
    // order: grammar -> penalties -> top_k -> min_p -> temp -> dist
    llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    // The grammar comes first, so every later sampler sees only tokens that keep the reply in the
    // three-line format. A grammar that does not parse is skipped, never fatal.
    std::string grammar_status = "off";
    if (!grammar.empty()) {
        llama_sampler* g = llama_sampler_init_grammar(s->vocab, grammar.c_str(), "root");
        if (g) {
            llama_sampler_chain_add(smpl, g);
            grammar_status = "on";
        } else {
            grammar_status = "failed";
            LOGE("answer grammar did not parse; sampling without it");
        }
    }
```

EDIT `app/src/main/cpp/vlm_bridge.cpp`
Find:
```cpp
    kv_str(j, "stop_reason", stop_reason);
```
Replace with:
```cpp
    kv_str(j, "stop_reason", stop_reason);
    kv_str(j, "grammar", grammar_status);
```

EDIT `app/src/main/java/com/example/identify/health/HealthConnectRepository.java`
Find:
```java
                if (v != null) p.eatenKcal = kcal(v);
```
Replace with:
```java
                // Nothing logged yet today is 0 kcal eaten, not "no data": the read permission is granted.
                if (v != null) p.eatenKcal = kcal(v);
                else if (err == null) p.eatenKcal = 0;
```

EDIT `app/src/main/java/com/example/identify/ui/HealthFormat.java`
Find:
```java
    static String slot(Context ctx, Meals.Slot slot) {
```
Replace with:
```java
    /** "1,234 steps", or "no data" without a unit. */
    static String stepsWithUnit(Context ctx, long v) {
        return v < 0 ? ctx.getString(R.string.health_no_data) : ctx.getString(R.string.steps_with_unit, steps(ctx, v));
    }

    /** "1,234 kcal", or "no data" without a unit. */
    static String kcalWithUnit(Context ctx, double v) {
        return Double.isNaN(v) ? ctx.getString(R.string.health_no_data) : ctx.getString(R.string.kcal_with_unit, kcal(ctx, v));
    }

    static String slot(Context ctx, Meals.Slot slot) {
```

EDIT `app/src/main/res/values/strings.xml`
Find:
```xml
    <string name="health_today_format">Today: %1$s steps (goal %2$s, %3$s to go)\nBurned: %4$s kcal total, %5$s kcal active\nEaten (logged in Health Connect): %6$s kcal</string>
```
Replace with:
```xml
    <string name="health_today_format">Today: %1$s (goal %2$s steps, %3$s to go)\nBurned: %4$s total, %5$s active\nEaten (logged in Health Connect): %6$s</string>
    <string name="steps_with_unit">%1$s steps</string>
    <string name="kcal_with_unit">%1$s kcal</string>
```

EDIT `app/src/main/res/values/strings.xml`
Find:
```xml
    <string name="meal_today_summary">Today: %1$s kcal eaten, %2$s kcal burned, %3$s of %4$s steps</string>
```
Replace with:
```xml
    <string name="meal_today_summary">Today: %1$s eaten, %2$s burned, %3$s of %4$s steps</string>
```

EDIT `app/src/main/res/values/strings.xml`
Find:
```xml
    <string name="today_burned">Burned %1$s kcal (%2$s active)</string>
```
Replace with:
```xml
    <string name="today_burned">Burned %1$s (%2$s active)</string>
```

EDIT `app/src/main/java/com/example/identify/ui/SettingsFragment.java`
Find:
```java
                    formatSteps(today.steps), formatSteps(goal), formatSteps(today.stepsRemaining(goal)),
                    formatKcal(today.burnedKcal), formatKcal(today.activeKcal), formatKcal(today.eatenKcal));
```
Replace with:
```java
                    HealthFormat.stepsWithUnit(requireContext(), today.steps), formatSteps(goal),
                    HealthFormat.stepsWithUnit(requireContext(), today.stepsRemaining(goal)),
                    HealthFormat.kcalWithUnit(requireContext(), today.burnedKcal),
                    HealthFormat.kcalWithUnit(requireContext(), today.activeKcal),
                    HealthFormat.kcalWithUnit(requireContext(), today.eatenKcal));
```

EDIT `app/src/main/java/com/example/identify/ui/ResultFragment.java`
Find:
```java
                    HealthFormat.kcal(app, today.eatenKcal), HealthFormat.kcal(app, today.burnedKcal),
```
Replace with:
```java
                    HealthFormat.kcalWithUnit(app, today.eatenKcal), HealthFormat.kcalWithUnit(app, today.burnedKcal),
```

EDIT `app/src/main/java/com/example/identify/ui/TodayFragment.java`
Find:
```java
                HealthFormat.kcal(ctx, Double.NaN), HealthFormat.kcal(ctx, Double.NaN)));
```
Replace with:
```java
                HealthFormat.kcalWithUnit(ctx, Double.NaN), HealthFormat.kcalWithUnit(ctx, Double.NaN)));
```

EDIT `app/src/main/java/com/example/identify/ui/TodayFragment.java`
Find:
```java
                    HealthFormat.kcal(ctx, today.burnedKcal), HealthFormat.kcal(ctx, today.activeKcal)));
```
Replace with:
```java
                    HealthFormat.kcalWithUnit(ctx, today.burnedKcal), HealthFormat.kcalWithUnit(ctx, today.activeKcal)));
```

EDIT `docs/phase11/README.md`
Find:
```markdown
| 11.5 | `STEP_11_5_device_fixes.md`: label cleaning (copied prompt words, free text), product names in the prompt, 20 packaged snacks, threads limited to fast cores | US-2.2, US-2.3, US-3.1 |
```
Replace with:
```markdown
| 11.5 | `STEP_11_5_device_fixes.md`: label cleaning (copied prompt words, free text), product names in the prompt, 20 packaged snacks, threads limited to fast cores | US-2.2, US-2.3, US-3.1 |
| 11.6 | `STEP_11_6_answer_fixes.md`: no example dishes in the prompt, "Unknown food", grammar-forced three-line answer, no non-answer few-shot examples, eaten 0 kcal instead of "no data" | US-2.2, US-2.3, US-4.1 |
```

## VERIFY 11.6

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew :core:test :app:assembleDebug :app:lintDebug --console=plain
tail -n 1 app/build/reports/lint-results-debug.txt
bash .toolchain/phase11_checks.sh
```
BUILD SUCCESSFUL (the native bridge recompiles); 24 core test classes, 0 failures; lint 0 errors and no new warning; every check prints its expected output except check 13, which also lists `settings.gradle.kts` (the rename) and `app/src/main/cpp/vlm_bridge.cpp` (this step). Ask the user before installing. On the phone, a run's `native_generate.grammar` must be `on`.

Commit subject: `Drop example dishes from the prompt, force the answer format, show 0 kcal eaten`.
