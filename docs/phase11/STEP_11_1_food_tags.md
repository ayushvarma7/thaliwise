# STEP 11.1: Diet tags for the food table, and diet rules

Goal: the data the coach needs to warn about diets and comment on "eat more of" answers (US-8.2, US-8.4). Every one of the 300 table foods gets a line in a new asset `food_tags.txt` saying what a typical serving usually contains (meat, pork, fish, shellfish, egg, dairy, gluten, alcohol) and what it usually is (fried, sweet, has vegetables). `DietRules` maps each onboarding diet to the tags it avoids. Everything here is pure Java in `:core` with tests, except the asset file.

Why a separate file instead of a 13th column in `foods.txt`: the table stays untouched (its 12-field parser and tests stay valid), the tags can be reviewed on their own, and a test proves both files list exactly the same ids.

Tag vocabulary (keys in the file are lowercase):

| Tag | Meaning (typical serving, "usually") |
|---|---|
| `meat` | any land animal or poultry |
| `pork` | pork meat or lard (vegetarians avoid it too, Halal and Kosher avoid it) |
| `fish` | finfish, including fish sauce, anchovy, bonito dashi, surimi |
| `shellfish` | shrimp, crab, lobster, clam |
| `egg` | egg, including mayonnaise |
| `dairy` | milk, cheese, butter, ghee, cream, yogurt, paneer |
| `gluten` | wheat, barley, rye, regular soy sauce |
| `alcohol` | beer, wine |
| `fried` | deep fried or batter fried |
| `sweet` | high in added sugar |
| `veg` | a real vegetable portion |

CREATE `core/src/main/java/com/example/identify/core/FoodTag.java`
```java
package com.example.identify.core;

import java.util.Locale;

/**
 * What a typical serving of a table food usually contains or is (assets/food_tags.txt). "Usually", not
 * "always": the table describes common restaurant and home recipes, not the plate in the photo. The
 * declaration order is the order in which diet conflicts are reported.
 */
public enum FoodTag {
    MEAT, PORK, FISH, SHELLFISH, EGG, DAIRY, GLUTEN, ALCOHOL, FRIED, SWEET, VEG;

    /** The lowercase key used in food_tags.txt. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The tag for a file key, or null when the key is unknown. */
    public static FoodTag fromKey(String key) {
        for (FoodTag t : values()) {
            if (t.key().equals(key)) return t;
        }
        return null;
    }
}
```

CREATE `core/src/main/java/com/example/identify/core/FoodTags.java`
```java
package com.example.identify.core;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Parses assets/food_tags.txt: one "id;tag|tag" line per table food, "id;-" for none, # comments. */
public final class FoodTags {
    private FoodTags() {}

    public static final String NONE = "-";

    /** Food id to its tags (iterating in FoodTag order). Throws IllegalArgumentException naming the line. */
    public static Map<String, Set<FoodTag>> parse(String text) {
        Map<String, Set<FoodTag>> out = new LinkedHashMap<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split(";", -1);
            if (parts.length != 2 || parts[0].trim().isEmpty()) {
                throw new IllegalArgumentException("line " + (i + 1) + ": expected id;tags");
            }
            String id = parts[0].trim();
            if (out.containsKey(id)) {
                throw new IllegalArgumentException("line " + (i + 1) + ": duplicate id " + id);
            }
            Set<FoodTag> tags = EnumSet.noneOf(FoodTag.class);
            String value = parts[1].trim();
            if (!value.equals(NONE)) {
                for (String key : value.split("\\|", -1)) {
                    FoodTag tag = FoodTag.fromKey(key.trim());
                    if (tag == null) {
                        throw new IllegalArgumentException("line " + (i + 1) + ": unknown tag '" + key + "'");
                    }
                    tags.add(tag);
                }
            }
            out.put(id, Collections.unmodifiableSet(tags));
        }
        return out;
    }
}
```

