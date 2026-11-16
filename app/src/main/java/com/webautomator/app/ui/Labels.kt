package com.webautomator.app.ui

import android.content.Context
import com.webautomator.app.R
import com.webautomator.app.data.ActionType
import com.webautomator.app.data.ConditionType
import com.webautomator.app.data.NotifyMode
import com.webautomator.app.data.Status

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

fun iconFor(t: ActionType): Int = when (t) {
    ActionType.OPEN_URL, ActionType.NAVIGATE -> R.drawable.ic_link
    ActionType.WAIT_LOAD, ActionType.WAIT -> R.drawable.ic_hourglass
    ActionType.WAIT_ELEMENT -> R.drawable.ic_clock
    ActionType.CLICK -> R.drawable.ic_cursor
    ActionType.EXTRACT_TEXT -> R.drawable.ic_text
    ActionType.EXTRACT_NUMBER -> R.drawable.ic_number
    ActionType.EXTRACT_ATTR -> R.drawable.ic_link
    ActionType.EXTRACT_TABLE -> R.drawable.ic_table
    ActionType.EXTRACT_LIST -> R.drawable.ic_list
    ActionType.REPEAT -> R.drawable.ic_refresh
    ActionType.SAVE_RESULTS -> R.drawable.ic_download
    ActionType.NOTIFY -> R.drawable.ic_notification
}

fun needsSelector(t: ActionType): Boolean = t == ActionType.WAIT_ELEMENT || t == ActionType.CLICK || t.isExtraction()

fun needsValue(t: ActionType): Boolean =
    t == ActionType.OPEN_URL || t == ActionType.NAVIGATE || t == ActionType.WAIT ||
        t == ActionType.REPEAT || t == ActionType.NOTIFY

fun usesTimeout(t: ActionType): Boolean =
    needsSelector(t) || t == ActionType.OPEN_URL || t == ActionType.NAVIGATE || t == ActionType.WAIT_LOAD

fun valueHint(t: ActionType): Int = when (t) {
    ActionType.WAIT -> R.string.hint_ms
    ActionType.REPEAT -> R.string.hint_times
    ActionType.NOTIFY -> R.string.hint_message
    else -> R.string.url_hint
}

class StatusStyle(val label: Int, val fg: Int, val bg: Int, val icon: Int)

fun statusStyle(name: String): StatusStyle = when (name) {
    Status.OK.name -> StatusStyle(R.string.st_ok, R.color.success, R.color.success_bg, R.drawable.ic_check_circle)
    Status.RUNNING.name -> StatusStyle(R.string.st_running, R.color.brand, R.color.brand_container, R.drawable.ic_clock)
    Status.ERROR.name -> StatusStyle(R.string.st_error, R.color.danger, R.color.danger_bg, R.drawable.ic_error)
    Status.NEEDS_ATTENTION.name -> StatusStyle(R.string.st_attention, R.color.warning, R.color.warning_bg, R.drawable.ic_warning)
    else -> StatusStyle(R.string.st_idle, R.color.text_secondary, R.color.stroke, R.drawable.ic_clock)
}

fun shortInterval(c: Context, minutes: Int): String =
    if (minutes >= 60 && minutes % 60 == 0) c.getString(R.string.hour_fmt, minutes / 60)
    else c.getString(R.string.min_fmt, minutes)

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

fun clipText(s: String, n: Int = 200): String = if (s.length > n) s.take(n) + "…" else s
