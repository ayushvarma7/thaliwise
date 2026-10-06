# BUILD SPEC: IdentifyVLM Phase 11, coach tips (nudges, diet warnings, eat-more feedback)

This folder is the single source of truth for Phase 11. It implements epic E8 of `docs/USER_STORIES.md` (stories US-8.1 to US-8.6): the "used later" column of the onboarding profile becomes real. The user's original idea was "Ayush, you haven't hit your steps and you're eating a double McDonald's cheeseburger". Phase 11 delivers it as gentle, factual tips that never block logging.

Execute the step files in order. Each lists its goal, the exact file operations, a VERIFY block, and the commit to make. Do not improvise beyond them; if something does not match, follow RULE 9.

| Step | File | Stories |
|---|---|---|
| 11.1 | `STEP_11_1_food_tags.md`: diet tags for all 300 foods (`food_tags.txt`), `FoodTag`, `FoodTags` parser, `DietRules`, tests | US-8.2, US-8.4 |
| 11.2 | `STEP_11_2_coach_core.md`: `WalkMath`, `Tip`, `Coach` (meal and day rules), tests including the Ayush scenario | US-8.1 to US-8.5 |
| 11.3 | `STEP_11_3_app_coach.md`: tip sentences, Coach card on the result screen, Today line, Settings switch, `nudge_shown` / `nudge_dismissed` events | US-8.1 to US-8.6 |
| 11.4 | `STEP_11_4_verify_and_device.md`: README section 14, compliance checks, ASK BEFORE INSTALLING, walk-through with the user | all |
| 11.5 | `STEP_11_5_device_fixes.md`: label cleaning (copied prompt words, free text), product names in the prompt, 20 packaged snacks, threads limited to fast cores | US-2.2, US-2.3, US-3.1 |
| 11.6 | `STEP_11_6_answer_fixes.md`: no example dishes in the prompt, "Unknown food", grammar-forced three-line answer, no non-answer few-shot examples, eaten 0 kcal instead of "no data" | US-2.2, US-2.3, US-4.1 |

## What the coach does (summary)

- **Result screen, before logging:** up to 3 tips for the guessed food at one serving, in this order: diet conflicts ("Usually contains meat, and your profile says Vegetarian."), a walk tip with the user's name when steps are below the goal and the meal is at least 400 kcal or goes over budget, an over-budget note, then feedback on "eat more of" answers (protein at least 25 g, vegetables, fried, sugar, half portion for meals of 700 kcal or more), and for "Build muscle" in the evening, the kcal still left.
- **Today:** one line: over budget with a 30 minute walk, step goal reached, steps left after 5 PM, or kcal left.
- **Settings:** "Coach tips" switch (on by default). Dismiss hides the result card for that photo; "Hide for today" hides the Today line until tomorrow.
- **Log:** `nudge_shown`, `nudge_dismissed`, and `meal_logged.coach_tips`.

## RULES (same as Phase 10, plus one)

1. Project root `/Users/ayush/Downloads/CLAUDE/IdentifyVLM`. Absolute paths. Read a file before editing it.
2. Java 17 only, zero Kotlin, no coroutines, Flow, Compose, or RxJava. No new dependency, no new permission (no notifications).
3. Do not touch native code, `third_party/`, the Room schema (version stays 1), the model prompt, the sampling settings, or `foods.txt`.
4. No em-dash (U+2014) or en-dash (U+2013) in any file. No `TODO`, `FIXME`, stubs, or placeholders. Framework drawables only.
5. Never write fake records into Health Connect, never grant permissions with adb, never uninstall the app, never type the user's personal data or change their profile.
6. **Ask the user before every install on the phone** (they asked for this). Building is fine without asking.
7. File operations: `CREATE \`path\`` (new file, complete content), `REPLACE \`path\`` (overwrite an existing file completely), `EDIT \`path\`` with `Find:` and `Replace with:` blocks (Find must match exactly once). Apply in order. The helper `.toolchain/apply_step.py <step file>` applies a whole step file and stops on the first mismatch. Step 11.4 Part A has one free-form append (README section 14) that the helper does not do; make it with the Edit tool.
8. Build with:
   ```bash
   cd /Users/ayush/Downloads/CLAUDE/IdentifyVLM
   JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew <tasks>
   ```
9. After each step's VERIFY passes, append `- 11.N <name>: DONE - <result>` under a `## Phase 11: Coach tips` heading in `PROGRESS.md` (create the heading once), then commit with a descriptive multi-paragraph message (subject; what changed per file and why; how verified; no co-author or tool-attribution trailers). Never push.
10. On failure: read the whole error, fix, rerun, at most 5 attempts per error, then write `BLOCKERS.md` and stop.

## FACTS (verified when this spec was written)

- Last commit before Phase 11 specs: `ef3c844 List step 10.8 (device fixes) in the Phase 10 step table`. Before the spec commit: 17 core test classes and 80 tests pass, lint has 0 errors and 22 warnings, and the Phase 10 compliance checks pass.
- The step files were replayed on the working tree at `ef3c844` before they were committed: 11.1 to 11.3 apply with no mismatch; `:core:test` passes (21 classes, 108 tests, 0 failures); `:app:assembleDebug` and `:app:lintDebug` succeed with 0 errors and the same 22 warnings; and the 11.4 compliance script prints its expected output for all 17 checks (checks 14 and 16 were also shown to catch a planted problem).
- Onboarding labels the rules depend on (from `app/src/main/res/values/arrays.xml`): diets "Vegetarian", "Vegan", "Pescatarian", "Halal", "Kosher", "Gluten-free", "Dairy-free", "No restrictions"; eat-more "More protein", "More vegetables", "Less sugar", "Fewer fried foods", "Smaller portions", "More home cooking" (the last has no rule; the app cannot see where food was cooked). Tests in 11.1 and 11.2 fail if these labels drift.
- `UserProfile.weightKg` is 0 when not given; `HealthConnectRepository.readToday` gives `null` without any read permission, steps `-1` and kcal `NaN` for single missing values.
- The Phase 10 device walk-through by the user (onboarding with real answers) may still be pending. Phase 11 does not depend on it, but the coach is most useful after the user has saved a profile.
- Phone: Pixel 8, serial `48071VDJH00284`, dark mode, 1080x2400, auto-rotate on.