CREATE `core/src/main/java/com/example/identify/core/DietRules.java`
```java
package com.example.identify.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Which food tags each onboarding diet avoids. The labels are exactly the items of the onboarding_diet
 * array in app/src/main/res/values/arrays.xml (a test checks this). Kosher covers pork and shellfish only,
 * not meat with dairy or certification; Halal covers pork and alcohol only.
 */
public final class DietRules {
    private DietRules() {}

    public static final String VEGETARIAN = "Vegetarian";
    public static final String VEGAN = "Vegan";
    public static final String PESCATARIAN = "Pescatarian";
    public static final String HALAL = "Halal";
    public static final String KOSHER = "Kosher";
    public static final String GLUTEN_FREE = "Gluten-free";
    public static final String DAIRY_FREE = "Dairy-free";

    /** The diets that avoid something, in the order onboarding shows them. "No restrictions" avoids nothing. */
    public static final List<String> DIETS = Collections.unmodifiableList(
            Arrays.asList(VEGETARIAN, VEGAN, PESCATARIAN, HALAL, KOSHER, GLUTEN_FREE, DAIRY_FREE));

    /** One reported conflict: the diet and the first avoided tag the food has. */
    public static final class Conflict {
        public final String diet;
        public final FoodTag tag;

        Conflict(String diet, FoodTag tag) {
            this.diet = diet;
            this.tag = tag;
        }
    }

    /** The avoided tags, iterating in FoodTag order; empty for unknown labels and "No restrictions". */
    public static Set<FoodTag> avoided(String diet) {
        switch (diet) {
            case VEGETARIAN: return EnumSet.of(FoodTag.MEAT, FoodTag.PORK, FoodTag.FISH, FoodTag.SHELLFISH);
            case VEGAN: return EnumSet.of(FoodTag.MEAT, FoodTag.PORK, FoodTag.FISH, FoodTag.SHELLFISH,
                    FoodTag.EGG, FoodTag.DAIRY);
            case PESCATARIAN: return EnumSet.of(FoodTag.MEAT, FoodTag.PORK);
            case HALAL: return EnumSet.of(FoodTag.PORK, FoodTag.ALCOHOL);
            case KOSHER: return EnumSet.of(FoodTag.PORK, FoodTag.SHELLFISH);
            case GLUTEN_FREE: return EnumSet.of(FoodTag.GLUTEN);
            case DAIRY_FREE: return EnumSet.of(FoodTag.DAIRY);
            default: return EnumSet.noneOf(FoodTag.class);
        }
    }

    /**
     * At most one conflict per chosen diet (its first avoided tag in FoodTag order), in DIETS order. A tag
     * already reported for an earlier diet is not repeated, so Vegetarian plus Vegan gives one "meat".
     */
    public static List<Conflict> conflicts(Set<String> diets, Set<FoodTag> tags) {
        List<Conflict> out = new ArrayList<>();
        Set<FoodTag> reported = EnumSet.noneOf(FoodTag.class);
        for (String diet : DIETS) {
            if (!diets.contains(diet)) continue;
            for (FoodTag t : avoided(diet)) {
                if (tags.contains(t)) {
                    if (reported.add(t)) out.add(new Conflict(diet, t));
                    break;
                }
            }
        }
        return out;
    }
}
```

