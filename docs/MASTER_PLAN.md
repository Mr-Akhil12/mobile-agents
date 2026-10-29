<!-- Authoritative v1.0 specification; a personal tester reference was redacted for this public repository. -->
# LFM — LOCAL-FIRST MOBILE AGENT
## Master Product, Engineering & Delivery Plan

**Document:** `LFM_MASTER_BUILD_PLAN_v1.0_2026-10-28.md`  
**Version:** 1.0  
**Prepared:** Wednesday, 28 October 2026 (SAST, UTC+02:00)  
**Phase 1 target:** Friday, **30 October 2026, 16:00 SAST**  
**Initial devices:** Xiaomi 15T (Android 15/HyperOS) and Huawei Mate 80 Pro (EMUI 15; compatibility validation required)  
**Product type:** Native Android application, offline-first AI assistant with permission-controlled device actions  
**Current status:** Termux proof of concept operational; native APK not yet built  
**Working principle:** Local inference and local user data by default. The internet is an *optional tool*, never a dependency for basic chat or on-device operations.

---

# 1. Product vision and constraints

LFM should feel like a private, installable, ChatGPT-style assistant that lives on the phone, answers by text and voice, remembers relevant conversations, understands intent, chooses tools, and executes **authorized** device actions. A user should **not** need Termux, a terminal, API keys for a hosted LLM, a personal Mac, or a separate backend for ordinary use.

Examples of the intended experience:

- “Hey LFM, turn on Bluetooth, find my car radio, connect if Android permits, and play a song.”
- “LFM, reject that incoming call.”
- “Open my browser and check recent anyPS5 jailbreak progress; tell me what sources you found.”
- “What time is it on my phone?”
- “Compare a Mk6 GTI with a GTI Edition 35, research the engines, and explain the tuning implications.”
- “What was I asking you earlier?”

**Non-negotiables**

1. AI model inference runs **on-device**, without hosted LLM tokens or ongoing AI subscription fees.
2. Offline mode must be real and testable with airplane mode; web research appropriately reports “internet required.”
3. All user memory, logs, conversation history and files are stored locally by default.
4. All device actions use documented OS interfaces where possible, with a permission broker and audit log.
5. Never claim an action succeeded until the Android OS/tool confirms it.
6. No unrestricted shell access, invisible root escalation, bypass of Android security, secret phone monitoring, or silent sensitive actions.
7. The app must install and run without Termux. Termux stays only as a reference/benchmark harness.
8. Support two local models first; add a third only after it demonstrably improves an identified task.
9. Deliver an actual build, source code and test evidence—not mockups described as working functionality.
10. If an Android API cannot perform an action silently, explain the limitation and offer the OS confirmation flow instead.

**Key distinction:** “Fully offline” applies to model inference, memories, and supported device actions. “Research what happened *today* on the internet” inherently needs connectivity; it can still be performed without a paid search API.

---

# 2. Verified starting point — preserve before development

## 2.1 Hardware and software baseline

- **Xiaomi 15T:** on-device Termux runtime, arm64, 12 GB RAM (user-reported device model); Android 15.
- **Huawei Mate 80 Pro:** user reports **EMUI 15.0**; confirm installed Android API level, CPU architecture, microphone/Bluetooth behaviour, and compatibility on the physical unit. Do not assume identical results to Xiaomi or automatic Google Play Services availability.
- **llama.cpp:** compiled successfully under Termux; successful runs observed on 28 October 2026; build identified as `b1-8642541` in logs. Pin an exact working upstream commit for production and document changes.
- **Models currently running locally:**
  - `LFM3-Omni-2B-Q4_K_M.gguf` plus `mmproj-LFM3-Omni-2B-Q8_0.gguf` (multimodal input, output speech, image understanding).
  - `LFM3-1.7B-Q5_K_M.gguf` (reasoning, chat, structured function calls).
- **Local tools:** Termux:API microphone recorder, FFmpeg audio conversion, Android TTS, local Python agent, and a localhost `llama-server` on `127.0.0.1:8089`.

## 2.2 Observed measurements, not guarantees

- 5.248-second microphone recording saved and successfully transcribed by Omni.
- Omni speech generation produced 7.36 seconds of WAV audio in **14.67 seconds** for one test; too slow to require as the default voice in the first APK.
- LFM3-1.7B generated text at roughly **11–13 tokens/s** in CLI tests.
- After moving the text model into a persistent local server, the isolated text stage took around **6 seconds**; the working time-tool cycle took around **7 seconds**. These are **not** end-to-end audio latency benchmarks.
- Tested an actual structured `get_local_time` function call and a completed voice → ASR → reasoning → Python tool → spoken answer round-trip.
- Test uncovered known shortcomings: inaccurate unsupported factual answers, reasoning traces leaking into output, transcript errors, fixed recording/sleep delays, and no persisted conversation history yet.

## 2.3 Reference artifacts to retain

Existing Termux prototype paths **on the user's phone**, not part of this plan file:

```text
~/lfm-talk
~/lfm-agent
~/lfm-answer
~/.lfm-voice/
~/models/lfm3-omni/
~/models/lfm3-agent/
~/llama.cpp/
```

