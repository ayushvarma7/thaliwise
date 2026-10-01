# NATIVE_API_NOTES

llama.cpp tag b11323, commit f11d642a27b921cf22b6a8beb1b899f960fedcde.
The headers in third_party/llama.cpp are the source of truth. This file lists the header grep output (spec Section 7.6) and every place where `app/src/main/cpp/vlm_bridge.cpp` differs from the reference code in spec Section 7.7.

## Deviations from Section 7.7

1. `mtmd_helper_bitmap_init_from_file` changed signature and return type.
   - Header (tools/mtmd/mtmd-helper.h:60): `struct mtmd_helper_bitmap_wrapper mtmd_helper_bitmap_init_from_file(const mtmd_context * ctx, const char * fname, bool placeholder, struct mtmd_helper_init_opt opt);`
   - `mtmd_helper_bitmap_wrapper` is `{ mtmd_bitmap * bitmap; mtmd_helper_video * video_ctx; }`.
   - Bridge: a `load_image_bitmap()` helper calls it with `placeholder = false` and `mtmd_helper_init_opt_default()`, then uses `.bitmap`. `video_ctx` is only set for video files; if it is ever set, the bridge frees both and treats it as a load failure (this app only sends JPEG files). This mirrors `load_media()` in mtmd-cli.cpp.
2. `struct mtmd_input_text` has a new field `size_t text_len` between `text` and `add_special` (mtmd.h:69). The bridge sets `text_len` to the byte length of the prompt string.
3. `llama_model_params` no longer has `use_mmap`. It has `enum llama_load_mode load_mode` (llama.h:206). The bridge sets `mp.load_mode = LLAMA_LOAD_MODE_MMAP` in place of `mp.use_mmap = true`.
4. `llama_sampler_init_penalties` takes the vocabulary size as a new first argument (llama.h:1555): `(int32_t n_vocab, int32_t penalty_last_n, float penalty_repeat, float penalty_freq, float penalty_present)`. The bridge passes `llama_vocab_n_tokens(vocab)`, then the spec values: last_n 64, repeat 1.05, freq 0, present 0.
5. Logging: the bridge calls `mtmd_helper_log_set(log_cb, nullptr)` in place of `mtmd_log_set(log_cb, nullptr)`. The header (mtmd-helper.h:46) says it "also call mtmd_log_set() internally", and it additionally routes the helper's own log lines (for example image decode errors) to logcat instead of stderr, which is invisible on Android. mtmd-cli.cpp uses the same call.

Unchanged and confirmed against the headers: `mtmd_init_from_file`, `mtmd_context_params` fields `use_gpu`, `n_threads`, `print_timings` (all still exist), `mtmd_tokenize`, `mtmd_encode_chunk`, `mtmd_get_output_embd` (row size is `llama_model_n_embd_inp(model)`), `mtmd_helper_eval_chunks` (same 8 parameters), `llama_memory_clear(llama_get_memory(ctx), true)`, `llama_decode`, `llama_batch_init(1, 0, 1)`, `llama_token_to_piece(vocab, token, buf, length, lstrip, special)`, `llama_sampler_init_min_p(p, min_keep)`, `llama_sampler_init_dist(LLAMA_DEFAULT_SEED)`, `llama_model_n_embd_inp` (exists, so no fallback to `llama_model_n_embd`).

## Header grep output (spec Section 7.6 commands)

Some declarations are column-aligned with spaces before `(`, so the `name(` pattern prints nothing for them. Those are covered in the supplementary section below.

