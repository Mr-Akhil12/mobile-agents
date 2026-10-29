# Publication recovery

## Connection repair and source restoration

After the user repaired the connection, personal login `Mr-Akhil12` remained correct and installation 176335068 for `Mr-Akhil12` became visible. Feature-branch creation and Git Data writes succeeded. The original implementation was reconstructed as commit `c26114fb55f94be81cf098fbccd38fc0d2c6ddf6` with **exactly the same tree** `07d03d5b62f5665f50ea9bc1d28f612c2f75e8fe` as bundled commit `bd2c04099a8347ebed868d39852b4ea1bbc12fd4`. The wrapper JAR blob, executable mode, licenses and llama.cpp gitlink are preserved. Different commit metadata accounts for the new commit SHA. CI and physical validation must still be checked separately.

The failed-access record and manual recovery below are historical/fallback instructions. Do not treat the earlier 403 as the current connection state or push the old bundle over a remote branch without first reconciling history.

## Earlier failed-access record

The continuation used the personal GitHub connection: authenticated login `Mr-Akhil12`, account 180930390. Repository metadata confirms admin/push access. Remote `main` is `d54f2158a4ebfb40f2de46ec88b406bd62f911e1`; no remote feature branch was present at inspection.

Actual write check: creating `feature/native-text-milestone` from `main` returned HTTP 403 **Resource not accessible by integration**. That same personal connection's installation lookup listed only `CS-AppDev` installation 151402660. No work-account write was attempted. Direct `git push --dry-run` failed because this environment has no configured GitHub username/credential. The branch, PR and CI are therefore **not published/run**. Readable metadata does not establish connector write access.

## User-assisted publication from the continuation ZIP

On a computer with Git and GitHub CLI installed, extract the ZIP. These commands use the supplied full-history bundle; no model download or Android toolchain is needed just to publish. Run them one at a time and stop on errors.

```sh
gh auth status
gh api user --jq .login
```

The login must be **Mr-Akhil12**. If needed, use `gh auth login --hostname github.com --git-protocol https` to authorize your personal account, then recheck. Never paste tokens into chat or repository files.

```sh
gh auth setup-git
git clone mobile-agents.bundle mobile-agents
cd mobile-agents
git checkout feature/native-text-milestone
git remote set-url origin https://github.com/Mr-Akhil12/mobile-agents.git
git fetch origin
git log --oneline --all -8
git ls-remote --heads origin
git push -u origin feature/native-text-milestone
```

No force push. A normal push preserves new remote work by refusing incompatible updates. If `main` advanced beyond the inspected baseline or a feature branch now exists, inspect differences before proceeding; do not reset or overwrite user commits. The bundle's `origin/*` refs describe the saved snapshot, so use fresh fetch/ls-remote output for remote state.

After push succeeds:

```sh
gh pr create --repo Mr-Akhil12/mobile-agents --base main --head feature/native-text-milestone --title "Add native offline text milestone and Xiaomi validation handoff" --body-file docs/PR_BODY.md
gh run list --repo Mr-Akhil12/mobile-agents --branch feature/native-text-milestone --limit 5
```

Push and PR events trigger the prepared Android workflow automatically. If a matching PR already exists, use it rather than creating a duplicate. Select the run for the actual pushed head SHA; a green run for an older head is insufficient.

```sh
gh run watch RUN_ID --repo Mr-Akhil12/mobile-agents --exit-status
gh run view RUN_ID --repo Mr-Akhil12/mobile-agents --log-failed
```

Replace `RUN_ID` with the returned numeric run ID. If successful, download that run's `lfm-arm64-debug-<sha>` and `validation-<sha>` artifacts through Actions or `gh run download RUN_ID --repo Mr-Akhil12/mobile-agents --dir ci-artifacts`. Record its APK hash/source SHA and use a separate physical-results record if replacing the supplied candidate. If failed, return the run URL and failing step for a scoped fix; never mark CI passed from the local build report.

The source commit associated with the supplied APK remains `bd2c04099a8347ebed868d39852b4ea1bbc12fd4`. The continuation adds documentation only; no updated APK is claimed. No merge, release tag, new voice capability or device-verification claim is included.
