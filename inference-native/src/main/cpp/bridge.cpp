#include "runtime.h"
#include <jni.h>
#include <codecvt>
#include <locale>
#include <stdexcept>

static Runtime runtime;
static std::string utf8(JNIEnv * env, jstring s) {
    const jchar * chars = env->GetStringChars(s, nullptr);
    std::u16string value(reinterpret_cast<const char16_t *>(chars), env->GetStringLength(s));
    env->ReleaseStringChars(s, chars);
    return std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>().to_bytes(value);
}
static jstring java_string(JNIEnv * env, const std::string & s) {
    auto value = std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>().from_bytes(s);
    return env->NewString(reinterpret_cast<const jchar *>(value.data()), static_cast<jsize>(value.size()));
}
static void fail(JNIEnv * env, const std::exception & e) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), e.what());
}
extern "C" JNIEXPORT void JNICALL Java_dev_lfm_nativebridge_NativeInference_load(JNIEnv * env, jobject, jstring path) {
    try { runtime.load(utf8(env, path)); } catch (const std::exception & e) { fail(env, e); }
}
extern "C" JNIEXPORT jstring JNICALL Java_dev_lfm_nativebridge_NativeInference_generateConfigured(JNIEnv * env, jobject, jstring messages, jstring tools, jstring settings, jobject listener) {
    try {
        auto cls = env->GetObjectClass(listener);
        auto method = env->GetMethodID(cls, "onContent", "(Ljava/lang/String;)V");
        if (!method) throw std::runtime_error("Missing stream listener");
        const auto json = common_json::parse(utf8(env, settings));
        GenerationOptions options;
        options.temperature = json.value("temperature", 0.1f);
        options.max_tokens = json.value("maxTokens", 512);
        options.top_k = json.value("topK", 50);
        options.repeat_penalty = json.value("repeatPenalty", 1.1f);
        options.thinking = json.value("thinking", false);
        auto result = runtime.generate(utf8(env, messages), utf8(env, tools), [&](const std::string & content) {
            jstring text = nullptr;
            try { text = java_string(env, content); } catch (const std::range_error &) { return; }
            env->CallVoidMethod(listener, method, text);
            env->DeleteLocalRef(text);
            if (env->ExceptionCheck()) throw std::runtime_error("Stream callback failed");
        }, options);
        env->DeleteLocalRef(cls);
        return java_string(env, result);
    } catch (const std::exception & e) { if (!env->ExceptionCheck()) fail(env, e); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL Java_dev_lfm_nativebridge_NativeInference_cancel(JNIEnv *, jobject) { runtime.cancel(); }
extern "C" JNIEXPORT void JNICALL Java_dev_lfm_nativebridge_NativeInference_unload(JNIEnv *, jobject) { runtime.unload(); }

extern "C" JNIEXPORT jboolean JNICALL Java_dev_lfm_nativebridge_NativeInference_supportsThinking(JNIEnv * env, jobject) {
    try { return runtime.supports_thinking(); }
    catch (const std::exception & e) { fail(env, e); return JNI_FALSE; }
}