Before moving to native development, archive **scripts, test prompts, command-line options, runtime version, key logs (redact personal content), performance measurements and dependency licenses**. Do not commit private conversation data or GGUF binaries to a public source repository. Android apps cannot directly access Termux's private app directory; use Android's Storage Access Framework or app-managed verified model downloads.

---

# 3. Scope and acceptance across three phases

| Dimension | **Phase 1 — Native working prototype** | **Phase 2 — Capable local agent** | **Phase 3 — System assistant** |
|---|---|---|---|
| Target | **Fri 30 Oct 2026, 16:00 SAST** | November 2026 (scope-gated) | December 2026 onward (device/API-dependent) |
| Install | Installable APK on Xiaomi, no Termux | Versioned releases, first-run downloads, Huawei test | Stable update process, broader Android device matrix |
| AI | Native on-device text reasoning; Omni speech integration target | Persistent two-model routing; smarter tool loops and memory | Optimized inference, optional third specialist model by benchmark |
| Chat | Streaming text, states, errors, conversation log | Search/history/memory, task traces | More polished proactive and contextual assistance |
| Voice | Push-to-talk offline voice *target*, built-in Android TTS | Auto-endpointing, better interruption, full voice loop | User-enabled “Hey LFM”, wake-word/battery/lock-screen testing |
| Actions | Read-only local clock; one or two safe OS actions | Bluetooth, media, notifications, app intents, browsing | Call handling, optional scoped UI automation, deeper workflows |
| Research | Not required; explicit “internet tool not yet available” | No-paid-search-API retrieval and source citations | Research orchestration, optional self-hosted SearXNG or browser service |
| Security | Consent, app-private storage, explicit permissions | Confirmation gates, audit ledger, prompt-injection boundaries | Role-based access, app action profiles, robustness/security hardening |

**Phase 1 is not marketed as a fully hands-free, always-on, unrestricted phone controller.** Its purpose is proving the native architecture and shipping an actually usable APK by the Friday target.

## 3.1 P1 release criteria: three levels, to avoid false success claims

- **P1 minimum deliverable (required):** one installable Xiaomi APK opens a native interface, can import/download and load the **1.7B local model**, answers a prompt with Wi-Fi/mobile data disabled, calls `get_local_time` locally, displays actual tool execution evidence, keeps basic local conversation history, and has no Termux dependency.
- **P1 target deliverable:** the above **plus** one full offline push-to-talk → speech recognition → model/tool → Android TTS interaction in the APK, ideally using Omni JNI multimodal support; no manually run server or terminal commands.
- **P1 stretch:** painless dual-model installer with resume/checksum and meaningful Huawei install testing.

**Hard honesty rule:** If native Omni audio bridging is not ready on Friday, release a **text-first P1 prototype** clearly labeled as such, with the native voice path identified as blocked and a documented workaround. Do not silently use a cloud speech service to meet the offline claim. An alternate **on-device Android SpeechRecognizer** is valid only if `isOnDeviceRecognitionAvailable()` and airplane-mode testing succeed on that phone.

---

# 4. Native application architecture

```text
┌─────────────────────────────────────────────────────────┐
│            LFM Android App — Kotlin/Compose             │
│ Chat • Voice • Models • Tool activity • Permissions      │
└────────────────────────┬────────────────────────────────┘
                         │ ViewModels / coroutines / StateFlow
┌────────────────────────▼────────────────────────────────┐
│                 AgentCoordinator                         │
│ Intent interpretation • context • tool loop • policy     │
│ Prompt templates • budgets • cancellation • tracing      │
└────────────┬───────────────────────┬─────────────────────┘
             │                       │
┌────────────▼────────────┐   ┌──────▼─────────────────────┐
│ NativeInferenceRuntime │   │ ToolRegistry / Permission  │
│ llama.cpp JNI + NDK     │   │ Broker / Action Audit       │
│ LFM3-1.7B: text/tools  │   ├────────────────────────────┤
│ LFM3-Omni: audio/image │   │ clock • battery • apps      │
│ model load scheduling  │   │ Bluetooth • media • calls  │
└────────────┬────────────┘   │ research • notifications   │
             │                └───────────┬────────────────┘
┌────────────▼────────────────────────────▼───────────────┐
│ Room(SQLite): chats, memory, tasks, citations, tool log  │
│ DataStore: preferences • private files: GGUF + assets   │
│ AndroidKeyStore: secrets/keys where applicable           │
└─────────────────────────────────────────────────────────┘
```

## 4.1 Technology decisions

| Layer | Choice | Rationale / caveat |
|---|---|---|
| Android client | **Kotlin + Jetpack Compose** | Native permissions, services, UI and Android OS integration. |
| UI state | ViewModel + StateFlow + coroutines | Cancellation-safe, single source of truth, testable. |
| Inference | **llama.cpp, NDK C++, JNI**; pinned source | Proven local GGUF; build native `arm64-v8a` library; avoid Termux subprocess dependency. |
| Chat model | **LFM3-1.7B Q5_K_M** | User-proven structured function calling, reasoning and text answers. |
| Multimodal model | **LFM3-Omni-2B Q4_K_M** + projector | User-proven microphone ASR and TTS in Termux; JNI Android port is an explicit integration risk. |
| Spoken reply | Android `TextToSpeech` default; Omni optional | Low-latency voice while Omni TTS remains slower. Require offline voice pack for full-offline claim. |
| Speech capture | `AudioRecord`/appropriate Android mic API | Use voice activity detection (VAD) later; no fixed seven-second sleep in production. |
| Storage | Room + DataStore + app-private files | Local chat history, structured event logs, model manifests and settings. |
| Data retrieval | Android networking + HTML extraction / Custom Tabs; optional SearXNG | No compulsory search API keys; online for web search only. |
| Build | Android Studio/Gradle/NDK + GitHub Actions | Reproducible signed test builds and test reports. |
| First distribution | Privately installed APK | Faster device validation; Google Play policy analysis before public distribution. |

