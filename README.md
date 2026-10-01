# IdentifyVLM

## 1. What this is

IdentifyVLM is an Android app that identifies the main object in a photo, entirely on the phone, using Liquid AI's LFM2.5-VL-1.6B vision language model running through llama.cpp. The app is written in pure Java (with a small C++ JNI bridge) and adapts to one user through a local memory of their past corrections. After a one-time model download of about 1.3 GB, it works fully offline.

## 2. Requirements

- macOS (built on Apple Silicon, macOS 26)
- Android Studio, or the Android SDK command-line tools
- JDK 17 or newer (built with the JetBrains Runtime 21.0.8 bundled with Android Studio)
- Android NDK 27.2.12479018 and SDK CMake 3.22.1 (the exact versions used for this build)
- Android SDK Platform 35 and Build-Tools 35.0.0 / 34.0.0
- A Pixel 8, or another arm64 Android 12+ device whose CPU supports dotprod and i8mm (see Known limitations)
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
  -> private copy (EXIF fixed, 1024 px)
  -> 512x512 crop
  -> vision encoder
  -> mean-pooled, L2-normalized embedding
  -> kNN over saved embeddings
       -> (score >= threshold) memory label
       -> (else) few-shot prompt + VLM generation -> Label/Description parse
  -> user accepts or corrects
  -> Room row + in-memory cache
```

The app adapts to the user through two layers. Neither changes the model.

- **Layer 1, few-shot prompt injection.** Up to 5 past corrections are written into the system prompt as "You said X. Correct answer: Y." Up to 3 are chosen as the most similar by embedding; the rest are the most recent, with duplicates removed.
- **Layer 2, embedding kNN cache.** Every saved photo stores its embedding and final label. When a new photo's nearest stored embedding has cosine similarity at or above the threshold (default 0.92, adjustable in Settings), the app returns that label without running text generation. "Run the model anyway" on the result screen forces a full model answer.

Module layout:

- `:core`: pure Java (no Android imports). Cosine similarity, kNN search, embedding byte codec, few-shot selection, prompt building, answer parsing, and history search. Covered by JVM unit tests (`./gradlew :core:test`, 7 test classes).
- `:app`: Android. Room database, the JNI bridge to llama.cpp (`app/src/main/cpp/vlm_bridge.cpp`), the model download, and the UI (XML layouts, Fragments, ViewModels, LiveData).

## 6. Performance on Pixel 8

No numbers have been measured on a real device yet.

| Step | Measured value | How measured |
|---|---|---|
| Model load (first identify) | not yet measured | `load_ms` in logcat |
| Image embedding | not yet measured | `embed_ms` in logcat |
| Text generation | not yet measured | `gen_ms` in logcat |
| Total (model path) | not yet measured | `total_ms` on a `source=MODEL` line |
| Total (memory path) | not yet measured | `total_ms` on a `source=MEMORY` line |

To measure, run this while identifying photos, then read `load_ms`, `embed_ms`, `gen_ms`, and `total_ms`:

```bash
adb logcat -s Identify:I VlmBridge:I
```

The memory path still runs the vision encoder to compute the embedding. It only skips text generation, so it is faster than the model path but not instant.

## 7. What the app learns and what it does not

- **Learns:** which labels this user prefers for things the model got wrong (through the prompt), and what this user's recurring objects look like (through stored embeddings).
- **Does not learn:** the model weights never change. There are no gradients, no fine-tuning, and no training on the device. Clearing history in Settings removes everything the app has "learned".
- **Why this is not reinforcement learning:** reinforcement learning updates a policy's parameters from a reward signal. Here, the thumbs up is not a reward used to update anything; the model is frozen. The accurate terms are personalized inference with a local correction memory, retrieval of few-shot examples, and a nearest-neighbor label cache.

## 8. Known limitations

- The 0.92 similarity threshold is a placeholder, not a validated value. Tune it in Settings using the `score` and `nearest` values that appear in the logcat lines.
- Mean-pooled projector outputs are not a trained retrieval embedding. Two different objects of the same kind (for example two different golden retrievers) may match each other.
- Text-only few-shot corrections can bias the model toward a corrected label on unrelated photos.
- CPU only. There is no Vulkan or OpenCL backend.
- `N_THREADS = 4` is a starting point. Try 3, 4, and 5 and compare `gen_ms`.
- The native code is compiled for `armv8.2-a+dotprod+i8mm+fp16` (the Pixel 8 Tensor G3 supports all of these). On an older arm64 CPU without i8mm, the app can crash when the model loads. Removing the `GGML_CPU_ARM_ARCH` line in `app/src/main/cpp/CMakeLists.txt` gives a more portable but slower build.

## 9. Pinned versions

| Component | Version |
|---|---|
| Android Gradle Plugin | 8.7.3 |
| Gradle wrapper | 8.9 |
| NDK | 27.2.12479018 |
| SDK CMake | 3.22.1 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 31 |
| ABI | arm64-v8a |
| llama.cpp | tag `b11323`, commit `f11d642a27b921cf22b6a8beb1b899f960fedcde` |
| Model file | `LFM2.5-VL-1.6B-Q4_0.gguf` |
| Projector file | `mmproj-LFM2.5-VL-1.6b-Q8_0.gguf` |
