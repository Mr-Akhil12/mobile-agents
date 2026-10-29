package dev.lfm.mobile.validation

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.os.StatFs
import android.view.accessibility.AccessibilityNodeInfo
import androidx.room.Room
import dev.lfm.mobile.MainActivity
import dev.lfm.mobile.agent.AgentCoordinator
import dev.lfm.mobile.agent.AnswerPolicy
import dev.lfm.mobile.data.ChatDatabase
import dev.lfm.mobile.data.ChatMessage
import dev.lfm.mobile.data.MemoryFact
import dev.lfm.mobile.data.Conversation
import dev.lfm.mobile.data.DEFAULT_CONVERSATION
import dev.lfm.mobile.inference.GenerationSettings
import dev.lfm.mobile.inference.InferenceMetrics
import android.database.sqlite.SQLiteDatabase
import dev.lfm.mobile.inference.InferenceRepository
import dev.lfm.mobile.inference.ModelStore
import dev.lfm.mobile.tools.ToolRegistry
import dev.lfm.nativebridge.NativeInference
import java.io.File
import java.io.FileOutputStream
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/** Test-APK-only harness using Android's Instrumentation API; no production test hooks.
 * The shell driver checks the JSON report, not am instrument's frequently-zero exit status.
 */
class LocalValidationRunner : Instrumentation() {
    private lateinit var phase: String
    private val results = JSONArray()
    private val evidence = JSONObject()
    private lateinit var output: File
    private var activity: Activity? = null