**Critical technical checkpoint:** the existing llama.cpp Android sample demonstrates native text inference; it is **not proof** that the sample already exposes the full LFM3-Omni audio encoding/generation pipeline. Multimodal JNI work may be required. Validate this in the first native development block.

## 4.2 Model management

- **First-run states:** no model → choose text-only / dual-model → show sizes, free space and license → import existing GGUF using Android file picker **or** download via HTTPS → verify SHA-256 from a trusted manifest → atomic rename → load → smoke test.
- **Minimum model:** Q5 text ~1.2 GB download; Omni model and projector add roughly 1.6 GB. Reserve additional headroom for native library, cache, context, temporary downloads and database. Display true sizes from actual manifests, not hardcoded marketing numbers.
- **Downloads:** resumable `WorkManager`/user-initiated transfer where OS permits, network preferences (Wi-Fi only), progress, cancel/retry, checksum, low-disk warnings and clean rollback.
- **Memory:** on-device runtime loads the 1.7B model for text/tool calls; Omni loaded when the mic/camera/image path is needed. Simultaneous residency must be benchmarked against RAM, LM KV-cache usage and thermals. Keep `n_ctx` realistic initially (2K–4K per model), despite published larger context capabilities.
- **Update policy:** pin known-good model versions, SHA-256, quantization, projector pairing, chat template and upstream llama.cpp commit; never hot-swap a model mid-task.
- **License:** include LFM Open License v1.0 and attributions; verify business eligibility and third-party dependencies before public/commercial distribution.

## 4.3 Tool-call protocol

The model never executes an Android action directly. It proposes a typed request such as:

```json
{"tool":"get_local_time","arguments":{},"request_id":"..."}
```

The controller validates tool name, JSON schema, current OS permissions, device state, per-tool policy, user authorization and timeouts. It executes a **specific native implementation**, records results, and sends a structured tool-result message back to the model. Allow a small bounded number of iterations (start **max 3 tool calls/turn**, configurable), with clear exceptions and a cancel button.

Tool descriptor contract:

```text
ToolDefinition {
  id, description, schema, permissionsRequired,
  risk: READ_ONLY | REVERSIBLE | SENSITIVE,
  confirmationPolicy, timeoutMs, executor
}
ToolResult {
  requestId, success, data, errorCode, requiresUserAction,
  observedAt, sourceOrEvidence
}
```

**Never add a general `run_shell_command` tool in the shipping app.** The executor is an allowlist of explicit Android actions. Observed read-only results and requested permission flows are distinct from completed action results.

## 4.4 Conversation and personal memory

- Each chat has messages, attachments, source links, tool calls, tool outcomes and timestamps.
- Basic Phase 1 retention: local chats via Room; explicit delete and clear buttons.
- Phase 2: context-window budgeting, recent N turns, pinned user-provided facts, optional on-device summarization, semantic retrieval if benchmarked useful.
- Show provenance: **“You told me”**, **“I read this on your phone”**, **“Web source”**, **“Model's own answer/uncertain.”**
- Do not inject all private context into arbitrary third-party pages or tools. Web queries should be minimal, and user review required for sensitive personal details.
- A user may view, export, edit and delete saved memories and conversation history.

## 4.5 Agent quality and hallucination controls

- Split model reasoning/`reasoning_content` from final text; never TTS raw planning traces.
- Use explicit final-answer parsing, stop conditions and truncated-output detection; no regex-only blanket promises.
- For facts outside local knowledge or time-sensitive claims, call a source tool or state that verification is unavailable.
- If a transcript looks ambiguous (e.g. “Addition 35” vs “Edition 35”), verify/clarify before consequential actions.
- Attach URL/title/access time to web claims; cite retrieved content and distinguish source timestamps from current time.
- Expose tool history and user-facing failure reasons, not hidden success claims.
- Tool output, webpages and notifications are **untrusted data**, never executable instructions for the agent.

---

# 5. Product experience and screen inventory

**Visual direction:** polished, fast, readable, premium dark/light themes; a clean ChatGPT-like conversation layout, not a terminal UI. Focus on one-hand phone operation and an obvious microphone action. No need for fancy animation before functionality.

**Screens (phased):**

