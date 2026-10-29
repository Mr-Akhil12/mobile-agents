# Local Agents Xiaomi validation handoff

Deadline: **Friday 30 October 2026, 16:00 SAST**. The accepted text-first fallback is Local Agents 0.2.0. The user reports successful functionality on Xiaomi 15T after reinstalling it, with an existing Termux-downloaded model; their screenshot demonstrates a clock answer and displayed 29.5 s latency. Review is recorded in [device results](device-validation-results.md). This permits the user-authorized Omni implementation work; it does not establish every physical acceptance row or a verified release.

## Accepted fallback identity

- Local_Agents_0.2.0.apk: 34,363,594 bytes, SHA-256 **c4b9d9c335ba512a5587786c8abec74481e476391441e5409640afa02645e163**.
- [ARM64 run 40813694290](https://github.com/Mr-Akhil12/mobile-agents/actions/runs/40813694290), artifact 12870998947. Actual checkout 1a84cd36f738e84b2de9ca4d4f111bddece6821b, source tree 14e341c19747cdac5773774d34c78e18ab407042. Build/lint and 13 unit tests passed. Separate generic x86_64 run 40813694227 passed 22 real-model checks.
- Package dev.lfm.mobile; arm64-v8a; minimum API 28, target 35. Debug prototype, not a verified release. Signature and 16 KB ZIP/ELF alignment checked. Debug signer SHA-256 0ba79a4503b948580d7e0240959ede64812c7392230f7e17b975f31ab4c415b7.
- 0.2.0 has no Internet or microphone permission. New UI/voice/update candidates are independently versioned and require their own validation.

## Preserve data and prepare

Keep the working installation until a replacement is ready. CI debug keys were ephemeral, explaining incompatible updates. Permanent signing is prepared in [APP_UPDATES.md](APP_UPDATES.md); no permanent-key build has been published. The first key transition may require one final reinstall, which erases private data. Original shared downloaded GGUFs can be reimported. Subsequent matching-key updates use Android's normal data-preserving upgrade mechanism; this must be tested, not assumed from a build.

Use Models → Import GGUF to import the exact LFM3-1.7B-Q5_K_M.gguf: 1,258,663,200 bytes, SHA-256 41e51fb0be5f472d323055f2734d986d5a2429570973508fa6322f3f07f6d7f3. Existing matching downloads work; no new download or Termux runtime is required. Keep at least 1.5 GB free app storage in addition to the downloaded file. Private Termux paths need copying to shared Downloads for the Android picker.

## Remaining physical text checks

Record exact installed APK hash, Android/HyperOS version, device/date and failures in the results sheet. Enable airplane mode and turn Wi-Fi/data off; close external servers. Ask a greeting, then current time and battery; expand real Android evidence and compare with the OS. Give a test word, ask a follow-up, cancel a long answer and immediately send another. Force-stop/reopen, check history and a fresh inference; delete only disposable history after retaining observations. Check rotation/background/lock, five turns and thermal behavior. The [QA checklist](qa-checklist.md) records remaining rows. Keep private notifications, histories, weights and logs out of public issues.

## Voice candidate handoff (after its CI passes)

Import both pinned Omni files through Models: LFM3-Omni-2B-Q4_K_M.gguf (1,099,630,080 bytes) and mmproj-LFM3-Omni-2B-Q8_0.gguf (540,892,768 bytes), revision 2918dd47cfceb4f260cd1734cd1aac1b22bbe4b6. Exact hashes are in model-manifests and checked on import. Existing matching Termux downloads are supported. Reserve temporary-copy headroom; the app swaps models to limit RAM.

Deny the first mic request and confirm typed fallback. Then grant it on a deliberate Voice input tap, speak, tap Stop recording, review/edit the transcript and press Send. Compare the resulting tool evidence/answer. An installed English Android TTS voice marked offline is required for spoken replies; absent engines/packs must leave the text reply usable. Repeat in airplane mode. Cancel and background/lock during capture/load/transcription; verify recording stops and typed chat recovers. Record actual ASR/TTS correctness, latency, memory and routing problems. Emulator synthetic PCM transcription and virtual AudioRecord tests do not validate Xiaomi acoustics, HyperOS or audible speech.

No cloud speech, always-on wake word, silent sensitive actions or verified-release claims. Preserve text-first delivery if voice fails physical acceptance.
