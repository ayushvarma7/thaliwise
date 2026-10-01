# STEP 10.1: Food knowledge (prompt, cuisines, table, matching)

Goal: the model names dishes (not objects) and their cuisine; the table covers the cuisines people eat in the USA (about 300 rows); matching uses the model's cuisine and, later, the user's favorite cuisines. Stories US-2.2 and US-2.3.

The table gains a `cuisine` column (12 fields per row): `id;name;brand;cuisine;aliases;serving;grams;kcal;protein_g;carbs_g;fat_g;source`. Restaurant chains use the cuisine `Fast food`; staples use `Everyday`.

EDIT `core/src/main/java/com/example/identify/core/PromptBuilder.java`
Find:
```java
    public static final String SYSTEM_BASE =
            "You identify the main object in a photo. Be specific: give the breed, species, variety, make, or model when it is visible.\n"
          + "Reply in exactly two lines and nothing else:\n"
          + "Label: <short name, at most 6 words>\n"
          + "Description: <one sentence>";
```
Replace with:
```java
    public static final String SYSTEM_BASE =
            "You identify the food or drink in a photo. Name the specific dish the way people order it, for example chicken tikka masala, carne asada tacos, pad thai, pepperoni pizza, or a menu item such as a Big Mac.\n"
          + "If several foods are shown, name the main one. If there is no food or drink, use the label Not food.\n"
          + "Reply in exactly three lines and nothing else:\n"
          + "Label: <dish name, at most 6 words>\n"
          + "Cuisine: <one cuisine, for example Indian, Mexican, Chinese, Japanese, Italian, American>\n"
          + "Description: <one sentence>";
```

EDIT `core/src/main/java/com/example/identify/core/PromptBuilder.java`
Find:
```java
    public static final String USER_PROMPT = "Identify the main object in this photo.";
```
Replace with:
```java
    public static final String USER_PROMPT = "What food is in this photo?";
```

REPLACE `core/src/main/java/com/example/identify/core/AnswerParser.java`
```java
package com.example.identify.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AnswerParser {
    private AnswerParser() {}

    public static final int MAX_LABEL_CHARS = 60;

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
        if (label == null || label.isEmpty()) label = other.isEmpty() ? "" : other.remove(0);
        if (description == null) description = String.join(" ", other).trim();
        label = cleanLabel(label);
        if (label.isEmpty()) label = "Unknown";
        return new ParsedAnswer(label, description, cuisine == null ? "" : cleanLabel(cuisine));
    }

    static String cleanLabel(String s) {
        String t = s.trim();
        while (!t.isEmpty() && (t.startsWith("\"") || t.startsWith("'"))) t = t.substring(1).trim();
        while (!t.isEmpty() && (t.endsWith("\"") || t.endsWith("'") || t.endsWith("."))) {
            t = t.substring(0, t.length() - 1).trim();
        }
        t = t.replaceAll("\\s+", " ");
        if (t.length() > MAX_LABEL_CHARS) t = t.substring(0, MAX_LABEL_CHARS).trim();
        return t;
    }
}
```

REPLACE `core/src/main/java/com/example/identify/core/FoodItem.java`
```java
package com.example.identify.core;

import java.util.Collections;
import java.util.List;

/** One row of the nutrition table. All amounts are for one serving. */
public final class FoodItem {
    public final String id;
    public final String name;
    public final String brand;           // empty for generic foods
    public final String cuisine;         // for example "Indian", "Fast food", "Everyday"
    public final List<String> aliases;   // names a model or a user may use for this food
    public final String serving;         // for example "sandwich" or "cup cooked"
    public final double servingGrams;
    public final double kcal;
    public final double proteinG;
    public final double carbsG;
    public final double fatG;
    public final String source;

    public FoodItem(String id, String name, String brand, String cuisine, List<String> aliases, String serving,
                    double servingGrams, double kcal, double proteinG, double carbsG, double fatG, String source) {
        this.id = id;
        this.name = name;
        this.brand = brand;
        this.cuisine = cuisine;
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

REPLACE `core/src/main/java/com/example/identify/core/FoodCatalog.java`
```java
package com.example.identify.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses the nutrition table shipped in app/src/main/assets/foods.txt.
 * One food per line, 12 fields separated by ';':
 * id;name;brand;cuisine;aliases;serving;grams;kcal;protein_g;carbs_g;fat_g;source
 * Aliases are separated by '|'. Blank lines and lines starting with '#' are skipped.
 */
public final class FoodCatalog {
    private FoodCatalog() {}