CREATE `app/src/main/assets/food_tags.txt`
```text
# Diet tags for every row of foods.txt, same ids and order: id;tag|tag, or id;- for none.
# A tag means a typical serving USUALLY has it (restaurant or common home recipe), not always.
# meat: any land animal or poultry; pork: pork meat or lard (vegetarian diets avoid it too);
# fish; shellfish (shrimp, crab, lobster, clam); egg; dairy (milk, cheese, butter, ghee, cream,
# yogurt, paneer); gluten (wheat, barley, rye, regular soy sauce); alcohol;
# fried (deep fried or batter fried); sweet (high in added sugar); veg (a real vegetable portion).
mcd_double_cheeseburger;meat|dairy|gluten
mcd_cheeseburger;meat|dairy|gluten
mcd_hamburger;meat|gluten
mcd_big_mac;meat|dairy|gluten
mcd_quarter_pounder_cheese;meat|dairy|gluten
mcd_mcchicken;meat|gluten|fried
mcd_nuggets_10;meat|gluten|fried
mcd_filet_o_fish;fish|dairy|gluten|fried
# US McDonald's fries are cooked with natural beef flavor (wheat and milk derivatives).
mcd_fries_medium;meat|dairy|gluten|fried
mcd_egg_mcmuffin;meat|pork|egg|dairy|gluten
mcd_coke_medium;sweet
mcd_vanilla_cone;dairy|sweet
mcd_mcflurry_oreo;dairy|gluten|sweet
bk_whopper;meat|gluten
wendys_daves_single;meat|dairy|gluten
cfa_chicken_sandwich;meat|egg|dairy|gluten|fried
cfa_nuggets_8;meat|egg|dairy|gluten|fried
popeyes_chicken_sandwich;meat|gluten|fried
kfc_drumstick;meat|gluten|fried
tb_crunchwrap_supreme;meat|dairy|gluten
tb_crunchy_taco;meat|dairy
chipotle_chicken_burrito;meat|dairy|gluten
chipotle_chicken_bowl;meat|dairy
panda_orange_chicken;meat|gluten|fried|sweet
subway_turkey_6;meat|gluten
starbucks_caramel_frappuccino;dairy|sweet
krispy_kreme_glazed;egg|dairy|gluten|fried|sweet
burger_generic;meat|gluten
cheeseburger_generic;meat|dairy|gluten
veggie_burger;gluten|veg
chicken_sandwich_fried;meat|gluten|fried
banana;-
apple;-
orange;-
grapes;-
strawberries;-
blueberries;-
mango;-
watermelon;-
pineapple;-
avocado;-
egg_boiled;egg
egg_fried;egg
scrambled_eggs;egg|dairy
bread_white;gluten
bagel;gluten
bagel_lox;fish|dairy|gluten
rice_white;-
pasta;gluten
spaghetti_meat_sauce;meat|gluten
spaghetti_meatballs;meat|egg|dairy|gluten
pizza_cheese;dairy|gluten
pizza_pepperoni;meat|pork|dairy|gluten
deep_dish_pizza;dairy|gluten
burrito;meat|dairy|gluten
taco;meat|dairy
hot_dog;meat|gluten
corn_dog;meat|egg|gluten|fried
chicken_breast;meat
fried_chicken;meat|gluten|fried
chicken_tenders;meat|gluten|fried
buffalo_wings;meat|dairy|fried
salmon;fish
steak;meat
bbq_ribs;meat|pork
pulled_pork;meat|pork|gluten
brisket;meat
cornbread;egg|dairy|gluten
biscuit;dairy|gluten
biscuits_gravy;meat|pork|dairy|gluten
chicken_waffles;meat|egg|dairy|gluten|fried
shrimp_grits;shellfish|dairy
jambalaya;meat|pork|shellfish
gumbo;meat|pork|shellfish|gluten
mac_and_cheese;dairy|gluten
clam_chowder;meat|pork|shellfish|dairy|gluten
lobster_roll;shellfish|dairy|gluten
crab_cakes;shellfish|egg|gluten
philly_cheesesteak;meat|dairy|gluten
chili;meat
meatloaf;meat|egg|gluten
mashed_potatoes;dairy
baked_potato;meat|pork|dairy
corn_cob;veg
coleslaw;egg|veg
potato_salad;egg
onion_rings;gluten|fried
fries_generic;fried
hash_browns;fried
bacon;meat|pork
sausage_links;meat|pork
eggs_benedict;meat|pork|egg|dairy|gluten
french_toast;egg|dairy|gluten|sweet
waffles;egg|dairy|gluten|sweet
pancakes;egg|dairy|gluten|sweet
oatmeal;-
avocado_toast;gluten
acai_bowl;sweet
blt;meat|pork|gluten
club_sandwich;meat|pork|gluten
reuben;meat|dairy|gluten
turkey_sandwich;meat|gluten
pbj_sandwich;gluten|sweet
grilled_cheese;dairy|gluten
chicken_pot_pie;meat|dairy|gluten
# Caesar dressing is made with anchovies and egg; croutons are bread.
caesar_salad;fish|egg|dairy|gluten|veg
cobb_salad;meat|pork|egg|dairy|veg
green_salad;veg
soft_pretzel;gluten
fish_and_chips;fish|gluten|fried
apple_pie;dairy|gluten|sweet
cheesecake;egg|dairy|gluten|sweet
brownie;egg|dairy|gluten|sweet
milkshake;dairy|sweet
potato_chips;fried
chocolate_bar;dairy|sweet
cookie_chocolate_chip;egg|dairy|gluten|sweet
donut_glazed;egg|dairy|gluten|fried|sweet
croissant;dairy|gluten
greek_yogurt;dairy
ice_cream_vanilla;dairy|sweet
peanut_butter;-
almonds;-
milk;dairy
coffee_black;-
latte;dairy
orange_juice;sweet
smoothie;sweet
protein_shake;dairy
beer;gluten|alcohol
wine_red;alcohol
cola;sweet
chicken_tikka_masala;meat|dairy
butter_chicken;meat|dairy
chicken_curry_rice;meat
chicken_korma;meat|dairy
chicken_vindaloo;meat
rogan_josh;meat|dairy
tandoori_chicken;meat|dairy
palak_paneer;dairy|veg
paneer_butter_masala;dairy
paneer_tikka;dairy|veg
malai_kofta;dairy|fried
chana_masala;-
chole_bhature;gluten|fried
rajma;-
aloo_gobi;veg
dal;-
dal_makhani;dairy
khichdi;-
biryani;meat|dairy
veg_biryani;dairy|veg
naan;dairy|gluten
garlic_naan;dairy|gluten
roti;gluten
paratha;dairy|gluten
aloo_paratha;dairy|gluten
masala_dosa;-
idli;veg
medu_vada;fried
uttapam;veg
sambar;veg
upma;gluten
poha;-
pav_bhaji;dairy|gluten|veg
vada_pav;gluten|fried
pani_puri;gluten|fried
bhel_puri;gluten|fried
samosa;gluten|fried
pakora;fried|veg
raita;dairy
gulab_jamun;dairy|gluten|fried|sweet
jalebi;gluten|fried|sweet
kheer;dairy|sweet
mango_lassi;dairy|sweet
masala_chai;dairy|sweet
tacos_al_pastor;meat|pork
tacos_carne_asada;meat
fish_tacos;fish
birria_tacos;meat
enchiladas;meat|dairy
quesadilla;dairy|gluten
nachos;dairy|fried
chips_guacamole;fried
chips_salsa;fried
queso_dip;dairy|fried
# Tamale masa and restaurant refried beans are usually made with lard.
tamales;meat|pork
chilaquiles;egg|dairy|fried
pozole;meat|pork
elote;egg|dairy
churros;gluten|fried|sweet
burrito_bowl;meat|dairy
fajitas;meat|gluten|veg
tostadas;meat|dairy|fried
tortilla_soup;meat|dairy
huevos_rancheros;egg|dairy
chimichanga;meat|dairy|gluten|fried
flautas;meat|fried
chicken_mole;meat|gluten
refried_beans;pork
mexican_rice;-
breakfast_burrito;meat|pork|egg|dairy|gluten
horchata;dairy|sweet
orange_chicken;meat|gluten|fried|sweet
general_tso_chicken;meat|gluten|fried|sweet
sesame_chicken;meat|gluten|fried|sweet
kung_pao_chicken;meat|gluten
sweet_sour_pork;meat|pork|gluten|fried|sweet
beef_broccoli;meat|gluten|veg
mongolian_beef;meat|gluten|sweet
lo_mein;egg|gluten
chow_mein;egg|gluten
fried_rice;egg|gluten
egg_roll;meat|pork|gluten|fried
spring_roll_fried;gluten|fried
dumplings_steamed;meat|pork|gluten
potstickers;meat|pork|gluten
mapo_tofu;meat|pork
wonton_soup;meat|pork|egg|gluten
hot_sour_soup;meat|pork|egg|gluten
pork_bao;meat|pork|gluten
peking_duck;meat|gluten
sushi_nigiri;fish
sashimi;fish
# California rolls use imitation crab (surimi, a fish product) and mayonnaise.
california_roll;fish|egg
spicy_tuna_roll;fish|egg
ramen_restaurant;meat|pork|egg|gluten
ramen_instant;gluten|fried
# Dashi broth is made from bonito (fish).
udon;fish|gluten
shrimp_tempura;shellfish|egg|gluten|fried
chicken_teriyaki;meat|gluten
katsu_curry;meat|egg|gluten|fried
gyudon;meat|gluten
gyoza;meat|pork|gluten
miso_soup;fish
onigiri;-
edamame;veg
poke_bowl;fish|gluten
bibimbap;meat|egg|veg
bulgogi;meat|gluten
korean_fried_chicken;meat|gluten|fried|sweet
# Traditional kimchi is seasoned with fish sauce or salted shrimp.
kimchi;fish|veg
japchae;meat|egg|gluten|veg
tteokbokki;fish|gluten
kimchi_fried_rice;fish|egg
green_curry;meat|fish
red_curry;meat|fish
massaman_curry;meat|fish
tom_yum;fish|shellfish
pad_thai;fish|egg
pad_see_ew;meat|egg|gluten
drunken_noodles;meat|fish|gluten
basil_chicken;meat|fish
papaya_salad;fish|shellfish|veg
chicken_satay;meat
mango_sticky_rice;sweet
thai_iced_tea;dairy|sweet
pho;meat|fish
banh_mi;meat|pork|gluten
fresh_spring_rolls;meat|pork|shellfish|veg
vermicelli_bowl;meat|pork|fish|veg
vietnamese_coffee;dairy|sweet
lasagna;meat|dairy|gluten
fettuccine_alfredo;dairy|gluten
carbonara;meat|pork|egg|dairy|gluten
pesto_pasta;dairy|gluten
chicken_parmesan;meat|egg|dairy|gluten|fried
risotto;dairy
gnocchi;dairy|gluten
ravioli;egg|dairy|gluten
minestrone;gluten|veg
bruschetta;gluten|veg
caprese_salad;dairy|veg
calzone;dairy|gluten
tiramisu;egg|dairy|gluten|sweet
cannoli;dairy|gluten|fried|sweet
gelato;dairy|sweet
falafel;fried
falafel_wrap;gluten|fried|veg
shawarma;meat|gluten
kebab;meat
hummus;-
pita;gluten
tabbouleh;gluten|veg
baba_ganoush;veg
shakshuka;egg|veg
baklava;dairy|gluten|sweet
gyro;meat|dairy|gluten
greek_salad;dairy|veg
spanakopita;egg|dairy|gluten|veg
tzatziki;dairy
jerk_chicken;meat
rice_and_beans;-
plantains;fried
cuban_sandwich;meat|pork|dairy|gluten
empanadas;meat|gluten
arepa;dairy
pupusas;dairy
ceviche;fish
injera_wat;meat|egg|dairy
```