1. **Onboarding / permissions:** explain local inference, storage requirement, microphone consent, optional capabilities, and download/import model choices.
2. **Model manager:** model choices and versions, size, health, CPU/RAM estimated vs observed, download state, unload/repair/delete, offline readiness.
3. **Chat home:** message history, streaming tokens, typed input, microphone button, cancel action, source citations and “offline/online” state.
4. **Voice overlay:** waveform / recording indicator, live or post-recording transcript, processing state, spoken response controls. Start as foreground push-to-talk.
5. **Tool activity / approvals:** requested tool, parameters, executing/complete/failed, confirmation and evidence/OS activity links.
6. **Memory:** saved facts, conversation history, controls and delete/export.
7. **Device permissions:** per-tool toggles, OS permission status, Bluetooth/dialer/notification roles with explanations, action policy.
8. **Settings / diagnostics:** voice, TTS engine, wake word (future), reasoning mode, model RAM mode, crash reports (local), benchmark, battery state.
9. **Research panel (Phase 2):** queries, sources, extracted highlights, fetch failures, citations, open in browser.

The app must always show whether it is **listening**, **thinking**, **using a tool**, **waiting for approval**, or **finished**. Microphone capture must have visible indicators and an explicit stop mechanism.

---

# 6. Phase 1 — native APK by Friday afternoon

## 6.1 Strict scope (priority order)

**P0 — mandatory**

- Android Studio project builds reproducibly for `arm64-v8a` and produces an installable debug APK.
- Native app opens a functional chat UI, accepts typed prompts, and renders streamed/on-device answers.
- Local llama.cpp JNI loads `LFM3-1.7B-Q5_K_M.gguf` without Termux, a localhost external server or network.
- First-run model import via the Android file picker **or** app-managed verified download; no manual shell setup.
- `get_local_time` works end-to-end via a structured tool call with actual OS time.
- At least one safe secondary tool, ideally `get_battery_status` or `open_app` via an explicit Android Intent, subject to time.
- Local message history survives app restart (Room); user can clear chat.
- Tool execution status/error surfaces; cancellation and no-crash behaviour.
- Airplane-mode validation of text chat + local time tool.
- APK install on Xiaomi 15T, documented smoke-test recording/screenshots, reproducible source commit.

**P1 — target**

- Push-to-talk input within APK, **offline speech recognition** on Xiaomi, with Omni/JNI path preferred.
- Fallback to Android `createOnDeviceSpeechRecognizer` **only if supported and verified offline on the device**; never silently call a hosted speech service.
- Device Android TTS speaking the answer; require an offline voice or mark TTS as OS-engine dependent.
- If native Omni mic bridge fails in time, preserve a clean API contract and log the failure; text-first APK still ships honestly as minimum P1.
- Dual-model manager showing installed/missing/loaded state and the Omni projector.

**P2 — stretch**

- Settings for fast/standard/deep prompts and token budgets.
- One-click verified model download with resume, progress and checksum.
- A working secondary Huawei APK validation pass; address OEM issues only after Xiaomi release is stable.
- Battery/RAM observation and basic latency instrumentation.

**Excluded until later:** wake word with screen locked, default assistant role, call screening, autonomous Bluetooth A2DP switching, notification access, browser agent, visual UI automation, public app store distribution, account sync, background proactivity, three simultaneous LLMs.

## 6.2 Execution schedule (SAST)

### Wednesday 28 Oct, remaining evening — design and preparation

- [ ] Lock this specification, app/module names, source control strategy and owners.
- [ ] Preserve the working Termux prototype, successful logs and exact model filenames.
- [ ] Create repo and Android Studio skeleton (Kotlin/Compose, version catalog, minimal permissions).
- [ ] Pin a llama.cpp commit known to support **both** model architectures.
- [ ] Spike model import and app-private file access; determine what can be copied with Android Storage Access Framework.
- [ ] Confirm actual Android API level and device free storage; gather Xiaomi baseline temperature and memory.

**Exit:** clean repository skeleton + working Android toolchain + native build proof, or a written blocker.

### Thursday 29 Oct — native inference is the critical path

**Morning / earliest development block:**

- [ ] Compile llama.cpp into Android native library for arm64-v8a (NDK, JNI).
- [ ] Load text GGUF and generate a deterministic smoke-test answer in app or instrumented harness.
- [ ] Check CPU features and load time; ensure prompt formatting/stop sequences are correct.

**Midday / next block:**

- [ ] Implement Compose chat, streaming state, error and cancel.
- [ ] Add typed tools `get_local_time` and `get_battery_status` (or `open_app`).
- [ ] Enforce tool allowlist, loop budget and tool-result feedback.
- [ ] Persist a conversation in Room.

**Thursday evening technical gate:**

- [ ] Attempt Omni model/projector loading and native microphone/audio pipeline.
- [ ] If multimodal JNI integration is blocked, verify on-device Android SpeechRecognizer fallback in airplane mode.
- [ ] Decide **P1 voice yes/no** based on a real test, not estimated ease.
- [ ] Cut polish or non-core features if necessary; protect P0 APK delivery.

**Exit:** Xiaomi debug APK loads the local 1.7B model and executes a real tool. Voice architecture decision recorded.

### Friday 30 Oct — integrate, validate, deliver by 16:00

- [ ] Finish chat/voice UI, graceful errors, local storage, action activity panel.
- [ ] Test airplane-mode chat, cold launch, screen lock/unlock, microphone lifecycle, restart and refusal paths.
- [ ] Test memory usage, thermal stability, battery impact; ensure no destructive action tool exposed.
- [ ] Fix P0 issues; do not start P2 features after the freeze.
- [ ] Build final **debug/privately installable APK** and verify installation on Xiaomi 15T.
- [ ] Commit/tag source (e.g. `v0.1.0-p1`), record exact build SHA and dependency versions.
- [ ] Deliver APK, README/installation steps, source reference, short demonstration, test evidence, known limitations and Phase 2 backlog.

