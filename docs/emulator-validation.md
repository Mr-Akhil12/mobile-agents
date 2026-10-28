# Independent Android validation

The emulator workflow uses a standard GitHub-hosted Ubuntu runner and hardware-accelerated generic Android API 35 x86_64. No persistent server, paid runner, hosted model API or developer laptop is needed. No emulator exists inside this ChatGPT workspace, which has no KVM access. GitHub starts and tears down each emulator job.

Run `Android real-model emulator validation` on feature/emulator-validation or use the branch PR. The ARM64 candidate in PR #1 remains independently available. Test dependencies do not add permissions or test hooks to the production application; the harness lives only in the instrumentation APK. The same C++/JNI source is compiled for x86_64 using `-PlfmAbi=x86_64`; normal builds remain arm64-v8a.

The job downloads the trusted model manifest URL, verifies size/SHA-256 on the runner, streams a fixture into the app-private directory through ADB run-as, and tests the real importer (copy + verification + atomic commit). This is file-URI import, not a claim that SAF UI was exercised. Wi-Fi/mobile data are disabled and airplane mode enabled before all model tests. No Internet permission is added to LFM. Each phase writes a complete named-check JSON report; the host validator rejects missing, failed or incomplete reports even if `am instrument` returns exit code zero. It also exports JUnit XML for review.

Reports include the actual Android clock evidence and model follow-up, screenshots of the real app, JNI/model checks and lifecycle results. Process force-stop between phases tests persistent Room history/model reload. No fixtures, mocks or synthetic responses substitute for model inference. The battery is naturally emulator-provided state, not a Xiaomi battery measurement.

Not covered: Xiaomi ARM64/HyperOS, low-memory/low-storage behavior, SAF picker interaction, rotation/background stress, microphone/audio hardware, thermals or physical battery/performance. Omni is not implemented or tested. Review docs/device-validation-handoff.md for the physical acceptance pass.

Artifacts retain for three days; only allowlisted test reports/screenshots/logs and Gradle lockfiles are uploaded. Model bytes stay temporary and are removed. A failing run is evidence of a failing test/setup, never a device pass. The workflow is not yet proven until an actual successful run has been inspected.
