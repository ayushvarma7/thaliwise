#include <jni.h>
#include <android/log.h>

#include <cmath>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define TAG "VlmBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

constexpr int kBatch        = 512;
constexpr int kPenaltyLastN = 64;

struct VlmSession {
    llama_model*       model = nullptr;
    llama_context*     lctx  = nullptr;
    mtmd_context*      mctx  = nullptr;
    const llama_vocab* vocab = nullptr;
    std::mutex         mu;
};

void log_cb(enum ggml_log_level level, const char* text, void* /*user*/) {
    int prio = ANDROID_LOG_INFO;
    if (level == GGML_LOG_LEVEL_ERROR)      prio = ANDROID_LOG_ERROR;
    else if (level == GGML_LOG_LEVEL_WARN)  prio = ANDROID_LOG_WARN;
    else if (level == GGML_LOG_LEVEL_DEBUG) prio = ANDROID_LOG_DEBUG;
    __android_log_print(prio, TAG, "%s", text);
}

void ensure_backend() {
    static std::once_flag once;
    std::call_once(once, [] {
        llama_log_set(log_cb, nullptr);
        mtmd_helper_log_set(log_cb, nullptr);   // also calls mtmd_log_set internally
        llama_backend_init();
    });
}

void throw_java(JNIEnv* env, const std::string& msg) {
    LOGE("%s", msg.c_str());
    jclass cls = env->FindClass("java/lang/RuntimeException");
    env->ThrowNew(cls, msg.c_str());
}

std::string from_jstring(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;   // only used for ASCII file paths
}

std::string from_utf8_bytes(JNIEnv* env, jbyteArray arr) {
    if (!arr) return {};
    jsize n = env->GetArrayLength(arr);
    std::string out(static_cast<size_t>(n), '\0');
    if (n > 0) env->GetByteArrayRegion(arr, 0, n, reinterpret_cast<jbyte*>(&out[0]));
    return out;
}

jbyteArray to_utf8_bytes(JNIEnv* env, const std::string& s) {
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(s.size()));
    if (!s.empty()) {
        env->SetByteArrayRegion(arr, 0, static_cast<jsize>(s.size()),
                                reinterpret_cast<const jbyte*>(s.data()));
    }
    return arr;
}

int embd_dim(const llama_model* model) {
    return llama_model_n_embd_inp(model);   // fallback: llama_model_n_embd(model)
}

VlmSession* session_or_throw(JNIEnv* env, jlong handle) {
    auto* s = reinterpret_cast<VlmSession*>(handle);
    if (!s) throw_java(env, "model not loaded");
    return s;
}

