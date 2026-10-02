# STEP 10.7: Verify, install, device test with the user, docs, final commit

Goal: make every user-facing and developer-facing text match the food app, prove the Phase 10 rules with a compliance script, install on the Pixel 8 without losing data, and walk through the storyboard on the phone with the user.

## Part A: texts that still describe the old app

The privacy screen (shown by Health Connect) still says meals are written "in a later version" and does not mention the profile. The manifest comment says the same.

EDIT `app/src/main/res/values/strings.xml`
Find:
```xml
    <string name="privacy_policy_text">IdentifyVLM reads your steps, calories burned, and logged nutrition from Health Connect only to show your daily progress inside the app. In a later version it will also write meals that you confirm as nutrition records.\n\nAll data stays on this phone. Nothing is sent to any server. The app uses the internet only once, to download the AI model.\n\nYou can remove access at any time in Health Connect.</string>
```
Replace with:
```xml
    <string name="privacy_policy_text">IdentifyVLM reads your steps, calories burned, and logged meals from Health Connect only to show your daily progress inside the app. It writes a meal into Health Connect only when you tap Log, and deletes only meals that it wrote, when you ask.\n\nYour profile (name, age, sex, height, weight, activity, goals, and food preferences) is used only to compute your calorie budget and step goal and to favor your cuisines.\n\nAll data stays on this phone. Nothing is sent to any server. The app uses the internet only once, to download the AI model.\n\nYou can remove access at any time in Health Connect.</string>
```

EDIT `app/src/main/AndroidManifest.xml`
Find:
```xml
    <!-- Health Connect. Read today's steps and calories; WRITE_NUTRITION is for logging meals in a later phase. -->
```
Replace with:
```xml
    <!-- Health Connect. Read today's steps, calories, and meals; WRITE_NUTRITION logs and deletes this app's meals. -->
```

## Part B: README

EDIT `README.md`
Find:
```markdown
IdentifyVLM is an Android app that identifies the main object in a photo, entirely on the phone, using Liquid AI's LFM2.5-VL-1.6B vision language model running through llama.cpp. The app is written in pure Java (with a small C++ JNI bridge) and adapts to one user through a local memory of their past corrections. After a one-time model download of about 1.3 GB, it works fully offline.
```
Replace with:
```markdown
IdentifyVLM is a private, offline food and activity companion for Android. You snap a meal; Liquid AI's LFM2.5-VL-1.6B vision language model, running on the phone through llama.cpp, names the dish and its cuisine; the app finds the calories for a typical serving in a 300-row table, lets you confirm the portion, and logs the meal into Health Connect next to the steps and calories burned that Fitbit or Google Fit record. The home screen shows calories eaten against a personal budget from a short onboarding profile, steps against a goal, and today's meals.

The app is written in pure Java (with a small C++ JNI bridge) and adapts to one user through a local memory of their past corrections. After a one-time model download of about 1.3 GB, it works fully offline. Personas, the profiling questions, user stories, and the UI storyboard are in `docs/USER_STORIES.md`.
```

EDIT `README.md`
Find:
```markdown
- A Pixel 8, or another arm64 Android 12+ device whose CPU supports dotprod and i8mm (see Known limitations)
```
Replace with:
```markdown
- A Pixel 8, or another arm64 Android 14+ device (Health Connect is built in from Android 14) whose CPU supports dotprod and i8mm (see Known limitations)
```

EDIT `README.md`
Find:
```markdown
          -> Label/Description parse
  -> user accepts or corrects
  -> Room row + in-memory cache + experiment log
```
Replace with:
```markdown
          -> Label/Cuisine/Description parse
  -> table match (FoodMatcher: label words, then the model's cuisine and the user's favorite cuisines)
  -> user logs the meal (the guessed food counts as an accept, any other food as a correction)
  -> Health Connect NutritionRecord + Room row + in-memory cache + experiment log
```

EDIT `README.md`
Find:
```markdown
- `:core`: pure Java (no Android imports). Cosine similarity, kNN search, embedding byte codec, few-shot selection, prompt building, answer parsing, and history search. Covered by JVM unit tests (`./gradlew :core:test`, 7 test classes).
- `:app`: Android. Room database, the JNI bridge to llama.cpp (`app/src/main/cpp/vlm_bridge.cpp`), the model download, and the UI (XML layouts, Fragments, ViewModels, LiveData).
```
Replace with:
```markdown
- `:core`: pure Java (no Android imports). Cosine similarity, kNN search, embedding byte codec, few-shot selection, prompt building, answer parsing, history search, the food table and matcher, meals and diary grouping, and the profile math (calorie budget, step goal, units). Covered by JVM unit tests (`./gradlew :core:test`, 17 test classes).
- `:app`: Android. Room database, the JNI bridge to llama.cpp (`app/src/main/cpp/vlm_bridge.cpp`), the model download, Health Connect, onboarding, and the UI (XML layouts, Fragments, ViewModels, LiveData).
```

