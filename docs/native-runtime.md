# Native runtime and voice checkpoint

Pinned llama.cpp: `522021325546ea322b0b1e58a50f9dbf03a5f197` (upstream snapshot retrieved 28 October 2026). This revision contains `LLM_ARCH_LFM3`, LFM3 chat-template detection, PEG/autoparser tooling and LFM3 Omni multimodal code. It is newer than the user-reported `8642541`; target-device compatibility is not yet established.

The bridge uses `common_chat_templates_init`, `common_chat_session`, native grammar-aware sampling and `to_json_oaicompat`. Chat/template parts tokenize user input without treating it as special template syntax. Generated tool calls are parsed by upstream, not guessed from natural language. Temperature 0.1, top-k 50, repeat penalty 1.1, seed 42, maximum 512 generated tokens. Token-limit exhaustion is an error; raw partial output is never stored as a completed answer. Generation uses CPU baseline arm64 without runtime network access, OpenMP, GPU or KleidiAI downloads.

NDK 28.0.13004108; CMake 3.22.1; API 28 minimum; CPU-only `arm64-v8a`; shared JNI library uses 16 KB ELF page alignment. Native dependencies are statically linked; the Android C++ runtime is supplied by the NDK. Model is user-provided and checksum-pinned separately.

## Omni investigation: incomplete integration, no fabricated voice status

Source inspection confirms `tools/mtmd/mtmd.h` exposes `mtmd_support_audio`, `mtmd_get_audio_sample_rate` and `mtmd_bitmap_init_from_audio` for float PCM. Multimodal prompt/chunk encoding and decode require mtmd plus the matching Omni projector and model, not just this text bridge. `tools/mtmd` and the TTS tools contain LFM3 audio code; Android sample text JNI is not an end-to-end Omni microphone pipeline.

This milestone has **not** built an Android Omni bridge, imported its model/projector, captured microphone PCM, decoded a real utterance or validated memory/model swaps. That unfinished integration is the voice blocker. Source API availability does not establish Android compiler support or device performance. Voice buttons, cloud ASR and fake transcriptions are deliberately absent. No on-device Android SpeechRecognizer fallback is claimed: availability and airplane-mode operation have not been tested on the Xiaomi.

Next gate after text/device smoke: build mtmd target for arm64; import/checksum Omni+projector; capture foreground push-to-talk AudioRecord PCM at runtime-declared sample rate; encode multimodal chunks; decode transcript; release Omni before loading text if RAM requires; run AgentCoordinator; Android TTS with a verified offline voice. Do not infer offline speech support from installed system services. No fixed sleep or Termux API is part of the APK.

## Reference prototype

The attached three scripts were inspected in full. Their key behaviors retained are strict empty arguments, real clock evidence, bounded tool calls, low-temperature text generation, rejection of truncated/planning output, and feedback of tool results. Termux, FFmpeg, shell tools and localhost llama-server remain reference-only; none is an APK dependency. Private logs and GGUF weights are excluded from source control.
