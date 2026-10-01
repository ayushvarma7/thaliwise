#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdio>
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
constexpr int kTopAlternatives = 5;   // candidates recorded per generated token

using Clock = std::chrono::steady_clock;

double ms_since(Clock::time_point t0) {
    return std::chrono::duration<double, std::milli>(Clock::now() - t0).count();
}

// The image encoding from the last embedding call, reused by the next generation on the same file.
struct EncodedImage {
    std::string                     path;
    mtmd_bitmap*                    bitmap = nullptr;   // owned
    std::vector<std::vector<float>> embds;              // one entry per image chunk: n_tokens * n_embd floats
    std::vector<size_t>             n_tokens;           // tokens per image chunk
};

struct VlmSession {
    llama_model*       model = nullptr;
    llama_context*     lctx  = nullptr;
    mtmd_context*      mctx  = nullptr;
    const llama_vocab* vocab = nullptr;
    EncodedImage       encoded;
    std::string        last_stats = "{}";   // JSON describing the most recent native call
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

void clear_encoded(VlmSession* s) {
    if (s->encoded.bitmap) mtmd_bitmap_free(s->encoded.bitmap);
    s->encoded = EncodedImage{};
}

// ---- minimal JSON writing (keys are ASCII literals; values are escaped, invalid UTF-8 becomes U+FFFD) ----

size_t utf8_seq_len(const std::string& s, size_t i) {
    const auto c = static_cast<unsigned char>(s[i]);
    size_t n;
    if (c < 0x80) return 1;
    if ((c & 0xE0) == 0xC0) n = 2;
    else if ((c & 0xF0) == 0xE0) n = 3;
    else if ((c & 0xF8) == 0xF0) n = 4;
    else return 0;
    if (i + n > s.size()) return 0;
    for (size_t k = 1; k < n; k++) {
        if ((static_cast<unsigned char>(s[i + k]) & 0xC0) != 0x80) return 0;
    }
    return n;
}

void json_str(std::string& out, const std::string& v) {
    out += '"';
    for (size_t i = 0; i < v.size();) {
        const auto c = static_cast<unsigned char>(v[i]);
        if (c == '"')       { out += "\\\""; i++; continue; }
        if (c == '\\')      { out += "\\\\"; i++; continue; }
        if (c == '\n')      { out += "\\n";  i++; continue; }
        if (c == '\r')      { out += "\\r";  i++; continue; }
        if (c == '\t')      { out += "\\t";  i++; continue; }
        if (c < 0x20) {
            char buf[8];
            std::snprintf(buf, sizeof(buf), "\\u%04x", c);
            out += buf;
            i++;
            continue;
        }
        const size_t n = utf8_seq_len(v, i);
        if (n == 0) { out += "\\ufffd"; i++; continue; }   // partial multi-byte token piece
        out.append(v, i, n);
        i += n;
    }
    out += '"';
}

std::string num(double v) {
    if (!std::isfinite(v)) return "null";
    char buf[48];
    std::snprintf(buf, sizeof(buf), "%.4f", v);
    return buf;
}

void kv(std::string& out, const char* key, const std::string& raw_value) {
    if (out.size() > 1) out += ',';
    out += '"';
    out += key;
    out += "\":";
    out += raw_value;
}

void kv_num(std::string& out, const char* key, double v) { kv(out, key, num(v)); }
void kv_int(std::string& out, const char* key, long long v) { kv(out, key, std::to_string(v)); }
void kv_bool(std::string& out, const char* key, bool v) { kv(out, key, v ? "true" : "false"); }
void kv_str(std::string& out, const char* key, const std::string& v) {
    std::string q;
    json_str(q, v);
    kv(out, key, q);
}

std::string token_piece(const llama_vocab* vocab, llama_token tok) {
    char buf[256];
    const int n = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, false);
    return n > 0 ? std::string(buf, static_cast<size_t>(n)) : std::string();
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_identify_model_VlmEngine_nativeLoadModel(
        JNIEnv* env, jclass, jstring jModelPath, jstring jMmprojPath, jint nCtx, jint nThreads,
        jint imageMaxTokens) {
    ensure_backend();
    const std::string modelPath  = from_jstring(env, jModelPath);
    const std::string mmprojPath = from_jstring(env, jMmprojPath);
    const auto t0 = Clock::now();

    auto* s = new VlmSession();

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    mp.load_mode    = LLAMA_LOAD_MODE_MMAP;
    s->model = llama_model_load_from_file(modelPath.c_str(), mp);
    if (!s->model) { LOGE("model load failed: %s", modelPath.c_str()); delete s; return 0; }
    s->vocab = llama_model_get_vocab(s->model);
    const double t_model = ms_since(t0);

    const auto t1 = Clock::now();
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx           = static_cast<uint32_t>(nCtx);
    cp.n_batch         = kBatch;
    cp.n_ubatch        = kBatch;
    cp.n_threads       = nThreads;
    cp.n_threads_batch = nThreads;
    cp.no_perf         = false;   // the default (true) leaves llama_perf_context timings at 0
    s->lctx = llama_init_from_model(s->model, cp);
    if (!s->lctx) {
        LOGE("context init failed");
        llama_model_free(s->model); delete s; return 0;
    }
    const double t_context = ms_since(t1);

    const auto t2 = Clock::now();
    mtmd_context_params mcp = mtmd_context_params_default();
    mcp.use_gpu          = false;
    mcp.n_threads        = nThreads;
    mcp.print_timings    = true;
    mcp.image_max_tokens = imageMaxTokens > 0 ? imageMaxTokens : -1;   // -1 keeps the model default (256)
    s->mctx = mtmd_init_from_file(mmprojPath.c_str(), s->model, mcp);
    if (!s->mctx) {
        LOGE("mmproj load failed: %s", mmprojPath.c_str());
        llama_free(s->lctx); llama_model_free(s->model); delete s; return 0;
    }
    const double t_mmproj = ms_since(t2);

    char desc[256];
    llama_model_desc(s->model, desc, sizeof(desc));
    std::string j = "{";
    kv_str(j, "op", "load");
    kv_num(j, "t_model_ms", t_model);
    kv_num(j, "t_context_ms", t_context);
    kv_num(j, "t_mmproj_ms", t_mmproj);
    kv_num(j, "t_total_ms", ms_since(t0));
    kv_str(j, "model_desc", desc);
    kv_int(j, "model_size_bytes", static_cast<long long>(llama_model_size(s->model)));
    kv_int(j, "model_n_params", static_cast<long long>(llama_model_n_params(s->model)));
    kv_int(j, "n_embd", llama_model_n_embd(s->model));
    kv_int(j, "n_embd_inp", embd_dim(s->model));
    kv_int(j, "n_vocab", llama_vocab_n_tokens(s->vocab));
    kv_int(j, "n_ctx", llama_n_ctx(s->lctx));
    kv_int(j, "n_threads", nThreads);
    kv_int(j, "image_max_tokens", imageMaxTokens);
    kv_str(j, "system_info", llama_print_system_info());
    j += "}";
    s->last_stats = j;

    LOGI("loaded. n_embd=%d threads=%d image_max_tokens=%d load_ms=%.0f (model %.0f, context %.0f, mmproj %.0f) system_info=%s",
         embd_dim(s->model), nThreads, imageMaxTokens, ms_since(t0), t_model, t_context, t_mmproj,
         llama_print_system_info());
    return reinterpret_cast<jlong>(s);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_identify_model_VlmEngine_nativeGetImageEmbedding(
        JNIEnv* env, jclass, jlong handle, jstring jImagePath) {
    VlmSession* s = session_or_throw(env, handle);
    if (!s) return nullptr;
    std::lock_guard<std::mutex> lock(s->mu);
    const std::string imagePath = from_jstring(env, jImagePath);
    const auto t0 = Clock::now();
    clear_encoded(s);

    mtmd_bitmap* bmp = load_image_bitmap(s->mctx, imagePath);
    if (!bmp) { throw_java(env, "failed to load image: " + imagePath); return nullptr; }
    const double t_load_image = ms_since(t0);

    const std::string marker = mtmd_default_marker();
    mtmd_input_text text{};
    text.text          = marker.c_str();
    text.text_len      = marker.size();
    text.add_special   = false;
    text.parse_special = true;

    const auto t1 = Clock::now();
    mtmd_input_chunks* chunks = mtmd_input_chunks_init();
    const mtmd_bitmap* bitmaps[] = { bmp };
    if (mtmd_tokenize(s->mctx, chunks, &text, bitmaps, 1) != 0) {
        mtmd_input_chunks_free(chunks); mtmd_bitmap_free(bmp);
        throw_java(env, "mtmd_tokenize failed"); return nullptr;
    }
    const double t_tokenize = ms_since(t1);

    const int n_embd = embd_dim(s->model);
    std::vector<double> sum(static_cast<size_t>(n_embd), 0.0);
    size_t total = 0;
    size_t n_image_chunks = 0;
    double t_encode = 0.0;
    const size_t n_chunks = mtmd_input_chunks_size(chunks);
    for (size_t i = 0; i < n_chunks; i++) {
        const mtmd_input_chunk* chunk = mtmd_input_chunks_get(chunks, i);
        if (mtmd_input_chunk_get_type(chunk) != MTMD_INPUT_CHUNK_TYPE_IMAGE) continue;
        const auto te = Clock::now();
        if (mtmd_encode_chunk(s->mctx, chunk) != 0) {
            mtmd_input_chunks_free(chunks); mtmd_bitmap_free(bmp); clear_encoded(s);
            throw_java(env, "mtmd_encode_chunk failed"); return nullptr;
        }
        t_encode += ms_since(te);
        const float* e = mtmd_get_output_embd(s->mctx);
        const size_t n_tok = mtmd_input_chunk_get_n_tokens(chunk);
        s->encoded.embds.emplace_back(e, e + n_tok * static_cast<size_t>(n_embd));
        s->encoded.n_tokens.push_back(n_tok);
        for (size_t t = 0; t < n_tok; t++) {
            const float* row = e + t * static_cast<size_t>(n_embd);
            for (int d = 0; d < n_embd; d++) sum[d] += row[d];
        }
        total += n_tok;
        n_image_chunks++;
    }
    mtmd_input_chunks_free(chunks);
    if (total == 0) {
        mtmd_bitmap_free(bmp); clear_encoded(s);
        throw_java(env, "no image tokens produced"); return nullptr;
    }
    // Keep the bitmap and encodings so the next generation on this file skips the vision encoder.
    s->encoded.path   = imagePath;
    s->encoded.bitmap = bmp;

    // mean-pool over all image tokens, then L2-normalize
    const auto tp = Clock::now();
    std::vector<float> out(static_cast<size_t>(n_embd));
    double norm = 0.0;
    for (int d = 0; d < n_embd; d++) {
        double v = sum[d] / static_cast<double>(total);
        out[d] = static_cast<float>(v);
        norm += v * v;
    }
    norm = std::sqrt(norm);
    if (norm > 0.0) for (int d = 0; d < n_embd; d++) out[d] = static_cast<float>(out[d] / norm);
    const double t_pool = ms_since(tp);

    std::string j = "{";
    kv_str(j, "op", "embed");
    kv_int(j, "image_w", mtmd_bitmap_get_nx(bmp));
    kv_int(j, "image_h", mtmd_bitmap_get_ny(bmp));
    kv_int(j, "n_chunks", static_cast<long long>(n_chunks));
    kv_int(j, "n_image_chunks", static_cast<long long>(n_image_chunks));
    kv_int(j, "n_image_tokens", static_cast<long long>(total));
    kv_int(j, "n_embd", n_embd);
    kv_num(j, "t_load_image_ms", t_load_image);
    kv_num(j, "t_tokenize_ms", t_tokenize);
    kv_num(j, "t_encode_ms", t_encode);
    kv_num(j, "t_pool_ms", t_pool);
    kv_num(j, "t_total_ms", ms_since(t0));
    j += "}";
    s->last_stats = j;
    LOGI("embed: %ux%u image, %zu image tokens in %zu chunk(s), encode %.0f ms, total %.0f ms",
         mtmd_bitmap_get_nx(bmp), mtmd_bitmap_get_ny(bmp), total, n_image_chunks, t_encode, ms_since(t0));

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
    const auto t0 = Clock::now();

    llama_memory_clear(llama_get_memory(s->lctx), true);   // fresh conversation each call
    llama_perf_context_reset(s->lctx);

    // LFM2 ChatML. Image goes BEFORE the text in the user turn. BOS is added by add_special.
    std::string prompt;
    prompt += "<|im_start|>system\n";
    prompt += sys;
    prompt += "<|im_end|>\n<|im_start|>user\n";
    prompt += mtmd_default_marker();
    prompt += "\n";
    prompt += user;
    prompt += "<|im_end|>\n<|im_start|>assistant\n";

    const bool reuse = s->encoded.bitmap != nullptr && s->encoded.path == imagePath;
    mtmd_bitmap* bmp = reuse ? s->encoded.bitmap : load_image_bitmap(s->mctx, imagePath);
    if (!bmp) { throw_java(env, "failed to load image: " + imagePath); return nullptr; }
    const double t_load_image = ms_since(t0);

    mtmd_input_text text{};
    text.text          = prompt.c_str();
    text.text_len      = prompt.size();
    text.add_special   = true;
    text.parse_special = true;

    const auto t1 = Clock::now();
    mtmd_input_chunks* chunks = mtmd_input_chunks_init();
    const mtmd_bitmap* bitmaps[] = { bmp };
    if (mtmd_tokenize(s->mctx, chunks, &text, bitmaps, 1) != 0) {
        mtmd_input_chunks_free(chunks);
        if (!reuse) mtmd_bitmap_free(bmp);
        throw_java(env, "mtmd_tokenize failed"); return nullptr;
    }
    const double t_tokenize = ms_since(t1);

    // Prefill chunk by chunk so image and text time can be told apart, reusing cached image encodings.
    const auto t2 = Clock::now();
    const size_t n_chunks = mtmd_input_chunks_size(chunks);
    llama_pos n_past = 0;
    size_t image_idx = 0, n_image_tokens = 0, n_text_tokens = 0, n_reused = 0, n_encoded = 0;
    double t_image_decode = 0.0, t_image_eval = 0.0, t_text = 0.0;
    for (size_t i = 0; i < n_chunks; i++) {
        const mtmd_input_chunk* chunk = mtmd_input_chunks_get(chunks, i);
        const size_t n_tok = mtmd_input_chunk_get_n_tokens(chunk);
        llama_pos new_n_past = n_past;   // eval_chunk_single adds to it for text chunks
        int32_t rc;
        const auto tc = Clock::now();
        if (mtmd_input_chunk_get_type(chunk) == MTMD_INPUT_CHUNK_TYPE_TEXT) {
            rc = mtmd_helper_eval_chunk_single(s->mctx, s->lctx, chunk, n_past, 0, kBatch, i == n_chunks - 1, &new_n_past);
            t_text += ms_since(tc);
            n_text_tokens += n_tok;
        } else {
            if (reuse && image_idx < s->encoded.embds.size() && s->encoded.n_tokens[image_idx] == n_tok) {
                rc = mtmd_helper_decode_image_chunk(s->mctx, s->lctx, chunk, s->encoded.embds[image_idx].data(),
                                                    n_past, 0, kBatch, &new_n_past, nullptr, nullptr);
                t_image_decode += ms_since(tc);
                n_reused++;
            } else {
                rc = mtmd_helper_eval_chunk_single(s->mctx, s->lctx, chunk, n_past, 0, kBatch, i == n_chunks - 1, &new_n_past);
                t_image_eval += ms_since(tc);
                n_encoded++;
            }
            image_idx++;
            n_image_tokens += n_tok;
        }
        if (rc != 0) {
            mtmd_input_chunks_free(chunks);
            if (!reuse) mtmd_bitmap_free(bmp);
            throw_java(env, "prefill failed at chunk " + std::to_string(i)); return nullptr;
        }
        n_past = new_n_past;
    }
    const double t_prefill = ms_since(t2);
    const llama_pos n_prompt = n_past;

    // order: penalties -> top_k -> min_p -> temp -> dist
    llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
            llama_vocab_n_tokens(s->vocab), kPenaltyLastN, repeatPenalty, 0.0f, 0.0f));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(smpl, llama_sampler_init_min_p(minP, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    const int n_vocab = llama_vocab_n_tokens(s->vocab);
    std::string tokens_json = "[";
    llama_batch batch = llama_batch_init(1, 0, 1);
    std::string out;
    std::string stop_reason = "max_tokens";
    double t_ttft = 0.0;
    int n_gen = 0;
    const auto t3 = Clock::now();
    for (int i = 0; i < maxTokens; i++) {
        llama_token tok = llama_sampler_sample(smpl, s->lctx, -1);   // also accepts the token
        if (i == 0) t_ttft = ms_since(t0);
        if (llama_vocab_is_eog(s->vocab, tok)) { stop_reason = "eog"; break; }

        // Raw model distribution (temperature 1, before the sampler chain) for the chosen token and its rivals.
        const float* logits = llama_get_logits_ith(s->lctx, -1);
        float max_l = logits[0];
        for (int v = 1; v < n_vocab; v++) max_l = std::max(max_l, logits[v]);
        double denom = 0.0;
        llama_token top_id[kTopAlternatives];
        float top_l[kTopAlternatives];
        int n_top = 0;
        for (int v = 0; v < n_vocab; v++) {
            denom += std::exp(static_cast<double>(logits[v] - max_l));
            if (n_top < kTopAlternatives || logits[v] > top_l[n_top - 1]) {
                int pos = n_top < kTopAlternatives ? n_top++ : kTopAlternatives - 1;
                while (pos > 0 && top_l[pos - 1] < logits[v]) {
                    top_l[pos] = top_l[pos - 1];
                    top_id[pos] = top_id[pos - 1];
                    pos--;
                }
                top_l[pos] = logits[v];
                top_id[pos] = v;
            }
        }
        const std::string piece = token_piece(s->vocab, tok);
        out += piece;

        std::string tj = "{";
        kv_str(tj, "t", piece);
        kv_num(tj, "p", std::exp(static_cast<double>(logits[tok] - max_l)) / denom);
        std::string top = "[";
        for (int k = 0; k < n_top; k++) {
            if (k > 0) top += ',';
            std::string cj = "{";
            kv_str(cj, "t", token_piece(s->vocab, top_id[k]));
            kv_num(cj, "p", std::exp(static_cast<double>(top_l[k] - max_l)) / denom);
            cj += "}";
            top += cj;
        }
        top += "]";
        kv(tj, "top", top);
        tj += "}";
        if (n_gen > 0) tokens_json += ',';
        tokens_json += tj;
        n_gen++;

        batch.n_tokens     = 1;
        batch.token[0]     = tok;
        batch.pos[0]       = n_past;
        batch.n_seq_id[0]  = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0]    = 1;
        if (llama_decode(s->lctx, batch) != 0) {
            LOGE("llama_decode failed at step %d", i);
            stop_reason = "decode_error";
            break;
        }
        n_past++;
    }
    const double t_generate = ms_since(t3);
    tokens_json += "]";

    const llama_perf_context_data perf = llama_perf_context(s->lctx);
    std::string pj = "{";
    kv_num(pj, "t_p_eval_ms", perf.t_p_eval_ms);
    kv_num(pj, "t_eval_ms", perf.t_eval_ms);
    kv_int(pj, "n_p_eval", perf.n_p_eval);
    kv_int(pj, "n_eval", perf.n_eval);
    pj += "}";

    std::string j = "{";
    kv_str(j, "op", "generate");
    kv_bool(j, "reused_image_encoding", n_reused > 0 && n_encoded == 0);
    kv_int(j, "image_w", mtmd_bitmap_get_nx(bmp));
    kv_int(j, "image_h", mtmd_bitmap_get_ny(bmp));
    kv_int(j, "n_chunks", static_cast<long long>(n_chunks));
    kv_int(j, "n_image_chunks", static_cast<long long>(image_idx));
    kv_int(j, "n_image_chunks_reused", static_cast<long long>(n_reused));
    kv_int(j, "n_image_chunks_encoded", static_cast<long long>(n_encoded));
    kv_int(j, "n_image_tokens", static_cast<long long>(n_image_tokens));
    kv_int(j, "n_text_prompt_tokens", static_cast<long long>(n_text_tokens));
    kv_int(j, "n_prompt_positions", n_prompt);
    kv_num(j, "t_load_image_ms", t_load_image);
    kv_num(j, "t_tokenize_ms", t_tokenize);
    kv_num(j, "t_image_decode_ms", t_image_decode);
    kv_num(j, "t_image_encode_and_decode_ms", t_image_eval);
    kv_num(j, "t_text_prefill_ms", t_text);
    kv_num(j, "t_prefill_ms", t_prefill);
    kv_num(j, "t_ttft_ms", t_ttft);
    kv_num(j, "t_generate_ms", t_generate);
    kv_int(j, "n_gen_tokens", n_gen);
    kv_num(j, "gen_tokens_per_s", t_generate > 0.0 ? n_gen * 1000.0 / t_generate : 0.0);
    kv_str(j, "stop_reason", stop_reason);
    kv_num(j, "t_total_ms", ms_since(t0));
    kv(j, "llama_perf", pj);
    kv(j, "tokens", tokens_json);
    j += "}";
    s->last_stats = j;
    LOGI("generate: prompt %d positions (%zu image tokens, %zu text), prefill %.0f ms (image %.0f/%.0f, text %.0f), "
         "ttft %.0f ms, %d tokens in %.0f ms (%.2f tok/s), stop=%s, reused=%zu",
         n_prompt, n_image_tokens, n_text_tokens, t_prefill, t_image_decode, t_image_eval, t_text,
         t_ttft, n_gen, t_generate, t_generate > 0.0 ? n_gen * 1000.0 / t_generate : 0.0,
         stop_reason.c_str(), n_reused);

    llama_batch_free(batch);
    llama_sampler_free(smpl);
    mtmd_input_chunks_free(chunks);
    if (!reuse) mtmd_bitmap_free(bmp);
    return to_utf8_bytes(env, out);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_identify_model_VlmEngine_nativeGetLastStats(JNIEnv* env, jclass, jlong handle) {
    auto* s = reinterpret_cast<VlmSession*>(handle);
    if (!s) return to_utf8_bytes(env, "{}");
    std::lock_guard<std::mutex> lock(s->mu);
    return to_utf8_bytes(env, s->last_stats);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_identify_model_VlmEngine_nativeUnloadModel(JNIEnv*, jclass, jlong handle) {
    auto* s = reinterpret_cast<VlmSession*>(handle);
    if (!s) return;
    {
        std::lock_guard<std::mutex> lock(s->mu);
        clear_encoded(s);
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
