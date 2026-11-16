package com.webautomator.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
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
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

/** Integrated browser: open a site, tap what you want to track, save it as an automation. */
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
    private lateinit var backBtn: ImageButton
    private lateinit var forwardBtn: ImageButton
    private lateinit var selectBtn: MaterialButton
    private lateinit var stepsBtn: MaterialButton
    private lateinit var panel: MaterialCardView
    private lateinit var tagPill: TextView
    private lateinit var preview: TextView
    private lateinit var selectorText: TextView
    private lateinit var chipsScroll: HorizontalScrollView
    private lateinit var applyBtn: MaterialButton
    private lateinit var emptyHint: View
    private lateinit var editBanner: MaterialCardView
    private lateinit var editTitle: TextView

    private enum class Loc { CSS, XPATH, BOTH }

    private var locMode = Loc.BOTH
    private lateinit var locRow: View
    private var selecting = false
    private var current: Sel? = null
    private var pollJob: Job? = null
    private var startUrl = ""
    private var lastPage = ""
    private val draft = mutableListOf<Action>()

    // edit-existing-script mode
    private var editMode = false
    private var editScript: ScriptEntity? = null
    private val editActions = mutableListOf<Action>()
    private var editIndex = -1
    private var pendingId = 0L
    private var pendingIdx = -1

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

        // ---- edit banner (only while re-picking elements of a saved automation) ----
        editTitle = c.tv("", 13f, true)
        editTitle.setTextColor(c.color(R.color.on_brand_container))
        editBanner = c.cardWith(12, R.color.brand_container, false) {
            add(editTitle)
            val r = c.hbox()
            r.addWrap(c.textBtn(c.getString(R.string.edit_pick_step), 0, R.string.h_edit_pick) { chooseStep() })
            r.addWrap(c.textBtn(c.getString(R.string.edit_done), 0, R.string.h_edit_done) { finishEdit(true) }, start = 8)
            add(r, top = 4)
        }
        editBanner.visibility = View.GONE
        root.add(editBanner, top = 8, start = 12, end = 12)

        // ---- address bar ----
        urlInput = EditText(c)
        urlInput.hint = getString(R.string.url_hint)
        urlInput.background = null
        urlInput.setSingleLine(true)
        urlInput.textSize = 14f
        urlInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        urlInput.imeOptions = EditorInfo.IME_ACTION_GO
        urlInput.setTextColor(c.themeColor(com.google.android.material.R.attr.colorOnSurface))
        backBtn = c.iconBtn(R.drawable.ic_back, R.string.back, R.string.h_back) { webView?.goBack() }
        forwardBtn = c.iconBtn(R.drawable.ic_forward, R.string.forward, R.string.h_forward) { webView?.goForward() }
        val addr = c.cardWith(0) {
            val r = c.hbox()
            r.setPadding(c.dp(4), 0, c.dp(4), 0)
            r.addWrap(backBtn)
            r.addWrap(forwardBtn)
            r.add(urlInput, 0, WRAP, 1f, start = 4)
            r.addWrap(c.textBtn(c.getString(R.string.open), 0, R.string.h_open) { openFromInput() })
            add(r)
        }
        root.add(addr, top = 8, start = 12, end = 12)

        progress = ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal)
        progress.max = 100
        progress.visibility = View.GONE
        progress.progressTintList = ColorStateList.valueOf(c.themeColor(com.google.android.material.R.attr.colorPrimary))
        root.add(progress, MATCH, c.dp(3), top = 6)

        // ---- browser ----
        val frame = FrameLayout(c)
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
                emptyHint.visibility = View.GONE
                progress.visibility = View.VISIBLE
                if (!urlInput.hasFocus() && url != null) urlInput.setText(url)
                current = null
                renderPanel()
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
        frame.addView(wv, FrameLayout.LayoutParams(MATCH, MATCH))
        emptyHint = c.emptyState(R.drawable.ic_link, R.string.create_empty_title, R.string.create_empty_body)
        emptyHint.setBackgroundColor(c.color(R.color.bg))
        frame.addView(emptyHint, FrameLayout.LayoutParams(MATCH, MATCH))
        root.add(frame, MATCH, 0, 1f, top = 6)

        // ---- selection panel ----
        tagPill = c.pill("", R.color.brand, R.color.brand_container)
        preview = c.tv("", 14f)
        preview.maxLines = 2
        preview.ellipsize = TextUtils.TruncateAt.END
        selectorText = c.caption("", 11f)
        selectorText.maxLines = 2
        selectorText.ellipsize = TextUtils.TruncateAt.MIDDLE
        chipsScroll = HorizontalScrollView(c)
        chipsScroll.isHorizontalScrollBarEnabled = false
        applyBtn = c.filledBtn(c.getString(R.string.apply_here), R.drawable.ic_check, R.string.h_apply) { applyEdit() }
        panel = c.cardWith(12) {
            val head = c.hbox()
            head.addWrap(tagPill)
            head.add(preview, 0, WRAP, 1f, start = 8)
            head.addWrap(c.iconBtn(R.drawable.ic_close, R.string.close, 0) { clearSelection() })
            add(head)
            add(selectorText, top = 2)

            val lr = c.vbox(0)
            lr.add(c.caption(c.getString(R.string.locate_by), 12f))
            val lg = ChipGroup(c)
            lg.isSingleSelection = true
            lg.isSelectionRequired = true
            val ids = HashMap<Int, Loc>()
            val labels = listOf(Loc.CSS to R.string.loc_css, Loc.XPATH to R.string.loc_xpath, Loc.BOTH to R.string.loc_both)
            for ((mode, label) in labels) {
                val ch = c.chip(c.getString(label), 0, true)
                ids[ch.id] = mode
                lg.addView(ch)
                if (mode == locMode) lg.check(ch.id)
            }
            lg.setOnCheckedStateChangeListener { _, checked ->
                locMode = checked.firstOrNull()?.let { ids[it] } ?: Loc.BOTH
            }
            lg.help(R.string.h_loc)
            lr.add(lg, top = 2)
            locRow = lr
            add(lr, top = 6)

            val chips = c.hbox()
            chips.addWrap(c.chip(c.getString(R.string.a_text), R.drawable.ic_text).also { it.setOnClickListener { pick(ActionType.EXTRACT_TEXT) }; it.help(R.string.h_a_text) })
            chips.addWrap(c.chip(c.getString(R.string.a_number), R.drawable.ic_number).also { it.setOnClickListener { pick(ActionType.EXTRACT_NUMBER) }; it.help(R.string.h_a_number) }, start = 8)
            chips.addWrap(c.chip(c.getString(R.string.a_attr), R.drawable.ic_link).also { it.setOnClickListener { pick(ActionType.EXTRACT_ATTR) }; it.help(R.string.h_a_attr) }, start = 8)
            chips.addWrap(c.chip(c.getString(R.string.a_table), R.drawable.ic_table).also { it.setOnClickListener { pick(ActionType.EXTRACT_TABLE) }; it.help(R.string.h_a_table) }, start = 8)
            chips.addWrap(c.chip(c.getString(R.string.a_list), R.drawable.ic_list).also { it.setOnClickListener { pick(ActionType.EXTRACT_LIST) }; it.help(R.string.h_a_list) }, start = 8)
            chips.addWrap(c.chip(c.getString(R.string.a_click), R.drawable.ic_cursor).also { it.setOnClickListener { pick(ActionType.CLICK) }; it.help(R.string.h_a_click) }, start = 8)
            chips.addWrap(c.chip(c.getString(R.string.a_wait), R.drawable.ic_hourglass).also { it.setOnClickListener { pick(ActionType.WAIT_ELEMENT) }; it.help(R.string.h_a_wait) }, start = 8)
            chipsScroll.addView(chips)
            add(chipsScroll, top = 8)
            add(applyBtn, top = 8)
        }
        panel.visibility = View.GONE
        root.add(panel, top = 6, start = 12, end = 12)

        // ---- bottom bar ----
        selectBtn = c.tonalBtn(getString(R.string.select_off), R.drawable.ic_cursor, R.string.h_select) { toggleSelect() }.compact()
        stepsBtn = c.tonalBtn("", R.drawable.ic_list, R.string.h_actions) { showDraft() }.compact()
        val saveBtn = c.filledBtn(getString(R.string.save), R.drawable.ic_check, R.string.h_save_auto) { save() }.compact()
        val bar = c.hbox()
        bar.setPadding(c.dp(12), c.dp(8), c.dp(12), c.dp(8))
        bar.setBackgroundColor(c.color(R.color.card))
        bar.add(selectBtn, 0, WRAP, 1f)
        bar.add(stepsBtn, 0, WRAP, 1f, start = 8)
        bar.add(saveBtn, 0, WRAP, 1f, start = 8)
        root.add(bar, top = 6)

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
        renderPanel()
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
        backBtn.alpha = if (wv.canGoBack()) 1f else 0.35f
        forwardBtn.alpha = if (wv.canGoForward()) 1f else 0.35f
        backCallback.isEnabled = !isHidden && wv.canGoBack()
    }

    private fun updateCount() {
        stepsBtn.text = getString(R.string.steps_count, draft.size)
    }

    private fun styleSelect() {
        selectBtn.text = getString(if (selecting) R.string.select_on else R.string.select_off)
        if (selecting) selectBtn.filledStyle() else selectBtn.tonalStyle()
    }

    /** Shows the panel while selecting: a hint first, then the chosen element with its actions. */
    private fun renderPanel() {
        panel.visibility = if (selecting) View.VISIBLE else View.GONE
        val sel = current
        if (sel == null) {
            tagPill.visibility = View.GONE
            preview.text = getString(R.string.sel_hint_short)
            selectorText.visibility = View.GONE
            locRow.visibility = View.GONE
            chipsScroll.visibility = View.GONE
            applyBtn.visibility = View.GONE
        } else {
            tagPill.visibility = View.VISIBLE
            tagPill.text = "<${sel.tag}>"
            preview.text = sel.text.ifBlank { getString(R.string.no_text) }
            selectorText.text = "CSS: ${sel.selector}\nXPath: ${sel.xpath}"
            selectorText.visibility = View.VISIBLE
            locRow.visibility = View.VISIBLE
            chipsScroll.visibility = if (editMode) View.GONE else View.VISIBLE
            applyBtn.visibility = if (editMode) View.VISIBLE else View.GONE
        }
    }

    // ---- visual selection ----

    private fun toggleSelect() {
        val wv = webView ?: return
        if (!selecting) {
            if (wv.url.isNullOrBlank()) {
                Toast.makeText(requireContext(), R.string.open_first, Toast.LENGTH_SHORT).show()
                return
            }
            selecting = true
            styleSelect()
            renderPanel()
            injectAndEnable()
            startPolling()
        } else {
            selecting = false
            current = null
            styleSelect()
            renderPanel()
            pollJob?.cancel()
            wv.evaluateJavascript("window.__wa&&window.__wa.disable();", null)
        }
    }

    private fun clearSelection() {
        current = null
        webView?.evaluateJavascript("window.__wa&&window.__wa.clear();", null)
        renderPanel()
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
                        renderPanel()
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
                val keys = attrsObj.keys()
                while (keys.hasNext()) names.add(keys.next())
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
                addAction(locate(Action(type), sel))
            ActionType.EXTRACT_ATTR -> askName(true, sel.attrs.firstOrNull() ?: "href") { name, attr ->
                addAction(locate(Action(type, attr = attr, name = name), sel))
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
                addAction(locate(Action(type, name = name), sel))
            }
        }
    }

    /** Fills CSS and/or XPath on a single-element step according to the chosen "locate by" mode. */
    private fun locate(a: Action, sel: Sel): Action = when (locMode) {
        Loc.CSS -> a.copy(selector = sel.selector, xpath = "")
        Loc.XPATH -> a.copy(selector = "", xpath = sel.xpath)
        Loc.BOTH -> a.copy(selector = sel.selector, xpath = sel.xpath)
    }

    private fun askName(withAttr: Boolean, defaultAttr: String, onOk: (String, String) -> Unit) {
        val c = requireContext()
        val name = c.input(R.string.f_name, "value${draft.count { it.type.isExtraction() } + 1}")
        val attr = c.input(R.string.f_attr, defaultAttr)
        val form = c.vbox(20)
        form.add(name)
        if (withAttr) form.add(attr, top = 8)
        MaterialAlertDialogBuilder(c)
            .setTitle(R.string.name_prompt)
            .setView(form)
            .setPositiveButton(R.string.ok) { _, _ ->
                val n = name.str().ifEmpty { "value" }
                val a = attr.str()
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
        clearSelection()
        Toast.makeText(requireContext(), R.string.added_action, Toast.LENGTH_SHORT).show()
    }

    private fun showDraft() {
        val c = requireContext()
        val text = if (draft.isEmpty()) getString(R.string.draft_empty)
        else draft.mapIndexed { i, a -> "${i + 1}. ${actionLabel(c, a.type)}  ${a.summary()}" }.joinToString("\n")
        MaterialAlertDialogBuilder(c)
            .setTitle(R.string.steps_title)
            .setMessage(text)
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

    // ---- edit an existing automation by picking elements on the real page ----

    /** Called by MainActivity when the user chooses "Edit script" in the editor. */
    fun startEdit(id: Long, index: Int) {
        pendingId = id
        pendingIdx = index
        if (view != null) consumePending()
    }

    private fun consumePending() {
        val id = pendingId
        if (id == 0L) return
        val idx = pendingIdx
        pendingId = 0L
        viewLifecycleOwner.lifecycleScope.launch {
            val s = AppDatabase.get(requireContext()).scripts().get(id) ?: return@launch
            editScript = s
            editActions.clear()
            editActions.addAll(try { ActionJson.decode(s.actionsJson) } catch (e: Exception) { emptyList() })
            editMode = true
            editTitle.text = getString(R.string.edit_mode_title, s.name)
            editBanner.visibility = View.VISIBLE
            renderPanel()
            if (idx in editActions.indices && editActions[idx].hasLocator()) selectStep(idx) else chooseStep()
        }
    }

    private fun chooseStep() {
        val c = requireContext()
        val idxs = editActions.indices.filter { editActions[it].hasLocator() }
        if (idxs.isEmpty()) {
            Toast.makeText(c, R.string.edit_no_selectors, Toast.LENGTH_LONG).show()
            return
        }
        val labels = idxs.map { "${it + 1}. ${actionLabel(c, editActions[it].type)}  ${editActions[it].summary()}" }
            .toTypedArray()
        MaterialAlertDialogBuilder(c)
            .setTitle(R.string.edit_choose_title)
            .setItems(labels) { _, which -> selectStep(idxs[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun selectStep(idx: Int) {
        val script = editScript ?: return
        editIndex = idx
        var page = script.url
        for (i in 0..idx) {
            val a = editActions[i]
            if (a.type == ActionType.OPEN_URL || a.type == ActionType.NAVIGATE) {
                page = a.value.ifBlank { script.url }
            }
        }
        editTitle.text = getString(R.string.edit_mode_title, script.name) + "\n" +
            getString(R.string.edit_tap_hint, idx + 1)
        current = null
        selecting = true
        styleSelect()
        renderPanel()
        startPolling()
        val valid = UrlUtils.normalize(page)
        if (valid is UrlResult.Valid) {
            webView?.loadUrl(valid.url)
        } else {
            Toast.makeText(requireContext(), R.string.invalid_url, Toast.LENGTH_LONG).show()
        }
    }

    private fun applyEdit() {
        val sel = current
        if (sel == null) {
            Toast.makeText(requireContext(), R.string.edit_no_selection, Toast.LENGTH_SHORT).show()
            return
        }
        val id = editScript?.id ?: return
        val step = editActions.getOrNull(editIndex) ?: return
        editActions[editIndex] = when (step.type) {
            ActionType.EXTRACT_TABLE -> step.copy(selector = sel.table.ifBlank { sel.selector }, xpath = "")
            ActionType.EXTRACT_LIST ->
                step.copy(selector = if (sel.listCount > 1) sel.listSelector else sel.selector, xpath = "")
            else -> locate(step, sel)
        }
        val json = ActionJson.encode(editActions)
        val db = AppDatabase.get(requireContext())
        AppScope.launch { db.scripts().updateActions(id, json) }
        clearSelection()
        Toast.makeText(requireContext(), R.string.edit_applied, Toast.LENGTH_SHORT).show()
    }

    private fun finishEdit(openEditor: Boolean) {
        val id = editScript?.id
        editMode = false
        editScript = null
        editBanner.visibility = View.GONE
        selecting = false
        current = null
        styleSelect()
        pollJob?.cancel()
        webView?.evaluateJavascript("window.__wa&&window.__wa.disable();", null)
        renderPanel()
        if (openEditor && id != null) {
            startActivity(Intent(requireContext(), EditorActivity::class.java).putExtra(EditorActivity.EXTRA_ID, id))
        }
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
        val nameField = c.input(R.string.script_name, UrlUtils.hostOf(startUrl))
        val form = c.vbox(20)
        form.add(nameField)
        val activity = requireActivity()
        MaterialAlertDialogBuilder(c)
            .setTitle(R.string.save_automation)
            .setView(form)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameField.str().ifEmpty { UrlUtils.hostOf(startUrl) }
                val actions = listOf(Action(ActionType.OPEN_URL, value = startUrl), Action(ActionType.WAIT_LOAD)) +
                    draft + Action(ActionType.SAVE_RESULTS)
                val entity = ScriptEntity(name = name, url = startUrl, actionsJson = ActionJson.encode(actions))
                val db = AppDatabase.get(c)
                AppScope.launch {
                    val id = db.scripts().insert(entity)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, R.string.saved_ok, Toast.LENGTH_SHORT).show()
                        draft.clear()
                        if (isAdded && view != null) updateCount()
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
        if (pendingId != 0L && view != null) consumePending()
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
