#include "omni.h"
#include "mtmd-helper.h"
#include "common.h"
#include "sampling.h"
#include <algorithm>
#include <thread>
#include <cmath>
#include <chrono>
#include <stdexcept>

OmniRuntime::~OmniRuntime() { release(); }
void OmniRuntime::release() {
    templates.reset();
    if (media) mtmd_free(media);
    if (context) llama_free(context);
    if (model) llama_model_free(model);
    media = nullptr; context = nullptr; model = nullptr;
}
void OmniRuntime::unload() { std::lock_guard<std::mutex> lock(mutex); release(); }
int OmniRuntime::sample_rate() {
    std::lock_guard<std::mutex> lock(mutex);
    if (!media) throw std::runtime_error("Import and load both Omni files first");
    return mtmd_get_audio_sample_rate(media);
}
void OmniRuntime::load(const std::string & path, const std::string & projector) {
    std::lock_guard<std::mutex> lock(mutex);
    release(); cancelled.store(false); initialize_backends();
    mtmd_helper_log_set([](ggml_log_level, const char *, void *) {}, nullptr);
    try {
        auto mp = llama_model_default_params(); mp.n_gpu_layers = 0;
        mp.progress_callback = [](float, void * p) { return !static_cast<std::atomic<bool> *>(p)->load(); };
        mp.progress_callback_user_data = &cancelled;
        model = llama_model_load_from_file(path.c_str(), mp);
        if (!model) throw std::runtime_error("Cannot load pinned Omni model");
        auto cp = llama_context_default_params();
        cp.n_ctx = 4096; cp.n_batch = 256; cp.n_ubatch = 256;
        cp.n_threads = std::min(4u, std::max(1u, std::thread::hardware_concurrency())); cp.n_threads_batch = cp.n_threads;
        cp.abort_callback = [](void * p) { return static_cast<std::atomic<bool> *>(p)->load(); }; cp.abort_callback_data = &cancelled;
        context = llama_init_from_model(model, cp);
        if (!context) throw std::runtime_error("Cannot allocate Omni context");
        auto mm = mtmd_context_params_default(); mm.use_gpu = false; mm.n_threads = cp.n_threads; mm.print_timings = false; mm.warmup = false;
        mm.progress_callback = [](float, void * p) { return !static_cast<std::atomic<bool> *>(p)->load(); }; mm.progress_callback_user_data = &cancelled;
        media = mtmd_init_from_file(projector.c_str(), model, mm);
        if (!media || !mtmd_support_audio(media)) throw std::runtime_error("Omni projector does not support audio; use the paired pinned file");
        const int rate = mtmd_get_audio_sample_rate(media);
        if (rate <= 0 || rate > 48000) throw std::runtime_error("Unsupported Omni audio sample rate");
        templates = common_chat_templates_init(model, "");
    } catch (...) { release(); throw; }
}
std::string OmniRuntime::transcribe(const std::vector<float> & samples, int rate) {
    std::lock_guard<std::mutex> lock(mutex);
    if (!media || !context) throw std::runtime_error("Load Omni and its projector first");
    if (rate != mtmd_get_audio_sample_rate(media) || samples.size() < static_cast<size_t>(rate / 4) || samples.size() > static_cast<size_t>(rate * 20))
        throw std::runtime_error("Record between 0.25 and 20 seconds at the Omni sample rate");
    double energy = 0;
    for (float s : samples) { if (!std::isfinite(s) || std::abs(s) > 1.01f) throw std::runtime_error("Invalid PCM audio"); energy += s * s; }
    if (std::sqrt(energy / samples.size()) < 0.002) throw std::runtime_error("No audible speech captured; try again");
    cancelled.store(false);
    const auto started = std::chrono::steady_clock::now();
    common_chat_templates_inputs input; input.enable_thinking = false; input.chat_template_kwargs["enable_thinking"] = "false";
    common_chat_msg system; system.role = "system"; system.content = "Perform ASR in English.";
    common_chat_msg user; user.role = "user"; user.content = mtmd_default_marker();
    input.messages = {system, user};
    const auto * vocab = llama_model_get_vocab(model);
    common_chat_session chat(templates.get(), vocab, input);
    std::string prompt;
    for (const auto & part : chat.prompt().parts) prompt += part.text;
    std::unique_ptr<mtmd_bitmap, decltype(&mtmd_bitmap_free)> audio(mtmd_bitmap_init_from_audio(samples.size(), samples.data()), mtmd_bitmap_free);
    std::unique_ptr<mtmd_input_chunks, decltype(&mtmd_input_chunks_free)> chunks(mtmd_input_chunks_init(), mtmd_input_chunks_free);
    const mtmd_bitmap * bitmaps[] = {audio.get()};
    mtmd_input_text text{prompt.data(), prompt.size(), true, true};
    if (!audio || mtmd_tokenize(media, chunks.get(), &text, bitmaps, 1) != 0) throw std::runtime_error("Cannot encode this audio input");
    if (mtmd_helper_get_n_pos(chunks.get()) + 256 > llama_n_ctx(context)) throw std::runtime_error("Recording too long for the voice context; use a shorter utterance");
    if (cancelled.load()) throw std::runtime_error("Voice cancelled");
    llama_memory_clear(llama_get_memory(context), true);
    llama_pos past = 0;
    if (mtmd_helper_eval_chunks(media, context, chunks.get(), 0, 0, 256, true, &past) != 0)
        throw std::runtime_error(cancelled.load() ? "Voice cancelled" : "Omni audio evaluation failed");
    if (cancelled.load()) throw std::runtime_error("Voice cancelled");
    common_params_sampling params; params.temp = 0; params.top_k = 1; params.penalty_repeat = 1.0f; params.seed = 42;
    common_sampler_ptr sampler(common_sampler_init(model, params));
    if (!sampler) throw std::runtime_error("Cannot allocate ASR sampler");
    bool ended = false;
    int generated = 0;
    for (int i = 0; i < 256; ++i) {
        if (cancelled.load()) throw std::runtime_error("Voice cancelled");
        auto token = common_sampler_sample(sampler.get(), context, -1); common_sampler_accept(sampler.get(), token, true);
        if (llama_vocab_is_eog(vocab, token)) { ended = true; break; }
        auto piece = common_token_to_piece(vocab, token, true);
        common_chat_input chunk; chunk.append(piece, token); chat.feed(chunk); ++generated;
        auto batch = llama_batch_get_one(&token, 1);
        if (llama_decode(context, batch) != 0) throw std::runtime_error(cancelled.load() ? "Voice cancelled" : "ASR token decode failed");
    }
    if (!ended) throw std::runtime_error("ASR token limit reached; transcript withheld");
    auto transcript = chat.finish().content;
    if (transcript.empty() || transcript.find("<|") != std::string::npos || transcript.find("<__") != std::string::npos)
        throw std::runtime_error("Omni returned an empty or protocol transcript");
    return common_json({{"transcript", transcript}, {"sample_rate", rate}, {"samples", samples.size()}, {"generated_tokens", generated},
        {"audio_ms", samples.size() * 1000.0 / rate}, {"asr_ms", std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - started).count()}}).dump();
}
