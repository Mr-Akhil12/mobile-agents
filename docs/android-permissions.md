# Android permissions

Text milestone requests no Android runtime permissions. In particular the manifest has no INTERNET, RECORD_AUDIO, Bluetooth, notification listener, accessibility, dialer, call, broad-storage or background-microphone access. Storage Access Framework grants read access only to the GGUF explicitly selected by the user. No persistent document URI grant is needed because the file is copied immediately.

Clock and battery tools are read-only public Android APIs. Model/license links launch ACTION_VIEW in a separate browser; a model output cannot trigger those UI actions. Backup is disabled and explicit Android 12+ cloud/device-transfer exclusions cover internal files and databases for local chat/model privacy. Future permissions require capability-specific implementation and user-facing policy; they must not be added just because they appear in the master plan.
