# Android permissions

Accepted 0.2.0 text APK declared no Internet or microphone permission. The developing voice/update branch adds:

- `RECORD_AUDIO`: runtime prompt only after tapping Voice input with the paired Omni files imported. Capture is foreground only; leaving the Activity stops/cancels it. Bounded PCM stays in RAM, never uploaded or logged.
- `INTERNET`: daily/manual public GitHub update checks and explicit APK download. Disable automatic checks in App updates. Text/ASR and tools remain local; airplane-mode emulator checks are separate from Xiaomi acceptance.
- `REQUEST_INSTALL_PACKAGES`: Android's per-app sideload permission and installation confirmation. The app does not silently install. APK hash, package, increasing version and installed signing identity are checked first.
- TTS service package query: choose an installed English voice marked as not requiring network. If no offline voice exists, keep the text reply and explain the missing voice pack. No cloud speech fallback.

Storage Access Framework grants read access only to files explicitly selected for immediate verified copy into private storage. No persistent broad storage grant, Bluetooth, notification listener, accessibility, dialer, call or background-microphone permission is declared. Android backups remain disabled. App updates preserve data only with the same package and signing identity; uninstall erases private data.