    override fun onCreate(arguments: Bundle?) {
        phase = arguments?.getString("phase") ?: "core"
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        output = File(targetContext.filesDir, "validation").apply { mkdirs() }
        var passed = false
        try {
            runBlocking {
                when (phase) {
                    "core" -> coreTests()
                    "restart" -> restartTests()
                    else -> error("Unknown validation phase: $phase")
                }
            }
            passed = true
        } catch (t: Throwable) {
            sendStatus(-2, Bundle().apply { putString("stream", "Validation failed: ${t.stackTraceToString()}\n") })
        } finally {
            runCatching { screenshot("$phase-final") }
            runCatching { dumpUi() }
            runCatching { NativeInference.unload() }
            val report = JSONObject().put("phase", phase).put("passed", passed)
                .put("evidenceLevel", "Android x86_64 emulator; NOT Xiaomi/ARM64 validation")
                .put("device", android.os.Build.MODEL).put("sdk", android.os.Build.VERSION.SDK_INT)
                .put("abis", JSONArray(android.os.Build.SUPPORTED_ABIS.toList()))
                .put("tests", results).put("evidence", evidence)
            File(output, "$phase.json").writeText(report.toString(2))
            finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply { putString("stream", "LFM $phase passed=$passed; ${results.length()} checks\n") })
        }
    }

    private suspend fun step(name: String, action: suspend () -> Unit) {
        val start = SystemClock.elapsedRealtime()
        val record = JSONObject().put("name", name)
        try {
            action()
            record.put("passed", true)
            sendStatus(0, Bundle().apply { putString("stream", "PASS $name\n") })
        } catch (t: Throwable) {
            record.put("passed", false).put("error", t.stackTraceToString())
            throw t
        } finally {
            record.put("durationMs", SystemClock.elapsedRealtime() - start)
            results.put(record)
        }
    }

    private suspend fun coreTests() {
        val models = ModelStore(targetContext)
        val fixture = File(targetContext.filesDir, "fixtures/model.gguf")
        check(fixture.length() == ModelStore.SIZE) { "Missing full pinned GGUF fixture" }
        step("native_UI_launch_without_model") {
            launch()
            waitForText("Private · On-device")
            waitForText("Local Agents")
            waitForText("Import the official text GGUF to begin")
            screenshot("onboarding")
            closeActivity()
        }
        step("SQLite_v1_migration_preserves_history_and_memory_CRUD") {
            val name = "migration-proof.db"
            targetContext.deleteDatabase(name)
            val path = targetContext.getDatabasePath(name).apply { parentFile!!.mkdirs() }
            SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
                old.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, turnId TEXT NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, observedAt INTEGER NOT NULL, contextJson TEXT)")
                old.execSQL("INSERT INTO messages (turnId, role, content, observedAt, contextJson) VALUES ('legacy', 'user', 'keep this history', 123, '{\"role\":\"user\",\"content\":\"keep this history\"}')")
                old.version = 1
            }
            var migrated = Room.databaseBuilder(targetContext, ChatDatabase::class.java, name).addMigrations(ChatDatabase.MIGRATION_1_2).build()
            try {
                val legacy = migrated.messages().all().single()
                check(legacy.content == "keep this history" && legacy.conversationId == DEFAULT_CONVERSATION && legacy.contextJson != null)
                migrated.messages().saveMemory(MemoryFact(content = "initial note"))
                val note = migrated.messages().allMemories().single()
                migrated.messages().editMemory(note.id, "edited note")
            } finally { migrated.close() }
            migrated = Room.databaseBuilder(targetContext, ChatDatabase::class.java, name).addMigrations(ChatDatabase.MIGRATION_1_2).build()
            try {
                val note = migrated.messages().allMemories().single()
                check(note.content == "edited note")
                migrated.messages().deleteMemory(note.id)
                check(migrated.messages().allMemories().isEmpty() && migrated.messages().all().size == 1)
            } finally { migrated.close(); targetContext.deleteDatabase(name) }
        }
        step("corrupt_import_rejected_without_partial_model") {
            val bad = File(targetContext.cacheDir, "bad.gguf").apply { writeText("not a GGUF") }
            var rejected = false
            try { models.importModel(Uri.fromFile(bad)) {} }
            catch (e: IllegalStateException) { rejected = e.message.orEmpty().contains("Checksum mismatch") }
            finally { bad.delete() }
            check(rejected && !models.model.exists())
            check(!File(models.model.parentFile, "text.gguf.partial").exists())
        }
        step("cancelled_import_removes_partial_file") {
            coroutineScope {
                val imported = AtomicInteger()
                val pending = async {
                    models.importModel(Uri.fromFile(fixture)) {
                        imported.incrementAndGet()
                        throw CancellationException("Scripted import cancellation")
                    }
                }
                try { pending.await(); error("Import did not cancel") }
                catch (_: CancellationException) { check(imported.get() > 0) }
            }
            check(!models.model.exists())
            check(!File(models.model.parentFile, "text.gguf.partial").exists())
        }
        step("real_model_copy_size_and_SHA256_import") {
            val hash = models.importModel(Uri.fromFile(fixture)) {}
            check(hash == ModelStore.SHA256 && models.model.length() == ModelStore.SIZE)
            evidence.put("modelSHA256", hash)
            // Free the source copy only after app import verifies it.
            check(fixture.delete())
        }
        step("low_storage_import_rejected_and_verified_model_preserved") {
            val filler = File(targetContext.filesDir, "fixtures/low-space.dat")
            try {
                val targetFree = ModelStore.SIZE + 128L * 1024 * 1024
                var written = 0L
                FileOutputStream(filler).use { stream ->
                    val chunk = ByteArray(4 * 1024 * 1024) { 1 }
                    while (StatFs(targetContext.filesDir.path).availableBytes > targetFree) {
                        check(written < 6L * 1024 * 1024 * 1024) { "Refusing to allocate more than 6 GB of test filler" }
                        stream.write(chunk)
                        written += chunk.size
                    }
                    stream.fd.sync()
                }
                val available = StatFs(targetContext.filesDir.path).availableBytes
                check(available < ModelStore.SIZE + 256L * 1024 * 1024)
                var rejected = false
                try { models.importModel(Uri.fromFile(models.model)) {} }
                catch (e: IllegalStateException) { rejected = e.message.orEmpty().contains("1.5 GB free") }
                check(rejected) { "Low-storage import was not rejected" }
                check(models.model.length() == ModelStore.SIZE)
                check(!File(models.model.parentFile, "text.gguf.partial").exists())
                evidence.put("lowStorageAvailableBytes", available).put("testFillerBytes", written)
            } finally { check(!filler.exists() || filler.delete()) { "Cannot remove disposable storage fixture" } }
            check(StatFs(targetContext.filesDir.path).availableBytes > ModelStore.SIZE + 256L * 1024 * 1024)
        }
        step("Android_JNI_load_and_stream_real_model") {
            InferenceRepository.load(models.model.path)
            val chunks = AtomicInteger()
            val response = JSONObject(InferenceRepository.generate(
                """[{"role":"user","content":"Reply with only the word LOCAL."}]""", "[]") {
                if (it.isNotBlank()) chunks.incrementAndGet()
            })
            check(response.optString("content").contains("LOCAL"))
            check(chunks.get() > 0)
            evidence.put("localReply", response.getString("content"))
        }
        step("actual_Android_clock_battery_and_policy") {
            val permissions = targetContext.packageManager.getPackageInfo(
                targetContext.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
            check("android.permission.INTERNET" !in permissions)
            check("android.permission.RECORD_AUDIO" !in permissions)
            evidence.put("requestedPermissions", JSONArray(permissions.toList()))
            val tools = ToolRegistry(targetContext)
            val clock = tools.execute("get_local_time", "{}", "emulator-clock")
            check(clock.getBoolean("success"))
            val seen = ZonedDateTime.parse(clock.getJSONObject("data").getString("datetime"))
            check(kotlin.math.abs(seen.toInstant().toEpochMilli() - System.currentTimeMillis()) < 5000)
            val battery = tools.execute("get_battery_status", "{}", "emulator-battery")
            check(battery.getBoolean("success"))
            check(battery.getJSONObject("data").getInt("percent") in 0..100)
            check(!tools.execute("run_shell_command", "{}", "negative").getBoolean("success"))
            check(!tools.execute("get_local_time", "{\"extra\":1}", "args").getBoolean("success"))
            check(!tools.execute("get_local_time", "{} trailing", "trailing").getBoolean("success"))
            evidence.put("AndroidClock", clock).put("emulatedBattery", battery)
        }
        step("configured_JNI_generation_reports_real_metrics_and_rejects_invalid_settings") {
            val prompt = """[{"role":"user","content":"Reply with only the word LOCAL."}]"""
            val result = JSONObject(InferenceRepository.generateConfigured(prompt, "[]", GenerationSettings(temperature = 0.2f, topK = 20, repeatPenalty = 1.05f)) {})
            check(result.optString("content").contains("LOCAL"))
            val measured = InferenceMetrics().add(result.getJSONObject("local_metrics"))
            check(measured.promptTokens > 0 && measured.generatedTokens > 0 && measured.promptMs > 0 && measured.generationMs > 0)
            check(measured.ingestionSpeed!!.isFinite() && measured.generationSpeed!!.isFinite())
            var rejected = false
            try { InferenceRepository.generateConfigured(prompt, "[]", GenerationSettings(temperature = 3f)) {} }
            catch (e: IllegalStateException) { rejected = e.message.orEmpty().contains("settings out of supported range") }
            check(rejected)
            val thinkingSupported = InferenceRepository.supportsThinking()
            if (thinkingSupported) {
                val thought = JSONObject(InferenceRepository.generateConfigured(prompt, "[]", GenerationSettings(thinking = true, maxTokens = 1024)) {})
                check(thought.optString("content").contains("LOCAL"))
                AnswerPolicy.validate(thought.getString("content"))
                evidence.put("thinkingEnabledMetrics", thought.getJSONObject("local_metrics"))
            }
            evidence.put("nativeMetrics", JSONObject(measured.toJson())).put("supportsThinking", thinkingSupported)
        }
        val db = Room.databaseBuilder(targetContext, ChatDatabase::class.java, "validation-chat.db").build()
        try {
            val agent = AgentCoordinator(db.messages(), ToolRegistry(targetContext))
            step("model_Android_time_tool_round_trip_and_evidence") {
                db.messages().clear()
                agent.answer("What time is it on my phone? Use get_local_time and include the exact ISO datetime from the tool result.", {}, {})
                val rows = db.messages().all()
                val tool = rows.firstOrNull { it.role == "tool" && it.content.startsWith("get_local_time\n") }
                    ?: error("Model produced no actual Android clock tool evidence")
                val observed = JSONObject(tool.content.substringAfter('\n'))
                check(observed.getBoolean("success"))
                check(rows.last().role == "assistant" && rows.last().content.isNotBlank())
                val timestamp = observed.getJSONObject("data").getString("datetime")
                check(rows.last().content.contains(timestamp.take(16))) { "Follow-up did not repeat the observed date and minute" }
                evidence.put("timeToolEvidence", observed).put("timeFollowup", rows.last().content)
            }
            step("two_consecutive_contextual_model_turns") {
                agent.answer("Remember the test word mango. Reply briefly.", {}, {})
                agent.answer("What test word did I just give you?", {}, {})
                check(db.messages().all().last().content.contains("mango", ignoreCase = true))
            }
            step("SQLite_conversation_isolation_and_saved_memory_ground_real_reply") {
                val dao = db.messages()
                dao.createConversation(Conversation("private-a", "Private chat A"))
                dao.createConversation(Conversation("memory-b", "Memory chat B"))
                dao.insert(ChatMessage(turnId = "private", role = "user", content = "Private word saffron", conversationId = "private-a"))
                dao.saveMemory(MemoryFact(content = "My saved test word is cedar."))
                agent.answer("What is my saved test word? Answer one word.", {}, {}, conversationId = "memory-b", memory = dao.allMemories().map { it.content })
                val rows = dao.inConversation("memory-b")
                check(rows.last().role == "assistant" && rows.last().content.contains("cedar", ignoreCase = true))
                check(rows.none { it.content.contains("saffron", ignoreCase = true) })
                check(InferenceMetrics.fromJson(checkNotNull(rows.last().metricsJson)).calls > 0)
                dao.clearConversation("memory-b")
                check(dao.inConversation("memory-b").isEmpty() && dao.inConversation("private-a").size == 1 && dao.allMemories().size == 1)
                evidence.put("savedMemoryReply", rows.last().content)
            }
        } finally { db.close() }
        step("native_cancellation_then_successful_generation") {
            var cancelled = false
            try {
                InferenceRepository.generate(
                    """[{"role":"user","content":"Write a long story about a journey."}]""", "[]") {
                    if (it.isNotBlank()) InferenceRepository.cancel()
                }
            } catch (e: IllegalStateException) { cancelled = e.message == "Cancelled" }
            check(cancelled)
            val recovery = JSONObject(InferenceRepository.generate(
                """[{"role":"user","content":"Reply with only the word LOCAL."}]""", "[]") {})
            check(recovery.optString("content").contains("LOCAL"))
        }
        step("unloaded_JNI_context_rejects_generation") {
            InferenceRepository.unload()
            var rejected = false
            try { InferenceRepository.generate("[]", "[]") {} }
            catch (e: IllegalStateException) { rejected = e.message.orEmpty().contains("Import and load") }
            check(rejected)
        }
        step("Compose_SAF_picker_imports_actual_pinned_model") {
            launch()
            waitForText("Ready", 180000)
            clickText("Models")
            clickText("Unload and delete model")
            waitForText("Model removed")
            clickText("Models")
            clickText("Import GGUF")
            val roots = waitNode { it.contentDescription?.toString() == "Show roots" }
            check(roots.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            clickText("Downloads")
            clickText("LFM3-1.7B-Q5_K_M.gguf")
            waitForText("Ready · verified local model", 180000)
            screenshot("saf-import")
        }
        step("Compose_send_button_to_real_model_and_Room") {
            val answer = completedUiTurn("Reply with only the word LOCAL.")
            check(answer.content.contains("LOCAL"))
            waitForText("Local Agents · model answer")
            screenshot("real-chat")
        }
        step("Compose_cancel_then_send_next_real_model_turn") {
            sendFromUi("Write a long story about a journey.")
            waitForText("Working for ")
            clickText("Cancel")
            waitForText("Cancelled", 30000)
            check(completedUiTurn("Reply with only the word LOCAL.").content.contains("LOCAL"))
            screenshot("cancel-recovery")
        }
        step("Compose_controls_memory_and_collapsible_real_tool_evidence") {
            clickText("Controls")
            waitForText("Generation controls")
            waitForText("Temperature")
            screenshot("generation-controls")
            clickText("Reset defaults")
            clickText("Done")
            clickText("Memory")
            waitForText("Saved memory")
            val field = waitNode { it.isEditable && it.isEnabled }
            check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "UI memory proof") }))
            waitNode { it.isEditable && it.text?.toString() == "UI memory proof" }
            clickText("Save memory")
            waitForText("UI memory proof")
            screenshot("saved-memory")
            clickText("Delete")
            clickText("Done")
            val answer = completedUiTurn("What time is it on my phone? Use get_local_time and include the exact ISO datetime from the tool result.")
            check(answer.metricsJson != null)
            waitForText("Worked for ")
            var visible = false
            for (attempt in 0 until 10) {
                if (find(uiAutomation.rootInActiveWindow) { it.text?.toString() == "Show evidence ▾" && it.isVisibleToUser } != null) { visible = true; break }
                val list = find(uiAutomation.rootInActiveWindow) { it.isScrollable }
                list?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                SystemClock.sleep(300)
            }
            check(visible) { "Tool evidence dropdown not reachable" }
            clickText("Show evidence ▾")
            waitForText("success")
            screenshot("expanded-tool-evidence")
            clickText("Hide evidence ▴")
            screenshot("collapsed-tool-evidence")
        }
        step("Compose_rotation_preserves_history_and_native_session") {
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            check(activity!!.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            check(completedUiTurn("Reply with only the word LOCAL.").content.contains("LOCAL"))
            screenshot("landscape-chat")
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            check(activity!!.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
            waitNode { it.isEditable && it.isEnabled }
            screenshot("portrait-after-rotation")
            closeActivity()
        }
    }

    private suspend fun restartTests() {
        step("Room_history_survives_actual_process_restart") {
            // The shell driver force-stops the process between core and restart.
            val db = Room.databaseBuilder(targetContext, ChatDatabase::class.java, "lfm-chats.db").build()
            try {
                val rows = db.messages().all()
                check(rows.any { it.role == "user" && it.content == "Reply with only the word LOCAL." })
                check(rows.last().role == "assistant" && rows.last().content.contains("LOCAL"))
            } finally { db.close() }
            launch()
            waitForText("Local Agents · model answer")
            waitForText("Ready", 180000)
            screenshot("history-after-process-restart")
        }
        step("reply_after_process_restart_and_model_reload") {
            val answer = completedUiTurn("Say hello in one short sentence.")
            check(answer.content.isNotBlank())
            evidence.put("postRestartReply", answer.content)
        }
        step("clear_chat_removes_Room_messages") {
            clickText("Clear chat")
            clickText("Delete history")
            waitForText("History cleared")
            waitForText("Your conversations stay on this phone.")
            val db = Room.databaseBuilder(targetContext, ChatDatabase::class.java, "lfm-chats.db").build()
            try { check(db.messages().all().isEmpty()) } finally { db.close() }
            screenshot("cleared-chat")
            closeActivity()
        }
    }

    private fun launch() {
        activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitForIdleSync()
    }
    private fun closeActivity() {
        activity?.let { a -> runOnMainSync { a.finish() }; waitForIdleSync() }
        activity = null
    }
    private fun rotate(orientation: Int) {
        val monitor = addMonitor(MainActivity::class.java.name, null, false)
        try {
            runOnMainSync { checkNotNull(activity).requestedOrientation = orientation }
            activity = checkNotNull(waitForMonitorWithTimeout(monitor, 30000)) { "Activity did not recreate after rotation" }
            waitForIdleSync()
        } finally { removeMonitor(monitor) }
    }
    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) find(node.getChild(i), predicate)?.let { return it }
        return null
    }
    private fun waitNode(timeout: Long = 30000, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val end = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < end) {
            find(uiAutomation.rootInActiveWindow, predicate)?.let { return it }
            SystemClock.sleep(200)
        }
        error("Timed out waiting for UI node; $phase")
    }
    private fun waitForText(text: String, timeout: Long = 30000) =
        waitNode(timeout) { it.text?.toString()?.contains(text) == true }
    private fun clickText(text: String) {
        val end = SystemClock.elapsedRealtime() + 30000
        while (SystemClock.elapsedRealtime() < end) {
            var node = find(uiAutomation.rootInActiveWindow) { it.text?.toString() == text }
            while (node != null && !node.isClickable) node = node.parent
            if (node?.isEnabled == true && node.isVisibleToUser && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            SystemClock.sleep(100)
        }
        error("Timed out waiting for enabled clickable $text")
    }
    private fun sendFromUi(text: String) {
        val field = waitNode { node -> node.isEditable && node.isEnabled && node.isVisibleToUser &&
            find(node) { it.text?.toString() == "Message Local Agents" } != null }
        check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }))
        // ACTION_SET_TEXT is acknowledged before Compose necessarily recomposes.
        waitNode { it.isEditable && it.text?.toString() == text }
        clickText("Send")
    }
    private suspend fun completedUiTurn(text: String): ChatMessage {
        val db = Room.databaseBuilder(targetContext, ChatDatabase::class.java, "lfm-chats.db").build()
        try {
            val before = db.messages().all().lastOrNull()?.id ?: 0L
            sendFromUi(text)
            val end = SystemClock.elapsedRealtime() + 180000
            while (SystemClock.elapsedRealtime() < end) {
                val fresh = db.messages().all().filter { it.id > before }
                fresh.firstOrNull { it.role == "error" }?.let { error("App rejected turn: ${it.content}") }
                fresh.lastOrNull { it.role == "assistant" }?.let {
                    waitForText("Finished")
                    return it
                }
                SystemClock.sleep(200)
            }
            error("No new persisted assistant reply within 180 seconds")
        } finally { db.close() }
    }
    private fun dumpUi() {
        fun snapshot(node: AccessibilityNodeInfo?, depth: Int): JSONObject {
            if (node == null || depth > 20) return JSONObject()
            val children = JSONArray()
            for (i in 0 until node.childCount) children.put(snapshot(node.getChild(i), depth + 1))
            return JSONObject().put("text", node.text?.toString()).put("class", node.className?.toString())
                .put("description", node.contentDescription?.toString()).put("visible", node.isVisibleToUser)
                .put("enabled", node.isEnabled).put("clickable", node.isClickable)
                .put("editable", node.isEditable).put("children", children)
        }
        File(output, "$phase-ui.json").writeText(snapshot(uiAutomation.rootInActiveWindow, 0).toString(2))
    }
    private fun screenshot(name: String) {
        val bitmap = uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        File(output, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
