package com.webautomator.app.util

import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONTokener
import kotlin.coroutines.resume

suspend fun WebView.evalJs(js: String): String? = suspendCancellableCoroutine { cont ->
    evaluateJavascript(js) { result ->
        if (cont.isActive) cont.resume(result)
    }
}

/** evaluateJavascript returns a JSON literal; this decodes a JS string result. */
fun decodeJsString(raw: String?): String? {
    if (raw == null || raw == "null") return null
    val v = try {
        JSONTokener(raw).nextValue()
    } catch (e: Exception) {
        return null
    }
    return v as? String
}
