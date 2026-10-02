# STEP 11.5: Fixes from using the Phase 11 build on the phone

Found while the app was used on the Pixel 8 after the Phase 11 install. Written generically: no personal data from the phone is used here.

1. **Copied prompt words in labels.** The answer template said `Label: <dish name, at most 6 words>`, and the model sometimes copied the instruction into its answer ("Pad thai, at most 6 words"). The copied words then went into the history, the saved corrections, and the memory. Fix: the prompt keeps its rules outside the template lines; the parser strips copied instruction words, template words, and free-text openings; labels saved earlier are cleaned when the memory and the history read them.
2. **Free-text answers for packaged food.** For a packaged snack the model wrote sentences ("The food in this photo is ... The package shows ...") instead of the three lines. The parser then used the first 60 characters, and the matcher found a wrong table row through generic words like "peanut butter". Fix: the prompt says to use the product name for packaged food; without a Label line the parser takes the first sentence and drops the opening; the matcher ignores "this", "photo", "is", and similar words; and the table gains 20 common US packaged snacks, with their diet tags.
3. **Too many threads.** Settings allowed up to 8 threads. On a Pixel 8 (4 little, 4 mid, 1 big core), threads beyond the 5 fast cores land on the little cores, and text generation became several times slower. Fix: the thread count is limited to the cores outside the slowest cluster (`TelemetryStats.fastCoreCount`); the Settings slider ends there, and a saved value above it is clamped.

Snack values are typical US single-serve package labels (approximate), like the other "Typical package label" rows. The table grows from 300 to 320 rows; tests, checks, and the README follow.

EDIT `core/src/main/java/com/example/identify/core/PromptBuilder.java`
Find:
```java
          + "Reply in exactly three lines and nothing else:\n"
          + "Label: <dish name, at most 6 words>\n"
          + "Cuisine: <one cuisine, for example Indian, Mexican, Chinese, Japanese, Italian, American>\n"
          + "Description: <one sentence>";
```
Replace with:
```java
          + "For packaged food or drinks, use the product name on the package, for example Doritos Nacho Cheese or Diet Coke.\n"
          + "The label is only the dish or product name, at most 6 words. The cuisine is one or two words, for example Indian, Mexican, Chinese, Japanese, Italian, American, or Snacks.\n"
          + "Reply in exactly three lines and nothing else:\n"
          + "Label: <dish name>\n"
          + "Cuisine: <cuisine>\n"
          + "Description: <one sentence>";
```

