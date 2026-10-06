// Computes the app's photo embedding on the Mac, for the reference memory.
//
// Same as nativeGetImageEmbedding in app/src/main/cpp/vlm_bridge.cpp: the same model files, mtmd on the
// CPU (use_gpu false) with the same image token cap, mtmd_helper_bitmap_init_from_file to read the JPEG,
// one image marker tokenized, every image chunk encoded, then the mean over all image tokens of the
// projector output (n_embd = llama_model_n_embd_inp), L2-normalized. Photos must already be prepared like
// the app prepares them (longest side 512 px, JPEG quality 90; tools/refmem/download.py does that).
//
// Usage: embed_images --model M.gguf --mmproj P.gguf --list paths.txt --out vectors.bin
//                     [--threads 8] [--image-max-tokens 256]
// Output: "REFEMB1\0", int32 n_embd, int32 count, then count x n_embd float32 in list order.

#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <string>
#include <vector>

static void quiet_log(enum ggml_log_level level, const char* text, void*) {
    if (level >= GGML_LOG_LEVEL_ERROR) fputs(text, stderr);
}

static bool embed_one(mtmd_context* ctx, int n_embd, const std::string& path, std::vector<float>& out,
                      int& n_tokens) {
    mtmd_helper_bitmap_wrapper w = mtmd_helper_bitmap_init_from_file(ctx, path.c_str(), false,
                                                                      mtmd_helper_init_opt_default());
    if (w.video_ctx) {
        if (w.bitmap) mtmd_bitmap_free(w.bitmap);
        mtmd_helper_video_free(w.video_ctx);
        return false;
    }
    mtmd_bitmap* bmp = w.bitmap;
    if (!bmp) return false;

    const std::string marker = mtmd_default_marker();
    mtmd_input_text text{};
    text.text = marker.c_str();
    text.text_len = marker.size();
    text.add_special = false;
    text.parse_special = true;

    mtmd_input_chunks* chunks = mtmd_input_chunks_init();
    const mtmd_bitmap* bitmaps[] = {bmp};
    if (mtmd_tokenize(ctx, chunks, &text, bitmaps, 1) != 0) {
        mtmd_input_chunks_free(chunks);
        mtmd_bitmap_free(bmp);
        return false;
    }
    std::vector<double> sum(static_cast<size_t>(n_embd), 0.0);
    size_t total = 0;
    for (size_t i = 0; i < mtmd_input_chunks_size(chunks); i++) {
        const mtmd_input_chunk* chunk = mtmd_input_chunks_get(chunks, i);
        if (mtmd_input_chunk_get_type(chunk) != MTMD_INPUT_CHUNK_TYPE_IMAGE) continue;
        if (mtmd_encode_chunk(ctx, chunk) != 0) {
            mtmd_input_chunks_free(chunks);
            mtmd_bitmap_free(bmp);
            return false;
        }
        const float* e = mtmd_get_output_embd(ctx);
        const size_t n_tok = mtmd_input_chunk_get_n_tokens(chunk);
        for (size_t t = 0; t < n_tok; t++) {
            const float* row = e + t * static_cast<size_t>(n_embd);
            for (int d = 0; d < n_embd; d++) sum[d] += row[d];
        }
        total += n_tok;
    }
    mtmd_input_chunks_free(chunks);
    mtmd_bitmap_free(bmp);
    if (total == 0) return false;

    out.assign(static_cast<size_t>(n_embd), 0.0f);
    double norm = 0.0;
    for (int d = 0; d < n_embd; d++) {
        double v = sum[d] / static_cast<double>(total);
        out[d] = static_cast<float>(v);
        norm += v * v;
    }
    norm = std::sqrt(norm);
    if (norm > 0.0) for (int d = 0; d < n_embd; d++) out[d] = static_cast<float>(out[d] / norm);
    n_tokens = static_cast<int>(total);
    return true;
}

int main(int argc, char** argv) {
    std::string model_path, mmproj_path, list_path, out_path;
    int threads = 8;
    int image_max_tokens = 256;
    for (int i = 1; i + 1 < argc; i += 2) {
        std::string k = argv[i];
        std::string v = argv[i + 1];
        if (k == "--model") model_path = v;
        else if (k == "--mmproj") mmproj_path = v;
        else if (k == "--list") list_path = v;
        else if (k == "--out") out_path = v;
        else if (k == "--threads") threads = std::stoi(v);
        else if (k == "--image-max-tokens") image_max_tokens = std::stoi(v);
        else { fprintf(stderr, "unknown argument %s\n", k.c_str()); return 2; }
    }
    if (model_path.empty() || mmproj_path.empty() || list_path.empty() || out_path.empty()) {
        fprintf(stderr, "usage: embed_images --model M --mmproj P --list L --out O [--threads N] "
                        "[--image-max-tokens T]\n");
        return 2;
    }

    std::vector<std::string> paths;
    {
        std::ifstream in(list_path);
        std::string line;
        while (std::getline(in, line)) if (!line.empty()) paths.push_back(line);
    }

    llama_log_set(quiet_log, nullptr);
    mtmd_helper_log_set(quiet_log, nullptr);
    llama_backend_init();
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    llama_model* model = llama_model_load_from_file(model_path.c_str(), mp);
    if (!model) { fprintf(stderr, "model load failed\n"); return 1; }

    mtmd_context_params mcp = mtmd_context_params_default();
    mcp.use_gpu = false;
    mcp.n_threads = threads;
    mcp.print_timings = false;
    mcp.image_max_tokens = image_max_tokens > 0 ? image_max_tokens : -1;
    mtmd_context* ctx = mtmd_init_from_file(mmproj_path.c_str(), model, mcp);
    if (!ctx) { fprintf(stderr, "mmproj load failed\n"); return 1; }
    const int n_embd = llama_model_n_embd_inp(model);

    std::ofstream out(out_path, std::ios::binary);
    const char magic[8] = {'R', 'E', 'F', 'E', 'M', 'B', '1', '\0'};
    const int32_t dim = n_embd;
    const int32_t count = static_cast<int32_t>(paths.size());
    out.write(magic, 8);
    out.write(reinterpret_cast<const char*>(&dim), 4);
    out.write(reinterpret_cast<const char*>(&count), 4);

    const auto t0 = std::chrono::steady_clock::now();
    std::vector<float> vec;
    int failed = 0;
    for (size_t i = 0; i < paths.size(); i++) {
        int n_tok = 0;
        if (!embed_one(ctx, n_embd, paths[i], vec, n_tok)) {
            fprintf(stderr, "FAILED %s\n", paths[i].c_str());
            vec.assign(static_cast<size_t>(n_embd), 0.0f);   // zero vector keeps the order; eval skips it
            failed++;
        }
        out.write(reinterpret_cast<const char*>(vec.data()), static_cast<std::streamsize>(vec.size() * 4));
        if ((i + 1) % 50 == 0 || i + 1 == paths.size()) {
            double s = std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count();
            fprintf(stderr, "%zu/%zu images, %.2f s each, last %d image tokens\n", i + 1, paths.size(),
                    s / static_cast<double>(i + 1), n_tok);
        }
    }
    mtmd_free(ctx);
    llama_model_free(model);
    llama_backend_free();
    fprintf(stderr, "done: n_embd=%d count=%d failed=%d\n", n_embd, count, failed);
    return failed == 0 ? 0 : 1;
}
