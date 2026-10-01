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