REPLACE `core/src/main/java/com/example/identify/core/AnswerParser.java`
```java
package com.example.identify.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the model's three-line answer (Label, Cuisine, Description). Small models drift: they sometimes
 * copy instruction words into the label (", at most 6 words"), return template words ("dish name"), or
 * answer in free text ("The food in this photo is ..."). Labels are cleaned of all three, and without a
 * Label line the first sentence of the answer becomes the label.
 */
public final class AnswerParser {
    private AnswerParser() {}

    public static final int MAX_LABEL_CHARS = 60;

    /** Instruction words copied from the prompt at the end of a label: ", at most 6 words", "(max 6 words)". */
    private static final Pattern ECHO = Pattern.compile(
            "[\\s,;:(\\[-]*(at most|no more than|up to|maximum|max)\\s+\\d+\\s+words?[\\s)\\].]*$",
            Pattern.CASE_INSENSITIVE);

    /** Openings of free-text answers: "The food in this photo is", "This image shows a", "It is". */
    private static final Pattern LEAD_IN = Pattern.compile(
            "^(the\\s+(main\\s+)?(food|dish|item|drink|snack)s?\\s+(in|shown in|on)\\s+(this|the)\\s+"
                    + "(photo|image|picture)\\s+(is|are|appears to be|looks like)"
                    + "|this\\s+(photo|image|picture)\\s+(shows|contains|is of|is)"
                    + "|this\\s+is|these\\s+are|it\\s+is|it's|there\\s+(is|are))\\s+((a|an|some)\\s+)?",
            Pattern.CASE_INSENSITIVE);

    /** A sentence end: '.', '!', or '?' followed by a space or the end of the text. */
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?](\\s|$)");

    /** Template words a model may return unchanged. */
    private static final Set<String> PLACEHOLDERS = new HashSet<>(Arrays.asList(
            "dish name", "cuisine", "one cuisine", "one sentence", "label", "description"));

    public static final class ParsedAnswer {
        public final String label;
        public final String description;
        /** The model's "Cuisine:" line, cleaned; empty when it gave none. */
        public final String cuisine;

        public ParsedAnswer(String label, String description) {
            this(label, description, "");
        }

        public ParsedAnswer(String label, String description, String cuisine) {
            this.label = label;
            this.description = description;
            this.cuisine = cuisine;
        }
    }

    public static ParsedAnswer parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new ParsedAnswer("Unknown", "");
        String label = null;
        String description = null;
        String cuisine = null;
        List<String> other = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            String l = line.replace("*", "").trim();
            if (l.isEmpty()) continue;
            String lower = l.toLowerCase(Locale.ROOT);
            if (label == null && lower.startsWith("label:")) {
                label = l.substring(6).trim();
            } else if (cuisine == null && lower.startsWith("cuisine:")) {
                cuisine = l.substring(8).trim();
            } else if (description == null && lower.startsWith("description:")) {
                description = l.substring(12).trim();
            } else {
                other.add(l);
            }
        }
        if (label == null || label.isEmpty()) {
            // No Label line: the first sentence is the label, the rest of that line starts the description.
            label = "";
            if (!other.isEmpty()) {
                String first = other.remove(0);
                Matcher end = SENTENCE_END.matcher(first);
                if (end.find()) {
                    String rest = first.substring(end.end()).trim();
                    first = first.substring(0, end.start());
                    if (!rest.isEmpty()) other.add(0, rest);
                }
                label = first;
            }
        }
        if (description == null) description = String.join(" ", other).trim();
        label = cleanLabel(label);
        if (label.isEmpty()) label = "Unknown";
        return new ParsedAnswer(label, description, cuisine == null ? "" : cleanLabel(cuisine));
    }

    /**
     * A label without surrounding quotes, a final period or comma, template brackets and words, free-text
     * openings, or copied instruction words; at most MAX_LABEL_CHARS. Also used for labels saved earlier.
     */
    public static String cleanLabel(String s) {
        if (s == null) return "";
        String t = s.replace('<', ' ').replace('>', ' ').replaceAll("\\s+", " ").trim();
        t = stripEnds(t);
        t = LEAD_IN.matcher(t).replaceFirst("");
        t = ECHO.matcher(t).replaceFirst("");
        t = stripEnds(t);
        if (PLACEHOLDERS.contains(t.toLowerCase(Locale.ROOT))) return "";
        if (t.length() > MAX_LABEL_CHARS) t = t.substring(0, MAX_LABEL_CHARS).trim();
        return t;
    }

    private static String stripEnds(String s) {
        String t = s.trim();
        while (!t.isEmpty() && (t.startsWith("\"") || t.startsWith("'"))) t = t.substring(1).trim();
        while (!t.isEmpty() && (t.endsWith("\"") || t.endsWith("'") || t.endsWith(".") || t.endsWith(","))) {
            t = t.substring(0, t.length() - 1).trim();
        }
        return t;
    }
}
```

CREATE `core/src/test/java/com/example/identify/core/AnswerParserEchoTest.java`
```java
package com.example.identify.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AnswerParserEchoTest {

    @Test
    public void copiedInstructionWordsAreRemoved() {
        AnswerParser.ParsedAnswer a = AnswerParser.parse(
                "Label: Pad thai, at most 6 words\nCuisine: Thai\nDescription: Stir-fried rice noodles.");
        assertEquals("Pad thai", a.label);
        assertEquals("Thai", a.cuisine);
        assertEquals("Stir-fried rice noodles.", a.description);
        assertEquals("Bibimbap", AnswerParser.parse("Label: Bibimbap (at most 6 words)").label);
        assertEquals("Pho", AnswerParser.parse("Label: Pho, max 6 words.").label);
    }

    @Test
    public void templateWordsMeanUnknown() {
        AnswerParser.ParsedAnswer a = AnswerParser.parse(
                "Label: <dish name>\nCuisine: <cuisine>\nDescription: <one sentence>");
        assertEquals("Unknown", a.label);
        assertEquals("", a.cuisine);
    }

    @Test
    public void freeTextUsesTheFirstSentenceWithoutTheOpening() {
        AnswerParser.ParsedAnswer a = AnswerParser.parse(
                "The food in this photo is Doritos Nacho Cheese. The bag shows the brand name.");
        assertEquals("Doritos Nacho Cheese", a.label);
        assertEquals("The bag shows the brand name.", a.description);
        assertEquals("bowl of pho with herbs", AnswerParser.parse("This image shows a bowl of pho with herbs.").label);
        assertEquals("burrito", AnswerParser.parse("It is a burrito").label);
    }

    @Test
    public void savedLabelsAreCleanedAndNormalLabelsKept() {
        assertEquals("Pad thai", AnswerParser.cleanLabel("Pad thai, at most 6 words"));
        assertEquals("", AnswerParser.cleanLabel(null));
        assertEquals("Pad see ew", AnswerParser.cleanLabel("Pad see ew"));
        assertEquals("Reese's Peanut Butter Cups", AnswerParser.cleanLabel("Reese's Peanut Butter Cups"));
        assertEquals("Not food", AnswerParser.cleanLabel("Not food"));
    }
}
```

