# IdentifyVLM

## 1. What this is

IdentifyVLM is a private, offline food and activity companion for Android. You snap a meal; Liquid AI's LFM2.5-VL-1.6B vision language model, running on the phone through llama.cpp, names the dish and its cuisine; the app finds the calories for a typical serving in a 300-row table, lets you confirm the portion, and logs the meal into Health Connect next to the steps and calories burned that Fitbit or Google Fit record. The home screen shows calories eaten against a personal budget from a short onboarding profile, steps against a goal, and today's meals.

The app is written in pure Java (with a small C++ JNI bridge) and adapts to one user through a local memory of their past corrections. After a one-time model download of about 1.3 GB, it works fully offline. Personas, the profiling questions, user stories, and the UI storyboard are in `docs/USER_STORIES.md`.

## 2. Requirements

- macOS (built on Apple Silicon, macOS 26)
- Android Studio, or the Android SDK command-line tools
- JDK 17 or newer (built with the JetBrains Runtime 21.0.8 bundled with Android Studio)
- Android NDK 27.2.12479018 and SDK CMake 3.22.1 (the exact versions used for this build)
- Android SDK Platform 35 and Build-Tools 35.0.0 / 34.0.0
- A Pixel 8, or another arm64 Android 14+ device (Health Connect is built in from Android 14) whose CPU supports dotprod and i8mm (see Known limitations)
- About 1.5 GB of free storage on the device

In the original checkout, the SDK, NDK, CMake, and Gradle caches live inside the project in `.toolchain/` (ignored by git), and `local.properties` points `sdk.dir` there. A fresh clone needs its own `local.properties` with `sdk.dir=<path to your Android SDK>`; Android Studio writes this file automatically.

## 3. Clone and build

```bash
git clone --recurse-submodules <repo-url> IdentifyVLM
cd IdentifyVLM            # or: git submodule update --init --recursive
./gradlew :core:test
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

llama.cpp is pinned as a git submodule at `third_party/llama.cpp`:

- tag: `b11323`
- commit: `f11d642a27b921cf22b6a8beb1b899f960fedcde`

The first build compiles llama.cpp, ggml, and libmtmd for arm64-v8a and can take several minutes on a slower machine. The native code is always built as Release, even for debug APKs, because a Debug native build makes inference many times slower.

## 4. Getting the model files

The model files are not part of the APK. Both come from https://huggingface.co/LiquidAI/LFM2.5-VL-1.6B-GGUF:

| File | Size | Role |
|---|---|---|
| `LFM2.5-VL-1.6B-Q4_0.gguf` | 696 MB | language model |
| `mmproj-LFM2.5-VL-1.6b-Q8_0.gguf` | 583 MB | vision projector (note the lowercase `b` in `1.6b`) |

In the app: open Settings and tap Download model. The download uses the system download manager and stores the files in `/sdcard/Android/data/com.example.identify/files/models/`.

Developer shortcut (install the app once first, so the package directory exists):

```bash
hf download LiquidAI/LFM2.5-VL-1.6B-GGUF LFM2.5-VL-1.6B-Q4_0.gguf mmproj-LFM2.5-VL-1.6b-Q8_0.gguf --local-dir .
adb shell mkdir -p /sdcard/Android/data/com.example.identify/files/models
adb push LFM2.5-VL-1.6B-Q4_0.gguf /sdcard/Android/data/com.example.identify/files/models/
adb push mmproj-LFM2.5-VL-1.6b-Q8_0.gguf /sdcard/Android/data/com.example.identify/files/models/
```

## 5. Architecture

```
photo
  -> private copy (EXIF fixed, 1024 px, kept for history and later fine-tuning)
  -> inference copy (longest side 512 px, so LFM2-VL encodes it as one tile)
  -> vision encoder, run once per photo
  -> mean-pooled, L2-normalized embedding
  -> kNN over saved embeddings
       -> (score >= threshold) memory label
       -> (else) few-shot prompt + VLM generation that reuses the same image encoding
          -> Label/Cuisine/Description parse
  -> table match (FoodMatcher: label words, then the model's cuisine and the user's favorite cuisines)
  -> user logs the meal (the guessed food counts as an accept, any other food as a correction)
  -> Health Connect NutritionRecord + Room row + in-memory cache + experiment log
