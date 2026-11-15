package com.webautomator.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.webautomator.app.AppScope
import com.webautomator.app.R
import com.webautomator.app.data.Action
import com.webautomator.app.data.ActionJson
import com.webautomator.app.data.ActionType
import com.webautomator.app.data.AppDatabase
import com.webautomator.app.data.ScriptEntity
import com.webautomator.app.util.UrlResult
import com.webautomator.app.util.UrlUtils
import com.webautomator.app.util.decodeJsString
import com.webautomator.app.util.evalJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Integrated browser with visual element selection; builds a draft automation. */
class CreateFragment : Fragment() {

    private data class Sel(
        val selector: String,
        val xpath: String,
        val tag: String,
        val text: String,
        val table: String,
        val listSelector: String,
        val listCount: Int,
        val attrs: List<String>
    )

    private var webView: WebView? = null
    private lateinit var urlInput: EditText
    private lateinit var progress: ProgressBar
    private lateinit var backBtn: MaterialButton
    private lateinit var forwardBtn: MaterialButton
    private lateinit var selectBtn: MaterialButton
    private lateinit var actionsBtn: MaterialButton
    private lateinit var panel: LinearLayout
    private lateinit var preview: TextView

    private var selecting = false
    private var current: Sel? = null
    private var pollJob: Job? = null
    private var startUrl = ""
    private var lastPage = ""
    private val draft = mutableListOf<Action>()

    private val selectorJs: String by lazy {
        requireContext().assets.open("selector.js").bufferedReader().use { it.readText() }
    }

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            webView?.goBack()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val c = requireContext()
        val root = c.vbox(0)

