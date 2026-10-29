# Local Agents app updates

The updater checks this project's public GitHub releases at most daily while opening the app (toggle off in Menu → App updates), or manually with Check now. It sends ordinary public HTTP requests, never chat history, memory, audio or model files. Offline chat remains independent. Download is explicit. Only newer, matching-package APKs with the installed signing certificate, exact expected size and SHA-256 are accepted. Android requires per-app install permission and a final installation confirmation. No silent update or access-control bypass.

## One-time permanent signing setup

Existing CI debug builds used ephemeral signing keys. Their private keys were not retained, so the permanent-key prototype cannot upgrade those old installations in place. Do not uninstall until ready: uninstalling erases app-private history/memory/models. Keep original downloaded GGUF files for reimport. The old working APK remains available as the text-first fallback.

On your own computer with JDK 17+ and authenticated GitHub CLI as Mr-Akhil12, run `python scripts/setup-update-signing.py`. Read its source first. It creates an encrypted PKCS12 key in `~/LocalAgentsSigning`, asks for a strong password, and—after you type SETUP—uploads two encrypted repository Actions secrets: LOCAL_AGENTS_KEYSTORE_BASE64 and LOCAL_AGENTS_KEYSTORE_PASSWORD. It reuses an existing key and verifies its password. Keep a private backup of both key and password. Never post them in chat, commit them, or upload them as artifacts. Do not replace a configured key for ordinary updates.

After the implementation passes CI and merges, run **Publish signed prototype** on main with a strictly increasing versionCode and semantic versionName. The workflow uses the permanent identity, runs unit tests/lint, verifies signing and 16 KB ZIP alignment, and publishes complete APK/metadata/evidence assets together in a GitHub prerelease. The tag is a prototype identifier, not a verified release. No model weights are published. This is a sideload prototype; store distribution requires a separate review.

Install that first permanent-key APK once. Later matching-key upgrades preserve app-private data through Android's normal update mechanism. The updater cannot make an incompatible old key match, and no data-preservation claim applies to uninstall/reinstall.

## Validation state

Implementation is under development. No permanent signing secrets have been configured by the assistant and no permanent-key release has been published. Emulator/CI and physical Xiaomi acceptance must be recorded separately before claiming this path works.
