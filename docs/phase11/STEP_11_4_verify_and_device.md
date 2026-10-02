# STEP 11.4: Verify, ask before installing, device test with the user, docs, final commit

Goal: prove the Phase 11 rules with a compliance script, document the coach in the README, and, only after the user says yes, install on the Pixel 8 and walk through the coach stories with the user.

## Part A: README section 14

EDIT `README.md`
Find:
```markdown
**Learning without Accept and Correct buttons:**
```
Replace with:
```markdown
**Coach (Phase 11):** see section 14.

**Learning without Accept and Correct buttons:**
```

Append this section at the very end of `README.md` (after the last line of section 13; if a "Device test" paragraph was added to section 13, append after it). Use the Edit tool on the last line of the file, keeping that line and adding the text below after it:

```markdown

## 14. Coach tips

Phase 11 uses the onboarding answers that Phase 10 stored (`docs/USER_STORIES.md` E8). The coach is a set of plain rules in `:core` (`Coach`, `DietRules`, `WalkMath`), not a model. It shows short tips where the user decides, and never blocks or delays logging.

- **Result screen, before logging:** a Coach card under the kcal card with up to 3 tips for the guessed food at one serving: diet warnings ("Usually contains meat, and your profile says Vegetarian."), a walk tip when the step goal is not reached and the meal is big or goes over budget ("Ayush, you are at 3,200 of 10,000 steps and this meal is about 450 kcal. A 30 minute walk (about 3,000 steps) burns about 141 kcal."), an over-budget note, and feedback on the "eat more of" answers (protein, vegetables, fried, sugar, portions). Dismiss hides it for that photo.
- **Today:** one line (over budget with a walk suggestion, step goal reached, steps left after 5 PM, or kcal left), with "Hide for today".
- **Settings:** "Coach tips" switch, on by default.

Data behind the tips: `app/src/main/assets/food_tags.txt` says what each of the 300 foods usually contains (meat, pork, fish, shellfish, egg, dairy, gluten, alcohol) and whether it is usually fried, sweet, or has vegetables. These are typical recipes, so the wording is always "usually". Walk numbers use 3.5 MET moderate walking at about 100 steps a minute (kcal per minute = 3.5 x 3.5 x kg / 200), with 70 kg when the weight was not given.

Experiment log: `nudge_shown` and `nudge_dismissed` (screen, run_id, food_id, kinds, and each tip with its numbers), and `meal_logged.coach_tips` (the kinds on screen when the meal was logged). Joining them shows which tips change what people log.

Limits: Kosher covers pork and shellfish only (not meat with dairy or certification), Halal covers pork and alcohol only, and the tags describe common recipes, not the photo. There are no push notifications; tips appear only inside the app.
```

## Part B: compliance checks

