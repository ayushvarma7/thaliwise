# BUILD SPEC: IdentifyVLM Phase 10, food app redesign with user profiling

This folder is the single source of truth for Phase 10. It implements `docs/USER_STORIES.md` (personas, profiling, user stories, storyboard). Execute the step files in order. Each step file lists its goal, the exact file operations, a VERIFY block, and the commit to make.

| Step | File | Stories |
|---|---|---|
| 10.1 | `STEP_10_1_food_knowledge.md`: food-focused prompt with a cuisine line, about 300-row multi-cuisine table, cuisine-aware matching | US-2.2, US-2.3 |
| 10.2 | `STEP_10_2_profile_and_diary_core.md`: UserProfile, ProfileMath (budget and step goal), MealEntry, Diary grouping, all in `:core` with tests | US-1.2 to US-1.6, US-5.1 |
| 10.3 | `STEP_10_3_app_data.md`: profile storage in AppPrefs, reading meals from Health Connect, cuisine in results | US-4.4, US-5.1 |
| 10.4 | `STEP_10_4_theme.md`: food-app colors for light and dark | storyboard |
| 10.5 | `STEP_10_5_screens.md`: Today dashboard, Diary, Snap, new Result screen, navigation | US-2.1, US-3.x, US-4.x, US-5.x |
| 10.6 | `STEP_10_6_onboarding.md`: six-step onboarding, profile in Settings, Edit profile | US-1.1 to US-1.7 |
| 10.7 | `STEP_10_7_verify_and_device.md`: compliance checks, install, test with the user, docs, final commit | all |
| 10.8 | `STEP_10_8_device_fixes.md`: fixes found on the phone (cut-off "Prefer not to say", orange progress tracks) | US-1.2, US-4.1, US-4.2 |

## RULES (same as Phase 9, restated)

1. Project root `/Users/ayush/Downloads/CLAUDE/IdentifyVLM`. Absolute paths. Read a file before editing it.
2. Java 17 only, zero Kotlin, no coroutines, Flow, Compose, or RxJava. No new dependency: Material Components, AndroidX, and the platform Health Connect API are already available.
3. Do not touch native code, `third_party/`, the Room schema (version stays 1), or the sampling settings. The prompt text changes in Step 10.1 on purpose (the app is now a food app); nothing else in the model pipeline changes.
4. No em-dash (U+2014) or en-dash (U+2013) in any file. No `TODO`, `FIXME`, stubs, or placeholders. Framework drawables only (no new vector drawables, no launcher icon).
5. Never write fake records into Health Connect, never grant permissions with adb, never uninstall the app.
6. File operations: `CREATE \`path\`` (new file, complete content), `REPLACE \`path\`` (overwrite an existing file completely), `EDIT \`path\`` with `Find:` and `Replace with:` blocks (Find must match exactly once). Apply in order. The project helper `.toolchain/apply_step.py <step file>` applies a whole step file and stops on the first mismatch.
7. Build with:
   ```bash
   cd /Users/ayush/Downloads/CLAUDE/IdentifyVLM
   JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" GRADLE_USER_HOME="/Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home" ./gradlew <tasks>
   ```
8. After each step's VERIFY passes, append `- 10.N <name>: DONE - <result>` under `## Phase 10: Food app redesign` in `PROGRESS.md`, then commit with a descriptive multi-paragraph message (subject; what changed per file and why; how verified; no co-author or tool-attribution trailers). Never push.
9. On failure: read the whole error, fix, rerun, at most 5 attempts per error, then write `BLOCKERS.md` and stop.

## FACTS (verified)

- Last commit before Phase 10: `22cfb7e Add product doc: personas, user profiling, user stories, UI storyboard`.
- minSdk 34, compileSdk 35. Pixel 8 test phone (serial `48071VDJH00284`), all 5 Health Connect permissions granted; the user cleared app history and the experiment log before this phase.
- Real model answers seen on the phone with the old object prompt: "mouse", "Chicken", "Pita bread" (single words, low confidence). Pita had no table row.
- Health Connect read API: `new ReadRecordsRequestUsingFilters.Builder<>(NutritionRecord.class).setTimeRangeFilter(filter).setPageSize(int).build()`, `HealthConnectManager.readRecords(request, executor, OutcomeReceiver<ReadRecordsResponse<NutritionRecord>, HealthConnectException>)`, `NutritionRecord.getStartTime()`, `getMealName()`, `getMealType()`, `getEnergy()`, `getMetadata().getId()`, `getMetadata().getDataOrigin().getPackageName()`.
- Framework drawables used for tabs: `@android:drawable/ic_menu_today`, `@android:drawable/ic_menu_agenda`, `@android:drawable/ic_menu_preferences`, `@android:drawable/ic_menu_camera`.