EDIT `core/src/main/java/com/example/identify/core/FoodMatcher.java`
Find:
```java
            "side", "one", "two", "small", "medium", "large"));
```
Replace with:
```java
            "side", "one", "two", "small", "medium", "large",
            // words of free-text answers such as "The food in this photo is ..."
            "photo", "image", "picture", "this", "that", "is", "are", "it"));
```

EDIT `core/src/main/java/com/example/identify/core/FoodMatcher.java`
Find:
```java
            String s = stem(w);
            if (!STOPWORDS.contains(s)) out.add(s);
```
Replace with:
```java
            String s = stem(w);
            // The raw word is checked too: the plural-s stem turns "this" into "thi".
            if (!STOPWORDS.contains(w) && !STOPWORDS.contains(s)) out.add(s);
```

EDIT `app/src/main/assets/foods.txt`
Find:
```text
injera_wat;Injera with wat;;Ethiopian;injera|doro wat|wat|ethiopian food;plate;450;650;30;85;20;Typical restaurant serving (approximate)
```
Replace with:
```text
injera_wat;Injera with wat;;Ethiopian;injera|doro wat|wat|ethiopian food;plate;450;650;30;85;20;Typical restaurant serving (approximate)
# Packaged snacks: typical US single-serve package labels (approximate).
mms_milk_chocolate;M&M's Milk Chocolate;;Snacks;m&ms|m&m's|plain m&ms|milk chocolate m&ms;1.69 oz pack;48;240;2;34;10;Typical package label (approximate)
mms_peanut;M&M's Peanut;;Snacks;peanut m&ms|m&ms peanut;1.74 oz pack;49;250;5;30;13;Typical package label (approximate)
mms_peanut_butter;M&M's Peanut Butter;;Snacks;peanut butter m&ms|m&ms peanut butter;1.63 oz pack;46;240;5;25;14;Typical package label (approximate)
snickers;Snickers bar;;Snacks;snickers;1.86 oz bar;52;250;4;33;12;Typical package label (approximate)
reeses_cups;Reese's Peanut Butter Cups;;Snacks;reeses|reese's cups|peanut butter cups;2 cups (1.5 oz);42;210;5;24;13;Typical package label (approximate)
kit_kat;Kit Kat bar;;Snacks;kit kat|kitkat;1.5 oz bar;42;210;3;27;11;Typical package label (approximate)
twix;Twix;;Snacks;twix bar|twix bars;2 bars (1.79 oz);50;250;2;33;12;Typical package label (approximate)
hersheys_bar;Hershey's milk chocolate bar;;Snacks;hersheys|hershey bar|hersheys bar;1.55 oz bar;43;210;3;26;13;Typical package label (approximate)
skittles;Skittles;;Snacks;skittles original;2.17 oz pack;61;250;0;56;2.5;Typical package label (approximate)
doritos_nacho;Doritos Nacho Cheese;;Snacks;doritos|nacho cheese doritos;1 oz bag;28;150;2;18;8;Typical package label (approximate)
lays_classic;Lay's Classic potato chips;;Snacks;lays|lays chips|lays classic;1 oz bag;28;160;2;15;10;Typical package label (approximate)
cheetos_crunchy;Cheetos Crunchy;;Snacks;cheetos;1 oz bag;28;160;2;15;10;Typical package label (approximate)
pringles_original;Pringles Original;;Snacks;pringles;16 crisps (1 oz);28;150;1;15;9;Typical package label (approximate)
oreo;Oreo cookies;;Snacks;oreo|oreos;3 cookies;34;160;1;25;7;Typical package label (approximate)
goldfish;Goldfish crackers;;Snacks;goldfish|goldfish cheddar;55 pieces (1 oz);30;140;3;20;5;Typical package label (approximate)
cheez_it;Cheez-It crackers;;Snacks;cheez it|cheez its|cheezits;27 crackers (1 oz);30;150;3;17;8;Typical package label (approximate)
clif_bar;Clif Bar, chocolate chip;;Snacks;clif bar|cliff bar;1 bar (2.4 oz);68;250;10;44;5;Typical package label (approximate)
kind_bar;KIND bar, dark chocolate nuts and sea salt;;Snacks;kind bar|kind nut bar;1 bar (1.4 oz);40;180;6;16;15;Typical package label (approximate)
nature_valley_crunchy;Nature Valley Crunchy granola bars;;Snacks;nature valley|granola bar|granola bars;2 bars (1.49 oz);42;190;4;29;7;Typical package label (approximate)
protein_bar;Protein bar;;Snacks;protein bar|quest bar|protein bars;1 bar (60 g);60;200;20;22;8;Typical package label (approximate)
```

