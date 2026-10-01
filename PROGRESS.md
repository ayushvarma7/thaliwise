# PROGRESS

## Phase 0: Environment check

- JDK_PATH: /Applications/Android Studio.app/Contents/jbr/Contents/Home (OpenJDK 21.0.8)
- SDK_PATH: /Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/android-sdk (project-local SDK, at the user's request; the global SDK at ~/Library/Android/sdk is untouched)
- NDK_VERSION: 27.2.12479018
- CMAKE_VERSION: 3.22.1
- DEVICE_CONNECTED: false
- GRADLE_USER_HOME: /Users/ayush/Downloads/CLAUDE/IdentifyVLM/.toolchain/gradle-home (project-local Gradle caches)
- Host: arm64 macOS, git 2.50.1. NDK, CMake, and ninja binaries are universal (native arm64).
- Installed into the project SDK with cmdline-tools 15641748 sdkmanager: ndk;27.2.12479018, cmake;3.22.1, platforms;android-35, build-tools;35.0.0, build-tools;34.0.0 (AGP 8.7.3 default build tools).
- Licenses: copied the user's existing acceptance records (android-sdk-license, android-sdk-arm-dbt-license) from ~/Library/Android/sdk/licenses. sdkmanager ran with stdin closed and did not prompt.

Phase 0: DONE - JDK 21, project SDK with NDK 27.2.12479018 and CMake 3.22.1, no device connected.

## Phase 1: Scaffold, wrapper, git, llama.cpp

- Gradle wrapper 8.9 generated with a project-local Gradle 8.9 distribution (no Homebrew install). `./gradlew --version` prints Gradle 8.9.
- llama.cpp submodule at third_party/llama.cpp, tag b11323, commit f11d642a27b921cf22b6a8beb1b899f960fedcde
- HAS_MTMD_OPTION: true (CMakeLists.txt:266 `option(LLAMA_BUILD_MTMD ...)`, standalone build when LLAMA_BUILD_TOOLS is OFF)
- HAS_LFM2_MTMD: true (tools/mtmd/clip.cpp, mtmd.cpp, mtmd-image.cpp, clip-impl.h reference lfm2)
- HAS_ARM_ARCH_VAR: true (ggml/CMakeLists.txt:184, ggml/src/ggml-cpu/CMakeLists.txt:210)
- HAS_N_EMBD_INP: true (include/llama.h:598)
- .gitignore has one extra line, `.toolchain/`, so the project-local SDK and Gradle caches are never committed.

Phase 1: DONE - Gradle 8.9 wrapper, llama.cpp b11323 pinned, all four flags true.

## Phase 2: :core module + unit tests

- `./gradlew :core:test`: BUILD SUCCESSFUL. 7 test classes, 34 tests, 0 failures, 0 errors (AnswerParser 5, CosineSimilarity 5, EmbeddingCodec 3, FewShotSelector 6, KnnSearch 5, PromptBuilder 4, SearchMatcher 6).
- `grep -rn "import android" core/src`: OK, core is android-free.

Phase 2: DONE - core compiles, all 7 test classes pass.

## Phase 3: :app non-UI layer (gradle, native, data, model, learning)

- `./gradlew :core:test :app:assembleDebug`: BUILD SUCCESSFUL in 1m 7s (first native build, llama.cpp b11323 compiled with NDK 27.2.12479018 / CMake 3.22.1).
- llvm-nm lists exactly 5 JNI symbols: nativeGenerateWithImage, nativeGetImageEmbedding, nativeLoadModel, nativeSystemInfo, nativeUnloadModel.
- APK contains lib/arm64-v8a/libvlm-bridge.so (7.2 MB stripped).
- CMakeCache: CMAKE_BUILD_TYPE=Release. `-march=armv8.2-a+dotprod+i8mm+fp16` reaches the 16 ggml-cpu compile units. LOAD segments aligned to 0x4000 (16 KB pages). MTMD_VIDEO=OFF (auto, because LLAMA_SUBPROCESS=OFF).
- Native API differences from spec Section 7.7 (5 items) are recorded in NATIVE_API_NOTES.md.

Phase 3: DONE - debug APK builds with the native bridge; 5 JNI symbols; Release native build.

## Phase 4: UI

- `./gradlew :core:test :app:assembleDebug`: BUILD SUCCESSFUL.
- `./gradlew :app:lintDebug`: BUILD SUCCESSFUL, 0 errors, 19 warnings. All warnings follow from fixed spec decisions: GradleDependency x13 (pinned versions), MissingApplicationIcon (no custom launcher icon), ChromeOsAbiSupport (arm64-v8a only), DataExtractionRules (allowBackup=false), UsableSpace (getUsableSpace per spec), SetTextI18n x2 ("Error" label, status concatenation).
- Small deviations from the spec text, each to avoid a concrete bug:
  - VlmEngine: `handle` and `systemInfo` are volatile and `isLoaded()` / `getSystemInfo()` are not synchronized. Settings calls them on the main thread every second; with the spec's synchronized versions the UI thread would block for the whole inference (ANR risk).
  - activity_main.xml root has `android:fitsSystemWindows="true"`: targetSdk 35 forces edge-to-edge on Android 15+, which would put the toolbar under the status bar.
  - CameraFragment also saves the camera capture Uri in onSaveInstanceState, because the system camera can outlive the app process.
  - ModelDownloader.enqueue removes the previous DownloadManager id for a file before re-enqueueing it, so a double tap never starts two downloads to the same path.
  - EmbeddingCache skips rows whose byte length does not equal embeddingDim * 4 (avoids a startup crash on a corrupt row).
  - ResultFragment writes result text only while the stage is DONE, so an ERROR message is not overwritten when observers re-attach after rotation.

Phase 4: DONE - APK with all screens builds; lint 0 errors.

## Phase 5: Compliance checks

All 10 pass after one fix (rebuilt, then rerun):
1. No .kt files in app/src or core/src: empty.
2. No ai.liquid.leap: empty.
3. Network classes: only ModelDownloader.java. (First run also listed SettingsViewModel.java because a comment said "DownloadManager"; the comment was reworded.)
4. No android imports in core: empty.
5. No reinforcement / coroutine / StateFlow / kotlinx / compose in app/src or core/src: empty.
6. No em-dash or en-dash in files we wrote: empty when `--exclude-dir=.toolchain` is added. Without it, the only matches are Google SDK resource files inside the project-local toolchain (.toolchain/android-sdk/platforms/android-35/data/res/...), which are not files we wrote.
7. No CAMERA or storage permissions: empty.
8. No TODO / FIXME / stubs: empty.
9. No gguf in the APK: empty.
10. Native build type: Release.

Phase 5: DONE - all 10 compliance checks pass.

## Phase 6: README.md

- README.md has all 9 sections. Performance table says "not yet measured" for every row (no device). Compliance check 6 still passes.

Phase 6: DONE - README written with all 9 sections, no invented numbers.

## Phase 7: On-device test

- First check: `adb devices` listed no device. The user then connected the phone.
- Device: Pixel 8 (shiba), Tensor G3, Android 17 (API 37), arm64-v8a, 4 KB pages, 7.7 GB free. /proc/cpuinfo has asimddp (dotprod), i8mm, fphp/asimdhp (fp16), so the armv8.2-a+dotprod+i8mm+fp16 build is safe on it.
- `adb install -r app-debug.apk`: Success. `am start -W`: cold start 844 ms. Process alive, crash buffer empty, no FATAL EXCEPTION for com.example.identify.
- Screenshot of the Identify screen: toolbar below the status bar (edge-to-edge handled), model-missing hint and Open settings shown, bottom navigation with Identify / History / Settings.
- libvlm-bridge.so NEEDED: libandroid, liblog, libm, libdl, libc only (C++ runtime is static).
- Models dir created; it is empty, so the model is not on the device yet. Not downloaded by the agent (spec rule); the user downloads it in Settings.
- Note for later: tapping Take photo logs `Implicit URI write grant for ImageCapture action will be discontinued from Android 18 onwards`. Works on Android 17; Android 18 will need an explicit grant on the camera intent.

Phase 7: automated part DONE; manual checklist handed to the user, timings pending.

## Changes after the spec (user requests, 2026-10-01)

Requested during the on-device test: the first identification on the Pixel 8 was extremely slow; add experiment tracking (time to infer, how close the other possible answers were) and CPU / telemetry metrics.

- Speed: the logs showed a 771x1024 photo split into 6 tiles + thumbnail, 17.8 to 54.4 s per tile encode, plus a separate embedding encode. Now the model gets a 512 px copy (one tile) and the vision encoder runs once per photo; the generation reuses that encoding. MODEL_ID gained the suffix `+emb2` because embeddings are now pooled from that shared encoding. The screen stays on during a run so Android does not move the work to background cores.
- Settings > Performance: CPU threads (1 to 8, default 4) and vision tokens per image (64 to 256, default 256). A change reloads the model on the next identification.
- Experiment log: experiments/experiment_log.jsonl in the app's external files dir, one JSON event per line (app_start with previous process exit reasons, image_prepare, run, feedback, setting_change, download events, model_unload, model_delete, trim_memory). Cleared by Clear history and memory.
- Telemetry per run: per-phase wall and CPU time, average and peak busy cores, CPU time by core type (from /proc/self/task and cpufreq), memory peaks, battery current, energy and temperature, thermal status and headroom. Settings shows live readings.
- Model alternatives: native code records each generated token's probability and its top 5 rivals; :core GenerationAnalysis turns that into label confidence and alternative label starts. kNN logs the 5 nearest saved photos.
- New :core classes GenerationAnalysis and TelemetryStats with tests. `./gradlew :core:test`: 9 test classes, 44 tests, 0 failures.
- `./gradlew :app:assembleDebug`: BUILD SUCCESSFUL. `:app:lintDebug`: 0 errors, 23 warnings (the 19 from Phase 4 plus PluralsCandidate x2, UsableSpace for the new download_start event, and one DiscouragedApi that was then fixed by switching the sampler to scheduleWithFixedDelay).
- libvlm-bridge.so now exports 6 JNI symbols (adds nativeGetLastStats). Native changes are listed in NATIVE_API_NOTES.md items 6 to 11.
- Compliance checks 1 to 10 rerun: all pass (check 6 with .toolchain excluded, as before).

## On-device results after the speed change (2026-10-01)

- Run 7392a6cf (Pixel 8, 4 threads, 256 vision token cap, first load): total 11.6 s = model load 2.3 s + image encode 7.3 s (386x512, 192 tokens) + generate 2.05 s (prefill 1.86 s, 2 tokens). Image encoding reused by the generation (n_image_chunks_reused 1). Before the change the same kind of photo needed 6 tiles + thumbnail at 18 to 54 s each.
- Telemetry: 3.49 cores average, 4.5 peak, CPU time by cluster mid 64% / big 28% / little 8%; RSS peak 2.2 GB (anon 1.4 GB, file 0.8 GB); thermal none.
- Model answered "mouse" (no Label/Description lines; parser fallback used). Label confidence 0.245, other starts Computer 0.16, The 0.15, Apple 0.14, Mouse 0.14. User accepted after 51.7 s; feedback event linked by run_id.
- Previous exits read from Android: the 15:23 restart was user_requested (swiped away) at RSS 1.5 GB, not a crash.
- Fixes after reading this run: battery energy now requires no external power (the phone was plugged in although isCharging() reported false under load) and is sign-agnostic; llama_perf timings enabled (no_perf = false). 45 core tests pass, lint 0 errors, reinstalled.

## Phase 8: Health Connect

- 8.0 Preflight: DONE - working tree clean except the new build prompt, last commit cbc0e5a, :core:test and :app:assembleDebug exit 0.
- 8.1 Raise minSdk to 34: DONE - minSdk = 34, appVersion() uses PackageInfoFlags only, unused android.os.Build import removed, :app:assembleDebug exit 0.
- 8.2 Manifest: DONE - 5 android.permission.health entries, PrivacyPolicyActivity plus ViewPermissionUsageActivity alias (VIEW_PERMISSION_USAGE / HEALTH_PERMISSIONS / START_VIEW_PERMISSION_USAGE); no other permission added.
- 8.6 Privacy policy screen: DONE - activity_privacy_policy.xml and PrivacyPolicyActivity added, privacy_title and privacy_policy_text strings added; :app:assembleDebug exit 0.
- 8.3 Config and prefs: DONE - Config.HEALTH_TAG "IdentifyHealth", Config.DEFAULT_STEP_GOAL 10000, AppPrefs get/setStepGoal (key step_goal); compileDebugJavaWithJavac exit 0.
- 8.4 :core DailyHealth: DONE - DailyHealth + DailyHealthTest (4 tests pass); 10 core test classes; core is android-free.
