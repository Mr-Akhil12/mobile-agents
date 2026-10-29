# Independent Android validation

The emulator workflow uses a standard GitHub-hosted Ubuntu runner and hardware-accelerated generic Android API 35 x86_64. No persistent server, paid runner, hosted model API or developer laptop is needed. No emulator exists inside this ChatGPT workspace, which has no KVM access. GitHub starts and tears down each emulator job.

Run `Android real-model emulator validation` on feature/emulator-validation or use the branch PR. The ARM64 candidate in PR #1 remains independently available. Test dependencies do not add permissions or test hooks to the production application; the harness lives only in the instrumentation APK. The same C++/JNI source is compiled for x86_64 using `-PlfmAbi=x86_64`; normal builds remain arm64-v8a.

The job downloads the trusted model manifest URL, verifies size/SHA-256 on the runner, streams a fixture into the app-private directory through ADB run-as, and tests the real importer (copy + verification + atomic commit). A separate test uses the actual Models/Import GGUF/system Documents UI with the same fixture in shared Downloads. The actual SAF test passed in the verified run below. Wi-Fi/mobile data are disabled and airplane mode enabled before all model tests. No Internet permission is added to LFM. Each phase writes a complete named-check JSON report; the host validator rejects missing, failed or incomplete reports even if `am instrument` returns exit code zero. It also exports JUnit XML for review.

Reports include the actual Android clock evidence and model follow-up, screenshots of the real app, JNI/model checks and lifecycle results. Process force-stop between phases tests persistent Room history/model reload. No fixtures, mocks or synthetic responses substitute for model inference. The battery is naturally emulator-provided state, not a Xiaomi battery measurement.

[Run 40785098598](https://github.com/Mr-Akhil12/mobile-agents/actions/runs/40785098598) passed **all 18 checks**, including actual SAF import, low-storage recovery, landscape/portrait Activity recreation and force-stop/restart. Source head 2efa7cf63dedc73f98391c83b16089581b462620; evidence artifact 12858589228. Downloaded reports contain 15 core + 3 restart passes; screenshots were inspected. Low-space testing created and removed a disposable 3,917,479,936-byte filler and observed 1,390,788,608 bytes free before import rejection; the valid model remained recoverable.

Not covered: Xiaomi ARM64/HyperOS, low-memory/background stress, microphone/audio hardware, thermals or physical battery/performance. Omni is not implemented or tested. Review docs/device-validation-handoff.md for physical acceptance.

Artifacts retain for three days; only allowlisted test reports/screenshots/logs and Gradle lockfiles are uploaded. Model bytes stay temporary and are removed. A failing run is evidence of a failing test/setup, never a device pass. An inspected successful run proves only its named generic Android checks.

Emulator builds opt into ccache with `-PlfmCcache=true`; normal builds do not require it. Its cache is bounded to 1 GB and contains compiler outputs, not models or signing keys. Gradle dependency caching is separate from uploaded evidence. Cache efficiency must be measured from actual ccache statistics.

Latest cache evidence: the real x86_64 build completed in 49 seconds after a 6m24s cold build; 133 compiler-cache hits were observed. This is build-time evidence only.