**Target handoff: Friday 30 October 2026 by 16:00 SAST.**

### Friday release gate

**GO:** installed APK; local model runs offline; typed prompt and real local tool succeed; no Termux; no uncontrolled permissions; launch and basic state restoration stable.  
**GO with explicitly documented limitation:** voice is unavailable or text-only, but P0 criteria pass.  
**NO-GO:** no native model inference, cloud inference hidden as “offline,” app requires Termux, misrepresents actions, or has unapproved sensitive permissions.

The schedule is aggressive and depends on available development time and an Android SDK/NDK-capable machine; the existence of the Termux prototype does **not** mean the native application is already complete.

---

# 7. Phase 2 — genuine on-device agent, November 2026

## Goals

Build out the daily-driver assistant. Make the app useful beyond a demonstration, without purchasing AI or search API credits.

### Workstream A: memory and model orchestration

- Full conversation continuation, context compaction, pinned facts, retrieval from local SQLite.
- Explicit role templates: **Quick** (small response), **Standard** (tool-aware), **Research** (retrieve/cite), **Deep** (bounded multi-step reasoning).
- Local model loading policy: 1.7B usually warm, Omni on demand; benchmark model co-residency; automatic unload under memory pressure.
- Reasoning output separate from final answer; no cutoff, accidental TTS of hidden reasoning or invented tool results.
- User-visible conversation and task audit; delete/export memory.

### Workstream B: self-controlled online research without Tavily credits

- Search abstraction: `search_web(query) -> list<SearchResult>`; `fetch_page(url)`; `extract_article(url)`; `open_in_browser(url)`.
- First provider path: opt-in **device-side browser search via supported interfaces and ordinary page retrieval**, with source links, domain filters, timeout/rate limiting, caching and user-visible browser fallback. This is **not guaranteed** to work unrestricted on every search engine; don't bypass CAPTCHAs or platform access controls.
- Optional second path: **user-hosted SearXNG** on an available machine. No paid search API requirement, but home-host reachability, provider rate limiting and power availability must be disclosed.
- Optional remote browser service only as an add-on; the agent must still answer offline when web tools are unavailable.
- Cite source title, canonical URL, retrieved/published date where known, and confidence. Never summarize live “today” news from stale cache as if current.
- Tool outputs are untrusted; protect against webpage instructions to “send emails,” “run commands,” etc.

### Workstream C: native Android actions

**Read-only / simple:** current time/date/timezone, battery/power, network/Bluetooth state, available paired devices, application listing within Android package-visibility rules, notification summary after explicit listener grant, clipboard only when allowed by OS/user action, open URL/app via Intent.

**Reversible / request confirmation:** Bluetooth enable through `ACTION_REQUEST_ENABLE`, scan with `BLUETOOTH_SCAN`, open pairing or system Bluetooth settings, play/pause/skip using authorized media sessions, volume via permitted audio APIs, launch requested music app or its supported search/deep links.

**Sensitive / explicit confirmation:** sending messages, deleting files, initiating calls, accepting/rejecting calls, exporting private data, modifying account settings. If the requested API is not available, ask user to complete the OS flow rather than claiming success.

**Bluetooth car-radio scenario:**

1. User: “Connect to my Pioneer radio and play [song].”
2. Agent checks Bluetooth permission/state and paired devices; asks to enable Bluetooth through system UI if off.
3. Identifies exact bonded radio, **does not assume it can programmatically force a classic A2DP connection** as a third-party app; falls back to Android device switcher/Bluetooth settings if needed.
4. After connection is confirmed by OS routing/session state, invokes a permitted music app intent or media control; playback of an arbitrary requested title depends on the music app's integrations, catalog and any subscription.
5. Reports which steps succeeded, require user input or failed.

### Workstream D: voice experience

- Push-to-talk with VAD/end-of-speech detection (avoid fixed six-second recording + seven-second wait).
- Interrupt/cancel answer and playback; handling Bluetooth microphones and speaker routing.
- Verify offline Android TTS voice on both devices; optional Omni voice for quality when user accepts slower output.
- Proper mic/media focus; stop on phone call, permission revocation or privacy mode.
- Better ASR corrections for names/technical terms, e.g. “Edition 35” / “anyPS5”, with confirmation when intent is unclear.

### Workstream E: Huawei compatibility

- Test actual API level, available device speech recognizer, Harmony/EMUI permission behaviour, background lifecycle and Bluetooth controls.
- Avoid Google Play Services hard dependency; run exact airplane-mode test on the Huawei tester’s handset.
- Resolve OEM-specific behaviours only after evidence from on-device QA.

**Phase 2 completion criteria:** One device can sustain ten useful, consecutive voice/tool turns with context, execute at least five approved tool categories, produce sourced answers online without a paid search subscription, and maintain offline chat/time/media functions with network disabled. Huawei passes core chat/function tests or limitations are explicitly documented.

---

# 8. Phase 3 — “Hey LFM” and deeper system integration

## Goals

Make LFM operate like a real user-selected voice assistant, with a robust on-device wake word and better OS workflows, while respecting Android's role and permission boundaries.