CREATE `core/src/test/java/com/example/identify/core/FoodTagsTest.java`
```java
package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class FoodTagsTest {

    /** The tag file shipped in the app. Gradle runs :core tests with the core/ folder as working directory. */
    static Map<String, Set<FoodTag>> shipped() throws IOException {
        File f = new File("../app/src/main/assets/food_tags.txt");
        return FoodTags.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void parsesTagsNoneAndComments() {
        Map<String, Set<FoodTag>> m = FoodTags.parse("# c\n\nburger;meat|dairy|gluten\nbanana;-\n");
        assertEquals(2, m.size());
        assertTrue(m.get("burger").contains(FoodTag.MEAT));
        assertTrue(m.get("burger").contains(FoodTag.GLUTEN));
        assertEquals(3, m.get("burger").size());
        assertTrue(m.get("banana").isEmpty());
    }

    @Test
    public void unknownTagNamesTheLine() {
        try {
            FoodTags.parse("a;meat\nb;meat|beef\n");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("line 2"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyTagFieldRejected() {
        FoodTags.parse("a;\n");
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateIdRejected() {
        FoodTags.parse("a;meat\na;fish\n");
    }

    @Test
    public void shippedFileCoversEveryTableFoodInOrder() throws IOException {
        List<String> foodIds = new ArrayList<>();
        for (FoodItem f : FoodCatalogTest.shipped()) foodIds.add(f.id);
        List<String> tagIds = new ArrayList<>(shipped().keySet());
        assertEquals(300, tagIds.size());
        assertEquals(foodIds, tagIds);
    }

    @Test
    public void shippedSpotChecks() throws IOException {
        Map<String, Set<FoodTag>> m = shipped();
        assertTrue("US fries use beef flavor", m.get("mcd_fries_medium").contains(FoodTag.MEAT));
        assertTrue("Caesar dressing has anchovies", m.get("caesar_salad").contains(FoodTag.FISH));
        assertTrue("refried beans use lard", m.get("refried_beans").contains(FoodTag.PORK));
        assertTrue(m.get("beer").contains(FoodTag.ALCOHOL));
        assertTrue(m.get("palak_paneer").contains(FoodTag.DAIRY));
        assertFalse(m.get("palak_paneer").contains(FoodTag.MEAT));
        assertFalse(m.get("veggie_burger").contains(FoodTag.MEAT));
        assertTrue(m.get("banana").isEmpty());
        assertTrue(m.get("samosa").contains(FoodTag.FRIED));
        assertTrue(m.get("gulab_jamun").contains(FoodTag.SWEET));
        assertTrue(m.get("green_salad").contains(FoodTag.VEG));
    }
}
```