        // URL row
        val urlRow = c.hbox()
        urlRow.setPadding(c.dp(8), c.dp(8), c.dp(8), 0)
        urlInput = c.field(R.string.url_hint, "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        urlInput.imeOptions = EditorInfo.IME_ACTION_GO
        urlInput.setSingleLine(true)
        urlRow.add(urlInput, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        urlRow.add(c.btn(R.string.open) { openFromInput() }, ViewGroup.LayoutParams.WRAP_CONTENT, start = 8)
        root.add(urlRow)

        // Controls row
        val ctl = c.hbox()
        ctl.setPadding(c.dp(4), 0, c.dp(4), 0)
        backBtn = c.smallBtn("‹") { webView?.goBack() }
        forwardBtn = c.smallBtn("›") { webView?.goForward() }
        val refresh = c.smallBtn("↻") { webView?.reload() }
        selectBtn = c.smallBtn(getString(R.string.select_off)) { toggleSelect() }
        actionsBtn = c.smallBtn("") { showDraft() }
        val saveBtn = c.smallBtn(getString(R.string.save_automation)) { save() }
        ctl.add(backBtn, ViewGroup.LayoutParams.WRAP_CONTENT)
        ctl.add(forwardBtn, ViewGroup.LayoutParams.WRAP_CONTENT)
        ctl.add(refresh, ViewGroup.LayoutParams.WRAP_CONTENT)
        ctl.add(selectBtn, ViewGroup.LayoutParams.WRAP_CONTENT)
        ctl.add(actionsBtn, ViewGroup.LayoutParams.WRAP_CONTENT)
        ctl.add(saveBtn, ViewGroup.LayoutParams.WRAP_CONTENT)
        val ctlScroll = HorizontalScrollView(c)
        ctlScroll.isHorizontalScrollBarEnabled = false
        ctlScroll.addView(ctl)
        root.add(ctlScroll)

        progress = ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal)
        progress.max = 100
        progress.visibility = View.GONE
        root.add(progress, h = c.dp(3))

        // Browser
        val wv = WebView(c)
        webView = wv
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = false
            allowContentAccess = false
        }
        // No addJavascriptInterface: web pages never get a handle to native code.
        // The app reads the selection by polling the page instead.
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                return scheme != "http" && scheme != "https"
            }

            override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
                if (!urlInput.hasFocus() && url != null) urlInput.setText(url)
                current = null
                panel.visibility = View.GONE
            }

            override fun onPageFinished(v: WebView, url: String?) {
                progress.visibility = View.GONE
                updateNav()
                if (selecting) injectAndEnable()
            }

            override fun doUpdateVisitedHistory(v: WebView, url: String?, isReload: Boolean) {
                updateNav()
            }

            override fun onReceivedError(v: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    Toast.makeText(requireContext(), getString(R.string.load_error, error.description), Toast.LENGTH_LONG).show()
                }
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, newProgress: Int) {
                progress.progress = newProgress
            }
        }
        root.add(wv, ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)

        // Selection panel
        panel = c.vbox(8)
        panel.visibility = View.GONE
        preview = c.tv("", 12f)
        preview.maxLines = 5
        panel.add(preview)
        val btns = c.hbox()
        btns.add(c.smallBtn(getString(R.string.a_text)) { pick(ActionType.EXTRACT_TEXT) })
        btns.add(c.smallBtn(getString(R.string.a_number)) { pick(ActionType.EXTRACT_NUMBER) })
        btns.add(c.smallBtn(getString(R.string.a_attr)) { pick(ActionType.EXTRACT_ATTR) })
        btns.add(c.smallBtn(getString(R.string.a_table)) { pick(ActionType.EXTRACT_TABLE) })
        btns.add(c.smallBtn(getString(R.string.a_list)) { pick(ActionType.EXTRACT_LIST) })
        btns.add(c.smallBtn(getString(R.string.a_click)) { pick(ActionType.CLICK) })
        btns.add(c.smallBtn(getString(R.string.a_wait)) { pick(ActionType.WAIT_ELEMENT) })
        val btnScroll = HorizontalScrollView(c)
        btnScroll.isHorizontalScrollBarEnabled = false
        btnScroll.addView(btns)
        panel.add(btnScroll)
        root.add(panel)

        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                openFromInput()
                true
            } else false
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        savedInstanceState?.getBundle(STATE_WEB)?.let { wv.restoreState(it) }
        updateNav()
        updateCount()
        return root
    }

    private fun openFromInput() {
        when (val r = UrlUtils.normalize(urlInput.text.toString())) {
            is UrlResult.Valid -> {
                urlInput.error = null
                urlInput.clearFocus()
                requireContext().getSystemService(InputMethodManager::class.java)
                    ?.hideSoftInputFromWindow(urlInput.windowToken, 0)
                if (draft.isEmpty()) {
                    startUrl = r.url
                    lastPage = UrlUtils.pageKey(r.url)
                }
                webView?.loadUrl(r.url)
            }
            UrlResult.Invalid -> urlInput.error = getString(R.string.invalid_url)
        }
    }

    private fun updateNav() {
        val wv = webView ?: return
        backBtn.isEnabled = wv.canGoBack()
        forwardBtn.isEnabled = wv.canGoForward()
        backBtn.alpha = if (wv.canGoBack()) 1f else 0.4f
        forwardBtn.alpha = if (wv.canGoForward()) 1f else 0.4f
        backCallback.isEnabled = !isHidden && wv.canGoBack()
    }

    private fun updateCount() {
        actionsBtn.text = getString(R.string.actions_count, draft.size)
    }

    // ---- visual selection ----

    private fun toggleSelect() {
        selecting = !selecting
        selectBtn.text = getString(if (selecting) R.string.select_on else R.string.select_off)
        val wv = webView ?: return
        if (selecting) {
            if (wv.url.isNullOrBlank()) {
                Toast.makeText(requireContext(), R.string.open_first, Toast.LENGTH_SHORT).show()
                selecting = false
                selectBtn.text = getString(R.string.select_off)
                return
            }
            Toast.makeText(requireContext(), R.string.sel_hint, Toast.LENGTH_LONG).show()
            injectAndEnable()
            startPolling()
        } else {
            pollJob?.cancel()
            wv.evaluateJavascript("window.__wa&&window.__wa.disable();", null)
        }
    }

    private fun injectAndEnable() {
        webView?.evaluateJavascript(selectorJs + ";window.__wa&&window.__wa.enable();", null)
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val wv = webView ?: break
                val raw = decodeJsString(
                    wv.evalJs(
                        "(function(){try{var s=window.__wa&&window.__wa.current();return s?JSON.stringify(s):null;}catch(e){return null;}})()"
                    )
                )
                if (raw != null) {
                    val sel = parseSel(raw)
                    if (sel != null && sel.selector != current?.selector) {
                        current = sel
                        preview.text = "<${sel.tag}>  ${sel.selector}\n${sel.text}"
                        panel.visibility = View.VISIBLE
                    }
                }
                delay(400)
            }
        }
    }

    private fun parseSel(raw: String): Sel? {
        return try {
            val o = JSONObject(raw)
            val attrsObj = o.optJSONObject("attrs")
            val names = ArrayList<String>()
            if (attrsObj != null) {
                val it = attrsObj.keys()
                while (it.hasNext()) names.add(it.next())
            }
            if (o.optString("href").isNotEmpty() && !names.contains("href")) names.add("href")
            if (o.optString("src").isNotEmpty() && !names.contains("src")) names.add("src")
            Sel(
                selector = o.optString("selector"),
                xpath = o.optString("xpath"),
                tag = o.optString("tag"),
                text = o.optString("text"),
                table = o.optString("table"),
                listSelector = o.optString("listSelector"),
                listCount = o.optInt("listCount"),
                attrs = names
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun pick(type: ActionType) {
        val sel = current ?: return
        when (type) {
            ActionType.CLICK, ActionType.WAIT_ELEMENT ->
                addAction(Action(type, selector = sel.selector))
            ActionType.EXTRACT_ATTR -> askName(true, sel.attrs.firstOrNull() ?: "href") { name, attr ->
                addAction(Action(type, selector = sel.selector, attr = attr, name = name))
            }
            ActionType.EXTRACT_TABLE -> askName(false, "") { name, _ ->
                addAction(Action(type, selector = sel.table.ifBlank { sel.selector }, name = name))
            }
            ActionType.EXTRACT_LIST -> askName(false, "") { name, _ ->
                val s = if (sel.listCount > 1) sel.listSelector else sel.selector
                Toast.makeText(requireContext(), getString(R.string.list_count, sel.listCount), Toast.LENGTH_SHORT).show()
                addAction(Action(type, selector = s, name = name))
            }
            else -> askName(false, "") { name, _ ->
                addAction(Action(type, selector = sel.selector, name = name))
            }
        }
    }

    private fun askName(withAttr: Boolean, defaultAttr: String, onOk: (String, String) -> Unit) {
        val c = requireContext()
        val name = c.field(R.string.f_name, "value${draft.count { it.type.isExtraction() } + 1}")
        val attr = c.field(R.string.f_attr, defaultAttr)
        val form = c.vbox(20)
        form.add(name)
        if (withAttr) form.add(attr)
        AlertDialog.Builder(c)
            .setTitle(R.string.name_prompt)
            .setView(form)
            .setPositiveButton(R.string.ok) { _, _ ->
                val n = name.text.toString().trim().ifEmpty { "value" }
                val a = attr.text.toString().trim()
                if (withAttr && a.isEmpty()) return@setPositiveButton
                onOk(n, a)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun addAction(a: Action) {
        val cur = webView?.url ?: ""
        val key = UrlUtils.pageKey(cur)
        if (key.isNotEmpty() && key != lastPage) {
            draft.add(Action(ActionType.NAVIGATE, value = cur))
            draft.add(Action(ActionType.WAIT_LOAD))
            lastPage = key
        }
        if (startUrl.isEmpty()) {
            startUrl = cur
            lastPage = key
        }
        draft.add(a)
        updateCount()
        Toast.makeText(requireContext(), R.string.added_action, Toast.LENGTH_SHORT).show()
    }

    private fun showDraft() {
        val c = requireContext()
        val text = if (draft.isEmpty()) getString(R.string.draft_empty)
        else draft.mapIndexed { i, a -> "${i + 1}. ${actionLabel(c, a.type)}  ${a.summary()}" }.joinToString("\n")
        val tvv = c.tv(text, 13f)
        tvv.setPadding(c.dp(20), c.dp(12), c.dp(20), 0)
        AlertDialog.Builder(c)
            .setTitle(R.string.nav_create)
            .setView(ScrollView(c).apply { addView(tvv) })
            .setPositiveButton(R.string.close, null)
            .setNeutralButton(R.string.draft_remove_last) { _, _ ->
                if (draft.isNotEmpty()) draft.removeAt(draft.size - 1)
                updateCount()
            }
            .setNegativeButton(R.string.draft_clear) { _, _ ->
                draft.clear()
                updateCount()
            }
            .show()
    }

    // ---- save ----

    private fun save() {
        val c = requireContext()
        if (draft.none { it.type.isExtraction() }) {
            Toast.makeText(c, R.string.need_extraction, Toast.LENGTH_LONG).show()
            return
        }
        if (UrlUtils.normalize(startUrl) !is UrlResult.Valid) {
            Toast.makeText(c, R.string.invalid_url, Toast.LENGTH_LONG).show()
            return
        }
        val nameField = c.field(R.string.script_name, UrlUtils.hostOf(startUrl))
        val form = c.vbox(20)
        form.add(nameField)
        val activity = requireActivity()
        AlertDialog.Builder(c)
            .setTitle(R.string.save_automation)
            .setView(form)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = nameField.text.toString().trim().ifEmpty { UrlUtils.hostOf(startUrl) }
                val actions = listOf(Action(ActionType.OPEN_URL, value = startUrl), Action(ActionType.WAIT_LOAD)) +
                    draft + Action(ActionType.SAVE_RESULTS)
                val entity = ScriptEntity(name = name, url = startUrl, actionsJson = ActionJson.encode(actions))
                val db = AppDatabase.get(c)
                AppScope.launch {
                    val id = db.scripts().insert(entity)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, R.string.saved_ok, Toast.LENGTH_SHORT).show()
                        draft.clear()
                        if (isAdded) updateCount()
                        activity.startActivity(Intent(activity, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_ID, id))
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (view != null) updateNav()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val b = Bundle()
        webView?.saveState(b)
        outState.putBundle(STATE_WEB, b)
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        webView?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        pollJob?.cancel()
        webView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        webView = null
        super.onDestroyView()
    }

    private companion object {
        const val STATE_WEB = "web_state"
    }
}
