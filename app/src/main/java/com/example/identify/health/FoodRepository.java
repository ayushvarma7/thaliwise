package com.example.identify.health;

import android.content.Context;
import android.util.Log;

import com.example.identify.Config;
import com.example.identify.core.FoodCatalog;
import com.example.identify.core.FoodItem;
import com.example.identify.core.FoodTag;
import com.example.identify.core.FoodTags;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The nutrition table (assets/foods.txt, 300 rows) and its diet tags (assets/food_tags.txt), each read
 * once and kept in memory. A file that cannot be read gives an empty result (logged) and is retried later.
 */
public final class FoodRepository {
    private FoodRepository() {}

    public static final String ASSET = "foods.txt";
    public static final String TAGS_ASSET = "food_tags.txt";

    private static volatile List<FoodItem> foods;
    private static volatile Map<String, Set<FoodTag>> tags;

    /** The table, or an empty list if the asset cannot be read (logged). */
    public static List<FoodItem> foods(Context ctx) {
        List<FoodItem> f = foods;
        if (f != null) return f;
        synchronized (FoodRepository.class) {
            if (foods == null) {
                try {
                    foods = Collections.unmodifiableList(FoodCatalog.parse(readAsset(ctx, ASSET)));
                    Log.i(Config.HEALTH_TAG, "food table loaded items=" + foods.size());
                } catch (IOException | IllegalArgumentException e) {
                    Log.e(Config.HEALTH_TAG, "cannot load " + ASSET, e);
                    return Collections.emptyList();
                }
            }
            return foods;
        }
    }

    /** What the food usually contains or is; empty for an unknown id or when the tag file cannot be read. */
    public static Set<FoodTag> tagsOf(Context ctx, String foodId) {
        Set<FoodTag> s = tags(ctx).get(foodId);
        return s == null ? Collections.emptySet() : s;
    }

    private static Map<String, Set<FoodTag>> tags(Context ctx) {
        Map<String, Set<FoodTag>> t = tags;
        if (t != null) return t;
        synchronized (FoodRepository.class) {
            if (tags == null) {
                try {
                    tags = Collections.unmodifiableMap(FoodTags.parse(readAsset(ctx, TAGS_ASSET)));
                    Log.i(Config.HEALTH_TAG, "food tags loaded items=" + tags.size());
                } catch (IOException | IllegalArgumentException e) {
                    Log.e(Config.HEALTH_TAG, "cannot load " + TAGS_ASSET, e);
                    return Collections.emptyMap();
                }
            }
            return tags;
        }
    }

    private static String readAsset(Context ctx, String name) throws IOException {
        try (InputStream in = ctx.getApplicationContext().getAssets().open(name)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
