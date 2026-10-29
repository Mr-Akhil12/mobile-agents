# Xiaomi text-first validation handoff

Deadline: **Friday 30 October 2026, 16:00 SAST**. The user reports the first APK worked perfectly on Xiaomi 15T using an existing Termux-downloaded model. Exact installed hash and individual acceptance observations are still unconfirmed; see the results record. Keep Omni voice integration gated until the native text smoke results are returned and P0 failures addressed. A CI pass alone cannot close the gate.

## Exact candidate

- File: **LFM_TEXT_FIRST_XIAOMI.apk**, 34,178,221 bytes. Also available as app-debug.apk in [ARM64 CI run 40785098594](https://github.com/Mr-Akhil12/mobile-agents/actions/runs/40785098594), APK artifact **12859576627**.
- APK SHA-256: **ef2481e2bdfd34d608adf33d6e9fd178849348487a8aa0032c33cb8e7a32feb1**.
- Feature implementation head: **2efa7cf63dedc73f98391c83b16089581b462620** (PR #2). Actual CI checkout: **daf3957c67cf5c5038f4e083e5358af4009074c9**; tree **ca0438578600efab6c04ed08339928d95bc414c8**. Later documentation changes do not rebuild this binary.
- Package dev.lfm.mobile; arm64-v8a; API 28 minimum / 35 target. Debug-signed prototype, not a verified release. CI verified APK signature v2 and 16 KB ZIP alignment.
- Debug certificate SHA-256: **6c1622a452322a902d36abf8bdc96a7f5b14c4ffe050a88038a44e86cd1a6394**. A previous candidate may have a different debug key.

## Install and prepare

1. Download this exact candidate and transfer the APK to Xiaomi 15T. Open it from Files and allow installation from that app when Android asks. Open **LFM**. No Termux or server is needed. Close any Termux model/server process before the smoke run so it does not consume RAM.
2. If an update fails due to a signing certificate mismatch, keep the installed version/data until you choose how to proceed. Uninstall deletes history and the app-private model; save any wanted data first. Independent CI builds may use a different debug key.
3. In **Models**, read **Model license (offline)**. Obtain the exact `LFM3-1.7B-Q5_K_M.gguf` through **Official model download** or copy your existing matching file into shared Downloads. Files inside Termux private storage are not visible to this APK. An optional one-time copy from Termux to shared storage is preparation only; the APK must subsequently run independently.
4. The model is 1,258,663,200 bytes with SHA-256 `41e51fb0be5f472d323055f2734d986d5a2429570973508fa6322f3f07f6d7f3`. Keep at least 1.5 GB free app storage **in addition to** that downloaded file. Choose **Import GGUF**, select it and wait for checksum verification and Ready. Other quantizations/revisions are rejected.
5. Enable airplane mode, explicitly turn Wi-Fi off and confirm mobile data is off. Keep these settings for chat, tool and restart tests. Browser downloads require connectivity beforehand; local inference does not.

## First device run

Use the blank [results sheet](device-validation-results.md). Mark PASS only after observing the expected behavior; use FAIL or BLOCKED otherwise.

| Check | Input/action | Required observation |
| --- | --- | --- |
| Install/JNI/import | Launch, import exact GGUF | No crash; checksum verified; Ready appears; no microphone permission request |
| Offline native text | `Say hello in one sentence.` | Actual answer streams with all networking disabled; note time to first text |
| Android clock | `What time is it on my phone? Use get_local_time.` | Tool request, **Tool evidence · Android** card with `success: true`, device datetime/timezone, and model follow-up consistent with it. Compare to phone clock; allow elapsed generation time. A bare model timestamp is a FAIL for tool execution |
| Battery | `Read my current battery status using get_battery_status.` | Successful evidence card and plausible percentage/plugged value compared with Android |
| Context | `Remember the test word mango.` then `What test word did I just give you?` | Correct follow-up without exposed planning/protocol text |
| Cancel/reuse | Start a long answer, tap Cancel, then send a short prompt | Stops and becomes usable for the next turn; no crash or stuck busy state |
| Restart/history | Note last exchange; force-stop and reopen LFM while offline | History retained, model reloads, a new local reply succeeds |
| Clear | After recording test evidence, use Clear chat and confirm | Messages/evidence removed; restart confirms removal |
| Unsupported action | `Run a shell command to delete a file.` | No actual action or success claim. A refusal is behavioral evidence only, not proof that the model emitted a rejected structured call; policy rejection is separately covered by bundled unit results |
| Independence | Repeat offline text/time with no external server running | Same native flow; record whether Termux is absent or merely closed |

Then complete the remaining [QA checklist](qa-checklist.md): corrupt/partial import, low storage, model deletion/reimport, lifecycle and five consecutive turns. Run destructive test-data operations only after retaining wanted observations. Record load/first-text times as stopwatch observations; do not invent tokens/s, RAM or temperature if no measurement is available. Stop and report a crash, repeated load failure, reasoning leakage or OS/model evidence mismatch before voice work.

## Evidence and decision

Return the filled results sheet (or paste its fields) with exact device/OS version, candidate hash, prompts, observed output, failures and date/time in SAST. Screenshots may help; include only test content and redact personal notifications. Do not upload model weights, chat databases, credentials or private logs to the public repository.

- **Current status:** ARM64 build/lint and 11 policy tests passed in run 40785098594. Generic Android x86_64 run 40785098598 passed 18 real-model/JNI/UI/tool/lifecycle checks, including SAF import, cancellation, low-storage recovery and rotation. These reports were downloaded and inspected. **Physical Xiaomi launch/inference, HyperOS behavior and performance remain untested.**
- **Text GO:** native launch/import, offline inference, real Android time evidence, history/restart and independence pass on Xiaomi, with other required QA checked and material failures resolved. Record known limits and exact candidate.
- **Text NO-GO:** no native inference, missing actual time-tool evidence, persistent crash, hidden network/Termux dependency or falsely reported execution.
- **Voice gate:** no Omni implementation advance before device handoff/results review and text P0 stability. At Thursday's technical gate, keep text-first if voice cannot be genuinely demonstrated. No verified release tag until the physical acceptance gate passes.
