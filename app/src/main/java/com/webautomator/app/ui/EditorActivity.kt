package com.webautomator.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.webautomator.app.AppScope
import com.webautomator.app.MainActivity
import com.webautomator.app.R
import com.webautomator.app.data.Action
import com.webautomator.app.data.ActionJson
import com.webautomator.app.data.ActionType
import com.webautomator.app.data.AppDatabase
import com.webautomator.app.data.ScriptEntity
import com.webautomator.app.data.Status
import com.webautomator.app.engine.ScriptEngine
import com.webautomator.app.util.UrlResult
import com.webautomator.app.util.UrlUtils
import com.webautomator.app.work.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The steps of one saved automation: tap to edit, hold to drag, menu for everything else. */
class EditorActivity : AppCompatActivity() {

    private var scriptId = 0L
    private val actions = mutableListOf<Action>()
    private lateinit var rows: RowAdapter<Action>
    private lateinit var titleView: TextView
    private lateinit var urlView: TextView
    private lateinit var db: AppDatabase
    private var scriptName = ""
    private var script: ScriptEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppDatabase.get(this)
        scriptId = intent.getLongExtra(EXTRA_ID, 0L)

        val frame = FrameLayout(this)
        val root = vbox(0)
        root.setPadding(dp(16), dp(8), dp(16), 0)

        val header = hbox()
        header.addWrap(iconBtn(R.drawable.ic_back, R.string.back, R.string.h_back) { finish() })
        titleView = tv("", 20f, true)
        titleView.maxLines = 1
        titleView.ellipsize = TextUtils.TruncateAt.END
        titleView.setOnClickListener { editInfo() }
        titleView.help(R.string.h_rename)
        header.add(titleView, 0, WRAP, 1f, start = 4)
        val more = iconBtn(R.drawable.ic_more, R.string.more, R.string.h_more) {}
        more.setOnClickListener { v -> showScriptMenu(v) }
        header.addWrap(more)
        root.add(header)

        urlView = caption("", 12f)
        urlView.maxLines = 1
        urlView.ellipsize = TextUtils.TruncateAt.MIDDLE
        root.add(urlView, start = 52)

        val row1 = hbox()
        row1.add(
            filledBtn(getString(R.string.run_now), R.drawable.ic_play, R.string.h_run_now) {
                Toast.makeText(this, R.string.run_started, Toast.LENGTH_SHORT).show()
                AppScope.launch { ScriptEngine.run(applicationContext, scriptId, true) }
            },
            0, WRAP, 1f
        )
        row1.add(
            tonalBtn(getString(R.string.schedule), R.drawable.ic_clock, R.string.h_schedule) {
                startActivity(Intent(this, SchedulerActivity::class.java).putExtra(EXTRA_ID, scriptId))
            },
            0, WRAP, 1f, start = 8
        )
        root.add(row1, top = 12)
        root.add(
            tonalBtn(getString(R.string.edit_script), R.drawable.ic_cursor, R.string.h_edit_script) { editOnPage(-1) },
            top = 8
        )

        val titleRow = hbox()
        titleRow.add(heading(getString(R.string.steps_title)), 0, WRAP, 1f)
        root.add(titleRow, top = 16)
        root.add(caption(getString(R.string.drag_hint), 12f), top = 2)

        rows = RowAdapter { c, box, a, i -> bindAction(c, box, a, i) }
        val list = RecyclerView(this)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = rows
        list.clipToPadding = false
        list.setPadding(0, dp(8), 0, dp(96))
        root.add(list, MATCH, 0, 1f)

