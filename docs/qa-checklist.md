# Physical device acceptance checklist

Status: **all physical-device tests pending**. Record APK SHA-256, source SHA, device exact model, Android API/version, free space and tester date/time. No physical device is connected to the build environment.

- [ ] T01 Install arm64 debug APK; launch without crash.
- [ ] T02 Import official text GGUF through Android file picker; size and checksum verified.
- [ ] T03 Airplane mode with Wi-Fi off: local model answers a typed question.
- [ ] T04 Ask current time: native model requests get_local_time; evidence matches Android clock/timezone; model follows up using result.
- [ ] T05 Unknown/dangerous requested tool does not execute; no shell path is exposed.
- [ ] T06 Cancel while thinking/generating; app remains usable for next turn.
- [ ] T07 Force-close/reopen: history persists and model reloads; Clear chat removes history.
- [ ] T08 Delete/reimport model; recover from model load errors.
- [ ] T09 No microphone permission requested in text milestone.
- [ ] T10 Voice explicitly unavailable; no full voice acceptance yet.
- [ ] T11 Battery request returns OS-observed percentage/charging state or explicit error.
- [ ] T12 Wrong/truncated file, low storage and cancelled import leave no accepted partial model.
- [ ] T13 Five consecutive contextual turns; no raw reasoning or runaway tool loop.
- [ ] T14 Install/use without Termux installed or running; no external server.
- [ ] T15 Record exact source commit, APK hash and result of every test.

Also measure load time, first-token latency, generation rate, RAM and thermals. Test rotation/background/lock/unlock, cancel then immediate new turn, process death during import and out-of-context input. No performance target has yet been measured on Xiaomi or Huawei.

Do not publish `v0.1.0-p1` as device-verified until required native text, time, history and offline tests pass on Xiaomi. A successful CI build is not the release GO gate.
