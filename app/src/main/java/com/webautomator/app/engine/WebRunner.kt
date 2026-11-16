package com.webautomator.app.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.webautomator.app.data.Action
import com.webautomator.app.data.ActionType
import com.webautomator.app.util.UrlResult
import com.webautomator.app.util.UrlUtils
import com.webautomator.app.util.decodeJsString
import com.webautomator.app.util.evalJs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

enum class ErrorKind {
    INVALID_URL, NETWORK, TIMEOUT, SELECTOR_MISSING, SESSION_EXPIRED,
    JS_ERROR, EMPTY, NO_INTERNET, DOMAIN, BLOCKED
}

class ScriptException(val kind: ErrorKind, message: String) : Exception(message)

interface RunHooks {
    suspend fun save(values: Map<String, String>)
    suspend fun notify(message: String, values: Map<String, String>)
}

/**
 * Executes an action list inside a WebView it owns.
 * A WebView is not headless: it is created and driven on the main thread, attached to no window,
 * and destroyed when the run ends. Total run time is bounded so it can never hang indefinitely.
 */
class WebRunner(context: Context) {
    private val appCtx = context.applicationContext
    private var view: WebView? = null
    private var waiter: CompletableDeferred<Unit>? = null
    private val allowedHosts = mutableSetOf<String>()
    private var startUrl = ""
    @Volatile private var mainStatus = 0

    suspend fun run(actions: List<Action>, startUrl: String, hooks: RunHooks): Map<String, String> =
        withContext(Dispatchers.Main) {
            this@WebRunner.startUrl = startUrl
            val wv = createWebView()
            view = wv
            try {
                withTimeout(TOTAL_TIMEOUT_MS) { runActions(actions, hooks) }
            } catch (e: TimeoutCancellationException) {
                throw ScriptException(ErrorKind.TIMEOUT, "run exceeded ${TOTAL_TIMEOUT_MS / 1000}s")
            } finally {
                try {
                    CookieManager.getInstance().flush()
                    wv.stopLoading()
                    wv.destroy()
                } catch (e: Exception) {
                    // ignore cleanup problems
                }
                view = null
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val wv = WebView(appCtx)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.allowFileAccess = false
        wv.settings.allowContentAccess = false
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        // Give the detached WebView a real layout size so responsive pages render their content.
        wv.layout(0, 0, 1080, 1920)
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                return scheme != "http" && scheme != "https"
            }

            override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) {
                // nothing: waiter is created before each loadUrl
            }

            override fun onPageFinished(v: WebView, url: String?) {
                waiter?.complete(Unit)
            }

