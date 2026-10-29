#pragma once
#include "runtime.h"
#include "mtmd.h"
class OmniRuntime {
public:
    ~OmniRuntime();
    void load(const std::string & model_path, const std::string & projector_path);
    void unload();
    void cancel() { cancelled.store(true); }
    int sample_rate();
    std::string transcribe(const std::vector<float> & samples, int rate);
private:
    std::mutex mutex;
    std::atomic<bool> cancelled{false};
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    mtmd_context * media = nullptr;
    common_chat_templates_ptr templates;
    void release();
};
