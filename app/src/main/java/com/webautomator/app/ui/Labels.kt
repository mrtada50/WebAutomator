package com.webautomator.app.ui

import android.content.Context
import com.webautomator.app.R
import com.webautomator.app.data.ActionType
import com.webautomator.app.data.ConditionType
import com.webautomator.app.data.NotifyMode
import com.webautomator.app.data.RunEntity
import com.webautomator.app.data.Status
import com.webautomator.app.data.MapJson

fun actionLabel(c: Context, t: ActionType): String = c.getString(
    when (t) {
        ActionType.OPEN_URL -> R.string.at_open_url
        ActionType.WAIT_LOAD -> R.string.at_wait_load
        ActionType.WAIT_ELEMENT -> R.string.at_wait_element
        ActionType.WAIT -> R.string.at_wait
        ActionType.CLICK -> R.string.at_click
        ActionType.EXTRACT_TEXT -> R.string.at_extract_text
        ActionType.EXTRACT_NUMBER -> R.string.at_extract_number
        ActionType.EXTRACT_ATTR -> R.string.at_extract_attr
        ActionType.EXTRACT_TABLE -> R.string.at_extract_table
        ActionType.EXTRACT_LIST -> R.string.at_extract_list
        ActionType.NAVIGATE -> R.string.at_navigate
        ActionType.REPEAT -> R.string.at_repeat
        ActionType.SAVE_RESULTS -> R.string.at_save
        ActionType.NOTIFY -> R.string.at_notify
    }
)

fun statusLabel(c: Context, name: String): String = c.getString(
    when (name) {
        Status.RUNNING.name -> R.string.st_running
        Status.OK.name -> R.string.st_ok
        Status.ERROR.name -> R.string.st_error
        Status.NEEDS_ATTENTION.name -> R.string.st_attention
        else -> R.string.st_idle
    }
)

fun notifyModeLabel(c: Context, m: NotifyMode): String = c.getString(
    when (m) {
        NotifyMode.ALWAYS -> R.string.nm_always
        NotifyMode.ON_CHANGE -> R.string.nm_change
        NotifyMode.ON_CONDITION -> R.string.nm_condition
        NotifyMode.ON_DISAPPEAR -> R.string.nm_disappear
        NotifyMode.NEVER -> R.string.nm_never
    }
)

fun conditionLabel(c: Context, t: ConditionType): String = c.getString(
    when (t) {
        ConditionType.NONE -> R.string.ct_none
        ConditionType.GREATER -> R.string.ct_greater
        ConditionType.LESS -> R.string.ct_less
        ConditionType.EQUAL -> R.string.ct_equal
        ConditionType.CONTAINS -> R.string.ct_contains
        ConditionType.NOT_CONTAINS -> R.string.ct_not_contains
        ConditionType.CHANGED -> R.string.ct_changed
    }
)

/** Errors are stored as "KIND|detail" so they can be shown in the current language. */
fun errorText(c: Context, stored: String): String {
    val kind = stored.substringBefore('|')
    val detail = stored.substringAfter('|', "")
    val res = when (kind) {
        "INVALID_URL" -> R.string.err_invalid_url
        "NETWORK" -> R.string.err_network
        "TIMEOUT" -> R.string.err_timeout
        "SELECTOR_MISSING" -> R.string.err_selector
        "SESSION_EXPIRED" -> R.string.err_session
        "JS_ERROR" -> R.string.err_js
        "EMPTY" -> R.string.err_empty
        "NO_INTERNET" -> R.string.err_no_internet
        "DOMAIN" -> R.string.err_domain
        else -> 0
    }
    val label = if (res != 0) c.getString(res) else kind
    return if (detail.isNotBlank()) "$label: $detail" else label
}

fun bindRun(c: Context, box: android.widget.LinearLayout, r: RunEntity) {
    box.add(c.tv("${r.scriptName} • ${fmtTime(r.timestamp)}", 13f, true))
    if (r.success) {
        val values = MapJson.decode(r.valuesJson)
        val text = values.entries.joinToString("\n") { it.key + ": " + clipText(it.value) }
        box.add(c.tv(text, 13f), top = 2)
        if (r.changeSummary.isNotBlank()) {
            box.add(c.tv(c.getString(R.string.changes) + "\n" + r.changeSummary, 12f), top = 4)
        }
    } else {
        val t = c.tv(errorText(c, r.error), 13f)
        t.setTextColor(android.graphics.Color.parseColor("#D32F2F"))
        box.add(t, top = 2)
    }
}

fun clipText(s: String, n: Int = 200): String = if (s.length > n) s.take(n) + "…" else s
