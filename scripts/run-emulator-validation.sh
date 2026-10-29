#!/usr/bin/env bash
set -euo pipefail
model_path=${1:?Pass the checksum-verified model path}
mkdir -p validation-evidence

collect_evidence() {
    adb logcat -d > validation-evidence/logcat.txt 2>&1 || true
    adb shell run-as dev.lfm.mobile ls files/validation > validation-evidence/device-files.txt 2>&1 || true
    for report in core.json restart.json voice.json upgrade.json core-ui.json restart-ui.json voice-ui.json upgrade-ui.json; do
        adb exec-out run-as dev.lfm.mobile cat "files/validation/$report" > "validation-evidence/$report" 2>/dev/null || true
    done
    for shot in onboarding saf-import real-chat cancel-recovery landscape-chat portrait-after-rotation history-after-process-restart cleared-chat expanded-tool-evidence collapsed-tool-evidence generation-controls saved-memory core-final restart-final voice-final upgrade-final after-package-upgrade; do
        adb exec-out run-as dev.lfm.mobile cat "files/validation/$shot.png" > "validation-evidence/$shot.png" 2>/dev/null || true
    done
    find validation-evidence -type f -empty -delete
}
trap collect_evidence EXIT

adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm clear dev.lfm.mobile
adb logcat -c
# Stream through run-as to app-private test-fixture storage. No storage permission or
# production download/network API is added. Instrumentation imports via a file URI;
# this tests ModelStore's real copy/hash path, NOT the SAF picker UI.
adb shell run-as dev.lfm.mobile mkdir -p files/fixtures
adb shell run-as dev.lfm.mobile sh -c '"cat > files/fixtures/model.gguf"' < "$model_path"
adb push "$model_path" /sdcard/Download/LFM3-1.7B-Q5_K_M.gguf
adb shell svc wifi disable
adb shell svc data disable
adb shell cmd connectivity airplane-mode enable
test "$(adb shell settings get global airplane_mode_on | tr -d '\r')" = 1
test "$(adb shell settings get global wifi_on | tr -d '\r')" = 0
{
    adb shell getprop ro.build.fingerprint
    adb shell getprop ro.product.cpu.abilist
    adb shell settings get global airplane_mode_on
    adb shell settings get global wifi_on
    adb shell settings get global mobile_data
    adb shell ip route
    adb shell cat /proc/meminfo
    cat "$ANDROID_HOME/emulator/source.properties"
    sdkmanager --list_installed
    git rev-parse HEAD
    sha256sum app/build/outputs/apk/debug/app-debug.apk
} > validation-evidence/environment.txt

run_phase() {
    local phase=$1
    adb shell am force-stop dev.lfm.mobile || return 1
    timeout 1800 adb shell am instrument -w -e phase "$phase" \
        dev.lfm.mobile.test/dev.lfm.mobile.validation.LocalValidationRunner \
        | tee "validation-evidence/instrumentation-$phase.txt" || return 1
    adb exec-out run-as dev.lfm.mobile cat "files/validation/$phase.json" > "validation-evidence/$phase.json" || return 1
    python3 scripts/verify-emulator-report.py "validation-evidence/$phase.json"
}
# Keep independent voice diagnostics even when an earlier UI phase fails.
# The final shell exit still fails closed if ANY required phase failed.
failed=0
for phase in core restart; do run_phase "$phase" || failed=1; done

# Core import checks are finished. Free duplicate text fixtures before importing
# the real Omni pair, which is copied/checksummed by the production store.
adb shell run-as dev.lfm.mobile rm -f files/fixtures/model.gguf
adb shell rm -f /sdcard/Download/LFM3-1.7B-Q5_K_M.gguf
for fixture in omni.gguf omni-projector.gguf voice.wav update.apk; do
    adb shell run-as dev.lfm.mobile sh -c "\"cat > files/fixtures/$fixture\"" < "$RUNNER_TEMP/$fixture"
done
run_phase voice || failed=1
# Real Android PackageManager upgrade with the same signer; no uninstall/clear.
adb shell am force-stop dev.lfm.mobile
adb install -r "$RUNNER_TEMP/update.apk"
run_phase upgrade || failed=1
exit "$failed"
