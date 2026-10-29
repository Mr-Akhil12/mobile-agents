Implements the first native text prototype for the 30 October 2026, 16:00 SAST milestone. The app imports and verifies the pinned local LFM3-1.7B GGUF, streams llama.cpp JNI inference, stores local Room history, and handles bounded allowlisted Android clock/battery tool calls with visible evidence.

Includes pinned Android CI, dependency locks, runtime/model licenses, and a Xiaomi installation/results handoff. The APK has no Internet or microphone permission; Omni voice remains gated behind text/device validation.

Validation: the original handoff records a successful arm64 APK build, seven passing policy tests, lint with zero errors, signature/alignment checks, and real-model Linux host tool/cancellation tests. The continuation independently verified all bundled checksums and restored the exact implementation commit/tree and llama.cpp pin. These are not Xiaomi results. Check this PR's actual Actions results separately; no CI success is asserted by this description.

Physical Xiaomi install, JNI inference, airplane-mode chat, Android time/battery, cancellation, restart/history and performance remain pending. See docs/device-validation-handoff.md and docs/device-validation-results.md. Do not mark the P1 release device-verified or advance Omni until the text gate is reviewed.

Source restoration: bundled implementation `bd2c04099a8347ebed868d39852b4ea1bbc12fd4` maps to remote `c26114fb55f94be81cf098fbccd38fc0d2c6ddf6`, with identical tree `07d03d5b62f5665f50ea9bc1d28f612c2f75e8fe`. Later commits add handoff documentation. The included pre-CI APK remains tied to the bundled implementation; CI produces its own candidate and evidence.
