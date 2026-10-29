package dev.lfm.mobile.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class ToolRegistry(private val context: Context) {
    val definitions: String = JSONArray().apply {
        listOf("get_local_time" to "Read current date, time and timezone from this Android device",
            "get_battery_status" to "Read device battery percentage and charging state").forEach { (name, description) ->
            put(JSONObject().put("type", "function").put("function", JSONObject()
                .put("name", name).put("description", description)
                .put("parameters", JSONObject().put("type", "object").put("properties", JSONObject()).put("additionalProperties", false))))
        }
    }.toString()

    fun execute(name: String, arguments: String, requestId: String): JSONObject {
        val observed = ZonedDateTime.now()
        val result = JSONObject().put("request_id", requestId).put("observedAt", observed.toString())
            .put("requiresUserAction", false)
        return try {
            ToolPolicy.validate(name, arguments)
            val data = when (name) {
                "get_local_time" -> JSONObject().put("datetime", observed.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .put("weekday", observed.dayOfWeek.name).put("timezone", observed.zone.id)
                "get_battery_status" -> {
                    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                        ?: error("Android returned no battery reading")
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    check(level >= 0 && scale > 0) { "Battery percentage unavailable" }
                    JSONObject().put("percent", level * 100 / scale)
                        .put("plugged", intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0)
                }
                else -> error("Tool not permitted")
            }
            result.put("success", true).put("data", data).put("sourceOrEvidence", "Android device API: $name")
        } catch (e: Exception) {
            result.put("success", false).put("errorCode", "TOOL_REJECTED_OR_UNAVAILABLE").put("error", e.message)
        }
    }
}