```

Why the inference copy is 512 px: LFM2-VL splits any image larger than about 724x724 into 512x512 tiles plus a thumbnail, and each tile is a full vision encoder pass. On the Pixel 8 CPU one pass took 18 to 54 s (see section 6), so a 1024 px photo (6 tiles + thumbnail) took minutes before the first word. One 512 px tile is one pass, and that single encoding now serves both the memory lookup and the answer.

The app adapts to the user through two layers. Neither changes the model.

- **Layer 1, few-shot prompt injection.** Up to 5 past corrections are written into the system prompt as "You said X. Correct answer: Y." Up to 3 are chosen as the most similar by embedding; the rest are the most recent, with duplicates removed.
- **Layer 2, embedding kNN cache.** Every saved photo stores its embedding and final label. When a new photo's nearest stored embedding has cosine similarity at or above the threshold (default 0.92, adjustable in Settings), the app returns that label without running text generation. "Run the model anyway" on the result screen forces a full model answer.

Module layout:

- `:core`: pure Java (no Android imports). Cosine similarity, kNN search, embedding byte codec, few-shot selection, prompt building, answer parsing, history search, the food table and matcher, meals and diary grouping, and the profile math (calorie budget, step goal, units). Covered by JVM unit tests (`./gradlew :core:test`, 17 test classes).
- `:app`: Android. Room database, the JNI bridge to llama.cpp (`app/src/main/cpp/vlm_bridge.cpp`), the model download, Health Connect, onboarding, and the UI (XML layouts, Fragments, ViewModels, LiveData).

## 6. Performance on Pixel 8

First measurement, with the original pipeline (Pixel 8, Android 17, 4 threads, phone charging over USB): a 771x1024 photo was split into a 2x3 grid of 512x512 tiles plus a thumbnail. Each tile took 17.8 to 54.4 s to encode (`image slice encoded in` lines) and 3.3 to 10.3 s to decode into the language model (`image decoded` lines). The answer had not appeared after several minutes. That run led to the single-tile, encode-once pipeline in section 5.

Current pipeline, first measured run (2026-10-01, Pixel 8, Android 17, 4 threads, vision token cap 256, phone plugged in, model not yet loaded). Camera photo 4080x3072, stored as 771x1024, handed to the model as 386x512 (one tile, 192 image tokens), 82 prompt text tokens:

| Step | Measured value | How measured |
|---|---|---|
| Model load (first identify) | 2.3 s (language model 1.3 s, projector 0.9 s) | experiment log `phases` model_load, `native_load` |
| Image embedding | 7.3 s (vision encoder, 4.1 cores busy) | `phases` image_encode, `native_embed.t_encode_ms` |
| Text generation | 2.05 s (prefill 1.86 s: image tokens 1.08 s, text 0.78 s; then 2 tokens in 0.18 s) | `native_generate` |
| Total (model path) | 11.6 s including the first load, so about 9.3 s once loaded | run `total_ms` |
| Total (memory path) | not yet measured | |

CPU and memory for that run: 40.5 s of CPU time in 11.6 s, so 3.5 cores busy on average (39% of the 9 cores) and 4.5 at peak. 64% of the CPU time ran on the mid cores (Cortex-A715, about 2.24 GHz), 28% on the big core (Cortex-X3), 8% on the little cores. App memory rose from 0.2 GB to a 2.2 GB peak (1.4 GB anonymous: repacked language-model weights, the projector, buffers; 0.8 GB memory-mapped model file) and stays near 2 GB while the model is loaded, also in the background. Thermal status stayed "none". The vision encoder is 63% of the total, so the vision token cap in Settings is the main speed lever.

To measure, run this while identifying photos, then read `load_ms`, `embed_ms`, `gen_ms`, and `total_ms`:

```bash
adb logcat -s Identify:I VlmBridge:I
```

The memory path still runs the vision encoder to compute the embedding. It only skips text generation, so it is faster than the model path but not instant.

Every run also writes its full timing breakdown, CPU use, memory, and battery numbers to the experiment log (section 10), and the result screen shows a summary under Show details.

## 7. What the app learns and what it does not

- **Learns:** which labels this user prefers for things the model got wrong (through the prompt), and what this user's recurring objects look like (through stored embeddings).
- **Does not learn:** the model weights never change. There are no gradients, no fine-tuning, and no training on the device. Clearing history in Settings removes everything the app has "learned".
- **Why this is not reinforcement learning:** reinforcement learning updates a policy's parameters from a reward signal. Here, logging the guessed food (an accept) is not a reward used to update anything; the model is frozen. The accurate terms are personalized inference with a local correction memory, retrieval of few-shot examples, and a nearest-neighbor label cache.

## 8. Known limitations

- The 0.92 similarity threshold is a placeholder, not a validated value. Tune it in Settings using the `score` and `nearest` values that appear in the logcat lines.
- Mean-pooled projector outputs are not a trained retrieval embedding. Two different objects of the same kind (for example two different golden retrievers) may match each other.
- Text-only few-shot corrections can bias the model toward a corrected label on unrelated photos.
- CPU only. There is no Vulkan or OpenCL backend.
- `N_THREADS = 4` is a starting point. Settings > Performance changes the thread count (1 to 8) and the vision token cap (64 to 256); compare runs in the experiment log.
- The vision encoder dominates the time on the CPU. Fewer vision tokens per image encode faster but see less detail.
- The native code is compiled for `armv8.2-a+dotprod+i8mm+fp16` (the Pixel 8 Tensor G3 supports all of these). On an older arm64 CPU without i8mm, the app can crash when the model loads. Removing the `GGML_CPU_ARM_ARCH` line in `app/src/main/cpp/CMakeLists.txt` gives a more portable but slower build.
- Calories are table values for one typical serving, not a measurement of the plate. A dish that is not in the table can be logged only by naming a food that is.
- Cuisine matching works on words, so a favorite or model cuisine of "Latin American" also gives "American" rows the small tie-break boost.
- The diet and "eat more of" answers are stored and shown in Settings but do not yet change suggestions or warn about conflicts, and there are no nudges yet (planned, `docs/USER_STORIES.md` E8).
- The calorie budget is an estimate from the Mifflin-St Jeor formula, not medical advice. "Prefer not to say" uses the midpoint of the male and female constants.

## 9. Pinned versions

| Component | Version |
|---|---|
| Android Gradle Plugin | 8.7.3 |
| Gradle wrapper | 8.9 |
| NDK | 27.2.12479018 |
| SDK CMake | 3.22.1 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 34 |
| ABI | arm64-v8a |
| llama.cpp | tag `b11323`, commit `f11d642a27b921cf22b6a8beb1b899f960fedcde` |
| Model file | `LFM2.5-VL-1.6B-Q4_0.gguf` |
| Projector file | `mmproj-LFM2.5-VL-1.6b-Q8_0.gguf` |

## 10. Experiment log and telemetry

Every identification and every important app event is appended as one JSON object per line to:

```
/sdcard/Android/data/com.example.identify/files/experiments/experiment_log.jsonl
```

Pull it to the computer:

```bash
adb pull /sdcard/Android/data/com.example.identify/files/experiments/experiment_log.jsonl
```

Event types (field `type`):

| Type | When | Main fields |
|---|---|---|
| `app_start` | process start | device, CPU core types, RAM, model files ready, `previous_exits` (why the last process ended: low_memory, crash_native, user_requested, ...) |
| `image_prepare` | photo taken or picked | source (camera or gallery), source and output size, rotation, ms |
| `run` | every identification, including errors | `run_id`, `config` (threads, vision tokens, sampling, threshold), `phases` (wall ms, CPU ms, cores per phase), `native_load`, `native_embed`, `native_generate` (per-stage ms, token counts, ttft, tokens/s, and for every generated token the chosen probability plus the 5 most likely tokens), `knn` (5 nearest saved photos with scores), `few_shot`, `system_prompt`, `raw_output`, `label_stats` (label confidence and the other label starts the model considered), `telemetry` |
| `feedback` | a meal is logged from the result screen (once per photo) | `run_id`, accepted (the logged food is the guessed one), correction (the logged food's name), final label, time from result to decision |
| `setting_change`, `download_start`, `download_state`, `model_unload`, `model_delete`, `trim_memory` | as named | the new value or state |
| `health_permission_result`, `health_read` | the Health Connect permission screen closed; today's numbers were read | granted and denied permissions; steps, active, burned, and eaten kcal, ms, error |
| `meal_logged`, `meal_undone`, `meal_deleted` | a meal was written, undone on the result screen, or deleted from Today or Diary | `run_id`, model label and cuisine, query, food and its cuisine, portion, table and logged kcal, macros, meal slot, Health Connect record id, screen, error |
| `profile_saved` | Start or Save at the end of onboarding | mode (first_run or edit), has_name (never the name), age, sex, height, weight, activity, goal, reasons, cuisines, diet, eat more, units, budget and step goal with their suggestions and whether they were edited, Health Connect permissions granted, time in onboarding |

`telemetry` in a run record:

- `cpu`: CPU time of the whole app process (all threads), average and peak busy cores (CPU time divided by wall time), percent of the device's cores, and CPU time by core type (little, mid, big, from each core's maximum frequency; on the Pixel 8 that is 4x Cortex-A510, 4x Cortex-A715, 1x Cortex-X3) with each type's average clock. Android does not let apps read whole-device CPU use, so these numbers cover this app only.
- `memory`: resident memory at start, end, and peak (the memory-mapped model files count in `rss_file`), native heap, device free memory, low-memory flag.
- `battery`: level, temperature, average and peak current, and charge used in mAh. Current and energy are only meaningful on battery; while charging the record says so.
- `thermal`: Android thermal status (none, light, moderate, severe, ...) and the lowest thermal headroom seen.

Settings > Telemetry shows the same readings live once a second (CPU cores in use right now, memory, battery temperature, thermal status, which model settings are loaded) and the log's size and path.

For fine-tuning later, join `run` and `feedback` lines on `run_id`: the feedback gives the correct label, the run gives the model's answer, its confidence, and its alternatives. The photos themselves stay in the app's private storage; on a debug build they can be copied out with:

```bash
adb exec-out run-as com.example.identify tar c files/images > images.tar
```

Clear history and memory deletes the experiment log too, and uninstalling the app deletes everything, so pull the log first. Logcat shows a one-line summary of every run: `adb logcat -s Identify:I VlmBridge:I IdentifyExp:D`.

## 11. Health Connect

The app reads today's steps, calories burned (total and active), and calories eaten (nutrition logged by any app) from Health Connect, which is built into Android 14 and newer. It uses the platform API in `android.health.connect`, so there is no extra library and no network use. This is why the minimum Android version is now 14.

Permissions (each one approved by the user on the Health Connect screen): READ_STEPS, READ_ACTIVE_CALORIES_BURNED, READ_TOTAL_CALORIES_BURNED, READ_NUTRITION (meals from every app, for Today and Diary), and WRITE_NUTRITION (logging meals, and deleting meals this app logged). Health Connect only shows its permission screen for apps that declare a privacy policy screen, which is `PrivacyPolicyActivity` behind the `ViewPermissionUsageActivity` alias in the manifest.

Settings > Health Connect shows the connection status and today's numbers. Step data comes from whichever app writes it into Health Connect (Fitbit or Google Fit on the test phone), so that app's Health Connect sync must be on. Every read is logged in the experiment log as a `health_read` event, and the permission screen outcome as `health_permission_result`.

First test on the Pixel 8 (2026-10-01, 4:38 PM): 1,502 steps, 1,322 kcal burned (152 active), nothing eaten logged yet. The Google Fit app showed 1,318 steps at the same time; Health Connect combines all step sources, so its total can differ from any single app's count. The "Open Health Connect" button opens Health Connect's home screen (`android.health.connect.action.HEALTH_HOME_SETTINGS`); the SDK's `ACTION_MANAGE_HEALTH_PERMISSIONS` is reserved for system apps and crashes a normal app.

## 12. Food logging

When the identified dish matches the nutrition table shipped in `app/src/main/assets/foods.txt` (300 foods: US fast-food chains, everyday staples, fruit, and dishes from Indian, Mexican, Chinese, Japanese, Korean, Thai, Vietnamese, Italian, Mediterranean, Middle Eastern, American, Southern, Caribbean, Latin American, Ethiopian, and Hawaiian cuisine), the result screen shows a big kcal card with the calories for one serving. "Log meal" opens a dialog to pick the food (type another name if the guess is wrong), set the portion (0.5x to 3x), and check or edit the calories; "Different food" opens it with an empty search. "Log" writes one NutritionRecord into Health Connect (calories, protein, carbohydrate, fat, meal name, and a meal type from the time of day), then shows today's eaten, burned, and step totals with Done and Undo. "Undo" deletes that record again. When nothing matches, or the photo is not food, a "No food recognized" card offers Name the food and Retake.

The model only names the food; calories come from the table and the user confirms them. Table values are approximate reference values for one serving (McDonald's US published figures and USDA FoodData Central typical servings, compiled for this prototype), not measurements of the actual plate. Matching (`FoodMatcher` in `:core`) compares words of the label with each food's names and aliases, ignores plurals and filler words, and prefers branded rows only when the brand is named. Among rows that already match by name, the model's cuisine adds 0.1 and the user's favorite cuisines add 0.05, so cuisines only break ties and never turn a non-match into a match.

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
