# Xiaomi validation results — initial user report

Status: **INITIAL PHYSICAL SMOKE REPORTED SUCCESSFUL BY USER**. The user reports: “The first apk worked absolutely perfectly with no issue” and imported the already-downloaded model from Termux rather than downloading it again. This records a user report, not agent-observed device testing. Exact installed APK hash, OS version and individual checklist observations have not been supplied; do not infer every row passed.

Reusing an existing GGUF is supported: the native importer still checks exact pinned size/SHA-256. Termux was the source of the previously downloaded file, not evidence of a runtime dependency. Airplane-mode operation and whether Termux was closed during inference remain unconfirmed.


- Tester/date/time (SAST):
- Exact device model:
- Android version/API and HyperOS version:
- Free storage before import:
- Prepared candidate (confirm installed file): LFM_TEXT_FIRST_XIAOMI.apk; SHA-256 ef2481e2bdfd34d608adf33d6e9fd178849348487a8aa0032c33cb8e7a32feb1
- APK filename/SHA-256 actually installed:
- Prepared CI source: daf3957c67cf5c5038f4e083e5358af4009074c9, run 40785098594, artifact 12859576627
- APK implementation or CI source actually installed:
- Model filename/SHA-256 verified by app:
- Airplane mode / Wi-Fi / mobile data state:
- Termux absent or closed; external model servers stopped:

| Test | PASS / FAIL / BLOCKED | Actual input/output, evidence or error |
| --- | --- | --- |
| Install and launch | USER-REPORTED PASS | Initial APK reported working without issues on Xiaomi 15T; exact build not confirmed. |
| Verified import and JNI load | USER-REPORTED PASS | Existing model previously downloaded through Termux imported successfully; no fresh download. No logs/timings supplied. |
| Offline typed reply | PENDING | |
| Android time tool and grounded follow-up | PENDING | |
| Android battery evidence | PENDING | |
| Follow-up context / five turns | PENDING | |
| Cancellation and subsequent reply | PENDING | |
| Force-stop/reopen, history and inference | PENDING | |
| Clear history persists after restart | PENDING | |
| Unsupported action behavior | PENDING | |
| No Termux/server dependency | PENDING | |
| Corrupt/cancelled import recovery | PENDING | |
| Low-storage handling | PENDING | |
| Delete/reimport model | PENDING | |
| Rotation/background/lock/unlock | PENDING | |
| No microphone permission; voice unavailable | PENDING | |

- Cold model-load duration (how measured):
- Warm first visible text duration (prompt and how measured):
- RAM/thermals/battery observations (or NOT MEASURED):
- Crashes, freezes, reasoning leaks, truncated answers or incorrect evidence:
- Text gate: PENDING / GO / NO-GO, with reason:
- Voice gate: HELD until text/device review:

Do not put private conversation data in public issues/PRs. CI results belong to the build record, not the physical-test rows.

## Reviewed follow-up: Local Agents 0.2.0

The user reports the prior update failed, then uninstalled/reinstalled the latest delivered APK and is happy with its functionality. Delivered candidate: Local_Agents_0.2.0.apk, 34,363,594 bytes, SHA-256 c4b9d9c335ba512a5587786c8abec74481e476391441e5409640afa02645e163; ARM64 run 40813694290 / artifact 12870998947; actual build checkout 1a84cd36f738e84b2de9ca4d4f111bddece6821b, tree 14e341c19747cdac5773774d34c78e18ab407042. The installed hash was not independently measured on the handset.

User-supplied screenshot: input “what time is it on my phone?”, collapsed request/evidence controls, answer 6:29 AM in Africa/Johannesburg, displayed Worked for 29.5 s, ingestion 18.7 tok/s, generation 11.7 tok/s, 501 input, 31 generated, 2 model calls. This is actual user-device UI evidence, not an emulator measurement. The raw expanded tool payload was not supplied; the earlier native tool implementation is separately validated in CI. The screenshot has connectivity indicators, so it proves no airplane-mode claim. Do not publish the private screenshot or notifications.

The successful user reports and explicit request to progress Omni were reviewed: **engineering gate permits voice implementation** while preserving the accepted text candidate. **Physical release acceptance remains partial**, with offline, restart/cancellation/lifecycle and individual QA rows pending explicit observations. **Voice acceptance remains pending**, independently from text. All earlier pending rows above describe the initial report; use this follow-up and the updated QA checklist for current status. No verified release tag is authorized by this review.