### Workstream A: user-enabled wake word

- Integrate a small local wake-word model for “Hey LFM”; never run Omni continuously just to detect the keyword.
- Explore Android `VoiceInteractionService` and user-selected assistant role. Keep service lightweight.
- Screen-on / screen-off / lock-screen behaviour tested separately on Xiaomi and Huawei; OEM/hardware low-power DSP integration **cannot be assumed**.
- Explicit opt-in to always-listening, persistent notification/indicator where required, recording cutoff, privacy mode, battery profiling, and no accidental background streaming.
- Use manual microphone button, headset action or a notification action as dependable fallbacks where wake word is restricted.

### Workstream B: call handling

- For **screening an incoming call before it rings**, user grants the call-screening role and the app handles it through `CallScreeningService`, with Android's short response deadline. Safety/latency-critical logic must be deterministic, **not** dependent on several seconds of LLM inference.
- For **rejecting a call already ringing**, investigate default dialer / `InCallService` route and exact Android/OEM capabilities. Do not promise third-party roleless control.
- Clarify whether user is screening a suspected spam caller, declining this specific call, or managing an active call; require appropriate permission/role and confirmation policy.

### Workstream C: visual/screen agent

- Add screenshot/vision inputs only with user permissions/consent and Android-appropriate APIs; prioritize accessibility **hierarchy** and native app intents over coordinate guessing.
- Evaluate dedicated LFM3-VL as a **third model only if** a benchmark on real phone tasks proves Omni vision insufficient.
- Device UI automation through explicit user-enabled accessibility service only where justified and legally/platform-permissible. Test protected screens, app version changes, failures and dangerous actions; no silent accessibility control assumptions.
- Prompt injection resistant: screen/web content cannot override tool policy or authorize actions.

### Workstream D: agentic routines and advanced workflows

- Multi-step task plan → tool calls → verification → response; visible task timeline and cancel/resume.
- User-defined routines such as “Car mode” (Bluetooth state, device route, media session), “Work mode” or “Check the latest on [subject].”
- Offline memory and tool policies per device, with export/import via encrypted files rather than a mandatory cloud account.
- Plugin/provider architecture for other apps only where authorized APIs/integrations exist. No hidden credential scraping.
- Add optional sync/remote services only by informed opt-in; separate the user-facing “offline inference” promise from network-requiring services.

### Phase 3 completion criteria

- Demonstrate on both phones either a supported wake-word activation or a clearly explained OEM-limited fallback, with measured idle battery use.
- Complete at least three real multi-tool workflows (e.g. Bluetooth check → pairing/UI → media; live research → citations; call-screening user flow) with actual success verification.
- No unauthorized sensitive action; documented failure handling when OS denies privileges or requests background permission.

---

# 9. Permissions, security and privacy matrix

| Capability | Android API/approach | Permission / role / user interaction | Guarantee level |
|---|---|---|---|
| Local offline text | Native library, app-private model | No internet permission for inference | **Core, mandatory** |
| Microphone | AudioRecord / on-device recognition | `RECORD_AUDIO`; while-in-use/FGS policy | **Native audio gated by implementation** |
| Spoken response | Android TextToSpeech | Installed voice engine/offline voice | **Depends on device voice pack** |
| Bluetooth status and devices | BluetoothAdapter and device APIs | `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` where needed | **Supported with permission** |
| Turn on Bluetooth | `ACTION_REQUEST_ENABLE` | User system confirmation | **Cannot silently assume** |
| Connect classic A2DP radio | System device routing / UI | Already paired; device and OEM-specific flows | **Not guaranteed as public direct API** |
| Media controls | MediaSession/MediaController | Authorized session / possibly enabled notification listener | **App/service-dependent** |
| Open app/URL | Android Intent, browser Custom Tabs | Package visibility, installed handler | **Generally available, not full UI automation** |
| Notifications | NotificationListenerService | Explicit notification access grant | **Opt-in only** |
| Call screening | CallScreeningService | User-selected screening role; response deadline | **Conditional OS support** |
| Reject already-ringing call | Default dialer/InCallService investigation | Role and OEM requirements | **Must validate** |
| Screen inspection | Accessibility tree / user-consented MediaProjection | Explicit accessibility/projection permission | **Restricted; sensitive screens may block** |
| Background wake word | VoiceInteractionService / permitted mic FGS | User-selected assistant, OS/OEM limits | **Research and benchmark required** |
| Web research | HTTPS networking / browser | Internet connectivity; source rate limits | **Not offline** |

**Security policies:** least privilege, deny by default, per-tool user toggles, confirmation for irreversible/sensitive requests, tool audit log, cancel button, no auto-send, no unencrypted sensitive sync, UI clearly indicates microphone and background activity, deletion/export flows, and protection against prompt injection from web and notifications. Keys (if an optional user-hosted service is configured) stay in secure app storage and are never committed to source.

**Distribution:** sideload for the private prototype. Before public store release, complete an additional Google Play and Huawei app-store compliance review, particularly for Accessibility, call handling, default assistant roles, background recording and permissions. Private distribution removes store listing review, **not** Android platform security boundaries.

---

# 10. Project repository and module structure

Proposed repository name: **`lfm-mobile-agent`** (placeholder; confirm before repo creation).

