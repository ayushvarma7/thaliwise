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
