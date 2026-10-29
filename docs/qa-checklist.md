# Physical device acceptance checklist

Xiaomi 15T: the user reports both the original APK and latest delivered 0.2.0 working, including import of an existing Termux-downloaded GGUF. The supplied screenshot demonstrates a completed clock-tool answer and real displayed latency. This is user-device evidence; individual unreported rows remain pending. No physical phone is connected to CI. Huawei testing remains pending.

- [x] User-reported install, launch, model import/load and local text functionality.
- [x] Screenshot shows a clock-question answer with tool activity controls, Africa/Johannesburg, 29.5 s / 501 input / 31 generated / 2 model calls.
- [ ] Confirm exact installed APK hash and Android API/HyperOS version.
- [ ] Airplane mode, Wi-Fi/data off: repeat text and real clock tool with external servers stopped.
- [ ] Battery request: compare OS-observed percentage/charging state.
- [ ] Five contextual turns, no raw reasoning/protocol leakage.
- [ ] Cancel a long turn, then send a short one successfully.
- [ ] Force-stop/reopen: history/model retained and new inference works; Clear chat persists.
- [ ] Corrupt/cancelled import, low storage and delete/reimport recovery.
- [ ] Rotation, background, lock/unlock, thermals/RAM/battery observation.
- [ ] New UI/prefix-cache performance on Xiaomi (emulator timings do not establish phone speed).
- [ ] Voice candidate: deny mic permission and verify typed fallback; import pinned Omni pair; capture → review transcript → Send → tool/text → installed offline TTS, all offline.
- [ ] Voice stops on Cancel/background/lock; repeat turns without memory pressure or stuck state. Audio focus/call/Bluetooth routing require separate physical checks and are not established by the initial voice path.
- [ ] Permanent-key candidate: explicit download/install flow, then a newer same-key update retains chats/memory/imported models. Old ephemeral-key installations need a one-time migration/reinstall decision first.

Text/device engineering review accepted the user's successful native smoke reports and explicit approval to advance Omni. This does not mark the full physical offline release checklist complete. Preserve the accepted 0.2.0 text APK and Friday 30 October 2026, 16:00 SAST deadline. No device-verified release tag until required physical observations are confirmed.