```text
lfm-mobile-agent/
├── README.md
├── docs/
│   ├── MASTER_PLAN.md                    # this document
│   ├── architecture.md
│   ├── android-permissions.md
│   ├── native-runtime.md
│   ├── qa-checklist.md
│   ├── risk-register.md
│   ├── device-benchmarks.md
│   └── decisions/ADR-0001-native-kotlin-llama.md
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/.../lfm/
│       │   ├── ui/{chat,voice,models,settings,tools}/
│       │   ├── agent/{AgentCoordinator,ToolRegistry,Policies}/
│       │   ├── inference/{InferenceRepository,ModelRouter}/
│       │   ├── audio/{Recorder,SpeechInput,Speaker}/
│       │   ├── tools/{ClockTool,BatteryTool,AppTool}/
│       │   ├── data/{Room,DataStore,Repositories}/
│       │   └── permissions/
│       └── res/
├── inference-native/
│   ├── CMakeLists.txt
│   ├── src/main/cpp/
│   └── src/main/java/.../NativeInference.kt
├── model-manifests/
│   └── lfm3-models.example.json
├── tests/{unit,instrumented,manual}/
├── benchmarks/
├── .github/workflows/{android-ci.yml,release-test-apk.yml}
├── THIRD_PARTY_NOTICES.md
├── LICENSES/
└── gradle/...
```

**Source-control rules:** short feature branches/PRs; build must pass; no binaries, private messages, conversation database, API keys, or local `searxng` secrets committed; commit pinned third-party dependency licenses and attribution files. Tag the Friday verified APK. Consider a GitHub Actions artifact for test APKs, **not** a public release carrying user data.

---

# 11. Test and benchmark plan

## 11.1 Phase 1 smoke tests

| ID | Scenario | Pass condition |
|---|---|---|
| T01 | Fresh install and open | No crash; onboarding visible |
| T02 | Import/download GGUF | Correct path; SHA verified; clear progress/error |
| T03 | Airplane mode chat | Local response returns with all networking disabled |
| T04 | Time-tool round-trip | Model requests allowed tool and reports OS-observed time |
| T05 | Negative tool request | Unknown/dangerous tool denied, no execution |
| T06 | Cancel generating answer | Generation stops; app remains responsive |
| T07 | Restart app | History persists or retention policy clearly configured |
| T08 | Close/reopen model | No corrupted session; graceful reload |
| T09 | Microphone permission denied | No crash; transparent fallback/error |
| T10 | Spoken question (target) | Genuine offline ASR and TTS or explicit P1 limitation |
| T11 | Tool request with missing permission | Shows OS permission/user action; never claims success |
| T12 | Low storage / corrupt model | Recoverable error and remove/retry |
| T13 | Five consecutive turns | No runaway memory, leaked reasoning or invalid JSON loop |
| T14 | App vs Termux dependency | Full P1 workflow runs after Termux closed; APK installs without Termux |
| T15 | Source/build audit | Exact commit, APK hash and known limits recorded |

## 11.2 Metrics to measure, not assume

- **Cold load:** from open app to text model ready, target measured baseline.
- **Warm first token:** voice/text request to first visible token; sample median and p95.
- **Text tokens/s:** compare to observed CLI baseline (~11–13 t/s).
- **Speech:** mic-start, detection/end-of-utterance, ASR latency, text/tool latency, TTS start, full end-to-end time.
- **RAM and thermals:** idle model, active inference, Omni swap, repeated sessions.
- **Battery:** 10-minute active use and future 30-minute wake-listener test (Phase 3).
- **Correctness:** 10 scripted local questions + 10 tool test cases; include known failure examples: GTI false specifications, truncated thought output, “Addition/Edition 35,” unverified “today” claims, and KFC fictional menu item.
- **Stability:** no app crash, deadlock, runaway tool loops or unbounded model memory after 10-turn session.

## 11.3 Definition of “working”

A feature is **working** only when demonstrated on the target physical phone with recorded input, visible output and verified OS/tool evidence. Compiling source, producing a valid JSON tool request, or showing a UI mockup does not alone constitute delivered functionality.

---

# 12. Risk register and mitigations

| Risk | Likelihood / impact | Mitigation / decision gate |
|---|---|---|
| Native Omni audio JNI not ready by Friday | High / High | First-day spike; on-device Android ASR fallback if verified; ship text-first P1 honestly |
| Build toolchain/setup slows delivery | Medium / High | Use official Android sample, pin NDK, minimize dependencies, build text model first |
| Both models exhaust RAM / thermal budget | Medium / High | Lazy-load Omni, cap context, measure RSS, unload on pressure |
| 1.7B invents facts | High / High | Tool grounding, proof requirements, clear uncertainty, source citations in Phase 2 |
| Tool calling parses incorrectly | Medium / High | Strict schema/allowlist, synthetic tests, max tool calls, log failures |
| Huawei EMUI has different speech/permission stack | High / Medium | Xiaomi-first release; real Huawei device tests in Phase 2 |
| Screen-off hotword blocked or drains battery | High / High | Phase 3 only; user-selected assistant role, low-power detector, explicit fallback |
| Browser search rate-limited/CAPTCHA | Medium / Medium | Browser-open fallback, caching, permitted sources, optional self-hosted SearXNG |
| Background service killed by HyperOS/EMUI | High / Medium | Appropriate service lifecycle, user battery-settings guidance, fast resume |
| Sensitive data exposure/tool misuse | Medium / Critical | Per-tool approvals, prompt injection boundaries, local storage, export/delete, no arbitrary shell |
| “Full control” expectation beyond public Android APIs | High / High | Capability-by-capability truth table, no silent Bluetooth/reject-call guarantees |
| Friday time constraint | High / High | P0 must ship first; explicit voice gate Thursday; defer Phase 2 features |
| Package size and mobile downloads | Medium / Medium | Small APK + model download/import, Wi-Fi only option, verified resume, free space checks |
| App/model redistribution licensing | Medium / High | Include LFM Open License notices, check business revenue threshold and upstream licenses |

