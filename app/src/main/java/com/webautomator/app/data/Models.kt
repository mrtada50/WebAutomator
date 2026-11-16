package com.webautomator.app.data

import org.json.JSONArray
import org.json.JSONObject

enum class ActionType {
    OPEN_URL, WAIT_LOAD, WAIT_ELEMENT, WAIT, CLICK,
    EXTRACT_TEXT, EXTRACT_NUMBER, EXTRACT_ATTR, EXTRACT_TABLE, EXTRACT_LIST,
    NAVIGATE, REPEAT, SAVE_RESULTS, NOTIFY;

    fun isExtraction(): Boolean = this == EXTRACT_TEXT || this == EXTRACT_NUMBER ||
        this == EXTRACT_ATTR || this == EXTRACT_TABLE || this == EXTRACT_LIST
}

enum class NotifyMode { ALWAYS, ON_CHANGE, ON_CONDITION, ON_DISAPPEAR, NEVER }

enum class NotifyStyle { POPUP, IMPORTANT }

enum class ConditionType { NONE, GREATER, LESS, EQUAL, CONTAINS, NOT_CONTAINS, CHANGED }

enum class Status { IDLE, RUNNING, OK, ERROR, NEEDS_ATTENTION }

inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

data class Action(
    val type: ActionType,
    val selector: String = "",
    val value: String = "",
    val attr: String = "",
    val name: String = "",
    val timeoutMs: Long = 15000,
    /** Optional XPath. When both CSS and XPath are set, CSS is tried first and XPath is the backup. */
    val xpath: String = ""
) {
    fun hasLocator(): Boolean = selector.isNotBlank() || xpath.isNotBlank()

    fun summary(): String =
        listOf(name, selector, if (xpath.isNotBlank()) "XPath: $xpath" else "", value, attr)
            .filter { it.isNotBlank() }.joinToString(" • ")
}

object ActionJson {
    fun encode(list: List<Action>): String {
        val arr = JSONArray()
        for (a in list) {
            val o = JSONObject()
            o.put("type", a.type.name)
            o.put("selector", a.selector)
            o.put("value", a.value)
            o.put("attr", a.attr)
            o.put("name", a.name)
            o.put("timeoutMs", a.timeoutMs)
            o.put("xpath", a.xpath)
            arr.put(o)
        }
        return arr.toString()
    }

    fun decode(json: String): List<Action> {
        val arr = JSONArray(json)
        val out = ArrayList<Action>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val type = enumValues<ActionType>().firstOrNull { it.name == o.optString("type") } ?: continue
            out.add(
                Action(
                    type = type,
                    selector = o.optString("selector"),
                    value = o.optString("value"),
                    attr = o.optString("attr"),
                    name = o.optString("name"),
                    timeoutMs = o.optLong("timeoutMs", 15000),
                    xpath = o.optString("xpath")
                )
            )
        }
        return out
    }
}

object MapJson {
    fun encode(m: Map<String, String>): String {
        val o = JSONObject()
        for ((k, v) in m) o.put(k, v)
        return o.toString()
    }

    fun decode(json: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        try {
            val o = JSONObject(json)
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k] = o.optString(k)
            }
        } catch (e: Exception) {
            // ignore malformed data
        }
        return out
    }
}
