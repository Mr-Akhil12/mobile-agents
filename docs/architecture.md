# Architecture

`app` owns Compose, ViewModel/StateFlow, Room, model import, agent policy and Android tools. `inference-native` owns JNI and a portable C++ Runtime linked statically against pinned llama.cpp and its common chat/Jinja/parser implementation. No server or subprocess is launched.

One process-wide coroutine dispatcher serializes model operations. A native mutex guards context ownership; a separate atomic abort flag interrupts llama_decode and token generation. The ViewModel exposes busy/loading/using-tool/error/finished states; import and generation support cancellation. Native context is 4096 tokens, 256 batch, CPU only, up to four threads. Whole recent completed turns are replayed; oversized prompts fail visibly. No inference survives a killed process.

Room stores user, assistant, tool-request and tool-result rows. A UUID groups each turn; incomplete/error turns are excluded from future model context. Transient reasoning is available to the active tool loop but removed from final assistant context; no raw reasoning is rendered by design. The upstream parser separates reasoning and tool calls; an additional conservative answer guard rejects known leaked protocol/planning markers. This is not a proof that all natural-language reasoning leakage is detectable.

Model import hashes during copy, bounds size/free space and commits only a matching official model with atomic rename. No network permission is needed. Cancellation leaves no accepted partial model. Importing a replacement unloads the current model first. Users can unload/delete the model and clear local history separately.

The controller validates allowlisted tool names and exact empty-object arguments. Clock uses ZonedDateTime/Android OS clock; battery uses ACTION_BATTERY_CHANGED. Each execution records request ID, success/error, timestamp and source evidence. Tool data feeds the native model as a tool message. Model output cannot bypass ToolRegistry.

Later phases add DataStore preferences, multi-chat navigation, model swapping, Omni audio, TTS, richer permission policy and opt-in research. They are outside this first text milestone.
