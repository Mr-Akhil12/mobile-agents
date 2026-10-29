#!/usr/bin/env python3
"""Fail closed on missing/incomplete instrumentation evidence; export JUnit XML."""
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

path = Path(sys.argv[1])
report = json.loads(path.read_text())
expected = {
    "core": [
        "native_UI_launch_without_model", "SQLite_v1_migration_preserves_history_and_memory_CRUD", "corrupt_import_rejected_without_partial_model",
        "cancelled_import_removes_partial_file", "real_model_copy_size_and_SHA256_import",
        "low_storage_import_rejected_and_verified_model_preserved",
        "Android_JNI_load_and_stream_real_model", "actual_Android_clock_battery_and_policy",
        "native_prefix_cache_reuses_exact_input_and_invalidates_changed_system",
        "configured_JNI_generation_reports_real_metrics_and_rejects_invalid_settings",
        "model_Android_time_tool_round_trip_and_evidence", "two_consecutive_contextual_model_turns",
        "SQLite_conversation_isolation_and_saved_memory_ground_real_reply",
        "long_context_retains_history_and_oversized_prompt_recovers",
        "native_cancellation_then_successful_generation", "unloaded_JNI_context_rejects_generation",
        "Compose_SAF_picker_imports_actual_pinned_model",
        "Compose_send_button_to_real_model_and_Room",
        "Compose_cancel_then_send_next_real_model_turn",
        "Compose_controls_memory_and_collapsible_real_tool_evidence",
        "Compose_chat_search_rename_export_cancel_and_delete",
        "production_JSON_export_paginates_and_preserves_private_storage",
        "Compose_rotation_preserves_history_and_native_session",
    ],
    "upgrade": ["same_key_Android_package_update_preserves_Room_and_model"],
    "voice": ["voice_pair_rejects_corrupt_import_and_verifies_real_files", "real_Omni_JNI_reference_speech_accuracy_and_protocol_safety_offline",
              "microphone_permission_denial_preserves_typed_fallback", "foreground_voice_recording_stops_when_activity_backgrounds",
              "Compose_voice_Send_cancel_prevents_late_submission", "voice_review_preference_persists_and_controls_render",
              "Android_TTS_offline_voice_or_explicit_text_fallback", "update_APK_same_signer_and_newer_version_metadata_verified"],
    "restart": ["Room_history_survives_actual_process_restart", "reply_after_process_restart_and_model_reload",
                "clear_chat_removes_Room_messages"],
}
tests = report.get("tests", [])
phase = report.get("phase")
complete = phase in expected and [test.get("name") for test in tests] == expected[phase]
failed = sum(test.get("passed") is not True for test in tests)
suite = ET.Element("testsuite", name="LFM_Android_emulator_" + str(phase), tests=str(len(tests)), failures=str(failed))
for test in tests:
    case = ET.SubElement(suite, "testcase", name=test.get("name", "unknown"),
                         classname="dev.lfm.mobile.validation", time=str(test.get("durationMs", 0) / 1000))
    if test.get("passed") is not True:
        ET.SubElement(case, "failure").text = test.get("error", "Missing positive result")
ET.ElementTree(suite).write(path.with_suffix(".xml"), encoding="utf-8", xml_declaration=True)
if report.get("passed") is not True or not complete or failed:
    raise SystemExit("Emulator suite failed or incomplete: " + str(path))
print(f"PASS {phase}: {len(tests)} real Android emulator checks; not Xiaomi validation")