---

# 13. Decisions, assumptions, and open checkpoints

## Locked for initial implementation

1. **Native Android app**, not a PWA shell or Termux front end.
2. **Kotlin/Compose + native llama.cpp JNI** for phase 1.
3. **LFM3-1.7B** is the local text/tool model; **LFM3-Omni-2B** for ASR/image/speech as engineering feasibility allows.
4. **Android TTS is the first default** for low-latency spoken answers; Omni voice optional.
5. **No mandatory paid LLM or search API.** No always-running user-owned Mac/server requirement.
6. **No third model until benchmarked need** (not just to inflate model count).
7. **Private sideloaded APK** for the first prototype; explicit user permissions and hard safety boundaries.
8. **Xiaomi first** for the Friday deadline; Huawei target after compatibility testing.

## To confirm at implementation start (not blockers to writing the plan)

- Final repository location and Android app package ID.
- Development machine/Android SDK/NDK versions available; device USB debugging or wireless ADB workflow.
- Whether Friday's tester can personally install the APK before 16:00 SAST.
- Physical Huawei API level and ability to sideload test APK.
- Omni audio JNI path versus verified offline Android ASR fallback.
- Desired initial UI theme and app icon; these should not delay P0.

---

# 14. Immediate next development actions

**A. Preserve and tag the prototype.** Archive only scripts, working version/build settings, tool-call JSON and redacted performance logs. Record the actual SHA-256 of each local model for future model manifests. No need to delete any currently working phone files.

**B. Stand up the native project.** Android Studio + Gradle + Compose; start from llama.cpp Android sample; build a minimal GGUF chooser with streaming local text generation.

**C. Implement the first safe native tool.** Re-create `get_local_time` in Kotlin and demonstrate exactly the round trip already proven in Termux; include action-state UI.

**D. Add memory and voice in priority order.** Room chat storage; then mic/Omni port spike; fallback only to verified on-device ASR; then offline TTS.

**E. Produce release evidence on Friday.** APK + source commit/tag + checklists + demo + known limitations. Do not label an untested feature working.

---

# 15. Reference documentation (reviewed 28 Oct 2026)

1. **LFM3-1.7B model card (reasoning, tool calling, limits and license):** https://huggingface.co/LiquidAI/LFM3-1.7B
2. **LFM3-Omni-2B model card (multimodal specifications, non-reasoning):** https://huggingface.co/LiquidAI/LFM3-Omni-2B
3. **Omni GGUF use with llama.cpp:** https://huggingface.co/LiquidAI/LFM3-Omni-2B-GGUF
4. **Liquid AI open model license:** https://www.liquid.ai/lfm-license
5. **Official Android llama.cpp sample:** https://github.com/ggml-org/llama.cpp/tree/master/examples/llama.android
6. **llama.cpp native Android build guidance:** https://github.com/ggml-org/llama.cpp/blob/master/docs/build.md
7. **Android app architecture recommendations:** https://developer.android.com/topic/architecture/recommendations
8. **On-device Android speech recognition API:** https://developer.android.com/reference/android/speech/SpeechRecognizer
9. **Android BluetoothAdapter / enable confirmation:** https://developer.android.com/reference/android/bluetooth/BluetoothAdapter
10. **Android Bluetooth runtime permissions:** https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
11. **Android media-session management:** https://developer.android.com/reference/android/media/session/MediaSessionManager
12. **Android call-screening constraints:** https://developer.android.com/reference/android/telecom/CallScreeningService
13. **Android VoiceInteractionService / assistant role:** https://developer.android.com/reference/android/service/voice/VoiceInteractionService
14. **Android microphone foreground-service restrictions:** https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
15. **SearXNG web search interface:** https://docs.searxng.org/dev/search_api.html

**Important:** API documentation evolves. Re-check target SDK, OEM limitations, Play policies and tool interfaces during implementation rather than assuming this document grants special Android privileges.

---

# 16. Project success statement

By Phase 1, LFM should be a **real APK**, locally running at least the reasoning model, independently calling a safe Android tool and not relying on Termux. By Phase 2, it should be a useful, private assistant with conversation memory, voice, approved device actions and source-grounded internet research. By Phase 3, it should be an opt-in, wake-word-activated system companion with deeper **supported** phone capabilities and clear boundaries where Android or an app does not allow direct control.

**Build philosophy:** prove every capability with evidence; preserve what works; optimize the bottleneck actually measured; never trade privacy or truthful status reporting for an impressive demo.
