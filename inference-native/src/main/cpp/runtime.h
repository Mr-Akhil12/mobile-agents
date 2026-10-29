#pragma once
#include <atomic>
#include <functional>
#include <mutex>
#include <string>
#include "llama.h"
#include "chat.h"

struct GenerationOptions {
    float temperature = 0.1f;
    int max_tokens = 512;
    int top_k = 50;
    float repeat_penalty = 1.1f;
    bool thinking = false;
};

class Runtime {
public:
    ~Runtime();
    void load(const std::string & path);
    void unload();
    bool supports_thinking();
    void cancel() { cancelled.store(true); }
    std::string generate(const std::string & messages, const std::string & tools,
                         const std::function<void(const std::string &)> & on_content,
                         const GenerationOptions & options = {});
private:
    std::mutex mutex;
    std::atomic<bool> cancelled{false};
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    common_chat_templates_ptr templates;
    std::vector<llama_token> cached_prompt;
    std::vector<uint8_t> cached_state;
    void release();
};
