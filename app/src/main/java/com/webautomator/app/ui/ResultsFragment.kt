package com.webautomator.app.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.webautomator.app.AppScope
import com.webautomator.app.R
import com.webautomator.app.data.AppDatabase
import com.webautomator.app.data.MapJson
import com.webautomator.app.data.RunEntity
import com.webautomator.app.data.ScriptEntity
import com.webautomator.app.util.Export
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResultsFragment : Fragment() {

    private var scripts: List<ScriptEntity> = emptyList()
    private var selectedId = 0L // 0 = all automations
    private var listJob: Job? = null
    private var pendingText = ""
    private lateinit var chips: ChipGroup
    private lateinit var adapter: RowAdapter<RunEntity>
    private lateinit var empty: View

    private val csvLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> writeTo(uri) }
    private val jsonLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> writeTo(uri) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val c = requireContext()
        val root = c.vbox(0)
        root.setPadding(c.dp(16), c.dp(16), c.dp(16), 0)

        val head = c.hbox()
        head.add(c.screenTitle(getString(R.string.nav_results)), 0, WRAP, 1f)
        val export = c.iconBtn(R.drawable.ic_download, R.string.export, R.string.h_export) {}
        export.setOnClickListener { v -> showExportMenu(v) }
        head.addWrap(export)
        head.addWrap(c.iconBtn(R.drawable.ic_delete, R.string.clear_history, R.string.h_clear) { confirmClear() })
        root.add(head)

        val scroll = HorizontalScrollView(c)
        scroll.isHorizontalScrollBarEnabled = false
        chips = ChipGroup(c)
        chips.isSingleLine = true
        chips.isSingleSelection = true
        chips.isSelectionRequired = true
        scroll.addView(chips)
        root.add(scroll, top = 8)

        empty = c.emptyState(R.drawable.ic_list, R.string.no_results, R.string.no_results_body)
        empty.visibility = View.GONE
        root.add(empty, top = 24)

        adapter = RowAdapter { ctx, box, r, _ -> box.add(runCard(ctx, r)) }
        val list = RecyclerView(c)
        list.layoutManager = LinearLayoutManager(c)
        list.adapter = adapter
        list.clipToPadding = false
        list.setPadding(0, c.dp(8), 0, c.dp(16))
        root.add(list, MATCH, 0, 1f)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val db = AppDatabase.get(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.scripts().observeAll().collect { list ->
                    scripts = list
                    if (list.none { it.id == selectedId }) selectedId = 0L
                    rebuildChips()
                    startListing()
                }
            }
        }
    }

    private fun rebuildChips() {
        val c = requireContext()
        chips.setOnCheckedStateChangeListener(null)
        chips.removeAllViews()
        val ids = HashMap<Int, Long>()
        val all = c.chip(getString(R.string.all_scripts), 0, true)
        chips.addView(all)
        ids[all.id] = 0L
        var toCheck = all.id
        for (s in scripts) {
            val ch = c.chip(s.name, 0, true)
            chips.addView(ch)
            ids[ch.id] = s.id
            if (s.id == selectedId) toCheck = ch.id
        }
        chips.check(toCheck)
        chips.setOnCheckedStateChangeListener { _, checked ->
            val id = checked.firstOrNull()?.let { ids[it] } ?: 0L
            if (id != selectedId) {
                selectedId = id
                startListing()
            }
        }
    }

    private fun startListing() {
        listJob?.cancel()
        val db = AppDatabase.get(requireContext())
        listJob = viewLifecycleOwner.lifecycleScope.launch {
            val flow = if (selectedId == 0L) db.runs().observeLatest(300) else db.runs().observeForScript(selectedId, 300)
            flow.collect {
                adapter.items = it
                empty.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun runCard(c: Context, r: RunEntity): View {
        val ok = r.success
        return c.cardWith(14, if (ok) R.color.card else R.color.danger_bg, ok) {
            val top = c.hbox()
            top.addWrap(
                c.iconView(
                    if (ok) R.drawable.ic_check_circle else R.drawable.ic_error,
                    if (ok) R.color.success else R.color.danger,
                    20
                )
            )
            val name = c.tv(r.scriptName, 15f, true)
            name.maxLines = 1
            name.ellipsize = TextUtils.TruncateAt.END
            top.add(name, 0, WRAP, 1f, start = 8)
            top.addWrap(c.caption(relTime(r.timestamp), 12f), start = 8)
            add(top)

            if (ok) {
                val values = MapJson.decode(r.valuesJson)
                for ((k, v) in values.entries.take(6)) {
                    val row = c.hbox()
                    val key = c.caption(k, 13f)
                    key.maxLines = 1
                    key.ellipsize = TextUtils.TruncateAt.END
                    row.add(key, 0, WRAP, 1f, end = 8)
                    val value = c.tv(clipText(v.replace('\n', ' '), 90), 14f, true)
                    value.maxLines = 2
                    value.ellipsize = TextUtils.TruncateAt.END
                    value.gravity = android.view.Gravity.END
                    row.add(value, 0, WRAP, 2f)
                    add(row, top = 6)
                }
                if (values.size > 6) add(c.caption(c.getString(R.string.more_values, values.size - 6), 12f), top = 4)
                if (r.changeSummary.isNotBlank()) {
                    val box = c.vbox(10)
                    val bg = GradientDrawable()
                    bg.cornerRadius = c.dp(10).toFloat()
                    bg.setColor(c.color(R.color.brand_container))
                    box.background = bg
                    box.add(c.caption(c.getString(R.string.changes), 12f))
                    val t = c.tv(r.changeSummary, 13f)
                    t.setTextColor(c.color(R.color.on_brand_container))
                    box.add(t, top = 2)
                    add(box, top = 10)
                }
            } else {
                val e = c.tv(errorText(c, r.error), 13f)
                e.setTextColor(c.color(R.color.danger))
                add(e, top = 6)
            }
        }
    }

    private fun confirmClear() {
        val ctx = requireContext()
        val id = selectedId
        val msg = if (id == 0L) R.string.clear_all_confirm else R.string.clear_one_confirm
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.clear_history)
            .setMessage(msg)
            .setPositiveButton(R.string.clear_btn) { _, _ ->
                val db = AppDatabase.get(ctx)
                val app = ctx.applicationContext
                AppScope.launch {
                    if (id == 0L) db.runs().deleteAll() else db.runs().deleteForScript(id)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(app, R.string.history_cleared, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showExportMenu(anchor: View) {
        val pm = PopupMenu(requireContext(), anchor)
        pm.menu.add(0, 1, 0, R.string.export_csv)
        pm.menu.add(0, 2, 1, R.string.export_json)
        pm.setOnMenuItemClickListener {
            export(it.itemId == 1)
            true
        }
        pm.show()
    }

    private fun export(csv: Boolean) {
        val db = AppDatabase.get(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            val runs = if (selectedId == 0L) db.runs().all() else db.runs().allForScript(selectedId)
            if (runs.isEmpty()) {
                Toast.makeText(requireContext(), R.string.export_empty, Toast.LENGTH_SHORT).show()
                return@launch
            }
            pendingText = if (csv) Export.csv(runs) else Export.json(runs)
            if (csv) csvLauncher.launch("results.csv") else jsonLauncher.launch("results.json")
        }
    }

    private fun writeTo(uri: Uri?) {
        if (uri == null) return
        val ctx = requireContext()
        try {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(pendingText.toByteArray(Charsets.UTF_8)) }
            Toast.makeText(ctx, R.string.exported, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, R.string.export_fail, Toast.LENGTH_LONG).show()
        }
    }
}
