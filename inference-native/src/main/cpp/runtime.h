#pragma once
#include <atomic>
#include <functional>
#include <mutex>
#include <string>
#include "llama.h"
#include "chat.h"

class Runtime {
public:
    ~Runtime();
    void load(const std::string & path);
    void unload();
    void cancel() { cancelled.store(true); }
    std::string generate(const std::string & messages, const std::string & tools,
                         const std::function<void(const std::string &)> & on_content);
private:
    std::mutex mutex;
    std::atomic<bool> cancelled{false};
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    common_chat_templates_ptr templates;
    void release();
};