// The helper returns a wrapper; video_ctx is only set for video files. This app sends JPEG stills only.
mtmd_bitmap* load_image_bitmap(mtmd_context* mctx, const std::string& path) {
    mtmd_helper_bitmap_wrapper w = mtmd_helper_bitmap_init_from_file(
            mctx, path.c_str(), false, mtmd_helper_init_opt_default());
    if (w.video_ctx) {
        if (w.bitmap) mtmd_bitmap_free(w.bitmap);
        mtmd_helper_video_free(w.video_ctx);
        return nullptr;
    }
    return w.bitmap;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_identify_model_VlmEngine_nativeLoadModel(
        JNIEnv* env, jclass, jstring jModelPath, jstring jMmprojPath, jint nCtx, jint nThreads) {
    ensure_backend();
    const std::string modelPath  = from_jstring(env, jModelPath);
    const std::string mmprojPath = from_jstring(env, jMmprojPath);

    auto* s = new VlmSession();

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    mp.load_mode    = LLAMA_LOAD_MODE_MMAP;
    s->model = llama_model_load_from_file(modelPath.c_str(), mp);
    if (!s->model) { LOGE("model load failed: %s", modelPath.c_str()); delete s; return 0; }
    s->vocab = llama_model_get_vocab(s->model);

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx           = static_cast<uint32_t>(nCtx);
    cp.n_batch         = kBatch;
    cp.n_ubatch        = kBatch;
    cp.n_threads       = nThreads;
    cp.n_threads_batch = nThreads;
    s->lctx = llama_init_from_model(s->model, cp);
    if (!s->lctx) {
        LOGE("context init failed");
        llama_model_free(s->model); delete s; return 0;
    }

    mtmd_context_params mcp = mtmd_context_params_default();
    mcp.use_gpu       = false;
    mcp.n_threads     = nThreads;
    mcp.print_timings = true;
    s->mctx = mtmd_init_from_file(mmprojPath.c_str(), s->model, mcp);
    if (!s->mctx) {
        LOGE("mmproj load failed: %s", mmprojPath.c_str());
        llama_free(s->lctx); llama_model_free(s->model); delete s; return 0;
    }

    LOGI("loaded. n_embd=%d system_info=%s", embd_dim(s->model), llama_print_system_info());
    return reinterpret_cast<jlong>(s);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_identify_model_VlmEngine_nativeGetImageEmbedding(
        JNIEnv* env, jclass, jlong handle, jstring jImagePath) {
    VlmSession* s = session_or_throw(env, handle);
    if (!s) return nullptr;
    std::lock_guard<std::mutex> lock(s->mu);
    const std::string imagePath = from_jstring(env, jImagePath);

    mtmd_bitmap* bmp = load_image_bitmap(s->mctx, imagePath);
    if (!bmp) { throw_java(env, "failed to load image: " + imagePath); return nullptr; }

    const std::string marker = mtmd_default_marker();
    mtmd_input_text text{};
    text.text          = marker.c_str();
    text.text_len      = marker.size();
    text.add_special   = false;
    text.parse_special = true;

    mtmd_input_chunks* chunks = mtmd_input_chunks_init();
    const mtmd_bitmap* bitmaps[] = { bmp };
    if (mtmd_tokenize(s->mctx, chunks, &text, bitmaps, 1) != 0) {
        mtmd_input_chunks_free(chunks); mtmd_bitmap_free(bmp);
        throw_java(env, "mtmd_tokenize failed"); return nullptr;
    }

    const int n_embd = embd_dim(s->model);
    std::vector<double> sum(static_cast<size_t>(n_embd), 0.0);
    size_t total = 0;
    for (size_t i = 0; i < mtmd_input_chunks_size(chunks); i++) {
        const mtmd_input_chunk* chunk = mtmd_input_chunks_get(chunks, i);
        if (mtmd_input_chunk_get_type(chunk) != MTMD_INPUT_CHUNK_TYPE_IMAGE) continue;
        if (mtmd_encode_chunk(s->mctx, chunk) != 0) {
            mtmd_input_chunks_free(chunks); mtmd_bitmap_free(bmp);
            throw_java(env, "mtmd_encode_chunk failed"); return nullptr;
        }
        const float* e = mtmd_get_output_embd(s->mctx);
        const size_t n_tok = mtmd_input_chunk_get_n_tokens(chunk);
        for (size_t t = 0; t < n_tok; t++) {
            const float* row = e + t * static_cast<size_t>(n_embd);
            for (int d = 0; d < n_embd; d++) sum[d] += row[d];
        }
        total += n_tok;
    }
    mtmd_input_chunks_free(chunks);
    mtmd_bitmap_free(bmp);
    if (total == 0) { throw_java(env, "no image tokens produced"); return nullptr; }

    // mean-pool over all image tokens, then L2-normalize
    std::vector<float> out(static_cast<size_t>(n_embd));
    double norm = 0.0;
    for (int d = 0; d < n_embd; d++) {
        double v = sum[d] / static_cast<double>(total);
        out[d] = static_cast<float>(v);
        norm += v * v;
    }
    norm = std::sqrt(norm);
    if (norm > 0.0) for (int d = 0; d < n_embd; d++) out[d] = static_cast<float>(out[d] / norm);

    jfloatArray arr = env->NewFloatArray(n_embd);
    env->SetFloatArrayRegion(arr, 0, n_embd, out.data());
    return arr;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_identify_model_VlmEngine_nativeGenerateWithImage(
        JNIEnv* env, jclass, jlong handle, jstring jImagePath,
        jbyteArray jSystem, jbyteArray jUser, jint maxTokens,
        jfloat temperature, jfloat minP, jfloat repeatPenalty, jint topK) {
    VlmSession* s = session_or_throw(env, handle);
    if (!s) return nullptr;
    std::lock_guard<std::mutex> lock(s->mu);
    const std::string imagePath = from_jstring(env, jImagePath);
    const std::string sys       = from_utf8_bytes(env, jSystem);
    const std::string user      = from_utf8_bytes(env, jUser);

    llama_memory_clear(llama_get_memory(s->lctx), true);   // fresh conversation each call

    // LFM2 ChatML. Image goes BEFORE the text in the user turn. BOS is added by add_special.
    std::string prompt;
    prompt += "<|im_start|>system\n";
    prompt += sys;
    prompt += "<|im_end|>\n<|im_start|>user\n";
    prompt += mtmd_default_marker();
    prompt += "\n";
    prompt += user;
    prompt += "<|im_end|>\n<|im_start|>assistant\n";

    mtmd_bitmap* bmp = load_image_bitmap(s->mctx, imagePath);
    if (!bmp) { throw_java(env, "failed to load image: " + imagePath); return nullptr; }

    mtmd_input_text text{};
    text.text          = prompt.c_str();
    text.text_len      = prompt.size();
    text.add_special   = true;
    text.parse_special = true;

    mtmd_input_chunks* chunks = mtmd_input_chunks_init();
    const mtmd_bitmap* bitmaps[] = { bmp };
    if (mtmd_tokenize(s->mctx, chunks, &text, bitmaps, 1) != 0) {
        mtmd_input_chunks_free(chunks); mtmd_bitmap_free(bmp);
        throw_java(env, "mtmd_tokenize failed"); return nullptr;
    }

    llama_pos new_n_past = 0;
    if (mtmd_helper_eval_chunks(s->mctx, s->lctx, chunks, 0, 0, kBatch, true, &new_n_past) != 0) {
        mtmd_input_chunks_free(chunks); mtmd_bitmap_free(bmp);
        throw_java(env, "mtmd_helper_eval_chunks failed"); return nullptr;
    }
    llama_pos n_past = new_n_past;

    // order: penalties -> top_k -> min_p -> temp -> dist
    llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
            llama_vocab_n_tokens(s->vocab), kPenaltyLastN, repeatPenalty, 0.0f, 0.0f));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(smpl, llama_sampler_init_min_p(minP, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    llama_batch batch = llama_batch_init(1, 0, 1);
    std::string out;
    for (int i = 0; i < maxTokens; i++) {
        llama_token tok = llama_sampler_sample(smpl, s->lctx, -1);   // also accepts the token
        if (llama_vocab_is_eog(s->vocab, tok)) break;
        char buf[256];
        int n = llama_token_to_piece(s->vocab, tok, buf, sizeof(buf), 0, false);
        if (n > 0) out.append(buf, static_cast<size_t>(n));

        batch.n_tokens     = 1;
        batch.token[0]     = tok;
        batch.pos[0]       = n_past;
        batch.n_seq_id[0]  = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0]    = 1;
        if (llama_decode(s->lctx, batch) != 0) { LOGE("llama_decode failed at step %d", i); break; }
        n_past++;
    }

    llama_batch_free(batch);
    llama_sampler_free(smpl);
    mtmd_input_chunks_free(chunks);
    mtmd_bitmap_free(bmp);
    return to_utf8_bytes(env, out);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_identify_model_VlmEngine_nativeUnloadModel(JNIEnv*, jclass, jlong handle) {
    auto* s = reinterpret_cast<VlmSession*>(handle);
    if (!s) return;
    {
        std::lock_guard<std::mutex> lock(s->mu);
        if (s->mctx)  mtmd_free(s->mctx);
        if (s->lctx)  llama_free(s->lctx);
        if (s->model) llama_model_free(s->model);
    }
    delete s;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_identify_model_VlmEngine_nativeSystemInfo(JNIEnv* env, jclass) {
    ensure_backend();
    return env->NewStringUTF(llama_print_system_info());   // ASCII only
}