EDIT `app/src/main/assets/food_tags.txt`
Find:
```text
injera_wat;meat|egg|dairy
```
Replace with:
```text
injera_wat;meat|egg|dairy
# Packaged snacks (same order as foods.txt). Snickers contains egg whites; Pringles contain wheat starch.
mms_milk_chocolate;dairy|sweet
mms_peanut;dairy|sweet
mms_peanut_butter;dairy|sweet
snickers;egg|dairy|sweet
reeses_cups;dairy|sweet
kit_kat;dairy|gluten|sweet
twix;dairy|gluten|sweet
hersheys_bar;dairy|sweet
skittles;sweet
doritos_nacho;dairy|fried
lays_classic;fried
cheetos_crunchy;dairy|fried
pringles_original;gluten|fried
oreo;gluten|sweet
goldfish;dairy|gluten
cheez_it;dairy|gluten
clif_bar;gluten|sweet
kind_bar;-
nature_valley_crunchy;gluten|sweet
protein_bar;dairy
```

EDIT `core/src/test/java/com/example/identify/core/FoodTagsTest.java`
Find:
```java
        assertEquals(300, tagIds.size());
```
Replace with:
```java
        assertEquals(320, tagIds.size());
```

CREATE `core/src/test/java/com/example/identify/core/FoodMatcherSnackTest.java`
```java
package com.example.identify.core;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class FoodMatcherSnackTest {

    private static String top(String text) throws IOException {
        List<FoodMatcher.Match> m = FoodMatcher.match(text, FoodCatalogTest.shipped(), 1);
        return m.isEmpty() ? "" : m.get(0).item.id;
    }

    @Test
    public void productNamesBeatGenericFoods() throws IOException {
        assertEquals("reeses_cups", top("Reese's Peanut Butter Cups"));
        assertEquals("snickers", top("Snickers bar"));
        assertEquals("doritos_nacho", top("Doritos Nacho Cheese"));
        assertEquals("lays_classic", top("Lay's Classic potato chips"));
        assertEquals("mms_peanut", top("Peanut M&M's"));
    }

    @Test
    public void genericFoodsStillWin() throws IOException {
        assertEquals("pbj_sandwich", top("Peanut butter and jelly sandwich"));
        assertEquals("peanut_butter", top("Peanut butter"));
        assertEquals("potato_chips", top("potato chips"));
    }

    @Test
    public void freeTextWordsAreIgnored() {
        assertEquals(Arrays.asList("dorito", "nacho", "cheese"),
                FoodMatcher.tokens("The food in this photo is Doritos Nacho Cheese"));
    }
}
```

EDIT `app/src/main/java/com/example/identify/learning/EmbeddingCache.java`
Find:
```java
import com.example.identify.core.EmbeddingCodec;
```
Replace with:
```java
import com.example.identify.core.AnswerParser;
import com.example.identify.core.EmbeddingCodec;
```

EDIT `app/src/main/java/com/example/identify/learning/EmbeddingCache.java`
Find:
```java
        return new Entry(e.id, EmbeddingCodec.fromBytes(e.embedding), e.predictedLabel, e.finalLabel,
                e.userCorrection, e.accepted, e.timestampMillis);
```
Replace with:
```java
        // Labels saved before step 11.5 can hold copied prompt words (", at most 6 words"); clean them here.
        return new Entry(e.id, EmbeddingCodec.fromBytes(e.embedding), AnswerParser.cleanLabel(e.predictedLabel),
                AnswerParser.cleanLabel(e.finalLabel), e.userCorrection, e.accepted, e.timestampMillis);
```

