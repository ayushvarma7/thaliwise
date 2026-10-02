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