EDIT `README.md`
Find:
```markdown
Here, the thumbs up is not a reward used to update anything; the model is frozen.
```
Replace with:
```markdown
Here, logging the guessed food (an accept) is not a reward used to update anything; the model is frozen.
```

EDIT `README.md`
Find:
```markdown
Removing the `GGML_CPU_ARM_ARCH` line in `app/src/main/cpp/CMakeLists.txt` gives a more portable but slower build.
```
Replace with:
```markdown
Removing the `GGML_CPU_ARM_ARCH` line in `app/src/main/cpp/CMakeLists.txt` gives a more portable but slower build.
- Calories are table values for one typical serving, not a measurement of the plate. A dish that is not in the table can be logged only by naming a food that is.
- Cuisine matching works on words, so a favorite or model cuisine of "Latin American" also gives "American" rows the small tie-break boost.
- The diet and "eat more of" answers are stored and shown in Settings but do not yet change suggestions or warn about conflicts, and there are no nudges yet (planned, `docs/USER_STORIES.md` E8).
- The calorie budget is an estimate from the Mifflin-St Jeor formula, not medical advice. "Prefer not to say" uses the midpoint of the male and female constants.
```

EDIT `README.md`
Find:
```markdown
| compileSdk / targetSdk / minSdk | 35 / 35 / 31 |
```
Replace with:
```markdown
| compileSdk / targetSdk / minSdk | 35 / 35 / 34 |
```

EDIT `README.md`
Find:
```markdown
| `feedback` | Accept or Correct | `run_id`, accepted, correction, final label, time from result to decision |
| `setting_change`, `download_start`, `download_state`, `model_unload`, `model_delete`, `trim_memory` | as named | the new value or state |
```
Replace with:
```markdown
| `feedback` | a meal is logged from the result screen (once per photo) | `run_id`, accepted (the logged food is the guessed one), correction (the logged food's name), final label, time from result to decision |
| `setting_change`, `download_start`, `download_state`, `model_unload`, `model_delete`, `trim_memory` | as named | the new value or state |
| `health_permission_result`, `health_read` | the Health Connect permission screen closed; today's numbers were read | granted and denied permissions; steps, active, burned, and eaten kcal, ms, error |
| `meal_logged`, `meal_undone`, `meal_deleted` | a meal was written, undone on the result screen, or deleted from Today or Diary | `run_id`, model label and cuisine, query, food and its cuisine, portion, table and logged kcal, macros, meal slot, Health Connect record id, screen, error |
| `profile_saved` | Start or Save at the end of onboarding | mode (first_run or edit), has_name (never the name), age, sex, height, weight, activity, goal, reasons, cuisines, diet, eat more, units, budget and step goal with their suggestions and whether they were edited, Health Connect permissions granted, time in onboarding |
```

EDIT `README.md`
Find:
```markdown
READ_NUTRITION, and WRITE_NUTRITION (for logging meals in a later version).
```
Replace with:
```markdown
READ_NUTRITION (meals from every app, for Today and Diary), and WRITE_NUTRITION (logging meals, and deleting meals this app logged).
```

EDIT `README.md`
Find:
```markdown
When the identified label matches the nutrition table shipped in `app/src/main/assets/foods.txt` (74 foods: McDonald's US menu items and common foods), the result screen shows "Looks like food" with the calories for one serving. "Log meal" opens a dialog to pick the food (type another name if the guess is wrong), set the portion (0.5x to 3x), and check or edit the calories. "Log" writes one NutritionRecord into Health Connect (calories, protein, carbohydrate, fat, meal name, and a meal type from the time of day), then shows today's eaten, burned, and step totals. "Undo" deletes that record again.
```
Replace with:
```markdown
When the identified dish matches the nutrition table shipped in `app/src/main/assets/foods.txt` (300 foods: US fast-food chains, everyday staples, fruit, and dishes from Indian, Mexican, Chinese, Japanese, Korean, Thai, Vietnamese, Italian, Mediterranean, Middle Eastern, American, Southern, Caribbean, Latin American, Ethiopian, and Hawaiian cuisine), the result screen shows a big kcal card with the calories for one serving. "Log meal" opens a dialog to pick the food (type another name if the guess is wrong), set the portion (0.5x to 3x), and check or edit the calories; "Different food" opens it with an empty search. "Log" writes one NutritionRecord into Health Connect (calories, protein, carbohydrate, fat, meal name, and a meal type from the time of day), then shows today's eaten, burned, and step totals with Done and Undo. "Undo" deletes that record again. When nothing matches, or the photo is not food, a "No food recognized" card offers Name the food and Retake.
```

