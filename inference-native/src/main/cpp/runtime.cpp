#include "runtime.h"
#include "common.h"
#include "sampling.h"
#include "ggml-backend.h"
#include "log.h"
#include <algorithm>
#include <stdexcept>
#include <thread>
#include <chrono>
#include <cmath>

void initialize_backends() {
    static std::once_flag backend;
    std::call_once(backend, [] {
        common_log_set_verbosity_thold(0);
        llama_log_set([](ggml_log_level, const char *, void *) {}, nullptr);
        ggml_backend_load_all();
        llama_backend_init();
    });
}

Runtime::~Runtime() { release(); }
void Runtime::release() {
    std::vector<llama_token>().swap(cached_prompt);
    std::vector<uint8_t>().swap(cached_state);
    templates.reset();
    if (context) llama_free(context);
    if (model) llama_model_free(model);
    context = nullptr;
    model = nullptr;
}
void Runtime::unload() { std::lock_guard<std::mutex> lock(mutex); release(); }
void Runtime::load(const std::string & path) {
    std::lock_guard<std::mutex> lock(mutex);
    release();
    initialize_backends();
    auto mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    cancelled.store(false);
    mp.progress_callback = [](float, void * data) { return !static_cast<std::atomic<bool> *>(data)->load(); };
    mp.progress_callback_user_data = &cancelled;
    model = llama_model_load_from_file(path.c_str(), mp);
    if (!model) throw std::runtime_error("Cannot load GGUF: corrupt, unsupported, or insufficient memory");
    try {
        auto cp = llama_context_default_params();
        cp.n_ctx = 4096;
        cp.n_batch = 256;
        cp.n_ubatch = 256;
        cp.n_threads = std::min(4u, std::max(1u, std::thread::hardware_concurrency()));
        cp.n_threads_batch = cp.n_threads;
        cp.abort_callback = [](void * data) { return static_cast<std::atomic<bool> *>(data)->load(); };
        cp.abort_callback_data = &cancelled;
        cancelled.store(false);
        context = llama_init_from_model(model, cp);
        if (!context) throw std::runtime_error("Cannot allocate native model context");
        templates = common_chat_templates_init(model, "");
    } catch (...) { release(); throw; }
}
bool Runtime::supports_thinking() {
    std::lock_guard<std::mutex> lock(mutex);
    return templates && common_chat_templates_support_enable_thinking(templates.get());
}
std::string Runtime::generate(const std::string & messages, const std::string & tools,
                             const std::function<void(const std::string &)> & on_content,
                             const GenerationOptions & options) {
    std::lock_guard<std::mutex> lock(mutex);
    if (!context) throw std::runtime_error("Import and load a text GGUF first");
    if (!std::isfinite(options.temperature) || options.temperature < 0 || options.temperature > 2 ||
        options.max_tokens < 64 || options.max_tokens > 1536 || options.top_k < 1 || options.top_k > 100 ||
        !std::isfinite(options.repeat_penalty) || options.repeat_penalty < 1 || options.repeat_penalty > 1.5f)
        throw std::runtime_error("Generation settings out of supported range");
    if (options.thinking && !common_chat_templates_support_enable_thinking(templates.get()))
        throw std::runtime_error("Loaded model template does not support thinking control");
    bool completed = false;
    struct Cleanup { std::function<void()> action; ~Cleanup() { action(); } } cleanup{[&] {
        if (!completed) { cached_prompt.clear(); cached_state.clear(); llama_memory_clear(llama_get_memory(context), true); }
    }};
    cancelled.store(false);
    common_chat_templates_inputs input;
    input.messages = common_chat_msgs_parse_oaicompat(common_json::parse(messages));
    input.tools = common_chat_tools_parse_oaicompat(common_json::parse(tools));
    input.enable_thinking = options.thinking;
    input.reasoning_format = COMMON_REASONING_FORMAT_DEEPSEEK;
    input.chat_template_kwargs["enable_thinking"] = options.thinking ? "true" : "false";
    const auto * vocab = llama_model_get_vocab(model);
    common_chat_session chat(templates.get(), vocab, input);
    std::vector<llama_token> tokens;
    for (const auto & part : chat.prompt().parts) {
        auto next = common_tokenize(vocab, part.text, false, !part.is_input);
        tokens.insert(tokens.end(), next.begin(), next.end());
    }
    const int budget = options.max_tokens;
    if (tokens.empty() || tokens.size() + budget > llama_n_ctx(context))
        throw std::runtime_error("Context full: start a new chat or use a shorter prompt");
    using Clock = std::chrono::steady_clock;
    const auto prompt_start = Clock::now();
    // Save prefill state before the final prompt token. Restoring a complete
    // sequence works for the hybrid recurrent model too; partial KV rollback
    // is deliberately not assumed. Decode at least one token for fresh logits.
    const size_t prefix_end = tokens.size() - 1;
    size_t reused = 0;
    llama_memory_clear(llama_get_memory(context), true);
    if (!cached_state.empty() && !cached_prompt.empty() && cached_prompt.size() <= prefix_end &&
        std::equal(cached_prompt.begin(), cached_prompt.end(), tokens.begin())) {
        if (llama_state_seq_set_data(context, cached_state.data(), cached_state.size(), 0) == cached_state.size())
            reused = cached_prompt.size();
        else llama_memory_clear(llama_get_memory(context), true);
    }
    for (size_t offset = reused; offset < prefix_end; offset += 256) {
        if (cancelled.load()) throw std::runtime_error("Cancelled");
        auto count = std::min<size_t>(256, prefix_end - offset);
        auto batch = llama_batch_get_one(tokens.data() + offset, static_cast<int>(count));
        if (llama_decode(context, batch) != 0) throw std::runtime_error(cancelled ? "Cancelled" : "Prompt decode failed");
    }
    // Process-local only, never written to disk. Bounded independently of KV.
    constexpr size_t cache_limit = 128ULL * 1024 * 1024;
    const auto state_size = prefix_end ? llama_state_seq_get_size(context, 0) : 0;
    if (state_size && state_size <= cache_limit) {
        try {
            cached_state.resize(state_size);
            if (llama_state_seq_get_data(context, cached_state.data(), state_size, 0) == state_size)
                cached_prompt.assign(tokens.begin(), tokens.begin() + prefix_end);
            else { cached_state.clear(); cached_prompt.clear(); }
        } catch (const std::bad_alloc &) { cached_state.clear(); cached_prompt.clear(); }
    } else { cached_state.clear(); cached_prompt.clear(); }
    if (cancelled.load()) throw std::runtime_error("Cancelled");
    auto last_batch = llama_batch_get_one(tokens.data() + prefix_end, 1);
    if (llama_decode(context, last_batch) != 0) throw std::runtime_error(cancelled ? "Cancelled" : "Prompt decode failed");
    const auto prompt_end = Clock::now();
    common_params_sampling params;
    params.temp = options.temperature;
    params.top_k = options.top_k;
    params.penalty_repeat = options.repeat_penalty;
    params.seed = 42;
    chat.apply_sampling(params);
    common_sampler_ptr sampler(common_sampler_init(model, params));
    if (!sampler) throw std::runtime_error("Cannot initialize sampler");
    std::string last;
    std::string raw;
    bool ended = false;
    int generated = 0;
    const auto generation_start = Clock::now();
    for (int i = 0; i < budget; ++i) {
        if (cancelled.load()) throw std::runtime_error("Cancelled");
        auto token = common_sampler_sample(sampler.get(), context, -1);
        common_sampler_accept(sampler.get(), token, true);
        if (llama_vocab_is_eog(vocab, token)) { ended = true; break; }
        ++generated;
        auto piece = common_token_to_piece(vocab, token, true);
        raw += piece;
        bool stop = false;
        for (const auto & marker : chat.additional_stops()) {
            if (!marker.empty() && raw.size() >= marker.size() && raw.compare(raw.size() - marker.size(), marker.size(), marker) == 0)
                stop = true;
        }
        if (stop) { ended = true; break; }
        common_chat_input chunk;
        chunk.append(piece, token);
        const auto & parsed = chat.feed(chunk);
        if (parsed.content != last) {
            last = parsed.content;
            on_content(last);
        }
        auto batch = llama_batch_get_one(&token, 1);
        if (llama_decode(context, batch) != 0) throw std::runtime_error(cancelled ? "Cancelled" : "Token decode failed");
    }
    if (!ended) throw std::runtime_error("Generation reached " + std::to_string(budget) + "-token limit; incomplete output rejected");
    auto result = chat.finish().to_json_oaicompat();
    const auto generation_end = Clock::now();
    result["local_metrics"] = {
        {"prompt_tokens", tokens.size() - reused}, {"prompt_total_tokens", tokens.size()}, {"reused_prompt_tokens", reused}, {"generated_tokens", generated},
        {"prompt_ms", std::chrono::duration<double, std::milli>(prompt_end - prompt_start).count()},
        {"generation_ms", std::chrono::duration<double, std::milli>(generation_end - generation_start).count()}
    };
    auto serialized = result.dump();
    completed = true;
    return serialized;
}