        // Hold a step and drag it to a new position.
        val touch = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from < 0 || to < 0 || from >= actions.size || to >= actions.size) return false
                actions.add(to, actions.removeAt(from))
                rows.moveItem(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                recyclerView.post { persist() }
            }
        })
        touch.attachToRecyclerView(list)

        frame.addView(root, FrameLayout.LayoutParams(MATCH, MATCH))
        val fab = ExtendedFloatingActionButton(this)
        fab.text = getString(R.string.add_action)
        fab.setIconResource(R.drawable.ic_add)
        fab.setOnClickListener { showActionDialog(null) { a -> actions.add(a); persist() } }
        fab.help(R.string.h_add_action)
        val lp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END)
        lp.setMargins(dp(16), dp(16), dp(16), dp(16))
        frame.addView(fab, lp)
        setContentView(frame)

        lifecycleScope.launch {
            val s = db.scripts().get(scriptId)
            if (s == null) {
                finish()
                return@launch
            }
            script = s
            scriptName = s.name
            titleView.text = s.name
            urlView.text = s.url
            actions.clear()
            actions.addAll(try { ActionJson.decode(s.actionsJson) } catch (e: Exception) { emptyList() })
            rows.items = actions.toList()
        }
    }

    private fun persist() {
        rows.items = actions.toList()
        val json = ActionJson.encode(actions)
        val id = scriptId
        AppScope.launch { db.scripts().updateActions(id, json) }
    }

    // ---------- step card ----------

    private fun bindAction(c: Context, box: LinearLayout, a: Action, i: Int) {
        val card = c.cardWith(12) {
            val row = c.hbox()
            val num = c.pill((i + 1).toString(), R.color.brand, R.color.brand_container)
            row.add(num, c.dp(28), c.dp(28))
            row.addWrap(c.iconView(iconFor(a.type), R.color.brand, 22), start = 10)
            val col = c.vbox(0)
            col.add(c.tv(actionLabel(c, a.type), 15f, true))
            val sum = a.summary()
            if (sum.isNotBlank()) {
                val s = c.caption(sum, 12f)
                s.maxLines = 2
                s.ellipsize = TextUtils.TruncateAt.END
                col.add(s, top = 2)
            }
            row.add(col, 0, WRAP, 1f, start = 10)
            val more = c.iconBtn(R.drawable.ic_more, R.string.more, R.string.h_stepmenu) {}
            more.setOnClickListener { v -> showStepMenu(v, a, i) }
            row.addWrap(more)
            add(row)
        }
        card.isClickable = true
        card.setOnClickListener { showActionDialog(a) { n -> actions[i] = n; persist() } }
        box.add(card)
    }

    private fun showStepMenu(anchor: View, a: Action, i: Int) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, R.string.edit)
        if (a.selector.isNotBlank()) pm.menu.add(0, 2, 1, R.string.pick_on_page)
        pm.menu.add(0, 3, 2, R.string.insert_after)
        pm.menu.add(0, 4, 3, R.string.move_to_prompt)
        pm.menu.add(0, 5, 4, R.string.duplicate)
        pm.menu.add(0, 6, 5, R.string.delete_step)
        pm.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showActionDialog(a) { n -> actions[i] = n; persist() }
                2 -> editOnPage(i)
                3 -> showActionDialog(null) { n -> actions.add(i + 1, n); persist() }
                4 -> moveTo(i)
                5 -> {
                    actions.add(i + 1, a.copy())
                    persist()
                }
                6 -> {
                    actions.removeAt(i)
                    persist()
                }
            }
            true
        }
        pm.show()
    }

    private fun showScriptMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, R.string.script_info)
        pm.menu.add(0, 2, 1, R.string.copy_script)
        pm.menu.add(0, 3, 2, R.string.delete_script)
        pm.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> editInfo()
                2 -> copyScript()
                3 -> confirmDelete()
            }
            true
        }
        pm.show()
    }

    // ---------- dialogs ----------

    /** Only the fields that matter for the chosen step type are shown. */
    private fun showActionDialog(existing: Action?, onOk: (Action) -> Unit) {
        val types = ActionType.values()
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types.map { actionLabel(this, it) })
        spinner.setSelection(if (existing != null) types.indexOf(existing.type) else 0)

        val nameF = input(R.string.f_name, existing?.name ?: "")
        val selF = input(R.string.f_selector, existing?.selector ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        val valF = input(R.string.f_value, existing?.value ?: "")
        val attrF = input(R.string.f_attr, existing?.attr ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        val toF = input(R.string.f_timeout, ((existing?.timeoutMs ?: 15000L) / 1000).toString(), InputType.TYPE_CLASS_NUMBER)

        fun refresh() {
            val t = types[spinner.selectedItemPosition]
            nameF.visibility = if (t.isExtraction()) View.VISIBLE else View.GONE
            selF.visibility = if (needsSelector(t)) View.VISIBLE else View.GONE
            valF.visibility = if (needsValue(t)) View.VISIBLE else View.GONE
            valF.hint = getString(valueHint(t))
            attrF.visibility = if (t == ActionType.EXTRACT_ATTR) View.VISIBLE else View.GONE
            toF.visibility = if (usesTimeout(t)) View.VISIBLE else View.GONE
        }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = refresh()
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val form = vbox(20)
        form.add(caption(getString(R.string.step_type), 12f))
        form.add(spinner)
        form.add(nameF, top = 8)
        form.add(selF, top = 8)
        form.add(valF, top = 8)
        form.add(attrF, top = 8)
        form.add(toF, top = 8)
        refresh()

        MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) R.string.add_action else R.string.edit)
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton(R.string.ok) { _, _ ->
                val t = types[spinner.selectedItemPosition]
                val selector = selF.str()
                if (needsSelector(t) && selector.isBlank()) {
                    Toast.makeText(this, R.string.need_selector, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val sec = toF.str().toLongOrNull()?.coerceIn(1L, 60L) ?: 15L
                onOk(
                    Action(
                        type = t,
                        selector = if (needsSelector(t)) selector else "",
                        value = if (needsValue(t)) valF.str() else "",
                        attr = if (t == ActionType.EXTRACT_ATTR) attrF.str() else "",
                        name = if (t.isExtraction()) nameF.str() else "",
                        timeoutMs = sec * 1000
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun moveTo(i: Int) {
        val f = input(R.string.move_to_prompt, (i + 1).toString(), InputType.TYPE_CLASS_NUMBER)
        val form = vbox(20)
        form.add(caption(getString(R.string.move_to_range, actions.size), 13f))
        form.add(f, top = 8)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.move_to_prompt)
            .setView(form)
            .setPositiveButton(R.string.ok) { _, _ ->
                val n = f.str().toIntOrNull()
                if (n == null || n < 1 || n > actions.size) {
                    Toast.makeText(this, R.string.position_invalid, Toast.LENGTH_SHORT).show()
                } else {
                    actions.add(n - 1, actions.removeAt(i))
                    persist()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun editInfo() {
        val s = script ?: return
        val nameF = input(R.string.script_name, s.name)
        val urlF = input(R.string.url_hint, s.url, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val form = vbox(20)
        form.add(nameF)
        form.add(urlF, top = 8)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.script_info)
            .setView(form)
            .setPositiveButton(R.string.ok) { _, _ ->
                val newName = nameF.str().ifEmpty { s.name }
                val valid = UrlUtils.normalize(urlF.str())
                if (valid !is UrlResult.Valid) {
                    Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val newUrl = valid.url
                val id = scriptId
                val first = actions.firstOrNull()
                if (first != null && first.type == ActionType.OPEN_URL && first.value == s.url) {
                    actions[0] = first.copy(value = newUrl)
                }
                script = s.copy(name = newName, url = newUrl)
                scriptName = newName
                titleView.text = newName
                urlView.text = newUrl
                AppScope.launch {
                    db.scripts().rename(id, newName)
                    db.scripts().updateUrl(id, newUrl)
                }
                persist()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.delete_script) { _, _ ->
                val id = scriptId
                val app = applicationContext
                AppScope.launch {
                    Scheduler.cancel(app, id)
                    db.runs().deleteForScript(id)
                    db.scripts().delete(id)
                }
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun copyScript() {
        val suffix = getString(R.string.copy_suffix)
        val done = getString(R.string.script_copied)
        val id = scriptId
        val ctx = this
        AppScope.launch {
            val s = db.scripts().get(id) ?: return@launch
            db.scripts().insert(
                s.copy(
                    id = 0, name = s.name + " " + suffix, enabled = false, lastRunAt = 0L,
                    status = Status.IDLE.name, lastMessage = "", runsToday = 0, runsDay = ""
                )
            )
            withContext(Dispatchers.Main) { Toast.makeText(ctx, done, Toast.LENGTH_SHORT).show() }
        }
    }

    /** Re-opens the real page in the browser so the user taps the element to change. */
    private fun editOnPage(index: Int) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_EDIT_ID, scriptId)
                .putExtra(MainActivity.EXTRA_EDIT_INDEX, index)
        )
        finish()
    }

    companion object {
        const val EXTRA_ID = "script_id"
    }
}
