# LFM Mobile Agent

Native Android assistant using Kotlin, Jetpack Compose, C++, and llama.cpp. The authoritative scope is [Master Build Plan v1.0](docs/MASTER_PLAN.md). Phase 1 target: **30 October 2026, 16:00 SAST**.

This milestone implements text chat, local Room history, verified GGUF import, streamed native inference, cancellation, and allowlisted Android clock/battery tools. **Physical Xiaomi 15T testing is pending.** Compilation or host inference does not prove Android runtime functionality. Voice is not enabled in this milestone; see [native integration status](docs/native-runtime.md).

## Install on Xiaomi 15T

For the exact verified CI candidate, start with the [device-validation handoff](docs/device-validation-handoff.md) and [blank results sheet](docs/device-validation-results.md). [Latest evidence](docs/CONTINUATION.md): 18 real-model Android x86_64 emulator checks and 11 policy tests passed; ARM64 build/lint passed. Physical Xiaomi acceptance remains pending.

1. Download the `lfm-arm64-debug-<commit>` ZIP from the repository's **Actions > Android native build > successful run > Artifacts**. Extract `app-debug.apk`. Builds are debug-signed test APKs, not store releases. A CI artifact exists only after the workflow actually succeeds.
2. Transfer the APK to your phone, open it in Files, and allow that app to install unknown apps when Android asks. No root, Termux, login, backend, or API key is needed. Android 9/API 28+ and arm64 are required; Xiaomi runtime remains unverified.
3. Open **LFM > Models > Model license**, review the terms, then **Official model download**. Download `LFM3-1.7B-Q5_K_M.gguf` from the pinned LiquidAI revision. You may instead copy this exact file from your existing model collection to Downloads via Android's file picker. The app cannot access Termux private storage directly.
4. Keep at least **1.5 GB free app storage in addition to the downloaded GGUF**. Choose **Import GGUF**. The app copies the file to private storage, reports progress, checks size/SHA-256, atomically installs it, and loads it. Other model files/quantizations are intentionally rejected in this first pinned milestone.
5. Enable airplane mode and turn off Wi-Fi. Send `Say hello in one sentence`, then `What time is it on my phone?`. A real time request must produce an Android tool-evidence card and a model follow-up. A model answer without an evidence card does not count as tool execution.
6. Ask a follow-up question, cancel a generation, close/reopen the app, check retained history, then use **Clear chat** to delete it. Complete [the device checklist](docs/qa-checklist.md); do not mark anything verified without observing it.

Optional USB install: `adb install -r app-debug.apk`. If Android reports an incompatible signing certificate, do not uninstall blindly: uninstalling deletes app data and its imported model. CI debug signing keys are ephemeral, so APKs from separate clean jobs may require uninstall/reimport. Stable private test signing is a later task; no signing keys are committed.

## Build

Install JDK 17 and Android SDK, accept the Android SDK licenses, then:

```sh
git clone --recurse-submodules https://github.com/Mr-Akhil12/mobile-agents.git
cd mobile-agents
# Latest text candidate and emulator validation PR:
git checkout feature/emulator-validation
git submodule update --init --recursive
sdkmanager 'platforms;android-35' 'build-tools;35.0.0' 'ndk;28.0.13004108' 'cmake;3.22.1'
# Set ANDROID_HOME or create local.properties with your sdk.dir.
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Versions are pinned in the version catalog, Gradle wrapper, native module, and llama.cpp submodule. The wrapper verifies the distribution checksum; app/native/settings lockfiles pin the resolved dependencies. See [build/test evidence](docs/build-evidence.md) for actual outcomes and limits. Debug keys/timestamps differ across clean machines; this is a pinned, repeatable build procedure, not a claim of byte-identical APKs.

## Privacy and tool policy

The APK has **no Internet or microphone permission** in this text milestone. Browser model/license links open a separate Android app. No hosted inference, paid search, web research, arbitrary shell, cloud speech, sensitive actions, analytics, or mandatory backend is present. Messages and tool evidence live in app-private Room storage; Android backups are disabled. Clear chat deletes Room rows; uninstall clears app data. This is not a forensic secure-erasure guarantee. Recent completed turns form model context; context overflow produces an explicit error rather than silently truncating a tool cycle.

A model can request only `get_local_time` or `get_battery_status`, with empty JSON arguments. Unknown tools and extra arguments return failures. At most three tool calls execute per turn. Only OS-observed results become evidence. Model answers can be inaccurate; this prototype cannot verify live web facts.

## Licenses

Project source: Apache-2.0. llama.cpp/ggml: MIT. Models are not bundled or redistributed. LiquidAI models use **LFM Open License v1.0**, including commercial-use conditions at annual revenue of USD 10 million or more. Review the full license, not just this summary. See [third-party notices](THIRD_PARTY_NOTICES.md) and bundled license assets. Public store/commercial distribution remains a separate review gate.