EDIT `app/src/main/java/com/example/identify/ui/HistoryAdapter.java`
Find:
```java
import com.example.identify.R;
import com.example.identify.data.CorrectionEntity;
```
Replace with:
```java
import com.example.identify.R;
import com.example.identify.core.AnswerParser;
import com.example.identify.data.CorrectionEntity;
```

EDIT `app/src/main/java/com/example/identify/ui/HistoryAdapter.java`
Find:
```java
        holder.b.itemLabel.setText(e.finalLabel);
```
Replace with:
```java
        holder.b.itemLabel.setText(AnswerParser.cleanLabel(e.finalLabel));
```

EDIT `app/src/main/java/com/example/identify/ui/HistoryAdapter.java`
Find:
```java
                ? ctx.getString(R.string.history_accepted, e.predictedLabel)
                : ctx.getString(R.string.history_corrected, e.predictedLabel, e.userCorrection));
```
Replace with:
```java
                ? ctx.getString(R.string.history_accepted, AnswerParser.cleanLabel(e.predictedLabel))
                : ctx.getString(R.string.history_corrected, AnswerParser.cleanLabel(e.predictedLabel),
                        e.userCorrection));
```

EDIT `core/src/main/java/com/example/identify/core/TelemetryStats.java`
Find:
```java
    /** Index of the cluster that contains the CPU, or -1. */
```
Replace with:
```java
    /**
     * CPUs outside the slowest cluster: the cores worth giving model threads to (5 on a Pixel 8: 4 mid and
     * 1 big). More threads put work on the little cores and made text generation several times slower.
     * With fewer than two known clusters every CPU counts. At least 1. Clusters are sorted slowest first.
     */
    public static int fastCoreCount(List<Cluster> clusters) {
        int all = 0;
        int known = 0;
        int slowest = 0;
        for (Cluster c : clusters) {
            all += c.cpus.size();
            if (c.maxKhz > 0) {
                if (known == 0) slowest = c.cpus.size();
                known++;
            }
        }
        if (known < 2) return Math.max(1, all);
        return Math.max(1, all - slowest);
    }

    /** Index of the cluster that contains the CPU, or -1. */
```

EDIT `core/src/test/java/com/example/identify/core/TelemetryStatsTest.java`
Find:
```java
        assertEquals("unknown", c.get(1).name);
        assertEquals(Arrays.asList(1), c.get(1).cpus);
    }
}
```
Replace with:
```java
        assertEquals("unknown", c.get(1).name);
        assertEquals(Arrays.asList(1), c.get(1).cpus);
    }

    @Test
    public void fastCoresSkipTheSlowestCluster() {
        // Pixel 8 maximum clocks: 4 little, 4 mid, 1 big
        long[] pixel8 = {1704000, 1704000, 1704000, 1704000, 2367000, 2367000, 2367000, 2367000, 2914000};
        assertEquals(5, TelemetryStats.fastCoreCount(TelemetryStats.clusters(pixel8)));
        assertEquals(4, TelemetryStats.fastCoreCount(TelemetryStats.clusters(
                new long[]{1000, 1000, 1000, 1000, 2000, 2000, 2000, 2000})));
        assertEquals(8, TelemetryStats.fastCoreCount(TelemetryStats.clusters(
                new long[]{2000, 2000, 2000, 2000, 2000, 2000, 2000, 2000})));
        assertEquals(2, TelemetryStats.fastCoreCount(TelemetryStats.clusters(new long[]{0, 0})));
        assertEquals(1, TelemetryStats.fastCoreCount(TelemetryStats.clusters(new long[0])));
    }
}
```

EDIT `app/src/main/java/com/example/identify/AppPrefs.java`
Find:
```java
import com.example.identify.core.ProfileMath;
import com.example.identify.core.UserProfile;
```
Replace with:
```java
import com.example.identify.core.ProfileMath;
import com.example.identify.core.TelemetryStats;
import com.example.identify.core.UserProfile;
import com.example.identify.util.Telemetry;
```

