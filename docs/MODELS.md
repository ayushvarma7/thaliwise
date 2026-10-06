# ThaliWise: the AI model and the logic around it

ThaliWise runs **one AI model, on the phone**: Liquid AI's LFM2.5-VL-1.6B vision language model. There is no cloud model, no API key, and no network call after the one-time model download. Everything else that looks "smart" (matching a dish to calories, the coach, the calorie budget) is plain code with unit tests.

| Part | What it is | Runs on | Used for |
|---|---|---|---|
| **LFM2.5-VL-1.6B** | Vision language model, 1.6B parameters | Phone CPU, through llama.cpp | Naming the dish, its cuisine, a one-line description; the photo embedding |
| kNN memory | Nearest-neighbor search over photo embeddings | Phone | Recognizing a photo like one the user already confirmed or corrected |
| Few-shot corrections | Past corrections written into the prompt | Phone | Steering the model toward the user's names for things |
| `FoodMatcher` | Word-overlap scoring | Phone | Model label to a row of the 320-food nutrition table |
| `Coach`, `DietRules`, `WalkMath`, `ProfileMath` | Rules and published formulas | Phone | Tips, diet warnings, walk suggestions, calorie budget, step goal |

## 1. LFM2.5-VL-1.6B

Facts from the [model card](https://huggingface.co/LiquidAI/LFM2.5-VL-1.6B):

| | |
|---|---|
| Maker | Liquid AI |
| Parameters | 1.6B in total |
| Language backbone | LFM2.5-1.2B-Base |
| Vision encoder | SigLIP2 NaFlex, shape-optimized, 400M |
| Images | Up to 512x512 without upscaling; larger images are split into 512x512 tiles; 64 to 256 image tokens per image |
| Context | 32,768 tokens; vocabulary 65,536 |
| Languages | English, Arabic, Chinese, French, German, Japanese, Korean, Spanish |
| Recommended sampling | temperature 0.1, min_p 0.15, repetition penalty 1.05 |
| License | LFM1.0 (LFM Open License). Read its terms before any commercial release. |
| Paper | arXiv 2511.23404 (November 2025) |

### Files and runtime in ThaliWise

| | |
|---|---|
| Language model file | `LFM2.5-VL-1.6B-Q4_0.gguf`, 696 MB, 4-bit |
| Vision encoder and projector file | `mmproj-LFM2.5-VL-1.6b-Q8_0.gguf`, 583 MB, 8-bit |
| Source | [LiquidAI/LFM2.5-VL-1.6B-GGUF](https://huggingface.co/LiquidAI/LFM2.5-VL-1.6B-GGUF), downloaded once from Settings |
| Engine | llama.cpp tag `b11323` (commit `f11d642`) with libmtmd, Release build, arm64 with dotprod, i8mm, and fp16, CPU only |
| Bridge | `app/src/main/cpp/vlm_bridge.cpp` (JNI, six native functions) |
| Context window used | 4,096 tokens |
| Answer length | at most 128 new tokens |
| Sampling | temperature 0.1, min_p 0.15, repetition penalty 1.05 (the model card's values), top_k 50 |
| Image | longest side 512 px, so every photo is one tile; image token cap 256 (64 to 256 in Settings) |
| Threads | 4 by default; at most one per fast core (5 on a Pixel 8) |
| Memory | about 2.2 GB peak while the model is loaded |

Measured speed on a Pixel 8 is in README section 6.

### One model, used three ways

1. **Embedding model.** The vision encoder runs once per photo. The projector outputs are averaged over all image tokens and normalized to length 1. That vector is the photo's key in the kNN memory (cosine similarity, default threshold 0.92). The same encoding is reused for the answer, so the encoder never runs twice.
2. **Text generation.** The system prompt asks for the dish the way people order it, the product name for packaged food, and exactly three lines (`Label`, `Cuisine`, `Description`). It names no example dishes, because a small model that cannot read a photo tends to repeat a prompt example; instead it offers the answer `Unknown food`. A grammar (`PromptBuilder.ANSWER_GRAMMAR`, applied by llama.cpp's grammar sampler) forces the three lines during generation, so free text and missing lines cannot happen. Up to 5 past corrections follow ("You said X. Correct answer: Y."): the 3 most similar by embedding, then the most recent. `AnswerParser` reads the reply and cleans copied prompt words, template words, and free-text openings.
3. **Confidence signal.** For every generated token the bridge records the chosen token's probability and the 5 most likely alternatives. These go to the experiment log only (label confidence, "other label starts the model weighed") and are the raw material for any later tuning.

### What the model does not do

- It does not estimate calories or portions. Calories come from the table (`app/src/main/assets/foods.txt`), and the user confirms the portion.
- It never changes. There is no training on the phone. "Learning" is the kNN memory and the few-shot corrections, both stored locally and erased by Clear history.
- It does not see the profile. Diet, goals, and body data are used only by the rule code.

## 2. The logic around the model (not AI models)

| Code | Method | Tested by |
|---|---|---|
| `KnnSearch`, `CosineSimilarity`, `EmbeddingCodec` | Exact nearest neighbors over float vectors | `KnnSearchTest`, `CosineSimilarityTest`, `EmbeddingCodecTest` |
| `FewShotSelector`, `PromptBuilder` | Similar plus recent corrections, sanitized into the prompt | `FewShotSelectorTest`, `PromptBuilderTest` |
| `AnswerParser` | Line parsing and label cleaning | `AnswerParserTest`, `AnswerParserCuisineTest`, `AnswerParserEchoTest` |
| `FoodMatcher` | 0.7 recall + 0.3 precision of words, +0.2 full-name bonus, brand and cuisine tie-breaks | `FoodMatcherTest`, `FoodMatcherCuisineTest`, `FoodMatcherSnackTest` |
| `ProfileMath` | Mifflin-St Jeor resting energy x activity factor, goal adjustment, safety floor | `ProfileMathTest` |
| `WalkMath` | ACSM walking energy (3.5 MET, 100 steps a minute) | `WalkMathTest` |
| `Coach`, `DietRules`, `FoodTags` | Rules over the profile, today's Health Connect numbers, and food tags | `CoachTest`, `DietRulesTest`, `FoodTagsTest` |
| `GenerationAnalysis`, `TelemetryStats` | Token statistics, CPU and memory statistics | `GenerationAnalysisTest`, `TelemetryStatsTest` |

## 3. Ways to get more out of models

Ordered from smallest change to largest. Item 1 is built (step 11.6); the others are not built yet.

1. **Grammar-constrained output (done).** `vlm_bridge.cpp` puts `llama_sampler_init_grammar` with `PromptBuilder.ANSWER_GRAMMAR` first in the sampler chain, so the reply is always `Label: ...`, `Cuisine: ...`, `Description: ...`. The grammar was checked with llama.cpp's grammar engine (it accepts a correct reply and rejects free text, a missing line, a label over 60 characters, and template brackets). The generation stats record `grammar` as `on`, `failed`, or `off`.
2. **Reference photo memory.** Seed the kNN memory with labeled public photos (tooling started in `tools/refmem`: 1,580 photos of 77 table dishes from Food-101 and an Indian food set, and a Mac build of the same embedding code). Needs the two model files on the Mac and an accuracy test on held-out photos before it ships. Model weights stay unchanged.
3. **GPU or NPU.** llama.cpp has OpenCL and Vulkan backends. The Pixel 8 GPU (Mali-G715) might speed up the vision encoder, the slowest step. Needs measurement; results vary by driver.
4. **A smaller vision model for speed.** Liquid AI also publishes smaller LFM2-VL models. Check availability and accuracy on the same held-out photos before switching. A different model needs a new `MODEL_ID`, since embeddings from different models are not comparable.
5. **LoRA fine-tuning on food photos.** Train adapters on labeled food photos on a computer, merge, convert with llama.cpp's converter (it supports `Lfm2VlForConditionalGeneration`), quantize, and ship a new model file. Biggest gain for dishes the model confuses, and the biggest cost: a Python ML setup, about 12 GB of disk, hours of training, dataset licenses, and a careful evaluation so general answers do not get worse.
