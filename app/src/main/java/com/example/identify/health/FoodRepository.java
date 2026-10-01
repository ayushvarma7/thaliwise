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