EDIT `app/src/main/java/com/example/identify/AppPrefs.java`
Find:
```java
    /** CPU threads for the model. A change reloads the model on the next identification. */
    public int getThreads() {
        return clamp(prefs.getInt(KEY_N_THREADS, Config.N_THREADS), Config.MIN_THREADS, Config.MAX_THREADS);
    }
```
Replace with:
```java
    /**
     * CPU threads for the model, at most maxThreads(). A change reloads the model on the next
     * identification. A value saved before the limit existed is clamped here.
     */
    public int getThreads() {
        return clamp(prefs.getInt(KEY_N_THREADS, Config.N_THREADS), Config.MIN_THREADS, maxThreads());
    }

    /**
     * One thread per core outside the slowest cluster (5 on a Pixel 8). In testing on a Pixel 8, threads
     * beyond the fast cores made text generation several times slower.
     */
    public static int maxThreads() {
        int fast = TelemetryStats.fastCoreCount(Telemetry.clusters());
        return Math.max(Config.MIN_THREADS, Math.min(Config.MAX_THREADS, fast));
    }
```

EDIT `app/src/main/java/com/example/identify/ui/SettingsFragment.java`
Find:
```java
        int threads = prefs.getThreads();
        binding.threadsSlider.setValue(threads);
```
Replace with:
```java
        int threads = prefs.getThreads();
        // The slider needs valueTo above valueFrom, so it keeps at least 2 even on a one-fast-core phone.
        binding.threadsSlider.setValueTo(Math.max(Config.MIN_THREADS + 1, AppPrefs.maxThreads()));
        binding.threadsSlider.setValue(threads);
```

EDIT `app/src/main/res/values/strings.xml`
Find:
```xml
    <string name="performance_note">Changes take effect on the next identification. The model reloads once.</string>
```
Replace with:
```xml
    <string name="performance_note">Changes take effect on the next identification. The model reloads once. Threads stop at this phone\'s fast cores; more would run on the slow cores and make answers several times slower.</string>
```

EDIT `README.md`
Find:
```markdown
a 300-row table
```
Replace with:
```markdown
a 320-row table
```

EDIT `README.md`
Find:
```markdown
Settings > Performance changes the thread count (1 to 8)
```
Replace with:
```markdown
Settings > Performance changes the thread count (1 up to the phone's fast cores, 5 on a Pixel 8; more threads run on the little cores and make generation several times slower)
```

EDIT `README.md`
Find:
```markdown
(300 foods: US fast-food chains, everyday staples, fruit,
```
Replace with:
```markdown
(320 foods: US fast-food chains, 20 packaged snacks, everyday staples, fruit,
```

EDIT `README.md`
Find:
```markdown
so cuisines only break ties and never turn a non-match into a match.
```
Replace with:
```markdown
so cuisines only break ties and never turn a non-match into a match. Before matching, `AnswerParser` cleans the label: instruction words the model sometimes copies from the prompt (", at most 6 words"), template words, and free-text openings ("The food in this photo is") are removed, and an answer without a Label line uses its first sentence. Labels saved before this cleaning are cleaned when the memory and the history read them.
```

EDIT `README.md`
Find:
```markdown
says what each of the 300 foods usually contains
```
Replace with:
```markdown
says what each of the 320 foods usually contains
```

EDIT `.toolchain/phase11_checks.sh`
Find:
```bash
echo "## 9 food table rows (must print 300)"
```
Replace with:
```bash
echo "## 9 food table rows (must print 320)"
```

EDIT `.toolchain/phase11_checks.sh`
Find:
```bash
echo "## 10 food tag rows (must print 300)"
```
Replace with:
```bash
echo "## 10 food tag rows (must print 320)"
```

EDIT `docs/phase11/README.md`
Find:
```markdown
| 11.4 | `STEP_11_4_verify_and_device.md`: README section 14, compliance checks, ASK BEFORE INSTALLING, walk-through with the user | all |
```
Replace with:
```markdown
| 11.4 | `STEP_11_4_verify_and_device.md`: README section 14, compliance checks, ASK BEFORE INSTALLING, walk-through with the user | all |
| 11.5 | `STEP_11_5_device_fixes.md`: label cleaning (copied prompt words, free text), product names in the prompt, 20 packaged snacks, threads limited to fast cores | US-2.2, US-2.3, US-3.1 |
```

## VERIFY 11.5

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew :core:test :app:assembleDebug :app:lintDebug --console=plain
tail -n 1 app/build/reports/lint-results-debug.txt
bash .toolchain/phase11_checks.sh
```
BUILD SUCCESSFUL; 23 core test classes, all `failures="0" errors="0"`; lint `0 errors` and no new warning; every check prints its expected output (rows 320). Then append the 11.5 line to `PROGRESS.md` and commit (subject `Clean model labels, name packaged products, add 20 snacks, cap threads at fast cores`). Ask the user before installing.