```
## llama_backend_init
486:    LLAMA_API void llama_backend_init(void);
492:    // TODO: deprecate and make part of llama_backend_init()
## llama_log_set
1667:    LLAMA_API void llama_log_set(ggml_log_callback   log_callback, void *  user_data);
## llama_model_default_params
479:    LLAMA_API struct llama_model_params          llama_model_default_params(void);
## llama_model_load_from_file
522:    LLAMA_API struct llama_model * llama_model_load_from_file(
## llama_model_get_vocab
593:    LLAMA_API const struct llama_vocab * llama_model_get_vocab(const struct llama_model * model);
## llama_context_default_params
480:    LLAMA_API struct llama_context_params        llama_context_default_params(void);
## llama_init_from_model
549:    LLAMA_API struct llama_context * llama_init_from_model(
## llama_get_memory
## llama_memory_clear
752:    LLAMA_API void llama_memory_clear(
## llama_sampler_chain_init
1343:    //    llama_sampler * smpl = llama_sampler_chain_init(sparams);
1448:    LLAMA_API struct llama_sampler * llama_sampler_chain_init(struct llama_sampler_chain_params params);
## llama_sampler_chain_default_params
481:    LLAMA_API struct llama_sampler_chain_params  llama_sampler_chain_default_params(void);
1341:    //    auto sparams = llama_sampler_chain_default_params();
## llama_sampler_chain_add
1345:    //    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(50));
1346:    //    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9, 1));
## llama_sampler_init_penalties
1555:    LLAMA_API struct llama_sampler * llama_sampler_init_penalties(
## llama_sampler_init_top_k
1345:    //    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(50));
## llama_sampler_init_min_p
## llama_sampler_init_temp
## llama_sampler_init_dist
1351:    //    llama_sampler_chain_add(smpl, llama_sampler_init_dist(seed));
1471:    LLAMA_API struct llama_sampler * llama_sampler_init_dist(uint32_t seed);
## llama_sampler_sample
1362:    //        const llama_token id = llama_sampler_sample(smpl, ctx, -1);
1641:    LLAMA_API llama_token llama_sampler_sample(struct llama_sampler * smpl, struct llama_context * ctx, int32_t idx);
## llama_sampler_free
1367:    //    llama_sampler_free(smpl);
## llama_vocab_is_eog
1206:    LLAMA_API bool llama_vocab_is_eog(const struct llama_vocab * vocab, llama_token token);
## llama_token_to_piece
1287:    LLAMA_API int32_t llama_token_to_piece(
## llama_batch_init
973:    LLAMA_API struct llama_batch llama_batch_init(
978:    // Frees a batch of tokens allocated with llama_batch_init()
## llama_batch_free
968:    // The batch has to be freed with llama_batch_free()
979:    LLAMA_API void llama_batch_free(struct llama_batch batch);
## llama_decode
399:        // if it returns true, execution of llama_decode() will be aborted
660:    // Returns true if the model contains a decoder that requires llama_decode() call
## llama_model_n_embd
## llama_model_n_embd_inp
## llama_print_system_info
1661:    LLAMA_API const char * llama_print_system_info(void);
## llama_free
559:    LLAMA_API void llama_free(struct llama_context * ctx);
## llama_model_free
547:    LLAMA_API void llama_model_free(struct llama_model * model);
## mtmd_context_params_default
129:MTMD_API struct mtmd_context_params mtmd_context_params_default(void);
## mtmd_init_from_file
133:MTMD_API mtmd_context * mtmd_init_from_file(const char * mmproj_fname,
## mtmd_free
137:MTMD_API void mtmd_free(mtmd_context * ctx);
466:    void operator()(mtmd_context * val) { mtmd_free(val); }
## mtmd_default_marker
127:MTMD_API const char * mtmd_default_marker(void);
288:// the default marker is defined by mtmd_default_marker()
## mtmd_input_chunks_init
223:MTMD_API mtmd_input_chunks *      mtmd_input_chunks_init(void);
## mtmd_input_chunks_size
224:MTMD_API size_t                   mtmd_input_chunks_size(const mtmd_input_chunks * chunks);
528:    size_t size() const { return mtmd_input_chunks_size(ptr.get()); }
## mtmd_input_chunks_get
225:MTMD_API const mtmd_input_chunk * mtmd_input_chunks_get (const mtmd_input_chunks * chunks, size_t idx);
530:        return mtmd_input_chunks_get(ptr.get(), idx);
## mtmd_input_chunks_free
226:MTMD_API void                     mtmd_input_chunks_free(mtmd_input_chunks * chunks);
476:    void operator()(mtmd_input_chunks * val) { mtmd_input_chunks_free(val); }
## mtmd_input_chunk_get_type
232:MTMD_API enum mtmd_input_chunk_type mtmd_input_chunk_get_type        (const mtmd_input_chunk * chunk);
## mtmd_input_chunk_get_n_tokens
235:MTMD_API size_t                     mtmd_input_chunk_get_n_tokens    (const mtmd_input_chunk * chunk);
331:// llama_model_n_embd_inp(model) * mtmd_input_chunk_get_n_tokens(chunk) * sizeof(float)
## mtmd_tokenize
165://           into one chunk; mtmd_tokenize() handles this, but remember to set
173://     you can pass the bitmap via mtmd_tokenize(), then call mtmd_*_get_n_tokens() to count the tokens
## mtmd_encode_chunk
321:           "use mtmd_encode_chunk() instead");
326:MTMD_API int32_t mtmd_encode_chunk(mtmd_context * ctx,
## mtmd_get_output_embd
332:MTMD_API float * mtmd_get_output_embd(mtmd_context * ctx);
## mtmd_bitmap_free
182:MTMD_API void                  mtmd_bitmap_free       (mtmd_bitmap * bitmap);
471:    void operator()(mtmd_bitmap * val) { mtmd_bitmap_free(val); }
## mtmd_log_set
356:MTMD_API void mtmd_log_set(ggml_log_callback log_callback, void * user_data);
## mtmd_helper_bitmap_init_from_file
60:MTMD_API struct mtmd_helper_bitmap_wrapper mtmd_helper_bitmap_init_from_file(
## mtmd_helper_eval_chunks
100:MTMD_API int32_t mtmd_helper_eval_chunks(mtmd_context * ctx,
109:// works like mtmd_helper_eval_chunks(), but only for a single chunk
## struct mtmd_context_params
97:struct mtmd_context_params {
98-    bool use_gpu;
99-    ggml_backend_dev_t device;
100-    bool print_timings;
101-    int n_threads;
102-    const char * image_marker; // deprecated, use media_marker instead
103-    const char * media_marker;
104-    enum llama_flash_attn_type flash_attn_type;
105-    bool warmup; // whether to run a warmup encode pass after initialization
106-
107-    // limit number of image tokens, only for vision models with dynamic resolution
108-    int image_min_tokens; // minimum number of tokens for image input (default: read from metadata)
109-    int image_max_tokens; // maximum number of tokens for image input (default: read from metadata)
110-
111-    // callback function passed over to mtmd proper
112-    ggml_backend_sched_eval_callback cb_eval;
113-    void * cb_eval_user_data;
114-
115-    // batching params
116-    int32_t batch_max_tokens; // maximum number of output tokens in a batch
117-                              // (note: this is not a hard-limit, the first image will always be added even if it exceeds this limit)
118-                              // (default: 1024)
119-
120-    // Called with a progress value between 0.0 and 1.0. Pass NULL to disable.
121-    // If the provided progress_callback returns true, model loading continues.
122-    // If it returns false, model loading is immediately aborted.
--
129:MTMD_API struct mtmd_context_params mtmd_context_params_default(void);
130-
131-// initialize the mtmd context
132-// return nullptr on failure
133-MTMD_API mtmd_context * mtmd_init_from_file(const char * mmproj_fname,
134-                                            const struct llama_model * text_model,
135:                                            const struct mtmd_context_params ctx_params);
136-
137-MTMD_API void mtmd_free(mtmd_context * ctx);
138-
139-// whether we need to set non-causal mask before llama_decode
140-// if chunk is nullptr, we assume the default case where chunk is an image chunk
141-MTMD_API bool mtmd_decode_use_non_causal(const mtmd_context * ctx, const mtmd_input_chunk * chunk);
142-
143-// whether the current model use M-RoPE for llama_decode
144-MTMD_API bool mtmd_decode_use_mrope(const mtmd_context * ctx);
145-
146-// whether the current model supports vision input
147-MTMD_API bool mtmd_support_vision(const mtmd_context * ctx);
148-
149-// whether the current model supports audio input
150-MTMD_API bool mtmd_support_audio(const mtmd_context * ctx);
151-
152-// get audio sample rate in Hz, for example 16000 for Whisper
153-// return -1 if audio is not supported
154-MTMD_API int mtmd_get_audio_sample_rate(const mtmd_context * ctx);
155-
156-// get the current marker string
157-MTMD_API const char * mtmd_get_marker(const mtmd_context * ctx);
158-
159-// mtmd_bitmap
160-//
--
454:    struct mtmd_context_params ctx_params);
455-#endif
456-
457-//
458-// C++ wrappers
459-//
460-
461-#ifdef __cplusplus
462-
463-namespace mtmd {
464-
465-struct mtmd_context_deleter {
466-    void operator()(mtmd_context * val) { mtmd_free(val); }
467-};
468-using context_ptr = std::unique_ptr<mtmd_context, mtmd_context_deleter>;
469-
470-struct mtmd_bitmap_deleter {
471-    void operator()(mtmd_bitmap * val) { mtmd_bitmap_free(val); }
472-};
473-using bitmap_ptr = std::unique_ptr<mtmd_bitmap, mtmd_bitmap_deleter>;
474-
475-struct mtmd_input_chunks_deleter {
476-    void operator()(mtmd_input_chunks * val) { mtmd_input_chunks_free(val); }
477-};
478-using input_chunks_ptr = std::unique_ptr<mtmd_input_chunks, mtmd_input_chunks_deleter>;
479-
## struct mtmd_input_text
69:struct mtmd_input_text {
70-    const char * text;
71-    size_t text_len;
72-    bool add_special;
73-    bool parse_special;
74-};
75-
--
78:    const struct mtmd_input_text * text;
79-    const struct mtmd_bitmap * bitmap;
80-};
81-
82-//
83-// C API
84-//
--
91:typedef struct mtmd_input_text   mtmd_input_text;
92-typedef struct mtmd_input_part   mtmd_input_part;
93-typedef struct mtmd_batch        mtmd_batch;
94-
95-typedef bool (*mtmd_progress_callback)(float progress, void * user_data);
96-
97-struct mtmd_context_params {
## LLAMA_DEFAULT_SEED
37:#define LLAMA_DEFAULT_SEED 0xFFFFFFFF
1470:    /// seed == LLAMA_DEFAULT_SEED to use a random seed.
1627:    // Returns the seed used by the sampler if applicable, LLAMA_DEFAULT_SEED otherwise
```

