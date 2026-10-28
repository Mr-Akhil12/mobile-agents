#!/usr/bin/env bash
set -euo pipefail
model_path=${1:?Pass the checksum-verified model path}
mkdir -p validation-evidence

collect_evidence() {
    adb logcat -d > validation-evidence/logcat.txt 2>&1 || true
    adb shell run-as dev.lfm.mobile ls files/validation > validation-evidence/device-files.txt 2>&1 || true
    for report in core.json restart.json core-ui.json restart-ui.json; do
        adb exec-out run-as dev.lfm.mobile cat "files/validation/$report" > "validation-evidence/$report" 2>/dev/null || true
    done
    for shot in onboarding saf-import real-chat cancel-recovery history-after-process-restart cleared-chat core-final restart-final; do
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

for phase in core restart; do
    # This terminates the actual target app process between phases. The second
    # instrumentation invocation must reopen the persisted Room DB and native model.
    adb shell am force-stop dev.lfm.mobile
    timeout 1500 adb shell am instrument -w -e phase "$phase" \
        dev.lfm.mobile.test/dev.lfm.mobile.validation.LocalValidationRunner \
        | tee "validation-evidence/instrumentation-$phase.txt"
    adb exec-out run-as dev.lfm.mobile cat "files/validation/$phase.json" > "validation-evidence/$phase.json"
    python3 scripts/verify-emulator-report.py "validation-evidence/$phase.json"
done