            override fun onReceivedHttpError(v: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame) mainStatus = errorResponse.statusCode
            }

            override fun onReceivedError(v: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    waiter?.completeExceptionally(
                        ScriptException(ErrorKind.NETWORK, error.description?.toString() ?: "load error")
                    )
                }
            }
        }
        return wv
    }

    private suspend fun runActions(actions: List<Action>, hooks: RunHooks): Map<String, String> {
        val values = LinkedHashMap<String, String>()
        allow(startUrl)
        val first = actions.firstOrNull()?.type
        if (first != ActionType.OPEN_URL && first != ActionType.NAVIGATE) load(startUrl, 30_000)
        var prev: Action? = null
        for (a in actions) {
            exec(a, values, hooks, prev, "")
            if (a.type != ActionType.REPEAT) prev = a
        }
        return values
    }

    private suspend fun exec(
        a: Action,
        values: LinkedHashMap<String, String>,
        hooks: RunHooks,
        prev: Action?,
        suffix: String
    ) {
        val timeout = a.timeoutMs.coerceIn(1_000L, 60_000L)
        when (a.type) {
            ActionType.OPEN_URL, ActionType.NAVIGATE ->
                load(a.value.ifBlank { startUrl }, timeout.coerceAtLeast(10_000L))
            ActionType.WAIT_LOAD -> waitReady(timeout)
            ActionType.WAIT_ELEMENT -> {
                needSelector(a)
                awaitResult(locator(a), elementJs(a, "return {found:true};"), timeout) {
                    it.optBoolean("found")
                }
            }
            ActionType.WAIT -> delay(a.value.toLongOrNull()?.coerceIn(0L, 60_000L) ?: 1_000L)
            ActionType.CLICK -> {
                needSelector(a)
                awaitResult(
                    locator(a),
                    elementJs(a, "try{e.scrollIntoView({block:'center'});}catch(x){} e.click(); return {found:true};"),
                    timeout
                ) { it.optBoolean("found") }
                delay(800)
                settle()
            }
            ActionType.EXTRACT_TEXT -> {
                needSelector(a)
                val o = awaitResult(locator(a), elementJs(a, TEXT_BODY), timeout) {
                    it.optBoolean("found") && it.optString("text").isNotBlank()
                }
                values[key(a, values, suffix)] = o.optString("text")
            }
            ActionType.EXTRACT_NUMBER -> {
                needSelector(a)
                val o = awaitResult(locator(a), elementJs(a, TEXT_BODY), timeout) {
                    it.optBoolean("found") && NumberParser.parse(it.optString("text")) != null
                }
                val n = NumberParser.parse(o.optString("text"))
                    ?: throw ScriptException(ErrorKind.EMPTY, "no number in element")
                values[key(a, values, suffix)] = NumberParser.format(n)
            }
            ActionType.EXTRACT_ATTR -> {
                needSelector(a)
                if (a.attr.isBlank()) throw ScriptException(ErrorKind.EMPTY, "attribute name missing")
                val body = "var a=" + JSONObject.quote(a.attr) +
                    ";var v=(a==='href'&&e.href)?e.href:((a==='src'&&e.src)?e.src:e.getAttribute(a));" +
                    "return {found:true,value:(v===null||v===undefined)?'':String(v)};"
                val o = awaitResult(locator(a), elementJs(a, body), timeout) {
                    it.optBoolean("found") && it.optString("value").isNotBlank()
                }
                values[key(a, values, suffix)] = o.optString("value")
            }
            ActionType.EXTRACT_TABLE -> {
                needSelector(a)
                val o = awaitResult(locator(a), elementJs(a, TABLE_BODY), timeout) {
                    it.optBoolean("found") && (it.optJSONArray("rows")?.length() ?: 0) > 0
                }
                val rows = o.getJSONArray("rows")
                val lines = ArrayList<String>()
                for (i in 0 until rows.length()) {
                    val r = rows.optJSONArray(i) ?: continue
                    val cells = ArrayList<String>()
                    for (j in 0 until r.length()) cells.add(r.optString(j))
                    val line = cells.joinToString(" | ")
                    if (line.isNotBlank()) lines.add(line)
                }
                if (lines.isEmpty()) throw ScriptException(ErrorKind.EMPTY, "table is empty")
                values[key(a, values, suffix)] = lines.joinToString("\n")
            }
            ActionType.EXTRACT_LIST -> {
                needSelector(a)
                val js = listJs(a)
                val o = awaitResult(locator(a), js, timeout) {
                    it.optBoolean("found") && (it.optJSONArray("items")?.length() ?: 0) > 0
                }
                val items = o.getJSONArray("items")
                val lines = ArrayList<String>()
                for (i in 0 until items.length()) {
                    val t = items.optString(i).replace('\n', ' ').trim()
                    if (t.isNotEmpty()) lines.add(t)
                }
                if (lines.isEmpty()) throw ScriptException(ErrorKind.EMPTY, "list items are empty")
                values[key(a, values, suffix)] = lines.joinToString("\n")
            }
            ActionType.REPEAT -> {
                val p = prev ?: throw ScriptException(ErrorKind.JS_ERROR, "nothing to repeat")
                val n = a.value.toIntOrNull()?.coerceIn(1, 50) ?: 1
                for (i in 1..n) {
                    delay(1_000)
                    exec(p, values, hooks, null, "#${i + 1}")
                }
            }
            ActionType.SAVE_RESULTS -> hooks.save(LinkedHashMap(values))
            ActionType.NOTIFY -> hooks.notify(a.value, LinkedHashMap(values))
        }
    }

    private fun needSelector(a: Action) {
        if (!a.hasLocator()) throw ScriptException(ErrorKind.SELECTOR_MISSING, "(empty selector)")
    }

    private fun key(a: Action, values: Map<String, String>, suffix: String): String =
        a.name.ifBlank { "value${values.size + 1}" } + suffix

    private fun allow(url: String) {
        val h = UrlUtils.hostOf(url)
        if (h.isNotEmpty()) allowedHosts.add(h)
    }

    private suspend fun load(url: String, timeoutMs: Long) {
        val valid = UrlUtils.normalize(url) as? UrlResult.Valid
            ?: throw ScriptException(ErrorKind.INVALID_URL, url)
        allow(valid.url)
        val w = CompletableDeferred<Unit>()
        waiter = w
        mainStatus = 0
        val wv = view ?: throw ScriptException(ErrorKind.JS_ERROR, "no webview")
        wv.loadUrl(valid.url)
        val done = withTimeoutOrNull(timeoutMs) { w.await() }
        if (done == null) {
            wv.stopLoading()
            throw ScriptException(ErrorKind.TIMEOUT, "page load: ${valid.url}")
        }
        if (BlockDetector.isBlockStatus(mainStatus)) {
            throw ScriptException(ErrorKind.BLOCKED, "HTTP $mainStatus")
        }
        waitReady(timeoutMs)
        checkBlocked()
    }

    private suspend fun waitReady(timeoutMs: Long) {
        var state = ""
        val ok = pollUntil(timeoutMs) {
            state = evalJsonOrNull("({state:document.readyState})")?.optString("state") ?: ""
            state == "complete"
        }
        if (!ok && state != "interactive") throw ScriptException(ErrorKind.TIMEOUT, "page not ready")
    }

    /** Stops (never works around) pages that ask for human verification or refuse access. */
    private suspend fun checkBlocked() {
        val o = evalJsonOrNull(
            "({title:document.title||'',text:((document.body&&document.body.innerText)||'').slice(0,1500)," +
                "challenge:!!document.querySelector('#challenge-form,iframe[src*=\"challenges.cloudflare.com\"]')})"
        ) ?: return
        if (o.optBoolean("challenge") || BlockDetector.isBlockText(o.optString("title") + " " + o.optString("text"))) {
            throw ScriptException(ErrorKind.BLOCKED, "human verification or access blocked")
        }
    }

    private suspend fun settle() {
        try {
            waitReady(8_000)
            checkBlocked()
        } catch (e: ScriptException) {
            if (e.kind == ErrorKind.DOMAIN || e.kind == ErrorKind.BLOCKED) throw e
        }
    }

    private suspend fun pollUntil(timeoutMs: Long, check: suspend () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (true) {
            if (check()) return true
            if (System.currentTimeMillis() >= end) return false
            delay(300)
        }
    }

    private suspend fun awaitResult(
        selector: String,
        js: String,
        timeoutMs: Long,
        ok: (JSONObject) -> Boolean
    ): JSONObject {
        var last: JSONObject? = null
        val success = pollUntil(timeoutMs) {
            last = evalJsonOrNull(js)
            val l = last
            l != null && ok(l)
        }
        val result = last
        if (success && result != null) return result
        if (result == null || !result.optBoolean("found")) {
            val url = view?.url ?: ""
            if (Regex("(login|signin|sign-in|auth)", RegexOption.IGNORE_CASE).containsMatchIn(UrlUtils.pageKey(url).substringAfter("://"))) {
                throw ScriptException(ErrorKind.SESSION_EXPIRED, "login page: $selector")
            }
            throw ScriptException(ErrorKind.SELECTOR_MISSING, selector)
        }
        throw ScriptException(ErrorKind.EMPTY, "empty result: $selector")
    }

    private suspend fun evalJsonOrNull(expr: String): JSONObject? {
        return try {
            evalJson(expr)
        } catch (e: ScriptException) {
            if (e.kind == ErrorKind.DOMAIN) throw e
            null
        }
    }

    private suspend fun evalJson(expr: String): JSONObject {
        val wv = view ?: throw ScriptException(ErrorKind.JS_ERROR, "no webview")
        checkDomain(wv)
        val wrapped = "(function(){try{return JSON.stringify(" + expr +
            ");}catch(err){return JSON.stringify({__err:String(err)});}})()"
        val inner = decodeJsString(wv.evalJs(wrapped))
            ?: throw ScriptException(ErrorKind.JS_ERROR, "no result")
        val obj = try {
            JSONObject(inner)
        } catch (e: Exception) {
            throw ScriptException(ErrorKind.JS_ERROR, "bad result")
        }
        if (obj.has("__err")) throw ScriptException(ErrorKind.JS_ERROR, obj.optString("__err"))
        return obj
    }

    /** Scripts only run on the sites the automation was built for. */
    private fun checkDomain(wv: WebView) {
        val url = wv.url ?: return
        val host = UrlUtils.hostOf(url)
        if (host.isEmpty()) return
        if (allowedHosts.none { UrlUtils.sameSite(host, it) }) {
            throw ScriptException(ErrorKind.DOMAIN, host)
        }
    }

    private fun locator(a: Action): String = a.selector.ifBlank { a.xpath }

    /** Finds one element by CSS first, then by XPath as a backup, then runs [body] on it. */
    private fun elementJs(a: Action, body: String): String =
        "(function(){var e=null;var c=" + JSONObject.quote(a.selector) + ";var xp=" + JSONObject.quote(a.xpath) +
            ";if(c){try{e=document.querySelector(c);}catch(x){}}" +
            "if(!e&&xp){try{e=document.evaluate(xp,document,null,XPathResult.FIRST_ORDERED_NODE_TYPE,null).singleNodeValue;}catch(x){}}" +
            "if(!e)return {found:false};" + body + "})()"

    /** All matching elements by CSS, or by XPath when CSS finds none. */
    private fun listJs(a: Action): String =
        "(function(){var n=[];var c=" + JSONObject.quote(a.selector) + ";var xp=" + JSONObject.quote(a.xpath) +
            ";if(c){try{n=Array.prototype.slice.call(document.querySelectorAll(c));}catch(x){}}" +
            "if(!n.length&&xp){try{var r=document.evaluate(xp,document,null,XPathResult.ORDERED_NODE_SNAPSHOT_TYPE,null);" +
            "for(var i=0;i<r.snapshotLength;i++){n.push(r.snapshotItem(i));}}catch(x){}}" +
            "var items=n.map(function(e){return (e.innerText||e.textContent||'').trim();});" +
            "return {found:n.length>0,items:items};})()"

    private companion object {
        const val TOTAL_TIMEOUT_MS = 120_000L
        const val TEXT_BODY = "return {found:true,text:(e.innerText||e.textContent||'').trim()};"
        const val TABLE_BODY =
            "var out=[];var rows=e.querySelectorAll('tr');" +
                "if(rows.length){Array.prototype.forEach.call(rows,function(r){var cells=r.querySelectorAll('th,td');" +
                "out.push(Array.prototype.map.call(cells,function(c){return (c.innerText||c.textContent||'').trim().replace(/\\s+/g,' ');}));});}" +
                "else{Array.prototype.forEach.call(e.children,function(r){out.push([(r.innerText||r.textContent||'').trim().replace(/\\s+/g,' ')]);});}" +
                "return {found:true,rows:out};"
    }
}