CREATE `.toolchain/phase11_checks.sh`
```bash
#!/bin/bash
# Phase 11 compliance checks. Each check prints what it finds; the expected output is in the heading.
cd /Users/ayush/Downloads/CLAUDE/IdentifyVLM
echo "## 1 kt files (must be empty)"; find app/src core/src -name "*.kt"
echo "## 2 no androidx.health library (must be empty)"; grep -rn "androidx.health\|connect-client" app core --include=*.kts --include=*.java
echo "## 3 network only in ModelDownloader (must list only ModelDownloader.java)"; grep -rln "DownloadManager\|java.net\|HttpURLConnection\|okhttp\|retrofit" app/src/main/java
echo "## 4 no android imports in core (must be empty)"; grep -rn "import android" core/src
echo "## 5 forbidden words (must be empty)"; grep -rni "reinforcement\|coroutine\|StateFlow\|kotlinx\|compose" app/src core/src
echo "## 6 no em or en dash (must be empty)"; grep -rlI $'\xe2\x80\x94\|\xe2\x80\x93' --exclude-dir=third_party --exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle --exclude-dir=.cxx --exclude-dir=.toolchain .
echo "## 7 no camera, storage, or notification permission (must be empty)"; grep -n "CAMERA\|READ_EXTERNAL\|WRITE_EXTERNAL\|READ_MEDIA\|POST_NOTIFICATIONS" app/src/main/AndroidManifest.xml
echo "## 8 no TODO (must be empty)"; grep -rn "TODO\|FIXME\|implement later\|not implemented" app/src core/src
echo "## 9 food table rows (must print 300)"; grep -vc "^#" app/src/main/assets/foods.txt
echo "## 10 food tag rows (must print 300)"; grep -vc "^#" app/src/main/assets/food_tags.txt
echo "## 11 health permissions (must print 5)"; grep -c "android.permission.health" app/src/main/AndroidManifest.xml
echo "## 12 database version unchanged (must print version = 1)"; grep -o "version = [0-9]*" app/src/main/java/com/example/identify/data/AppDatabase.java
echo "## 13 no build, dependency, native, or llama.cpp change in Phase 11 (must be empty)"; git diff --stat b610e8f -- build.gradle.kts settings.gradle.kts app/build.gradle.kts core/build.gradle.kts gradle app/src/main/cpp third_party
echo "## 14 no shaming words in coach strings (must be empty)"; grep -E 'name="(tip|coach)_' app/src/main/res/values/strings.xml | grep -iwE "bad|guilt|guilty|cheat|cheating|junk|unhealthy|should|must|don.t"
echo "## 15 diet tips say usually (must print 1)"; grep -c 'name="tip_diet_conflict">Usually contains' app/src/main/res/values/strings.xml
echo "## 16 every tip kind has its own sentence (must be empty)"
for k in $(sed -n '/enum Kind/,/}/p' core/src/main/java/com/example/identify/core/Tip.java | grep -oE '^[[:space:]]+[A-Z_]+,?$' | tr -d ' ,'); do
  [ "$k" = "DAY_ON_TRACK" ] && continue
  grep -q "case $k:" app/src/main/java/com/example/identify/ui/TipFormat.java || echo "missing case $k"
done
echo "## 17 coach code never touches the log button (must be empty)"; grep -n "logMealButton" app/src/main/java/com/example/identify/ui/TipFormat.java app/src/main/java/com/example/identify/ui/CoachEvents.java core/src/main/java/com/example/identify/core/Coach.java
```

## Part C: build and check

```bash
cd /Users/ayush/Downloads/CLAUDE/IdentifyVLM
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew :core:test :app:assembleDebug :app:lintDebug --console=plain
tail -n 1 app/build/reports/lint-results-debug.txt
bash .toolchain/phase11_checks.sh
```
BUILD SUCCESSFUL, 21 core test classes with 0 failures, lint `0 errors`, and every check prints its expected output. Then append the 11.4 line to `PROGRESS.md` and commit (subject `Phase 11 verification: README coach section and compliance checks`).

## Part D: ask, then install

STOP and ask the user before installing; the user asked to approve every install. Tell them the APK path, its size, its SHA-256 (`shasum -a 256 app/build/outputs/apk/debug/app-debug.apk`), and that `adb install -r` keeps the model files, the experiment log, and the profile. Install only after a clear yes:

```bash
ADB=/Users/ayush/Library/Android/sdk/platform-tools/adb
$ADB devices -l
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
```

Never uninstall (that deletes the model, the history, and the log). Never type the user's personal data, never change their profile yourself, and never tap Health Connect permission screens.

## Part E: walk-through with the user

The user does each step; the executor may read screenshots (`$ADB exec-out screencap -p`) and the log.

1. Settings: the profile section has a "Coach tips" switch, on (US-8.6).
2. Before the step goal is reached, snap something big from the table (a burger, pizza, or burrito). The result shows the Coach card under the kcal card with the walk tip, starting with the user's name (US-8.1). If eaten plus the meal is over budget, the over-budget line follows (US-8.3).
3. Dismiss hides the card for that photo; log the meal anyway (logging works with or without the card).
4. If the user has a diet in their profile, snap a food it avoids (for a vegetarian, a chicken or beef dish): the first tip says "Usually contains meat, and your profile says Vegetarian." (US-8.2). If the user has no diet, skip this; do not edit their profile for them.
5. If the user picked "eat more of" answers, a matching food shows the feedback line (US-8.4).
6. Today shows one coach line; "Hide for today" hides it, and it stays hidden after switching tabs (US-8.5).
7. Turn the switch off: no Coach card and no Today line. Turn it back on.

Then read the coach events:

```bash
$ADB shell cat /sdcard/Android/data/com.example.identify/files/experiments/experiment_log.jsonl | grep -E '"type":"(nudge_shown|nudge_dismissed|meal_logged|setting_change)"' | cut -c1-400
```

Record what the phone showed (tip texts, numbers, events) as a "Device test" paragraph at the end of README section 14, fix anything the walk-through finds (each fix in its own commit, with its own step file `STEP_11_5_device_fixes.md` in the same format), append the PROGRESS line, and commit with a detailed message. Never push.