    public static final int FIELDS = 12;

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
            for (String a : f[4].split("\\|")) {
                if (!a.trim().isEmpty()) aliases.add(a.trim());
            }
            out.add(new FoodItem(id, name, f[2].trim(), f[3].trim(), aliases, f[5].trim(),
                    number(f[6], n), number(f[7], n), number(f[8], n), number(f[9], n), number(f[10], n),
                    f[11].trim()));
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

REPLACE `core/src/main/java/com/example/identify/core/FoodMatcher.java`
```java
package com.example.identify.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Finds the nutrition table rows that a free-text food name (a model label or user text) refers to. */
public final class FoodMatcher {
    private FoodMatcher() {}

    /** Matches scoring below this (before cuisine bonuses) are dropped. */
    public static final double MIN_SCORE = 0.5;
    /** Added when the row's cuisine is the cuisine the model named. */
    public static final double CUISINE_BONUS = 0.1;
    /** Added when the row's cuisine is one of the user's favorite cuisines. */
    public static final double FAVORITE_BONUS = 0.05;

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

    public static List<Match> match(String text, List<FoodItem> foods, int max) {
        return match(text, null, Collections.emptySet(), foods, max);
    }

    /**
     * Up to max foods, best first. Each food scores by its best name or alias; naming the brand adds 0.2,
     * and a branded food loses 0.05 when the text does not name the brand, so "cheeseburger" prefers the
     * generic row while "McDonald's cheeseburger" prefers the branded one. A row must reach MIN_SCORE by
     * name; only then can the model's cuisine (CUISINE_BONUS) and the user's favorite cuisines
     * (FAVORITE_BONUS) reorder it, so a cuisine never turns a non-match into a match.
     */
    public static List<Match> match(String text, String cuisine, Set<String> favoriteCuisines,
                                    List<FoodItem> foods, int max) {
        List<Match> out = new ArrayList<>();
        Set<String> query = new HashSet<>(tokens(text));
        if (query.isEmpty() || max <= 0) return out;
        Set<String> cuisineWords = new HashSet<>(tokens(cuisine));
        Set<String> favoriteWords = new HashSet<>();
        for (String f : favoriteCuisines) favoriteWords.addAll(tokens(f));
        for (FoodItem f : foods) {
            double best = score(tokens(f.name), query);
            for (String alias : f.aliases) best = Math.max(best, score(tokens(alias), query));
            if (best <= 0) continue;
            List<String> brand = tokens(f.brand);
            if (!brand.isEmpty()) {
                best += containsAny(query, brand) ? 0.2 : -0.05;
            }
            if (best < MIN_SCORE) continue;
            List<String> rowCuisine = tokens(f.cuisine);
            if (containsAny(cuisineWords, rowCuisine)) best += CUISINE_BONUS;
            if (containsAny(favoriteWords, rowCuisine)) best += FAVORITE_BONUS;
            out.add(new Match(f, best));
        }
        out.sort((a, b) -> {
            int c = Double.compare(b.score, a.score);
            return c != 0 ? c : a.item.name.compareTo(b.item.name);
        });
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    private static boolean containsAny(Set<String> set, List<String> words) {
        for (String w : words) {
            if (set.contains(w)) return true;
        }
        return false;
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

REPLACE `app/src/main/assets/foods.txt`
```text
# IdentifyVLM nutrition table. One food per line, 12 fields separated by ';':
# id;name;brand;cuisine;aliases;serving;grams;kcal;protein_g;carbs_g;fat_g;source
# Aliases are separated by '|'. Values are for one serving and are approximate reference values.
mcd_double_cheeseburger;Double Cheeseburger;McDonald's;Fast food;double cheeseburger|double cheese burger;sandwich;165;450;25;34;24;McDonald's US published nutrition (approximate)
mcd_cheeseburger;Cheeseburger;McDonald's;Fast food;cheeseburger|cheese burger;sandwich;114;300;15;32;13;McDonald's US published nutrition (approximate)
mcd_hamburger;Hamburger;McDonald's;Fast food;hamburger;sandwich;100;250;12;31;9;McDonald's US published nutrition (approximate)
mcd_big_mac;Big Mac;McDonald's;Fast food;big mac;sandwich;219;590;25;46;34;McDonald's US published nutrition (approximate)
mcd_quarter_pounder_cheese;Quarter Pounder with Cheese;McDonald's;Fast food;quarter pounder|quarter pounder with cheese;sandwich;202;520;30;42;26;McDonald's US published nutrition (approximate)
mcd_mcchicken;McChicken;McDonald's;Fast food;mcchicken|mc chicken;sandwich;147;400;14;39;21;McDonald's US published nutrition (approximate)
mcd_nuggets_10;Chicken McNuggets, 10 piece;McDonald's;Fast food;chicken mcnuggets|mcnuggets|chicken nuggets|nuggets;10 pieces;162;410;23;26;24;McDonald's US published nutrition (approximate)
mcd_filet_o_fish;Filet-O-Fish;McDonald's;Fast food;filet o fish|fillet o fish|fish sandwich;sandwich;142;390;16;39;19;McDonald's US published nutrition (approximate)
mcd_fries_medium;French Fries, medium;McDonald's;Fast food;mcdonalds fries|french fries|fries;medium serving;111;320;5;43;15;McDonald's US published nutrition (approximate)
mcd_egg_mcmuffin;Egg McMuffin;McDonald's;Fast food;egg mcmuffin|mcmuffin;sandwich;137;310;17;30;13;McDonald's US published nutrition (approximate)
mcd_coke_medium;Coca-Cola, medium;McDonald's;Fast food;coke|coca cola;medium cup;621;200;0;55;0;McDonald's US published nutrition (approximate)
mcd_vanilla_cone;Vanilla Cone;McDonald's;Fast food;vanilla cone|ice cream cone|soft serve;cone;90;200;5;33;5;McDonald's US published nutrition (approximate)
mcd_mcflurry_oreo;McFlurry with Oreo Cookies;McDonald's;Fast food;mcflurry|oreo mcflurry;regular cup;285;510;12;80;16;McDonald's US published nutrition (approximate)
bk_whopper;Whopper;Burger King;Fast food;whopper;sandwich;270;660;28;49;40;Burger King US published nutrition (approximate)
wendys_daves_single;Dave's Single;Wendy's;Fast food;daves single|dave single;sandwich;250;590;29;39;34;Wendy's US published nutrition (approximate)
cfa_chicken_sandwich;Chicken Sandwich;Chick-fil-A;Fast food;chick fil a sandwich|chick fil a chicken sandwich;sandwich;180;420;29;41;18;Chick-fil-A US published nutrition (approximate)
cfa_nuggets_8;Nuggets, 8 count;Chick-fil-A;Fast food;chick fil a nuggets;8 pieces;113;250;27;11;11;Chick-fil-A US published nutrition (approximate)
popeyes_chicken_sandwich;Chicken Sandwich;Popeyes;Fast food;popeyes chicken sandwich|popeyes sandwich;sandwich;220;700;28;50;42;Popeyes US published nutrition (approximate)
kfc_drumstick;Original Recipe drumstick;KFC;Fast food;kfc drumstick|kfc chicken|kfc;drumstick;60;130;12;4;8;KFC US published nutrition (approximate)
tb_crunchwrap_supreme;Crunchwrap Supreme;Taco Bell;Fast food;crunchwrap|crunchwrap supreme;crunchwrap;250;530;16;71;21;Taco Bell US published nutrition (approximate)
tb_crunchy_taco;Crunchy Taco;Taco Bell;Fast food;taco bell taco|crunchy taco;taco;78;170;8;13;10;Taco Bell US published nutrition (approximate)
chipotle_chicken_burrito;Chicken burrito, typical build;Chipotle;Fast food;chipotle burrito|chipotle chicken burrito;burrito;550;1080;56;125;40;Chipotle published ingredient values, typical build (approximate)
chipotle_chicken_bowl;Chicken burrito bowl, typical build;Chipotle;Fast food;chipotle bowl|chipotle burrito bowl;bowl;480;760;52;85;23;Chipotle published ingredient values, typical build (approximate)
panda_orange_chicken;Orange Chicken;Panda Express;Fast food;panda express orange chicken|panda orange chicken|panda express;entree;162;490;25;51;23;Panda Express US published nutrition (approximate)
subway_turkey_6;Turkey sub, 6 inch;Subway;Fast food;subway|subway sandwich|subway turkey;6 inch sub;220;280;18;46;3.5;Subway US published nutrition (approximate)
starbucks_caramel_frappuccino;Caramel Frappuccino, grande;Starbucks;Fast food;frappuccino|caramel frappuccino;grande;470;380;5;55;16;Starbucks US published nutrition (approximate)
krispy_kreme_glazed;Original Glazed doughnut;Krispy Kreme;Fast food;krispy kreme;doughnut;49;190;3;22;11;Krispy Kreme US published nutrition (approximate)
burger_generic;Hamburger (restaurant);;American;burger|hamburger|beef burger;burger;220;550;30;40;30;Typical restaurant serving (approximate)
cheeseburger_generic;Cheeseburger (restaurant);;American;cheeseburger|cheese burger;burger;230;600;32;40;34;Typical restaurant serving (approximate)
veggie_burger;Veggie burger;;American;veggie burger|impossible burger|beyond burger|plant based burger;burger;220;500;22;45;25;Typical restaurant serving (approximate)
chicken_sandwich_fried;Fried chicken sandwich;;American;chicken sandwich|fried chicken sandwich|crispy chicken sandwich;sandwich;220;600;30;50;30;Typical restaurant serving (approximate)
banana;Banana;;Fruit;banana;medium banana;118;105;1.3;27;0.4;USDA FoodData Central typical serving (approximate)
apple;Apple;;Fruit;apple|red apple|green apple;medium apple;182;95;0.5;25;0.3;USDA FoodData Central typical serving (approximate)
orange;Orange;;Fruit;orange|navel orange;medium orange;131;62;1.2;15.4;0.2;USDA FoodData Central typical serving (approximate)
grapes;Grapes;;Fruit;grapes|grape;cup;151;104;1.1;27;0.2;USDA FoodData Central typical serving (approximate)
strawberries;Strawberries;;Fruit;strawberries|strawberry;cup;152;49;1;12;0.5;USDA FoodData Central typical serving (approximate)
blueberries;Blueberries;;Fruit;blueberries|blueberry;cup;148;85;1.1;21;0.5;USDA FoodData Central typical serving (approximate)
mango;Mango;;Fruit;mango;cup sliced;165;100;1.4;25;0.6;USDA FoodData Central typical serving (approximate)
watermelon;Watermelon;;Fruit;watermelon;2 cups diced;300;90;1.8;23;0.5;USDA FoodData Central typical serving (approximate)
pineapple;Pineapple;;Fruit;pineapple;cup chunks;165;82;0.9;22;0.2;USDA FoodData Central typical serving (approximate)
avocado;Avocado;;Fruit;avocado;half avocado;100;160;2;8.5;14.7;USDA FoodData Central typical serving (approximate)
egg_boiled;Boiled egg;;American;boiled egg|hard boiled egg|egg;large egg;50;78;6.3;0.6;5.3;USDA FoodData Central typical serving (approximate)
egg_fried;Fried egg;;American;fried egg|sunny side up egg;large egg;46;90;6.3;0.4;6.8;USDA FoodData Central typical serving (approximate)
scrambled_eggs;Scrambled eggs;;American;scrambled eggs|scrambled egg|omelette|omelet;2 eggs;122;200;13.5;2;15;USDA FoodData Central typical serving (approximate)
bread_white;White bread;;Everyday;bread|white bread|toast;slice;25;67;2;12.7;0.8;USDA FoodData Central typical serving (approximate)
bagel;Bagel;;American;bagel|plain bagel;medium bagel;105;270;10;53;1.5;USDA FoodData Central typical serving (approximate)
bagel_lox;Bagel with lox and cream cheese;;American;bagel and lox|lox bagel|bagel with lox|lox;bagel;200;500;25;60;17;Typical deli serving (approximate)
rice_white;White rice;;Everyday;rice|white rice|steamed rice;cup cooked;158;205;4.3;44.5;0.4;USDA FoodData Central typical serving (approximate)
pasta;Pasta;;Italian;pasta|spaghetti|penne|noodles;cup cooked;140;220;8;43;1.3;USDA FoodData Central typical serving (approximate)
spaghetti_meat_sauce;Spaghetti with meat sauce;;Italian;spaghetti bolognese|spaghetti with meat sauce|bolognese;cup;250;330;16;40;11;USDA FoodData Central typical serving (approximate)
spaghetti_meatballs;Spaghetti and meatballs;;Italian;spaghetti and meatballs|meatballs;plate;450;800;38;85;32;Typical restaurant serving (approximate)
pizza_cheese;Cheese pizza;;Italian;pizza|cheese pizza|margherita pizza;slice (14 inch);107;285;12;36;10;USDA FoodData Central typical serving (approximate)
pizza_pepperoni;Pepperoni pizza;;Italian;pepperoni pizza;slice (14 inch);107;313;13;35;13;USDA FoodData Central typical serving (approximate)
deep_dish_pizza;Chicago deep dish pizza;;American;deep dish pizza|chicago pizza|chicago deep dish;slice;220;650;27;50;37;Typical restaurant serving (approximate)
burrito;Burrito, beef and bean;;Mexican;burrito|beef burrito|bean burrito;burrito;220;480;20;58;18;Typical restaurant serving (approximate)
taco;Taco, beef;;Mexican;taco|beef taco|ground beef taco;taco;78;170;8;13;10;Typical restaurant serving (approximate)
hot_dog;Hot dog with bun;;American;hot dog|hotdog;hot dog;98;290;11;24;17;USDA FoodData Central typical serving (approximate)
corn_dog;Corn dog;;American;corn dog|corndog;corn dog;110;300;10;30;16;Typical serving (approximate)
chicken_breast;Grilled chicken breast;;American;chicken breast|grilled chicken|chicken;breast cooked;120;198;37;0;4.3;USDA FoodData Central typical serving (approximate)
fried_chicken;Fried chicken drumstick;;American;fried chicken|chicken drumstick|drumstick|chicken leg;drumstick;75;195;15;6;12;USDA FoodData Central typical serving (approximate)
chicken_tenders;Chicken tenders;;American;chicken tenders|chicken strips|chicken fingers;4 pieces;180;480;35;25;27;Typical restaurant serving (approximate)
buffalo_wings;Buffalo wings;;American;wings|buffalo wings|chicken wings|hot wings;10 wings;320;900;80;5;62;Typical restaurant serving (approximate)
salmon;Salmon fillet;;American;salmon|salmon fillet|grilled salmon;fillet cooked;150;309;33;0;18.6;USDA FoodData Central typical serving (approximate)
steak;Sirloin steak;;American;steak|sirloin|beef steak;steak cooked;150;360;42;0;21;USDA FoodData Central typical serving (approximate)
bbq_ribs;Barbecue pork ribs;;Southern;ribs|bbq ribs|barbecue ribs|pork ribs|baby back ribs;half rack;350;900;65;30;58;Typical restaurant serving (approximate)
pulled_pork;Pulled pork sandwich;;Southern;pulled pork|pulled pork sandwich|bbq sandwich;sandwich;250;550;32;50;23;Typical restaurant serving (approximate)
brisket;Smoked brisket;;Southern;brisket|beef brisket|smoked brisket;serving;170;450;40;3;30;Typical restaurant serving (approximate)
cornbread;Cornbread;;Southern;cornbread;piece;80;260;5;36;10;Typical serving (approximate)
biscuit;Buttermilk biscuit;;Southern;biscuit|biscuits;biscuit;60;210;4;27;10;Typical serving (approximate)
biscuits_gravy;Biscuits and gravy;;Southern;biscuits and gravy|biscuit and gravy;2 biscuits with gravy;300;700;16;70;40;Typical restaurant serving (approximate)
chicken_waffles;Chicken and waffles;;Southern;chicken and waffles;plate;400;1000;45;90;50;Typical restaurant serving (approximate)
shrimp_grits;Shrimp and grits;;Southern;shrimp and grits|grits;bowl;350;550;30;40;30;Typical restaurant serving (approximate)
jambalaya;Jambalaya;;Southern;jambalaya;cup;250;400;20;45;15;Typical serving (approximate)
gumbo;Gumbo;;Southern;gumbo|seafood gumbo|chicken gumbo;bowl;350;400;25;30;20;Typical serving (approximate)
mac_and_cheese;Macaroni and cheese;;American;mac and cheese|macaroni and cheese|macaroni;cup;200;350;13;44;14;Typical package label (approximate)
clam_chowder;New England clam chowder;;American;clam chowder|chowder;bowl;350;400;15;30;24;Typical restaurant serving (approximate)
lobster_roll;Lobster roll;;American;lobster roll;roll;200;500;25;40;27;Typical restaurant serving (approximate)
crab_cakes;Crab cakes;;American;crab cake|crab cakes;2 cakes;170;380;24;16;24;Typical restaurant serving (approximate)
philly_cheesesteak;Philly cheesesteak;;American;philly cheesesteak|cheesesteak|cheese steak;sandwich;350;800;45;60;40;Typical restaurant serving (approximate)
chili;Chili con carne;;American;chili|chili con carne|beef chili;bowl;300;400;27;30;18;Typical serving (approximate)
meatloaf;Meatloaf;;American;meatloaf|meat loaf;slice;150;350;24;15;22;Typical serving (approximate)
mashed_potatoes;Mashed potatoes;;American;mashed potatoes|mashed potato;cup;210;240;4;35;9;USDA FoodData Central typical serving (approximate)
baked_potato;Loaded baked potato;;American;baked potato|loaded baked potato;potato;300;400;9;63;14;Typical restaurant serving (approximate)
corn_cob;Corn on the cob;;American;corn|corn on the cob;ear;100;90;3;19;1.4;USDA FoodData Central typical serving (approximate)
coleslaw;Coleslaw;;American;coleslaw|slaw;half cup;100;150;1;14;10;Typical serving (approximate)
potato_salad;Potato salad;;American;potato salad;half cup;125;180;3;14;12;Typical serving (approximate)
onion_rings;Onion rings;;American;onion rings;serving;120;400;5;45;22;Typical restaurant serving (approximate)
fries_generic;French fries;;American;french fries|fries;medium serving;117;365;4;48;17;USDA FoodData Central typical serving (approximate)
hash_browns;Hash browns;;American;hash browns|hashbrown|hash brown;cup;150;320;3;35;19;Typical serving (approximate)
bacon;Bacon;;American;bacon;3 slices;24;130;9;0.3;10;USDA FoodData Central typical serving (approximate)
sausage_links;Breakfast sausage;;American;sausage|breakfast sausage|sausage links;3 links;70;250;10;1;23;Typical serving (approximate)
eggs_benedict;Eggs Benedict;;American;eggs benedict|eggs benny;2 halves;300;750;30;35;54;Typical restaurant serving (approximate)
french_toast;French toast;;American;french toast;2 slices;140;360;12;40;17;Typical serving (approximate)
waffles;Waffles;;American;waffle|waffles|belgian waffle;2 waffles;150;420;10;50;20;Typical serving (approximate)
pancakes;Pancakes;;American;pancakes|pancake;3 medium, no syrup;114;260;7;33;11;USDA FoodData Central typical serving (approximate)
oatmeal;Oatmeal;;Everyday;oatmeal|porridge|oats;cup cooked;234;166;6;28;3.6;USDA FoodData Central typical serving (approximate)
avocado_toast;Avocado toast;;American;avocado toast;slice;150;300;8;30;18;Typical cafe serving (approximate)
acai_bowl;Acai bowl;;American;acai|acai bowl;bowl;350;450;6;80;12;Typical cafe serving (approximate)
blt;BLT sandwich;;American;blt|bacon lettuce tomato;sandwich;160;450;15;35;27;Typical serving (approximate)
club_sandwich;Club sandwich;;American;club sandwich|turkey club;sandwich;300;650;35;45;35;Typical restaurant serving (approximate)
reuben;Reuben sandwich;;American;reuben|reuben sandwich|corned beef sandwich;sandwich;300;750;40;45;45;Typical restaurant serving (approximate)
turkey_sandwich;Turkey sandwich;;American;turkey sandwich|sandwich|sub;sandwich;230;350;24;36;12;Typical deli serving (approximate)
pbj_sandwich;Peanut butter and jelly sandwich;;American;pb and j|pbj|peanut butter sandwich|peanut butter and jelly sandwich;sandwich;93;380;13;48;17;Typical home recipe (approximate)
grilled_cheese;Grilled cheese sandwich;;American;grilled cheese|cheese sandwich;sandwich;130;400;15;30;24;Typical home recipe (approximate)
chicken_pot_pie;Chicken pot pie;;American;chicken pot pie|pot pie;pie;300;700;25;55;42;Typical serving (approximate)
caesar_salad;Caesar salad;;American;caesar salad;side salad with dressing;150;190;5;8;16;Typical restaurant serving (approximate)
cobb_salad;Cobb salad;;American;cobb salad|cobb;bowl;400;600;40;12;44;Typical restaurant serving (approximate)
green_salad;Green salad, no dressing;;Everyday;salad|green salad|garden salad|lettuce;bowl;100;20;1.5;3.5;0.2;USDA FoodData Central typical serving (approximate)
soft_pretzel;Soft pretzel;;American;pretzel|soft pretzel;large pretzel;140;480;12;99;4;Typical serving (approximate)
fish_and_chips;Fish and chips;;American;fish and chips|fish n chips|fried fish;plate;400;850;35;80;45;Typical restaurant serving (approximate)
apple_pie;Apple pie;;American;apple pie|pie;slice;125;300;2.5;43;14;USDA FoodData Central typical serving (approximate)
cheesecake;Cheesecake;;American;cheesecake|new york cheesecake;slice;125;400;7;32;28;Typical restaurant serving (approximate)
brownie;Brownie;;American;brownie|brownies;piece;60;270;3;35;14;Typical serving (approximate)
milkshake;Milkshake;;American;milkshake|shake|chocolate milkshake;16 oz;450;700;15;100;27;Typical restaurant serving (approximate)
potato_chips;Potato chips;;Everyday;potato chips|chips|crisps;small bag (1 oz);28;152;2;15;10;USDA FoodData Central typical serving (approximate)
chocolate_bar;Milk chocolate bar;;Everyday;chocolate|chocolate bar|candy bar;bar;43;220;3;26;13;Typical package label (approximate)
cookie_chocolate_chip;Chocolate chip cookie;;American;cookie|chocolate chip cookie;large cookie;40;195;2;26;10;USDA FoodData Central typical serving (approximate)
donut_glazed;Glazed donut;;American;donut|doughnut|glazed donut;donut;52;240;4;33;11;Typical restaurant serving (approximate)
croissant;Butter croissant;;Everyday;croissant;croissant;57;231;4.7;26;12;USDA FoodData Central typical serving (approximate)
greek_yogurt;Greek yogurt, plain nonfat;;Everyday;yogurt|greek yogurt|yoghurt;container;170;100;17;6;0.7;USDA FoodData Central typical serving (approximate)
ice_cream_vanilla;Vanilla ice cream;;Everyday;ice cream|vanilla ice cream;half cup;66;137;2.3;16;7.3;USDA FoodData Central typical serving (approximate)
peanut_butter;Peanut butter;;Everyday;peanut butter;2 tbsp;32;190;7;7;16;USDA FoodData Central typical serving (approximate)
almonds;Almonds;;Everyday;almonds|almond|nuts;1 oz handful;28;164;6;6;14;USDA FoodData Central typical serving (approximate)
milk;Milk, 2 percent;;Everyday;milk|glass of milk;cup;244;122;8;12;4.8;USDA FoodData Central typical serving (approximate)
coffee_black;Coffee, black;;Everyday;coffee|black coffee|espresso;cup;240;2;0.3;0;0;USDA FoodData Central typical serving (approximate)
latte;Latte, 2 percent milk;;Everyday;latte|caffe latte|cappuccino;16 oz cup;473;190;13;19;7;Typical cafe serving (approximate)
orange_juice;Orange juice;;Everyday;orange juice|juice;cup;248;112;1.7;26;0.5;USDA FoodData Central typical serving (approximate)
smoothie;Fruit smoothie;;Everyday;smoothie|fruit smoothie;16 oz;470;300;5;65;2;Typical cafe serving (approximate)
protein_shake;Protein shake;;Everyday;protein shake;bottle;330;160;30;6;3;Typical package label (approximate)
beer;Beer;;Everyday;beer|lager;12 oz can;355;153;1.6;12.6;0;USDA FoodData Central typical serving (approximate)
wine_red;Red wine;;Everyday;wine|red wine;5 oz glass;147;125;0.1;3.8;0;USDA FoodData Central typical serving (approximate)
cola;Cola;;Everyday;cola|soda|coke|pepsi;12 oz can;368;140;0;39;0;Typical package label (approximate)
chicken_tikka_masala;Chicken tikka masala;;Indian;chicken tikka masala|tikka masala|chicken tikka;cup;240;400;30;14;24;Typical restaurant serving (approximate)
butter_chicken;Butter chicken;;Indian;butter chicken|murgh makhani;cup;240;440;30;14;30;Typical restaurant serving (approximate)
chicken_curry_rice;Chicken curry with rice;;Indian;chicken curry|curry|curry and rice;plate;400;600;30;70;20;Typical restaurant serving (approximate)
chicken_korma;Chicken korma;;Indian;korma|chicken korma;cup;240;460;28;14;33;Typical restaurant serving (approximate)
chicken_vindaloo;Chicken vindaloo;;Indian;vindaloo|chicken vindaloo;cup;240;350;30;12;20;Typical restaurant serving (approximate)
rogan_josh;Lamb rogan josh;;Indian;rogan josh|lamb curry|mutton curry|goat curry;cup;240;400;30;10;27;Typical restaurant serving (approximate)
tandoori_chicken;Tandoori chicken;;Indian;tandoori chicken|tandoori;leg quarter;200;360;45;5;17;Typical restaurant serving (approximate)
palak_paneer;Palak paneer;;Indian;palak paneer|saag paneer|spinach paneer|saag;cup;240;340;15;14;26;Typical restaurant serving (approximate)
paneer_butter_masala;Paneer butter masala;;Indian;paneer butter masala|paneer makhani|shahi paneer|paneer curry|paneer;cup;240;450;16;16;36;Typical restaurant serving (approximate)
paneer_tikka;Paneer tikka;;Indian;paneer tikka;6 pieces;150;300;18;8;22;Typical restaurant serving (approximate)
malai_kofta;Malai kofta;;Indian;malai kofta|kofta curry;cup;240;450;12;30;32;Typical restaurant serving (approximate)
chana_masala;Chana masala;;Indian;chana masala|chole|chickpea curry;cup;240;280;12;40;9;Typical serving (approximate)
chole_bhature;Chole bhature;;Indian;chole bhature;2 bhature with chole;350;750;20;90;34;Typical restaurant serving (approximate)
rajma;Rajma;;Indian;rajma|kidney bean curry;cup;240;240;13;38;5;Typical home recipe (approximate)
aloo_gobi;Aloo gobi;;Indian;aloo gobi|potato cauliflower curry;cup;200;200;5;24;10;Typical home recipe (approximate)
dal;Dal (lentil curry);;Indian;dal|daal|dhal|dal tadka|lentil curry|lentils;cup;200;230;12;30;7;Typical home recipe (approximate)
dal_makhani;Dal makhani;;Indian;dal makhani;cup;240;330;14;36;15;Typical restaurant serving (approximate)
khichdi;Khichdi;;Indian;khichdi;cup;240;250;10;40;6;Typical home recipe (approximate)
biryani;Chicken biryani;;Indian;biryani|chicken biryani;cup;200;350;18;42;12;Typical restaurant serving (approximate)
veg_biryani;Vegetable biryani;;Indian;veg biryani|vegetable biryani;cup;200;290;6;48;8;Typical restaurant serving (approximate)
naan;Naan;;Indian;naan|naan bread;piece;90;262;8.7;45;5.1;USDA FoodData Central typical serving (approximate)
garlic_naan;Garlic naan;;Indian;garlic naan|butter naan;piece;100;300;9;47;8;Typical restaurant serving (approximate)
roti;Roti;;Indian;roti|chapati|phulka;piece;40;120;3;18;4;Typical home recipe (approximate)
paratha;Paratha;;Indian;paratha|parotta;piece;80;260;5;36;11;Typical serving (approximate)
aloo_paratha;Aloo paratha;;Indian;aloo paratha|stuffed paratha;piece;130;320;7;45;12;Typical home recipe (approximate)
masala_dosa;Masala dosa;;Indian;masala dosa|dosa;dosa with potato filling;250;390;8;56;15;Typical restaurant serving (approximate)
idli;Idli with sambar;;Indian;idli|idly;2 idli with sambar;250;250;10;46;3;Typical restaurant serving (approximate)
medu_vada;Medu vada;;Indian;vada|medu vada;2 pieces;100;290;9;30;15;Typical restaurant serving (approximate)
uttapam;Uttapam;;Indian;uttapam;piece;180;280;8;46;7;Typical restaurant serving (approximate)
sambar;Sambar;;Indian;sambar;cup;240;140;7;22;3;Typical home recipe (approximate)
upma;Upma;;Indian;upma;cup;200;250;6;38;8;Typical home recipe (approximate)
poha;Poha;;Indian;poha;cup;180;250;5;40;8;Typical home recipe (approximate)
pav_bhaji;Pav bhaji;;Indian;pav bhaji;2 pav with bhaji;300;500;12;70;20;Typical street serving (approximate)
vada_pav;Vada pav;;Indian;vada pav;piece;150;300;7;40;13;Typical street serving (approximate)
pani_puri;Pani puri;;Indian;pani puri|golgappa|puchka;6 pieces;120;200;4;30;7;Typical street serving (approximate)
bhel_puri;Bhel puri;;Indian;bhel puri|bhel;cup;100;250;6;38;8;Typical street serving (approximate)
samosa;Samosa, potato;;Indian;samosa;piece;100;260;5;30;13;Typical restaurant serving (approximate)
pakora;Vegetable pakora;;Indian;pakora|pakoda|bhaji|onion bhaji;5 pieces;100;300;6;28;18;Typical restaurant serving (approximate)
raita;Raita;;Indian;raita;half cup;120;80;4;6;4;Typical serving (approximate)
gulab_jamun;Gulab jamun;;Indian;gulab jamun;2 pieces;80;300;4;45;12;Typical serving (approximate)
jalebi;Jalebi;;Indian;jalebi;3 pieces;75;300;2;55;9;Typical serving (approximate)
kheer;Kheer;;Indian;kheer|rice pudding;cup;200;300;8;45;9;Typical home recipe (approximate)
mango_lassi;Mango lassi;;Indian;mango lassi|lassi;glass;250;250;7;45;5;Typical restaurant serving (approximate)
masala_chai;Masala chai;;Indian;chai|masala chai|chai tea;cup;240;120;4;18;4;Typical serving (approximate)
tacos_al_pastor;Tacos al pastor;;Mexican;tacos al pastor|al pastor|pastor tacos;3 tacos;270;500;27;45;23;Typical taqueria serving (approximate)
tacos_carne_asada;Carne asada tacos;;Mexican;carne asada tacos|carne asada|steak tacos;3 tacos;270;520;33;42;24;Typical taqueria serving (approximate)
fish_tacos;Fish tacos;;Mexican;fish tacos|baja fish tacos;2 tacos;250;480;22;46;23;Typical restaurant serving (approximate)
birria_tacos;Birria tacos;;Mexican;birria|birria tacos|quesabirria;3 tacos;300;650;35;40;38;Typical taqueria serving (approximate)
enchiladas;Enchiladas;;Mexican;enchiladas|enchilada|chicken enchiladas;2 enchiladas;300;540;28;40;29;Typical restaurant serving (approximate)
quesadilla;Quesadilla;;Mexican;quesadilla|cheese quesadilla|chicken quesadilla;quesadilla;200;530;24;40;30;Typical restaurant serving (approximate)
nachos;Nachos with cheese;;Mexican;nachos|loaded nachos;plate;250;700;20;60;42;Typical restaurant serving (approximate)
chips_guacamole;Chips and guacamole;;Mexican;guacamole|chips and guacamole|chips and guac|guac;chips with half cup guacamole;150;600;7;55;40;Typical restaurant serving (approximate)
chips_salsa;Tortilla chips and salsa;;Mexican;chips and salsa|tortilla chips|salsa;basket;100;480;6;62;23;Typical restaurant serving (approximate)
queso_dip;Queso dip with chips;;Mexican;queso|queso dip|chile con queso;half cup with chips;150;450;12;35;30;Typical restaurant serving (approximate)
tamales;Tamales;;Mexican;tamales|tamale|pork tamales;2 tamales;260;570;20;56;30;Typical serving (approximate)
chilaquiles;Chilaquiles;;Mexican;chilaquiles;plate;300;600;20;55;33;Typical restaurant serving (approximate)
pozole;Pozole;;Mexican;pozole|posole;bowl;450;350;25;30;14;Typical serving (approximate)
elote;Elote;;Mexican;elote|mexican street corn|street corn;ear;150;250;6;27;15;Typical street serving (approximate)
churros;Churros;;Mexican;churros|churro;3 churros;100;350;4;40;20;Typical serving (approximate)
burrito_bowl;Burrito bowl;;Mexican;burrito bowl|chicken burrito bowl;bowl;500;700;40;75;24;Typical restaurant serving (approximate)
fajitas;Chicken fajitas;;Mexican;fajitas|chicken fajitas|steak fajitas;2 fajitas;350;600;38;50;26;Typical restaurant serving (approximate)
tostadas;Tostadas;;Mexican;tostada|tostadas;2 tostadas;200;400;18;34;21;Typical serving (approximate)
tortilla_soup;Tortilla soup;;Mexican;tortilla soup|sopa de tortilla;bowl;400;300;16;28;14;Typical restaurant serving (approximate)
huevos_rancheros;Huevos rancheros;;Mexican;huevos rancheros;plate;350;550;24;45;30;Typical restaurant serving (approximate)
chimichanga;Chimichanga;;Mexican;chimichanga;chimichanga;300;760;30;65;42;Typical restaurant serving (approximate)
flautas;Flautas;;Mexican;flautas|taquitos;4 pieces;200;500;20;42;28;Typical restaurant serving (approximate)
chicken_mole;Chicken mole;;Mexican;mole|chicken mole|mole poblano;plate;350;550;35;30;32;Typical restaurant serving (approximate)
refried_beans;Refried beans;;Mexican;refried beans|frijoles;half cup;120;120;7;19;2;USDA FoodData Central typical serving (approximate)
mexican_rice;Mexican rice;;Mexican;mexican rice|spanish rice|arroz rojo;cup;180;240;4;45;5;Typical restaurant serving (approximate)
breakfast_burrito;Breakfast burrito;;Mexican;breakfast burrito;burrito;250;550;24;45;30;Typical restaurant serving (approximate)
horchata;Horchata;;Mexican;horchata;glass;350;250;1;54;3;Typical serving (approximate)
orange_chicken;Orange chicken;;Chinese;orange chicken;cup;150;490;25;51;23;Typical takeout serving (approximate)
general_tso_chicken;General Tso chicken;;Chinese;general tso chicken|general tsos chicken|general tso;cup;160;500;25;50;23;Typical takeout serving (approximate)
sesame_chicken;Sesame chicken;;Chinese;sesame chicken;cup;160;520;24;55;22;Typical takeout serving (approximate)
kung_pao_chicken;Kung pao chicken;;Chinese;kung pao chicken|kung pao;cup;200;430;30;20;27;Typical takeout serving (approximate)
sweet_sour_pork;Sweet and sour pork;;Chinese;sweet and sour pork|sweet and sour chicken|sweet and sour;cup;200;480;18;56;20;Typical takeout serving (approximate)
beef_broccoli;Beef and broccoli;;Chinese;beef and broccoli|broccoli beef;cup;220;300;25;15;15;Typical takeout serving (approximate)
mongolian_beef;Mongolian beef;;Chinese;mongolian beef;cup;200;450;28;30;24;Typical takeout serving (approximate)
lo_mein;Lo mein;;Chinese;lo mein|chicken lo mein;cup;200;400;17;55;13;Typical takeout serving (approximate)
chow_mein;Chow mein;;Chinese;chow mein;cup;200;400;15;50;16;Typical takeout serving (approximate)
fried_rice;Fried rice;;Chinese;fried rice;cup;137;238;5.5;45;4;USDA FoodData Central typical serving (approximate)
egg_roll;Egg roll;;Chinese;egg roll|eggroll;roll;90;220;7;24;11;Typical takeout serving (approximate)
spring_roll_fried;Spring roll, fried;;Chinese;spring roll|spring rolls;2 rolls;100;250;5;30;12;Typical takeout serving (approximate)
dumplings_steamed;Steamed dumplings;;Chinese;dumplings|dumpling|jiaozi|xiao long bao|soup dumplings;6 dumplings;180;330;15;40;12;Typical restaurant serving (approximate)
potstickers;Potstickers;;Chinese;potstickers|pot stickers|fried dumplings;6 pieces;180;380;15;40;17;Typical restaurant serving (approximate)
mapo_tofu;Mapo tofu;;Chinese;mapo tofu;cup;240;330;18;12;24;Typical restaurant serving (approximate)
wonton_soup;Wonton soup;;Chinese;wonton soup|wontons;bowl;450;300;17;35;10;Typical takeout serving (approximate)
hot_sour_soup;Hot and sour soup;;Chinese;hot and sour soup;bowl;350;160;9;17;6;Typical takeout serving (approximate)
pork_bao;Pork bao bun;;Chinese;bao|bao bun|steamed bun|char siu bao|pork bun;2 buns;160;400;14;54;14;Typical restaurant serving (approximate)
peking_duck;Peking duck;;Chinese;peking duck|roast duck;serving with pancakes;250;650;35;35;40;Typical restaurant serving (approximate)
sushi_nigiri;Nigiri sushi;;Japanese;nigiri|nigiri sushi|salmon nigiri|tuna nigiri;6 pieces;220;350;20;56;4;Typical restaurant serving (approximate)
sashimi;Sashimi;;Japanese;sashimi|salmon sashimi|tuna sashimi;8 slices;150;230;35;0;9;Typical restaurant serving (approximate)
california_roll;California roll;;Japanese;sushi|california roll|sushi roll|maki;6 pieces;180;255;9;38;7;Typical restaurant serving (approximate)
spicy_tuna_roll;Spicy tuna roll;;Japanese;spicy tuna roll|spicy tuna;8 pieces;200;380;15;42;15;Typical restaurant serving (approximate)
ramen_restaurant;Ramen (restaurant);;Japanese;ramen|tonkotsu ramen|tonkotsu|shoyu ramen|miso ramen;bowl;600;800;35;80;38;Typical restaurant serving (approximate)
ramen_instant;Instant ramen;;Japanese;instant ramen|instant noodles|cup noodles;pack prepared;300;380;8;52;14;Typical package label (approximate)
udon;Udon noodle soup;;Japanese;udon|udon soup|udon noodles;bowl;500;450;15;80;7;Typical restaurant serving (approximate)
shrimp_tempura;Shrimp tempura;;Japanese;tempura|shrimp tempura;5 pieces;150;400;18;30;23;Typical restaurant serving (approximate)
chicken_teriyaki;Chicken teriyaki with rice;;Japanese;teriyaki|chicken teriyaki|teriyaki chicken;plate;400;650;40;80;17;Typical restaurant serving (approximate)
katsu_curry;Katsu curry;;Japanese;katsu curry|chicken katsu|tonkatsu|katsu;plate;450;900;35;100;38;Typical restaurant serving (approximate)
gyudon;Gyudon beef bowl;;Japanese;gyudon|beef bowl|donburi;bowl;450;700;28;95;22;Typical restaurant serving (approximate)
gyoza;Gyoza;;Japanese;gyoza;6 pieces;150;320;13;34;14;Typical restaurant serving (approximate)
miso_soup;Miso soup;;Japanese;miso soup|miso;bowl;240;60;4;6;2;Typical restaurant serving (approximate)
onigiri;Onigiri;;Japanese;onigiri|rice ball;piece;110;180;4;38;1;Typical serving (approximate)
edamame;Edamame;;Japanese;edamame;cup in pods;155;190;17;14;8;USDA FoodData Central typical serving (approximate)
poke_bowl;Poke bowl;;Hawaiian;poke|poke bowl|ahi poke;bowl;450;650;35;75;20;Typical restaurant serving (approximate)
bibimbap;Bibimbap;;Korean;bibimbap;bowl;500;600;25;85;18;Typical restaurant serving (approximate)
bulgogi;Bulgogi with rice;;Korean;bulgogi|korean bbq beef|korean barbecue;cup with rice;350;600;35;60;22;Typical restaurant serving (approximate)
korean_fried_chicken;Korean fried chicken;;Korean;korean fried chicken|yangnyeom chicken;6 pieces;250;700;40;35;43;Typical restaurant serving (approximate)
kimchi;Kimchi;;Korean;kimchi;half cup;75;15;1;2;0.5;USDA FoodData Central typical serving (approximate)
japchae;Japchae;;Korean;japchae|glass noodles;cup;150;300;8;45;10;Typical restaurant serving (approximate)
tteokbokki;Tteokbokki;;Korean;tteokbokki|rice cakes|topokki;cup;200;400;8;80;5;Typical street serving (approximate)
kimchi_fried_rice;Kimchi fried rice;;Korean;kimchi fried rice|kimchi bokkeumbap;cup;250;400;12;55;14;Typical restaurant serving (approximate)
green_curry;Thai green curry with rice;;Thai;green curry|thai green curry;cup with rice;450;650;28;65;30;Typical restaurant serving (approximate)
red_curry;Thai red curry with rice;;Thai;red curry|thai red curry|panang curry|panang;cup with rice;450;650;28;65;30;Typical restaurant serving (approximate)
massaman_curry;Massaman curry with rice;;Thai;massaman curry|massaman;cup with rice;450;700;26;72;34;Typical restaurant serving (approximate)
tom_yum;Tom yum soup;;Thai;tom yum|tom yum soup|tom kha|tom kha gai;bowl;350;200;18;10;10;Typical restaurant serving (approximate)
pad_thai;Pad thai;;Thai;pad thai;plate;300;550;22;70;20;Typical restaurant serving (approximate)
pad_see_ew;Pad see ew;;Thai;pad see ew;plate;300;600;22;80;20;Typical restaurant serving (approximate)
drunken_noodles;Drunken noodles;;Thai;drunken noodles|pad kee mao;plate;300;600;22;75;22;Typical restaurant serving (approximate)
basil_chicken;Thai basil chicken with rice;;Thai;basil chicken|pad kra pao|thai basil chicken;plate with rice;400;600;30;70;20;Typical restaurant serving (approximate)
papaya_salad;Green papaya salad;;Thai;papaya salad|som tam;plate;200;120;3;25;1;Typical restaurant serving (approximate)
chicken_satay;Chicken satay;;Thai;satay|chicken satay;4 skewers with peanut sauce;180;400;30;12;26;Typical restaurant serving (approximate)
mango_sticky_rice;Mango sticky rice;;Thai;mango sticky rice;plate;250;450;5;80;13;Typical restaurant serving (approximate)
thai_iced_tea;Thai iced tea;;Thai;thai tea|thai iced tea;glass;350;200;3;35;6;Typical restaurant serving (approximate)
pho;Pho;;Vietnamese;pho|beef pho|pho bo|chicken pho;bowl;700;500;30;65;12;Typical restaurant serving (approximate)
banh_mi;Banh mi;;Vietnamese;banh mi;sandwich;250;550;25;65;20;Typical restaurant serving (approximate)
fresh_spring_rolls;Fresh spring rolls;;Vietnamese;fresh spring rolls|summer rolls|goi cuon|rice paper rolls;2 rolls;200;250;12;40;4;Typical restaurant serving (approximate)
vermicelli_bowl;Vermicelli bowl;;Vietnamese;bun|vermicelli bowl|bun cha|bun thit nuong;bowl;450;550;25;75;16;Typical restaurant serving (approximate)
vietnamese_coffee;Vietnamese iced coffee;;Vietnamese;vietnamese coffee|ca phe sua da;glass;250;150;3;25;4;Typical restaurant serving (approximate)
lasagna;Lasagna;;Italian;lasagna|lasagne;piece;300;600;32;45;32;Typical restaurant serving (approximate)
fettuccine_alfredo;Fettuccine Alfredo;;Italian;fettuccine alfredo|alfredo|alfredo pasta;plate;350;900;25;85;50;Typical restaurant serving (approximate)
carbonara;Spaghetti carbonara;;Italian;carbonara|spaghetti carbonara;plate;350;750;30;80;33;Typical restaurant serving (approximate)
pesto_pasta;Pasta with pesto;;Italian;pesto|pesto pasta|pasta with pesto;plate;300;650;18;75;30;Typical restaurant serving (approximate)
chicken_parmesan;Chicken parmesan with pasta;;Italian;chicken parmesan|chicken parm|chicken parmigiana;plate with pasta;450;900;55;80;38;Typical restaurant serving (approximate)
risotto;Risotto;;Italian;risotto|mushroom risotto;cup;250;400;10;55;15;Typical restaurant serving (approximate)
gnocchi;Gnocchi;;Italian;gnocchi;cup with sauce;200;350;8;60;8;Typical restaurant serving (approximate)
ravioli;Ravioli;;Italian;ravioli|cheese ravioli;cup with sauce;250;400;17;50;14;Typical restaurant serving (approximate)
minestrone;Minestrone soup;;Italian;minestrone|minestrone soup|vegetable soup;bowl;350;160;6;28;3;Typical restaurant serving (approximate)
bruschetta;Bruschetta;;Italian;bruschetta;2 pieces;100;200;5;26;8;Typical restaurant serving (approximate)
caprese_salad;Caprese salad;;Italian;caprese|caprese salad|mozzarella and tomato;plate;200;350;18;8;27;Typical restaurant serving (approximate)
calzone;Calzone;;Italian;calzone;calzone;350;900;40;90;40;Typical restaurant serving (approximate)
tiramisu;Tiramisu;;Italian;tiramisu;slice;140;450;7;40;28;Typical restaurant serving (approximate)
cannoli;Cannoli;;Italian;cannoli;piece;100;350;7;35;20;Typical bakery serving (approximate)
gelato;Gelato;;Italian;gelato;half cup;90;200;4;28;8;Typical serving (approximate)
falafel;Falafel;;Middle Eastern;falafel;5 pieces;100;330;13;32;18;USDA FoodData Central typical serving (approximate)
falafel_wrap;Falafel wrap;;Middle Eastern;falafel wrap|falafel pita|falafel sandwich;wrap;300;600;20;75;25;Typical restaurant serving (approximate)
shawarma;Chicken shawarma wrap;;Middle Eastern;shawarma|chicken shawarma|shawarma wrap|doner|doner kebab;wrap;350;700;40;60;32;Typical restaurant serving (approximate)
kebab;Shish kebab;;Middle Eastern;kebab|kabob|shish kebab|chicken kebab|kofta kebab;2 skewers;200;400;40;5;24;Typical restaurant serving (approximate)
hummus;Hummus;;Middle Eastern;hummus;2 tbsp;30;50;2.4;4.3;2.9;USDA FoodData Central typical serving (approximate)
pita;Pita bread;;Middle Eastern;pita|pita bread;large pita;60;165;5.5;33;0.7;USDA FoodData Central typical serving (approximate)
tabbouleh;Tabbouleh;;Middle Eastern;tabbouleh|tabouli;cup;160;180;3;14;13;Typical serving (approximate)
baba_ganoush;Baba ganoush;;Middle Eastern;baba ganoush|baba ghanoush|eggplant dip;quarter cup;60;80;1.5;6;6;Typical serving (approximate)
shakshuka;Shakshuka;;Middle Eastern;shakshuka;pan with 2 eggs;300;350;16;20;23;Typical serving (approximate)
baklava;Baklava;;Middle Eastern;baklava;piece;60;330;5;30;23;Typical bakery serving (approximate)
gyro;Gyro;;Mediterranean;gyro|gyros|lamb gyro;pita wrap;300;650;30;55;34;Typical restaurant serving (approximate)
greek_salad;Greek salad;;Mediterranean;greek salad|horiatiki;bowl;250;320;7;14;27;Typical restaurant serving (approximate)
spanakopita;Spanakopita;;Mediterranean;spanakopita|spinach pie;piece;150;400;10;30;27;Typical restaurant serving (approximate)
tzatziki;Tzatziki;;Mediterranean;tzatziki;quarter cup;60;60;3;3;4;Typical serving (approximate)
jerk_chicken;Jerk chicken;;Caribbean;jerk chicken|jerk;leg quarter;200;380;40;5;22;Typical restaurant serving (approximate)
rice_and_beans;Rice and beans;;Caribbean;rice and beans|rice and peas|arroz con frijoles;cup;200;300;10;55;4;Typical serving (approximate)
plantains;Fried plantains;;Caribbean;plantains|maduros|tostones|fried plantain;cup;150;330;2;55;13;Typical serving (approximate)
cuban_sandwich;Cuban sandwich;;Caribbean;cuban sandwich|cubano;sandwich;300;750;45;55;38;Typical restaurant serving (approximate)
empanadas;Empanadas;;Latin American;empanada|empanadas;2 empanadas;200;560;20;50;30;Typical serving (approximate)
arepa;Arepa with cheese;;Latin American;arepa|arepas;arepa;180;450;15;45;22;Typical restaurant serving (approximate)
pupusas;Pupusas;;Latin American;pupusa|pupusas;2 pupusas;250;600;20;65;28;Typical restaurant serving (approximate)
ceviche;Ceviche;;Latin American;ceviche;cup;200;200;30;10;4;Typical restaurant serving (approximate)
injera_wat;Injera with wat;;Ethiopian;injera|doro wat|wat|ethiopian food;plate;450;650;30;85;20;Typical restaurant serving (approximate)
```

REPLACE `core/src/test/java/com/example/identify/core/FoodCatalogTest.java`
```java
package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class FoodCatalogTest {

    /** The table shipped in the app. Gradle runs :core tests with the core/ folder as working directory. */
    static List<FoodItem> shipped() throws IOException {
        File f = new File("../app/src/main/assets/foods.txt");
        return FoodCatalog.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void parsesFieldsSkippingCommentsAndBlanks() {
        String text = "# header\n\nbanana;Banana;;Fruit;banana|bananas;medium banana;118;105;1.3;27;0.4;USDA\n"
                + "mcd_big_mac;Big Mac;McDonald's;Fast food;big mac;sandwich;219;590;25;46;34;McDonald's\n";
        List<FoodItem> foods = FoodCatalog.parse(text);
        assertEquals(2, foods.size());
        FoodItem b = foods.get(0);
        assertEquals("banana", b.id);
        assertEquals("", b.brand);
        assertEquals("Fruit", b.cuisine);
        assertEquals(2, b.aliases.size());
        assertEquals(105, b.kcal, 0);
        assertEquals("Banana", b.displayName());
        assertEquals("Big Mac (McDonald's)", foods.get(1).displayName());
        assertEquals("Fast food", foods.get(1).cuisine);
    }

    @Test
    public void wrongFieldCountNamesTheLine() {
        try {
            FoodCatalog.parse("# x\nok;Ok;;Everyday;ok;s;1;1;1;1;1;src\nbad;Bad;;bad\n");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("line 3"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateIdRejected() {
        FoodCatalog.parse("a;A;;Everyday;a;s;1;1;1;1;1;src\na;B;;Everyday;b;s;1;1;1;1;1;src\n");
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeNumberRejected() {
        FoodCatalog.parse("a;A;;Everyday;a;s;1;-5;1;1;1;src\n");
    }

    @Test
    public void shippedTableIsValid() throws IOException {
        List<FoodItem> foods = shipped();
        assertTrue("table too small: " + foods.size(), foods.size() >= 280);
        Set<String> cuisines = new HashSet<>();
        for (FoodItem f : foods) {
            assertTrue(f.id + " needs an alias", !f.aliases.isEmpty());
            assertTrue(f.id + " kcal", f.kcal > 0 && f.kcal < 2000);
            assertTrue(f.id + " source", !f.source.isEmpty());
            assertTrue(f.id + " cuisine", !f.cuisine.isEmpty());
            cuisines.add(f.cuisine);
        }
        for (String c : new String[]{"Indian", "Mexican", "Chinese", "Japanese", "Korean", "Thai", "Vietnamese",
                "Italian", "Middle Eastern", "Mediterranean", "American", "Southern", "Caribbean",
                "Latin American", "Fast food"}) {
            assertTrue("missing cuisine " + c, cuisines.contains(c));
        }
    }
}
```

EDIT `core/src/test/java/com/example/identify/core/MealsTest.java`
Find:
```java
        FoodItem f = new FoodItem("x", "X", "", Collections.singletonList("x"), "sandwich", 165, 450, 25, 34, 24, "src");
```
Replace with:
```java
        FoodItem f = new FoodItem("x", "X", "", "American", Collections.singletonList("x"), "sandwich",
                165, 450, 25, 34, 24, "src");
```

CREATE `core/src/test/java/com/example/identify/core/FoodMatcherCuisineTest.java`
```java
package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

public class FoodMatcherCuisineTest {

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
    public void dishesAcrossCuisines() {
        assertEquals("chicken_tikka_masala", top("Chicken tikka masala"));
        assertEquals("masala_dosa", top("Masala dosa"));
        assertEquals("tacos_carne_asada", top("Carne asada tacos"));
        assertEquals("tacos_al_pastor", top("Tacos al pastor"));
        assertEquals("pho", top("Pho"));
        assertEquals("pad_thai", top("Pad thai"));
        assertEquals("bibimbap", top("Bibimbap"));
        assertEquals("pita", top("Pita bread"));
        assertEquals("shawarma", top("Chicken shawarma"));
        assertEquals("general_tso_chicken", top("General Tso's chicken"));
        assertEquals("bbq_ribs", top("BBQ ribs"));
        assertEquals("ramen_restaurant", top("Ramen"));
    }

    @Test
    public void chainItemsNeedTheirName() {
        assertEquals("bk_whopper", top("Whopper"));
        assertEquals("orange_chicken", top("Orange chicken"));
        assertEquals("panda_orange_chicken", top("Panda Express orange chicken"));
    }

    @Test
    public void cuisineAndFavoritesBreakTies() {
        // "spring rolls" matches the Chinese fried rolls and the Vietnamese fresh rolls equally by name
        // ("fresh" is a stop word), so the cuisine decides.
        List<FoodMatcher.Match> chinese = FoodMatcher.match("spring rolls", "Chinese", Collections.emptySet(), foods, 2);
        assertEquals("spring_roll_fried", chinese.get(0).item.id);
        List<FoodMatcher.Match> fav = FoodMatcher.match("spring rolls", null, Collections.singleton("Chinese"), foods, 2);
        assertEquals("spring_roll_fried", fav.get(0).item.id);
        List<FoodMatcher.Match> viet = FoodMatcher.match("spring rolls", "Vietnamese", Collections.emptySet(), foods, 2);
        assertEquals("fresh_spring_rolls", viet.get(0).item.id);
    }

    @Test
    public void cuisineNeverCreatesAMatch() {
        assertTrue(FoodMatcher.match("Not food", "Indian", Collections.singleton("Indian"), foods, 5).isEmpty());
        assertTrue(FoodMatcher.match("computer mouse", "American", Collections.emptySet(), foods, 5).isEmpty());
    }
}
```

CREATE `core/src/test/java/com/example/identify/core/AnswerParserCuisineTest.java`
```java
package com.example.identify.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AnswerParserCuisineTest {

    @Test
    public void threeLineAnswer() {
        AnswerParser.ParsedAnswer a = AnswerParser.parse(
                "Label: Chicken tikka masala\nCuisine: Indian\nDescription: Chicken in a spiced tomato cream sauce.");
        assertEquals("Chicken tikka masala", a.label);
        assertEquals("Indian", a.cuisine);
        assertEquals("Chicken in a spiced tomato cream sauce.", a.description);
    }

    @Test
    public void markdownCuisine() {
        AnswerParser.ParsedAnswer a = AnswerParser.parse("**Label:** Pho\n**Cuisine:** \"Vietnamese\".");
        assertEquals("Pho", a.label);
        assertEquals("Vietnamese", a.cuisine);
        assertEquals("", a.description);
    }

    @Test
    public void missingCuisineIsEmpty() {
        assertEquals("", AnswerParser.parse("Label: Banana\nDescription: A fruit.").cuisine);
        assertEquals("", AnswerParser.parse(null).cuisine);
        assertEquals("", AnswerParser.parse("mouse").cuisine);
    }
}
```

## VERIFY 10.1

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew :core:test --console=plain -q
ls core/build/test-results/test/ | grep -c "^TEST-"
grep -vc "^#" app/src/main/assets/foods.txt
```
Exit 0 (every old and new test passes), 15 test classes, at least 280 rows. The app module is not built in this step (ResultViewModel still uses the 2-argument ParsedAnswer constructor, which still exists, so it would compile; it is updated in 10.3).

Commit subject: `Make identification food-first: dish prompt, cuisine line, 300-dish table`.