EDIT `README.md`
Find:
```markdown
and prefers branded rows only when the brand is named.
```
Replace with:
```markdown
and prefers branded rows only when the brand is named. Among rows that already match by name, the model's cuisine adds 0.1 and the user's favorite cuisines add 0.05, so cuisines only break ties and never turn a non-match into a match.
```

EDIT `README.md`
Find:
```markdown
Experiment log events: `meal_logged` (run_id, model label, query, food, portion, table kcal, logged kcal, whether the user edited it, macros, meal slot, Health Connect record id, error) and `meal_undone`.
```
Replace with:
```markdown
Experiment log events: `meal_logged` (run_id, model label and cuisine, query, food and its cuisine, portion, table kcal, logged kcal, whether the user edited it, macros, meal slot, Health Connect record id, error), `meal_undone`, and `meal_deleted`.

## 13. Food app screens and user profile

Phase 10 turned the object identifier into the food app described in `docs/USER_STORIES.md` (personas, profiling, user stories, storyboard).

**Onboarding** runs once on first launch, before the main screens, and again from Settings > Edit profile with the answers filled in. Six steps, every question optional:

| Step | Asks | Used now for |
|---|---|---|
| 1 Welcome | name | "Hi Ayush" on Today |
| 2 About you | age, sex, height and weight (US or metric), activity level | calorie budget (Mifflin-St Jeor x activity) and step goal (6,000 to 12,000 by activity) |
| 3 Why you are here | one goal (lose, maintain, build muscle, eat healthier, just track) and reasons | budget minus 500 to lose, plus 300 to build muscle, never below 1,200 (1,500 for men); goal line on Today |
| 4 Food you love | favorite cuisines (17 chips) | tie-break boost for table rows of those cuisines |
| 5 How you eat | diet ("No restrictions" is exclusive) and what to eat more of | shown in Settings (warnings and nudges come later) |
| 6 Your plan | the suggested budget and step goal, editable, with the reasoning; optional Connect Health Connect | the daily budget and step goal |

The answers are stored in the app's SharedPreferences and logged locally as `profile_saved` (without the name).

**Screens** (bottom tabs Today, Diary, Settings):

- **Today** (home): greeting and goal, a calorie ring (eaten / budget, "kcal left" or "kcal over budget" in red), calories burned (total and active), a steps bar with "steps to go", today's meals from every app (other apps' meals are marked), and a Snap meal button. Without Health Connect access it shows a Connect card.
- **Snap**: Take photo or Pick from gallery, then Identify food.
- **Result**: the dish name, a cuisine chip, the description, the kcal card (Log meal, Different food) or the "No food recognized" card (Name the food, Retake), then the Logged card with today's totals, Done (back to a fresh Today), and Undo. Show details keeps the full timing, CPU, memory, and confidence breakdown.
- **Diary**: the last 7 days of meals grouped by day with daily totals. Tapping a meal this app logged deletes it after a confirmation; meals from other apps point to Health Connect.
- **Settings**: the profile summary with Edit profile and Identification history (the old History screen), then the model, memory, performance, telemetry, and Health Connect sections as before.

**Learning without Accept and Correct buttons:** logging a meal is the feedback. If the logged food is the table row the app guessed, the photo is saved as an accept; if it is another food, as a correction to that food's name. This happens once per photo, and the memory and few-shot layers (section 5) use it as before.
```

## Part C: compliance checks

