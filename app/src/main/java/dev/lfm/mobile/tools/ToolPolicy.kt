package dev.lfm.mobile.tools

import org.json.JSONObject

object ToolPolicy {
    val allowed = setOf("get_local_time", "get_battery_status")
    fun validate(name: String, arguments: String) {
        require(name in allowed) { "Tool not permitted: $name" }
        val args = JSONObject(arguments)
        require(args.length() == 0) { "Unexpected arguments for $name" }
    }
}