CREATE `core/src/test/java/com/example/identify/core/DietRulesTest.java`
```java
package com.example.identify.core;

import static org.junit.Assert.assertEquals;
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

public class DietRulesTest {

    private static Set<String> diets(String... d) {
        return new HashSet<>(Arrays.asList(d));
    }

    @Test
    public void vegetarianAvoidsMeatFirst() {
        List<DietRules.Conflict> c = DietRules.conflicts(diets(DietRules.VEGETARIAN),
                EnumSet.of(FoodTag.MEAT, FoodTag.PORK, FoodTag.DAIRY));
        assertEquals(1, c.size());
        assertEquals(DietRules.VEGETARIAN, c.get(0).diet);
        assertEquals(FoodTag.MEAT, c.get(0).tag);
    }

    @Test
    public void veganAvoidsDairyAndEgg() {
        assertEquals(FoodTag.EGG, DietRules.conflicts(diets(DietRules.VEGAN),
                EnumSet.of(FoodTag.EGG, FoodTag.DAIRY, FoodTag.GLUTEN)).get(0).tag);
        assertTrue(DietRules.conflicts(diets(DietRules.VEGAN), EnumSet.of(FoodTag.GLUTEN, FoodTag.VEG)).isEmpty());
    }

    @Test
    public void pescatarianHalalKosherGlutenDairy() {
        assertTrue(DietRules.conflicts(diets(DietRules.PESCATARIAN), EnumSet.of(FoodTag.FISH)).isEmpty());
        assertEquals(FoodTag.PORK, DietRules.conflicts(diets(DietRules.HALAL),
                EnumSet.of(FoodTag.MEAT, FoodTag.PORK)).get(0).tag);
        assertEquals(FoodTag.ALCOHOL, DietRules.conflicts(diets(DietRules.HALAL),
                EnumSet.of(FoodTag.GLUTEN, FoodTag.ALCOHOL)).get(0).tag);
        assertEquals(FoodTag.SHELLFISH, DietRules.conflicts(diets(DietRules.KOSHER),
                EnumSet.of(FoodTag.SHELLFISH, FoodTag.DAIRY)).get(0).tag);
        assertEquals(FoodTag.GLUTEN, DietRules.conflicts(diets(DietRules.GLUTEN_FREE),
                EnumSet.of(FoodTag.GLUTEN)).get(0).tag);
        assertEquals(FoodTag.DAIRY, DietRules.conflicts(diets(DietRules.DAIRY_FREE),
                EnumSet.of(FoodTag.DAIRY)).get(0).tag);
    }

    @Test
    public void noRestrictionsAndUnknownLabelsAvoidNothing() {
        assertTrue(DietRules.conflicts(diets("No restrictions"), EnumSet.allOf(FoodTag.class)).isEmpty());
        assertTrue(DietRules.conflicts(diets("Paleo"), EnumSet.allOf(FoodTag.class)).isEmpty());
        assertTrue(DietRules.conflicts(Collections.emptySet(), EnumSet.allOf(FoodTag.class)).isEmpty());
    }

    @Test
    public void sameTagIsReportedOnce() {
        List<DietRules.Conflict> c = DietRules.conflicts(diets(DietRules.VEGETARIAN, DietRules.VEGAN),
                EnumSet.of(FoodTag.MEAT));
        assertEquals(1, c.size());
        assertEquals(DietRules.VEGETARIAN, c.get(0).diet);
    }

    @Test
    public void severalDietsInOnboardingOrder() {
        List<DietRules.Conflict> c = DietRules.conflicts(diets(DietRules.GLUTEN_FREE, DietRules.VEGAN),
                EnumSet.of(FoodTag.DAIRY, FoodTag.GLUTEN));
        assertEquals(2, c.size());
        assertEquals(DietRules.VEGAN, c.get(0).diet);
        assertEquals(FoodTag.DAIRY, c.get(0).tag);
        assertEquals(DietRules.GLUTEN_FREE, c.get(1).diet);
    }

    @Test
    public void labelsMatchTheOnboardingChips() throws IOException {
        String arrays = new String(Files.readAllBytes(new File("../app/src/main/res/values/arrays.xml").toPath()),
                StandardCharsets.UTF_8);
        for (String d : DietRules.DIETS) {
            assertTrue(d, arrays.contains("<item>" + d + "</item>"));
        }
    }
}
```

## VERIFY 11.1

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew :core:test --console=plain
```
BUILD SUCCESSFUL; `core/build/test-results/test` holds 19 result files (17 before plus FoodTagsTest and DietRulesTest), all with `failures="0" errors="0"`.

Commit subject: `Tag all 300 foods with what they usually contain, and map diets to tags`.
