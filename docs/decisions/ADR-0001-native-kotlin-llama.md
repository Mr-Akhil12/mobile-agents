# ADR 0001: Native text-first Android architecture

Accepted 28 October 2026, per master plan and user direction.

Use Kotlin/Compose with Room and a C++ JNI bridge to a pinned llama.cpp source snapshot. Do not embed a terminal, run Termux, launch a server or depend on a cloud API. Import one pinned official text GGUF through SAF; model management and inference are separate owners. First tools are read-only clock and battery. No advanced actions or voice facade precede a real text APK.

Use upstream template/parsing/grammar infrastructure instead of duplicating LFM3 XML tool syntax. Keep a portable native core for host inference smoke checks and an Android-specific JNI shim. Host checks cannot establish physical device acceptance. Omni audio requires a separate mtmd integration gate.