## Supplementary greps (space-aligned declarations and changed structs)

```
## llama_get_memory
590:    LLAMA_API           llama_memory_t   llama_get_memory  (const struct llama_context * ctx);
591-    LLAMA_API  enum llama_pooling_type   llama_pooling_type(const struct llama_context * ctx); // TODO: rename to llama_get_pooling_type
592-
593-    LLAMA_API const struct llama_vocab * llama_model_get_vocab(const struct llama_model * model);
594-    LLAMA_API enum llama_rope_type       llama_model_rope_type(const struct llama_model * model);
## llama_memory_clear
752:    LLAMA_API void llama_memory_clear(
753-            llama_memory_t mem,
754-                      bool data);
755-
756-    // Removes all tokens that belong to the specified sequence and have positions in [p0, p1)
## llama_sampler_chain_add
1451:    LLAMA_API void                   llama_sampler_chain_add(      struct llama_sampler * chain, struct llama_sampler * smpl);
1452-
1453-    // return NULL if:
1454-    //   - the sampler is NULL
1455-    //   - the sampler is not a llama_sampler_chain
## llama_sampler_init_penalties
1555:    LLAMA_API struct llama_sampler * llama_sampler_init_penalties(
1556-                             int32_t   n_vocab,
1557-                             int32_t   penalty_last_n,   // last n tokens to penalize (0 = disable penalty)
1558-                               float   penalty_repeat,   // must be > 0.0, 1.0 = disabled
1559-                               float   penalty_freq,     // must be finite, 0.0 = disabled
## llama_sampler_init_top_k
1475:    LLAMA_API struct llama_sampler * llama_sampler_init_top_k      (int32_t k);
1476-
1477-    /// @details Nucleus sampling described in academic paper "The Curious Case of Neural Text Degeneration" https://arxiv.org/abs/1904.09751
1478-    LLAMA_API struct llama_sampler * llama_sampler_init_top_p      (float   p, size_t min_keep);
1479-
## llama_sampler_init_min_p
1481:    LLAMA_API struct llama_sampler * llama_sampler_init_min_p      (float   p, size_t min_keep);
1482-
1483-    /// @details Locally Typical Sampling implementation described in the paper https://arxiv.org/abs/2202.00666.
1484-    LLAMA_API struct llama_sampler * llama_sampler_init_typical    (float   p, size_t min_keep);
1485-
## llama_sampler_init_temp
1487:    LLAMA_API struct llama_sampler * llama_sampler_init_temp       (float   t);
1488-
1489-    /// @details Dynamic temperature implementation (a.k.a. entropy) described in the paper https://arxiv.org/abs/2309.02772.
1490-    LLAMA_API struct llama_sampler * llama_sampler_init_temp_ext   (float   t, float   delta, float exponent);
1491-
## llama_sampler_free
1443:    LLAMA_API void                   llama_sampler_free  (      struct llama_sampler * smpl);
1444-
1445-    // llama_sampler_chain
1446-    // a type of llama_sampler that can chain multiple samplers one after another
1447-
## llama_token_to_piece
1287:    LLAMA_API int32_t llama_token_to_piece(
1288-              const struct llama_vocab * vocab,
1289-                           llama_token   token,
1290-                                  char * buf,
1291-                               int32_t   length,
## llama_batch_init
973:    LLAMA_API struct llama_batch llama_batch_init(
974-            int32_t n_tokens,
975-            int32_t embd,
976-            int32_t n_seq_max);
977-
## llama_decode
1003:    LLAMA_API int32_t llama_decode(
1004-            struct llama_context * ctx,
1005-              struct llama_batch   batch);
1006-
1007-    //
## llama_model_n_embd
597:    LLAMA_API int32_t llama_model_n_embd       (const struct llama_model * model);
598-    LLAMA_API int32_t llama_model_n_embd_inp   (const struct llama_model * model);
599-    LLAMA_API int32_t llama_model_n_embd_out   (const struct llama_model * model);
600-    LLAMA_API int32_t llama_model_n_layer      (const struct llama_model * model);
601-    LLAMA_API int32_t llama_model_n_layer_nextn(const struct llama_model * model);
## llama_model_n_embd_inp
598:    LLAMA_API int32_t llama_model_n_embd_inp   (const struct llama_model * model);
599-    LLAMA_API int32_t llama_model_n_embd_out   (const struct llama_model * model);
600-    LLAMA_API int32_t llama_model_n_layer      (const struct llama_model * model);
601-    LLAMA_API int32_t llama_model_n_layer_nextn(const struct llama_model * model);
602-    LLAMA_API int32_t llama_model_n_head       (const struct llama_model * model);
## llama_model_load_from_file
522:    LLAMA_API struct llama_model * llama_model_load_from_file(
523-                             const char * path_model,
524-              struct llama_model_params   params);
525-
526-    // Load a model from an open FILE pointer
## llama_init_from_model
549:    LLAMA_API struct llama_context * llama_init_from_model(
550-                     struct llama_model * model,
551-            struct llama_context_params   params);
552-
553-    DEPRECATED(LLAMA_API struct llama_context * llama_new_context_with_model(
## llama_log_set
1667:    LLAMA_API void llama_log_set(ggml_log_callback   log_callback, void *  user_data);
1668-
1669-    //
1670-    // Performance utils
1671-    //
## llama_batch struct
261-    //            )
262-    //
263:    typedef struct llama_batch {
264-        int32_t n_tokens;
265-
266-        llama_token  *  token;
267-        float        *  embd;
268-        llama_pos    *  pos;
269-        int32_t      *  n_seq_id;
270-        llama_seq_id ** seq_id;
271-        int8_t       *  logits;   // TODO: rename this to "output"
272-    } llama_batch;
273-
274-    enum llama_model_kv_override_type {
275-        LLAMA_KV_OVERRIDE_TYPE_INT,
276-        LLAMA_KV_OVERRIDE_TYPE_FLOAT,
277-        LLAMA_KV_OVERRIDE_TYPE_BOOL,
## ggml_log_callback
ggml/include/ggml.h:2917:    typedef void (*ggml_log_callback)(enum ggml_log_level level, const char * text, void * user_data);
## ggml_log_level
652:    enum ggml_log_level {
653-        GGML_LOG_LEVEL_NONE  = 0,
654-        GGML_LOG_LEVEL_DEBUG = 1,
655-        GGML_LOG_LEVEL_INFO  = 2,
656-        GGML_LOG_LEVEL_WARN  = 3,
657-        GGML_LOG_LEVEL_ERROR = 4,
658-        GGML_LOG_LEVEL_CONT  = 5, // continue previous log
659-    };
660-
## model params
8:326-        int32_t n_gpu_layers; // number of layers to store in VRAM, a negative value means all layers
38:356-    };
## context params
366-        uint32_t n_ctx;                 // text context, 0 = from model
367-        uint32_t n_batch;               // logical maximum batch size that can be submitted to llama_decode
368-        uint32_t n_ubatch;              // physical maximum batch size
369-        uint32_t n_seq_max;             // max number of sequences (i.e. distinct states for recurrent models)
371-        uint32_t n_outputs_max;         // max outputs in a ubatch (0 = n_batch)
373-        int32_t  n_threads;             // number of threads to use for generation
374-        int32_t  n_threads_batch;       // number of threads to use for batch processing
380-        enum llama_flash_attn_type   flash_attn_type;   // when to enable Flash Attention
410-                          // NOTE: setting to false when n_seq_max > 1 can cause bad performance in some cases
413-                          // try to disable when n_seq_max > 1 for improved performance when the sequences do not share a large prefix
425-    };

## llama_model_params (llama.h:318)

    struct llama_model_params {
        // NULL-terminated list of devices to use for offloading (if NULL, all available devices are used)
        ggml_backend_dev_t * devices;

        // NULL-terminated list of buffer types to use for tensors that match a pattern
        const struct llama_model_tensor_buft_override * tensor_buft_overrides;

        int32_t n_gpu_layers; // number of layers to store in VRAM, a negative value means all layers
        enum llama_split_mode split_mode; // how to split the model across multiple GPUs
        enum llama_load_mode  load_mode;  // how to load the model

        enum llama_lazy_mode lazy_mode; // on-demand reading of tensors marked by the arch

        // the GPU that is used for the entire model when split_mode is LLAMA_SPLIT_MODE_NONE
        int32_t main_gpu;

        // proportion of the model (layers or rows) to offload to each GPU, size: llama_max_devices()
        const float * tensor_split;

        // Called with a progress value between 0.0 and 1.0. Pass NULL to disable.
        // If the provided progress_callback returns true, model loading continues.
        // If it returns false, model loading is immediately aborted.
        llama_progress_callback progress_callback;

        // context pointer passed to the progress callback
        void * progress_callback_user_data;

        // override key-value pairs of the model meta data
        const struct llama_model_kv_override * kv_overrides;

        // Keep the booleans together to avoid misalignment during copy-by-value.
        bool vocab_only;      // only load the vocabulary, no weights
        bool check_tensors;   // validate model tensor data
        bool use_extra_bufts; // use extra buffer types (used for weight repacking)
        bool no_host;         // bypass host buffer allowing extra buffers to be used
        bool no_alloc;        // only load metadata and simulate memory allocations
        bool load_mtp;        // whether to load MTP layers
    };
## llama_load_mode (llama.h:206)
    enum llama_load_mode {
        LLAMA_LOAD_MODE_AUTO       = -1, // auto-detect based on device capabilities
        LLAMA_LOAD_MODE_NONE       =  0, // no special loading mode
        LLAMA_LOAD_MODE_MMAP       =  1, // memory map the model
        LLAMA_LOAD_MODE_MLOCK      =  2, // force system to keep model in RAM rather than swapping or compressing
        LLAMA_LOAD_MODE_MMAP_MLOCK =  3, // mmap + force system to keep model in RAM rather than swapping or compressing
        LLAMA_LOAD_MODE_DIRECT_IO  =  4, // use direct I/O if available
    };
## llama_sampler_init_penalties (llama.h:1555)
    LLAMA_API struct llama_sampler * llama_sampler_init_penalties(
                             int32_t   n_vocab,
                             int32_t   penalty_last_n,   // last n tokens to penalize (0 = disable penalty)
                               float   penalty_repeat,   // must be > 0.0, 1.0 = disabled
                               float   penalty_freq,     // must be finite, 0.0 = disabled
                               float   penalty_present); // must be finite, 0.0 = disabled
## llama_token_to_piece (llama.h:1287)
    LLAMA_API int32_t llama_token_to_piece(
              const struct llama_vocab * vocab,
                           llama_token   token,
                                  char * buf,
                               int32_t   length,
                               int32_t   lstrip,
                                  bool   special);
## mtmd_helper_bitmap_wrapper and init_from_file (mtmd-helper.h:51)
struct mtmd_helper_bitmap_wrapper {
    mtmd_bitmap * bitmap;
    mtmd_helper_video * video_ctx;
};

// helper function to construct a mtmd_bitmap from a file
// it calls mtmd_helper_bitmap_init_from_buf() internally
// returns nullptr on failure
// this function is thread-safe
MTMD_API struct mtmd_helper_bitmap_wrapper mtmd_helper_bitmap_init_from_file(
                    const mtmd_context * ctx,
                    const char * fname,
                    bool placeholder,
                    struct mtmd_helper_init_opt opt);
## mtmd_helper_eval_chunks (mtmd-helper.h:100)
MTMD_API int32_t mtmd_helper_eval_chunks(mtmd_context * ctx,
                                         struct llama_context * lctx,
                                         const mtmd_input_chunks * chunks,
                                         llama_pos n_past,
                                         llama_seq_id seq_id,
                                         int32_t n_batch,
                                         bool logits_last,
                                         llama_pos * new_n_past);
## mtmd_tokenize (mtmd.h:302)
MTMD_API int32_t mtmd_tokenize(const mtmd_context * ctx,
                               mtmd_input_chunks * output,
                               const mtmd_input_text * text,
                               const mtmd_bitmap * const * bitmaps,
                               size_t n_bitmaps);
```
