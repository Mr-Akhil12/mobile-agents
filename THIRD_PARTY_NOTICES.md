# Third-party notices and distribution gate

- llama.cpp and ggml (ggml-org), pinned submodule revision recorded in native-runtime.md: MIT. Original copyright/permission notices are retained in source and APK assets.
- Kotlin and kotlinx.coroutines: Apache-2.0. AndroidX Compose, Activity, Lifecycle and Room: Apache-2.0. Apache license is included in APK assets.
- Native bundled dependency licenses from llama.cpp/licenses are included unchanged in APK assets, including nlohmann JSON, cpp-httplib and other upstream third-party notices. The native build disables OpenSSL, OpenMP and KleidiAI; these optional components are not intentionally linked.
- Gradle wrapper: Apache-2.0; the wrapper JAR and distribution checksum are pinned. Android build tools/NDK are build-time dependencies governed by their respective SDK licenses; SDK acceptance is required on the build machine.
- LFM3-1.7B/LFM3-Omni-2B: Liquid AI, Inc., LFM Open License v1.0. Models are downloaded/imported separately and are not present in this repository or APK. Review full terms, attribution obligations and commercial threshold (annual revenue USD 10 million or more). No business eligibility has been assumed. The shared model license is bundled for review; model/projector weights are imported separately, never bundled.

License review for this development milestone found no copyleft requirement in the chosen app/runtime dependency licenses that would prevent this Apache-2.0 project. This is not a completed public-store/commercial distribution clearance: transitive resolved dependency notices, optional future audio components, commercial model eligibility and store policies must be reviewed again for the exact release.

Full license copies are under app/src/main/assets/licenses and in the pinned upstream source. No model weights or user logs are redistributed.

Omni voice pins: LiquidAI/LFM3-Omni-2B-GGUF revision 2918dd47cfceb4f260cd1734cd1aac1b22bbe4b6, model Q4_K_M plus paired Q8_0 projector. Its pinned LICENSE was fetched and byte-compared (ignoring outer whitespace) with the bundled LFM Open License v1.0: identical, 10,574 bytes. llama.cpp mtmd uses the same pinned source and retained third-party licenses. CI synthetic speech uses runner-installed espeak-ng; it is not embedded in the APK.

Native mtmd additionally links miniaudio (MIT No Attribution / Unlicense), stb_image (MIT / Unlicense) and the subprocess header interface (Unlicense; subprocess support disabled). Their original license blocks are copied from the exact pinned headers into APK assets, alongside existing hash/JSON/llama notices.
