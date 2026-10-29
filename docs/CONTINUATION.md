# Durable continuation checkpoint — 29 October 2026

Read this and MASTER_PLAN.md in full before editing. Deadline: **Friday 30 October 2026, 16:00 SAST**. Text first. **Initial Xiaomi smoke is user-reported successful; detailed acceptance remains pending; Omni voice remains held until text/device gate review.** User now authorizes merging PRs after successful tests. No verified release tag, paid services, model weights or private logs are authorized.

## Verified candidate and actual CI

- Personal GitHub connection: Mr-Akhil12. PR #1 merged at bef78e7f4d9a47bcfd45227863014b0f1285bb1c (feature head 66c8fc2eaae11ff6e623102d04cfeb258515e1fe); its original run 40774787452 passed build, seven policy tests and lint.
- PR #2 merged into main at a74cc274ddf4850abd610f4594c4e5400d2337a1 after successful conflict-resolution runs 40806949995 (ARM64) and 40806949947 (emulator). Tested implementation head: **2efa7cf63dedc73f98391c83b16089581b462620**, tree **ca0438578600efab6c04ed08339928d95bc414c8**. Later documentation commits do not change the delivered APK.
- [Emulator run 40785098598](https://github.com/Mr-Akhil12/mobile-agents/actions/runs/40785098598): SUCCESS, **18/18 checks** (15 core + 3 restart), real pinned GGUF and JNI, artifact **12858589228**. Downloaded JSON reports and screenshots inspected. Emulator checks out the feature head directly.
- [ARM64 run 40785098594](https://github.com/Mr-Akhil12/mobile-agents/actions/runs/40785098594): SUCCESS, build/lint and **11 policy tests**, zero failures/errors/skips. Actual PR-merge checkout **daf3957c67cf5c5038f4e083e5358af4009074c9**, same tree ca0438578600efab6c04ed08339928d95bc414c8. APK artifact **12859576627**, reports **12859950499**.
- Delivered **LFM_TEXT_FIRST_XIAOMI.apk**, **34,178,221 bytes**, SHA-256 **ef2481e2bdfd34d608adf33d6e9fd178849348487a8aa0032c33cb8e7a32feb1**. APK signature v2 and 16 KB ZIP alignment verified in CI. ARM64-only, API 28 minimum / 35 target; no Internet or microphone permission. Debug certificate SHA-256 **6c1622a452322a902d36abf8bdc96a7f5b14c4ffe050a88038a44e86cd1a6394**. Separate clean CI builds can use different debug keys.

## Coverage and fixes

The generic Android API 35 x86_64 suite passed actual SAF Documents UI import/checksum, corrupt/cancelled import recovery, real filesystem low-space rejection/recovery, JNI load and streamed inference, Android clock/battery and strict tool policy, model → time tool → grounded final answer, contextual turns, native cancellation/recovery, Compose Send/Cancel/reuse, landscape/portrait Activity recreation with subsequent inference, force-stop/history/model reload, post-restart greeting and Clear chat (DB and UI).

Earlier failures were real and are preserved in run history: first SDK setup used retired tools; a UI driver clicked before Compose became ready; run 40780321065 then exposed post-restart planning/protocol output correctly withheld by the app. The product fix permits one bounded final-answer repair without storing/replaying rejected reasoning, retains the three-tool budget and errors on invalid repair. Streaming withholds known partial planning prefixes. Strict empty arguments reject trailing data/comments. The driver requires new persisted assistant output, checks errors promptly and validates a complete named report.

Compiler caching is opt-in and bounded to 1 GB. Latest cached x86_64 build took 49 seconds versus 6m24s cold, with 133 compiler cache hits observed; these are CI build observations, not phone performance. No model weights or signing keys are cached/uploaded.

## Next authorized work and gate

1. User reports the first APK worked perfectly on Xiaomi 15T, importing the model already downloaded through Termux. Exact installed hash and individual offline/tool/lifecycle acceptance observations are not yet supplied. See device-validation-results.md; do not mark the full checklist passed. User requested Local Agents branding, bot icon and black/grey/white theme. Current patch changes presentation/version only; new CI must be inspected before delivering a new binary. Package dev.lfm.mobile and model pins/storage are preserved; ephemeral CI debug certificates can still prevent an in-place update.
2. Review actual returned Xiaomi results, fix text P0s and rerun affected tests. Generic emulator evidence cannot establish Xiaomi ARM64/HyperOS, low-memory/background behavior, thermals, battery or phone latency.
3. Keep Omni voice integration held until text/device gate review. No microphone/audio loop is implemented or validated. Do not waive physical acceptance or call this a verified release.
4. Before editing, inspect current branch, PRs and active CI. Do not duplicate runs or concurrent edits. Preserve source, llama.cpp pin, licenses and user work; use expected-head updates without force push.

## Autonomy and limits

User removed the 5% safety-stop requirement. Continue useful authorized work within actual account limits; no live usage/reset telemetry is exposed. Never invent percentages or reset status, buy credits, or bypass limits. The scheduled **29 October 03:55 SAST** continuation is a conservative retry, not evidence of quota reset. Checkpoint actual outcomes frequently. Stop for actual limits, necessary external blockers, completion or new instructions.

## Current chat experience implementation — pending fresh CI

User requested ChatGPT-like local UX after reporting the first APK worked perfectly. PR #1 merged at bef78e7f4d9a47bcfd45227863014b0f1285bb1c. PR #2 branding head 44510c9 passed ARM64 run 40805455611 and all 18 emulator checks in 40805455677. Local Agents 0.1.1 APK artifact 12867982572 / checkout 16f98896371bbd1bb5b3370a093a45aaaa99ecff / SHA-256 3bc9e6dad96df689a38efc054589de083bee646701daeaf52b6f5a9f604ab3cb. Debug certificate changed; keep the first installation/data if Android rejects an update. PR #2 was retargeted to main; a concurrent user README title edit caused a merge conflict. Commit 37f8b84ecff65e1c8042696f3aaba986df6dbc54 merges main while retaining that description as a tagline and Local Agents branding. That conflict-resolution commit passed CI and PR #2 merged at a74cc274ddf4850abd610f4594c4e5400d2337a1.

Separate feature/chat-controls-memory adds live monotonic prompt timing, collapsed tool request/evidence cards, measured native ingestion/generation token rates across tool/repair calls, persisted temperature/token-budget/top-k/repetition controls and a capability-gated thinking toggle. A token budget covers all generated output including hidden reasoning; no separate unsupported reasoning-level claim is made. Reasoning stays hidden; tool permissions/budget remain unchanged.

Room already used SQLite. Version 2 adds explicit conversations and editable user-saved memory plus per-answer native metrics, with a non-destructive v1→v2 migration. New chats isolate context; the last selected chat survives restart. Clear chat removes its rows/title, while saved memory is separately editable/deletable. Recent four completed turns and up to 6,000 characters of explicit saved notes are included; no automatic semantic recall or internet research is claimed. Default inference behavior remains thinking off / temperature 0.1 / 512 tokens. No voice changes.

Fresh tests must verify real configured JNI metrics/bounds, v1 migration and note persistence/edit/delete, conversation isolation and actual saved-memory model recall, plus the real Compose controls/memory/tool dropdown flow. Existing real-model import/cancel/rotation/restart suite remains. Do not call these new checks passed until actual CI completes. The generated Room v2 schema was retrieved from evidence artifact 12869084300 and is now committed.

### Actual first QoL validation and prepared correction

PR #3 targets main, branch feature/chat-controls-memory. At implementation b94c3903113c02fadc3e4c92e83f7554251312c2, ARM64 run 40807920454 passed build/lint and **13 unit tests** (11 policy + 2 metrics; zero failures/errors/skips), independently read from artifact 12868354768. Emulator run 40807920471 **FAILED** after 17 successful checks: the Controls dialog's Reset defaults button was below the reachable viewport. Do not call the 22-check suite passed. Evidence artifact 12869084300 contains the failure, actual v1 migration/memory CRUD pass, real configured JNI metrics/bounds, thinking-enabled generation, saved-memory reply `cedar`, chat isolation and prior cancellation/import passes.

Measured generic emulator example, not Xiaomi: 18 input tokens in 2415.976619 ms; 3 generated tokens in 489.647051 ms. The pinned model reports thinking support; an actual enabled run generated 48 tokens and returned a valid final answer (reasoning not displayed). These are test-specific timings, not general phone performance claims.

Current correction moves Reset defaults into the always-visible dialog footer, keeps a compact landscape header/composer, preserves the bot's dark eyes, records each answer's settings snapshot, and captures Controls/Memory/tool expansion screenshots. Schema v2 is included. All **19 core + 3 restart** named checks remain required; inspect current CI and screenshots before merging PR #3 or delivering its 0.2.0 binary. The PR description records final run/APK identifiers after CI without a documentation-only rebuild loop. User authorizes merging successful PRs; no release verification tag or voice integration is authorized by these emulator results.

### Second QoL run: explicit composer targeting

Head ecce90502282a124e32712942974cb4b49f80795 (assistant identity now Local Agents) passed ARM64 run 40810080132: build/lint and all 13 unit tests. Emulator run 40810080375 failed after 17 passes in the combined Controls/Memory/tool UI check. Reset defaults was reachable and the Memory editor add/delete flow completed, but sendFromUi selected the closing dialog's editable field after Done, then timed out waiting for the chat input text. Failure artifact 12870411816 and its screenshot show the main composer remained blank; no model turn had started. This is an automation target/window-transition race, not a successful full suite.

The test driver now waits for an enabled, visible editable node containing the actual Message Local Agents label before setting chat text; it also requires tool dropdown text to be visible before stopping its scroll search. Complete reports and new persisted assistant rows remain mandatory. No production inference behavior is altered by this correction. Inspect the new actual CI before merging PR #3 or delivering 0.2.0.

### Third QoL run: refresh Android accessibility snapshots

Head 9d4fab274877642709b41c3415caebe613435db7 passed ARM64 run 40811804908, all 13 unit tests/build/lint. Its APK artifact 12870826556 is source merge 5fc620b84af74d52b50978e55de36dd27f5fb3ce, tree 0147273b1ef07f56f3f536763d13ec246f54a4fe; SHA-256 7d1bd784688a17009e89a02163a2f818e16e45d6c13d929b686da63d8b0540fe, 34,363,594 bytes. Not delivered as the final 0.2.0 candidate because emulator run 40811804926 failed tool-dropdown discovery after 17 passes (artifact 12871391226).

The new composer target allowed the time prompt to run. The failed artifact shows a stale accessibility tree (previous Worked for 27.3 s / previous reply / question still in editor), while the screenshot shows the subsequent completed 89.9 s run and a different scroll position. Runner lookups now clear the accessibility cache on API 34+ and refresh the root before reading virtual nodes. Also require an actual successful Android clock-tool row belonging to the new assistant turn before checking its dropdown, so a model-only timestamp cannot pass. Production app code is unchanged by this driver correction. All 22 complete checks and screenshot review remain required before merging PR #3.
