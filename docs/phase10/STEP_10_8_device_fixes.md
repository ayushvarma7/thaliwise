# STEP 10.8: Fixes from the device walk-through

Found on the Pixel 8 (dark mode, 1080x2400) while paging through onboarding:

1. Step 2: the three sex buttons share the width equally, so in portrait "Prefer not to say" is cut to "Prefer not ...". In landscape it fits. Fix: give that button 1.5 times the width and use 8dp side padding on all three (about 285, 285, and 427 px on this screen; the label needs about 380 px).
2. Progress tracks: Material 3 draws the empty track of a determinate progress indicator in `colorSecondaryContainer`, which is the brand orange container (dark: #7A3500). The onboarding bar showed a green bar on a brown track, and the Today ring and steps bar get the same. Fix: a neutral `?attr/colorSurfaceVariant` track on all three.

Checked on the device and working as designed: all six steps render; Next with every field empty reaches the plan (2,000 kcal default and 8,000 steps for "Lightly active"); "No restrictions" clears the other diet chips and any diet chip clears it; switching Metric to US converts 178 cm to 5 ft 10 in; answers survive a rotation; an age of 5 shows "Enter an age between 13 and 100." and stays on step 2.

EDIT `app/src/main/res/layout/activity_onboarding.xml`
Find:
```xml
                        android:layout_weight="1"
                        android:saveEnabled="false"
                        android:text="@string/sex_female" />
```
Replace with:
```xml
                        android:layout_weight="1"
                        android:paddingStart="8dp"
                        android:paddingEnd="8dp"
                        android:saveEnabled="false"
                        android:text="@string/sex_female" />
```

EDIT `app/src/main/res/layout/activity_onboarding.xml`
Find:
```xml
                        android:layout_weight="1"
                        android:saveEnabled="false"
                        android:text="@string/sex_male" />
```
Replace with:
```xml
                        android:layout_weight="1"
                        android:paddingStart="8dp"
                        android:paddingEnd="8dp"
                        android:saveEnabled="false"
                        android:text="@string/sex_male" />
```

EDIT `app/src/main/res/layout/activity_onboarding.xml`
Find:
```xml
                        android:layout_weight="1"
                        android:saveEnabled="false"
                        android:text="@string/sex_unspecified" />
```
Replace with:
```xml
                        android:layout_weight="1.5"
                        android:paddingStart="8dp"
                        android:paddingEnd="8dp"
                        android:saveEnabled="false"
                        android:text="@string/sex_unspecified" />
```

EDIT `app/src/main/res/layout/activity_onboarding.xml`
Find:
```xml
        app:trackCornerRadius="4dp"
        app:trackThickness="8dp" />
```
Replace with:
```xml
        app:trackColor="?attr/colorSurfaceVariant"
        app:trackCornerRadius="4dp"
        app:trackThickness="8dp" />
```

EDIT `app/src/main/res/layout/fragment_today.xml`
Find:
```xml
                            app:indicatorSize="136dp"
```
Replace with:
```xml
                            app:indicatorSize="136dp"
                            app:trackColor="?attr/colorSurfaceVariant"
```

EDIT `app/src/main/res/layout/fragment_today.xml`
Find:
```xml
                        app:indicatorColor="@color/steps_bar"
```
Replace with:
```xml
                        app:indicatorColor="@color/steps_bar"
                        app:trackColor="?attr/colorSurfaceVariant"
```

## VERIFY 10.8

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew :app:assembleDebug :app:lintDebug --console=plain
tail -n 1 app/build/reports/lint-results-debug.txt
/Users/ayush/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```
BUILD SUCCESSFUL, lint `0 errors`; on the phone, step 2 shows the whole "Prefer not to say" label in portrait and the progress track is neutral gray.

Commit subject: `Fix cut-off sex label and orange progress tracks found on the device`.
