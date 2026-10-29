# Active delivery risks

| Risk | Current mitigation/status |
| --- | --- |
| Physical Android inference unverified | Require Xiaomi install/import/airplane-mode/time/history tests; never infer from host run |
| Omni JNI/audio incomplete | Text-first milestone; no voice claim or cloud fallback |
| New upstream commit vs Termux baseline | Exact pin; native compilation and host GGUF smoke before device acceptance |
| Tool parsing/template differences | Upstream Jinja + PEG session and strict Kotlin allowlist |
| Memory/context/thermals | 4096 context, single resident text model, 4 CPU threads; measure on device |
| Reasoning leakage | Upstream split plus marker guard; natural-language leakage remains possible |
| GitHub connection denies writes | Preserve local commits/builds and document actual 403; do not claim push/CI success |
| Ephemeral debug certificates | Clean CI jobs may produce incompatible updates; warn before uninstall; private signing deferred |
| Model license | No weights bundled; full LFM terms and attribution included; commercial eligibility must be reviewed |
