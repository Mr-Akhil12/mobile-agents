package dev.lfm.mobile.tools

object ToolPolicy {
    val allowed = setOf("get_local_time", "get_battery_status")
    fun validate(name: String, arguments: String) {
        require(name in allowed) { "Tool not permitted: $name" }
        // Every P1 tool has an empty-object schema. Check the entire narrow JSON
        // grammar: JSONObject accepts some comments/trailing data on Android.
        val compact = arguments.filterNot { it == ' ' || it == '\t' || it == '\r' || it == '\n' }
        require(compact == "{}") { "Expected an empty JSON object for $name" }
    }
}
