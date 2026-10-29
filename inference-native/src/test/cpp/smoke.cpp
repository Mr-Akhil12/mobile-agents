#include "runtime.h"
#include "json.h"
#include <chrono>
#include <ctime>
#include <iomanip>
#include <iostream>
#include <sstream>
#include <stdexcept>

static void require(bool condition, const char * message) { if (!condition) throw std::runtime_error(message); }
int main(int argc, char ** argv) {
    if (argc != 2) return 2;
    try {
        Runtime engine;
        engine.load(argv[1]);
        auto messages = common_json::parse(R"([{"role":"system","content":"Use get_local_time for current time. Never invent results. Answer concisely with the observed time after the tool result."},{"role":"user","content":"What time is it on my device?"}])");
        const std::string tools = R"([{"type":"function","function":{"name":"get_local_time","description":"Read current device time","parameters":{"type":"object","properties":{},"additionalProperties":false}}}])";
        auto request = common_json::parse(engine.generate(messages.dump(), tools, [](const std::string &) {}));
        require(request.contains("tool_calls") && request["tool_calls"].size() == 1, "Expected one real model tool call");
        auto & call = request["tool_calls"][0];
        require(call["function"]["name"] == "get_local_time", "Unexpected tool");
        require(common_json::parse(call["function"]["arguments"].get<std::string>()).empty(), "Unexpected arguments");
        call["id"] = "host-clock-1";
        messages.push_back(request);
        const auto now = std::time(nullptr);
        std::ostringstream timestamp;
        timestamp << std::put_time(std::gmtime(&now), "%Y-%m-%dT%H:%M:%SZ");
        common_json evidence = {{"success",true},{"data",{{"datetime",timestamp.str()},{"timezone","UTC"}}},{"sourceOrEvidence","HOST OS clock; not an Android device test"}};
        messages.push_back({{"role","tool"},{"tool_call_id","host-clock-1"},{"name","get_local_time"},{"content",evidence.dump()}});
        auto answer = common_json::parse(engine.generate(messages.dump(), tools, [](const std::string &) {}));
        require(!answer["content"].get<std::string>().empty(), "Expected tool follow-up answer");
        require(!answer.contains("tool_calls") || answer["tool_calls"].empty(), "Unexpected repeated tool call");
        std::cout << common_json({{"test","host_time_round_trip"},{"passed",true},{"evidence",evidence},{"answer",answer["content"]}}).dump() << std::endl;
        bool cancelled = false;
        try {
            engine.generate(R"([{"role":"user","content":"Write a long story about a journey."}])", "[]", [&](const std::string & text) { if (!text.empty()) engine.cancel(); });
        } catch (const std::exception & e) { cancelled = std::string(e.what()) == "Cancelled"; }
        require(cancelled, "Cancellation did not interrupt generation");
        auto recovery = common_json::parse(engine.generate(R"([{"role":"user","content":"Reply with only the word LOCAL."}])", "[]", [](const std::string &) {}));
        require(recovery["content"].get<std::string>().find("LOCAL") != std::string::npos, "Generation did not recover after cancellation");
        std::cout << "{\"test\":\"host_cancel_and_recovery\",\"passed\":true}" << std::endl;
        engine.unload();
        try { engine.generate("[]", "[]", [](const std::string &) {}); throw std::runtime_error("Unloaded model unexpectedly generated"); }
        catch (const std::exception & e) { require(std::string(e.what()).find("Import and load") != std::string::npos, "Wrong unloaded error"); }
        std::cout << "{\"test\":\"host_unload_guard\",\"passed\":true}" << std::endl;
    } catch (const std::exception & e) { std::cerr << e.what() << std::endl; return 1; }
}