CREATE `.toolchain/phase10_checks.sh`
```bash
#!/bin/bash
# Phase 10 compliance checks. Each check prints what it finds; the expected output is in the heading.
cd /Users/ayush/Downloads/CLAUDE/IdentifyVLM
echo "## 1 kt files (must be empty)"; find app/src core/src -name "*.kt"
echo "## 2 no androidx.health library (must be empty)"; grep -rn "androidx.health\|connect-client" app core --include=*.kts --include=*.java
echo "## 3 network only in ModelDownloader (must list only ModelDownloader.java)"; grep -rln "DownloadManager\|java.net\|HttpURLConnection\|okhttp\|retrofit" app/src/main/java
echo "## 4 no android imports in core (must be empty)"; grep -rn "import android" core/src
echo "## 5 forbidden words (must be empty)"; grep -rni "reinforcement\|coroutine\|StateFlow\|kotlinx\|compose" app/src core/src
echo "## 6 no em or en dash (must be empty)"; grep -rlI $'\xe2\x80\x94\|\xe2\x80\x93' --exclude-dir=third_party --exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle --exclude-dir=.cxx --exclude-dir=.toolchain .
echo "## 7 no camera or storage permission (must be empty)"; grep -n "CAMERA\|READ_EXTERNAL\|WRITE_EXTERNAL\|READ_MEDIA" app/src/main/AndroidManifest.xml
echo "## 8 no TODO (must be empty)"; grep -rn "TODO\|FIXME\|implement later\|not implemented" app/src core/src
echo "## 9 food table rows (must print 300)"; grep -vc "^#" app/src/main/assets/foods.txt
echo "## 10 health permissions (must print 5)"; grep -c "android.permission.health" app/src/main/AndroidManifest.xml
echo "## 11 database version unchanged (must print version = 1)"; grep -o "version = [0-9]*" app/src/main/java/com/example/identify/data/AppDatabase.java
echo "## 12 no build, dependency, native, or llama.cpp change since Phase 9 (must be empty)"; git diff --stat f59e2b6 -- build.gradle.kts settings.gradle.kts app/build.gradle.kts core/build.gradle.kts gradle app/src/main/cpp third_party
echo "## 13 onboarding not exported (must print android:exported=\"false\")"; grep -A2 'name=".OnboardingActivity"' app/src/main/AndroidManifest.xml | grep -o 'android:exported="false"'
echo "## 14 profile_saved never logs the name (must be empty)"; grep -n 'put(e, "name"' app/src/main/java/com/example/identify/OnboardingActivity.java
echo "## 15 deletes only this app's meals (must print 1)"; grep -c "if (!meal.mine)" app/src/main/java/com/example/identify/ui/MealDelete.java
echo "## 16 no vector drawables or launcher icons added (must be empty)"; find app/src/main/res -name "*.xml" -path "*drawable*"; find app/src/main/res -path "*mipmap*"
echo "## 17 privacy text no longer says later version (must be empty)"; grep -n "later version" app/src/main/res/values/strings.xml
```

## Part D: build and check

```bash
cd /Users/ayush/Downloads/CLAUDE/IdentifyVLM
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew :core:test :app:assembleDebug :app:lintDebug --console=plain
tail -n 1 app/build/reports/lint-results-debug.txt
bash .toolchain/phase10_checks.sh
```
BUILD SUCCESSFUL, lint `0 errors`, and every check prints its expected output.

## Part E: install and device test with the user

Install over the existing app. Never uninstall: that deletes the model files, the history, and the experiment log.

```bash
ADB=/Users/ayush/Library/Android/sdk/platform-tools/adb
$ADB devices -l
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB shell am start -n com.example.identify/.MainActivity
```

The executor checks that onboarding renders (screenshots with `$ADB exec-out screencap -p`) by paging with Next and Back without typing anything and without pressing Start, then leaves it on step 1. The user fills in the real answers: the executor never types the user's personal data and never taps a Health Connect permission screen.

Walk-through with the user (stories in brackets):

1. First launch opens onboarding at "Step 1 of 6" (US-1.1). Fill in all six steps; on step 2 try the US/Metric toggle (US-1.2); on step 5 check that "No restrictions" clears the other diet chips (US-1.5); on step 6 check the suggested budget and step goal and their explanation (US-1.6); tap Start.
2. Today shows "Hi <name>", the goal line, the calorie ring with the budget from step 6, burned calories, the steps bar, and today's meals (US-4.1 to US-4.4).
3. Snap meal, take or pick a photo of food, Identify food: the result shows the dish, a cuisine chip, and the kcal card (US-2.1, US-2.2, US-3.1).
4. Log meal, keep or change the portion, Log: the Logged card shows today's totals (US-3.2, US-3.3). Try Undo once and log again (US-3.4). Tap Done: Today shows the meal and the ring moved.
5. Diary lists the meal under Today with the day's total (US-5.1). Tap it and delete it (US-5.2); a meal from another app shows the "Logged by another app" message.
6. Settings shows the profile summary; Edit profile opens onboarding with the answers filled in (US-1.4, US-1.7); Identification history opens the old list.

Then read the new events from the experiment log:

```bash
$ADB shell cat /sdcard/Android/data/com.example.identify/files/experiments/experiment_log.jsonl | grep -E '"type":"(profile_saved|run|feedback|meal_logged|meal_undone|meal_deleted)"' | cut -c1-400
```

Record the measured values (label, cuisine, kcal, time to answer, the profile_saved fields) as a "Device test" paragraph at the end of README section 13, and fix anything the walk-through finds, each fix in its own commit.

## Part F: progress and commit

Append the 10.7 line to `PROGRESS.md` and commit with the subject `Phase 10 verification: privacy text, README for the food app, compliance checks` (and, after the device test, a separate commit for its results).
