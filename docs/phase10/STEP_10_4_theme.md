# STEP 10.4: Food-app theme (light and dark)

Goal: a fresh, food-app look: green primary (fresh food, "on track"), orange secondary (energy, steps), red only for "over budget". The dark palette is set by a `values-night` color file, so the single theme works in both modes.

REPLACE `app/src/main/res/values/colors.xml`
```xml
<resources>
    <color name="accept_green">#2E7D32</color>
    <color name="correct_amber">#FF8F00</color>

    <color name="brand_green">#2E7D32</color>
    <color name="on_brand_green">#FFFFFF</color>
    <color name="brand_green_container">#C8E6C9</color>
    <color name="on_brand_green_container">#0B3D0F</color>
    <color name="brand_orange">#E65100</color>
    <color name="on_brand_orange">#FFFFFF</color>
    <color name="brand_orange_container">#FFE0B2</color>
    <color name="on_brand_orange_container">#3E1C00</color>
    <color name="kcal_ok">#2E7D32</color>
    <color name="kcal_over">#C62828</color>
    <color name="steps_bar">#E65100</color>
</resources>
```

CREATE `app/src/main/res/values-night/colors.xml`
```xml
<resources>
    <color name="brand_green">#81C784</color>
    <color name="on_brand_green">#0B3D0F</color>
    <color name="brand_green_container">#1B5E20</color>
    <color name="on_brand_green_container">#C8E6C9</color>
    <color name="brand_orange">#FFB74D</color>
    <color name="on_brand_orange">#3E1C00</color>
    <color name="brand_orange_container">#7A3500</color>
    <color name="on_brand_orange_container">#FFE0B2</color>
    <color name="kcal_ok">#81C784</color>
    <color name="kcal_over">#EF9A9A</color>
    <color name="steps_bar">#FFB74D</color>
</resources>
```

REPLACE `app/src/main/res/values/themes.xml`
```xml
<resources>
    <style name="Theme.Identify" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="colorPrimary">@color/brand_green</item>
        <item name="colorOnPrimary">@color/on_brand_green</item>
        <item name="colorPrimaryContainer">@color/brand_green_container</item>
        <item name="colorOnPrimaryContainer">@color/on_brand_green_container</item>
        <item name="colorSecondary">@color/brand_orange</item>
        <item name="colorOnSecondary">@color/on_brand_orange</item>
        <item name="colorSecondaryContainer">@color/brand_orange_container</item>
        <item name="colorOnSecondaryContainer">@color/on_brand_orange_container</item>
        <item name="colorTertiary">@color/brand_orange</item>
    </style>
    <style name="ShapeAppearance.Identify.Rounded" parent="">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">16dp</item>
    </style>
</resources>
```

## VERIFY 10.4

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew :app:assembleDebug --console=plain -q
```
Exit 0.

Commit subject: `Give the app a food-app color theme for light and dark mode`.
