package com.webautomator.app.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.widget.HorizontalScrollView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.webautomator.app.AppScope
import com.webautomator.app.MainActivity
import com.webautomator.app.R
import com.webautomator.app.data.Action
import com.webautomator.app.data.ActionJson
import com.webautomator.app.data.ActionType
import com.webautomator.app.data.AppDatabase
import com.webautomator.app.data.ScriptEntity
import com.webautomator.app.util.UrlResult
import com.webautomator.app.util.UrlUtils
import com.webautomator.app.engine.ScriptEngine
import com.webautomator.app.work.Scheduler
import kotlinx.coroutines.launch

/** Edit the action list of one saved script: add, edit, duplicate, delete and reorder. */
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

        val root = vbox(12)
        titleView = tv("", 20f, true)
        titleView.setOnClickListener { rename() }
        titleView.help(R.string.h_rename)
        urlView = tv("", 12f)
        root.add(titleView)
        root.add(urlView)

        val bar = hbox()
        bar.add(btn(R.string.add_action, R.string.h_add_action) { showActionDialog(null) { a -> actions.add(a); persist() } },
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        bar.add(btn(R.string.schedule, R.string.h_schedule) {
            startActivity(Intent(this, SchedulerActivity::class.java).putExtra(EXTRA_ID, scriptId))
        }, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f, start = 8)
        root.add(bar, top = 8)

        val bar2 = hbox()
        bar2.add(btn(R.string.run_now, R.string.h_run_now) {
            Toast.makeText(this, R.string.run_started, Toast.LENGTH_SHORT).show()
            AppScope.launch { ScriptEngine.run(applicationContext, scriptId, true) }
        }, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        bar2.add(btn(R.string.delete_script, R.string.h_delete) { confirmDelete() }, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f, start = 8)
        root.add(bar2, top = 4)

        val bar3 = hbox()
        bar3.add(btn(R.string.edit_script, R.string.h_edit_script) { editOnPage(-1) }, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        bar3.add(btn(R.string.copy_script, R.string.h_copy_script) { copyScript() }, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f, start = 8)
        root.add(bar3, top = 4)
        root.add(btn(R.string.script_info, R.string.h_script_info) { editInfo() }, top = 4)
        root.add(tv(getString(R.string.drag_hint), 12f), top = 8)

        rows = RowAdapter { c, box, a, i -> bindAction(c, box, a, i) }
        val list = RecyclerView(this)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = rows
        list.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        root.add(list, ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f, top = 8)

        // Long-press a step and drag it to a new position.
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
        setContentView(root)

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

    private fun bindAction(c: android.content.Context, box: android.widget.LinearLayout, a: Action, i: Int) {
        box.add(c.tv("${i + 1}. ${actionLabel(c, a.type)}", 15f, true))
        val sum = a.summary()
        if (sum.isNotBlank()) box.add(c.tv(sum, 12f))
        val row = c.hbox()
        row.add(c.smallBtn("▲", R.string.h_up) { move(i, -1) })
        row.add(c.smallBtn("▼", R.string.h_down) { move(i, 1) })
        row.add(c.smallBtn("⇅", R.string.h_moveto) { moveTo(i) })
        if (a.selector.isNotBlank()) row.add(c.smallBtn("🎯", R.string.h_retarget) { editOnPage(i) })
        row.add(c.smallBtn("＋", R.string.h_insert) {
            showActionDialog(null) { n -> actions.add(i + 1, n); persist() }
        })
        row.add(c.smallBtn("✎", R.string.h_edit_action) { showActionDialog(a) { n -> actions[i] = n; persist() } })
        row.add(c.smallBtn("⧉", R.string.h_dup) { actions.add(i + 1, a.copy()); persist() })
        row.add(c.smallBtn("✕", R.string.h_remove) { actions.removeAt(i); persist() })
        val scroll = HorizontalScrollView(c)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.addView(row)
        box.add(scroll)
    }

    private fun moveTo(i: Int) {
        val f = field(R.string.move_to_prompt, (i + 1).toString(), InputType.TYPE_CLASS_NUMBER)
        val form = vbox(20)
        form.add(tv(getString(R.string.move_to_range, actions.size), 13f))
        form.add(f)
        AlertDialog.Builder(this)
            .setTitle(R.string.move_to_prompt)
            .setView(form)
            .setPositiveButton(R.string.ok) { _, _ ->
                val n = f.text.toString().toIntOrNull()
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

    private fun editInfo() {
        val s = script ?: return
        val nameF = field(R.string.script_name, s.name)
        val urlF = field(R.string.url_hint, s.url, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val form = vbox(20)
        form.add(nameF)
        form.add(urlF)
        AlertDialog.Builder(this)
            .setTitle(R.string.script_info)
            .setView(form)
            .setPositiveButton(R.string.ok) { _, _ ->
                val newName = nameF.text.toString().trim().ifEmpty { s.name }
                val valid = UrlUtils.normalize(urlF.text.toString())
                if (valid !is UrlResult.Valid) {
                    Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val newUrl = valid.url
                val id = scriptId
                // keep the first "open URL" step in sync when it pointed to the old address
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
                    status = com.webautomator.app.data.Status.IDLE.name, lastMessage = "", runsToday = 0, runsDay = ""
                )
            )
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(ctx, done, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun move(i: Int, delta: Int) {
        val j = i + delta
        if (j < 0 || j >= actions.size) return
        val a = actions.removeAt(i)
        actions.add(j, a)
        persist()
    }

    private fun showActionDialog(existing: Action?, onOk: (Action) -> Unit) {
        val types = ActionType.values()
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types.map { actionLabel(this, it) })
        spinner.setSelection(if (existing != null) types.indexOf(existing.type) else 0)
        val name = field(R.string.f_name, existing?.name ?: "")
        val selector = field(R.string.f_selector, existing?.selector ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        val value = field(R.string.f_value, existing?.value ?: "")
        val attr = field(R.string.f_attr, existing?.attr ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        val timeout = field(R.string.f_timeout, ((existing?.timeoutMs ?: 15000L) / 1000).toString(), InputType.TYPE_CLASS_NUMBER)

        val form = vbox(20)
        form.add(spinner)
        form.add(name)
        form.add(selector)
        form.add(value)
        form.add(attr)
        form.add(timeout)

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.add_action else R.string.edit)
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton(R.string.ok) { _, _ ->
                val sec = timeout.text.toString().toLongOrNull()?.coerceIn(1L, 60L) ?: 15L
                onOk(
                    Action(
                        type = types[spinner.selectedItemPosition],
                        selector = selector.text.toString().trim(),
                        value = value.text.toString().trim(),
                        attr = attr.text.toString().trim(),
                        name = name.text.toString().trim(),
                        timeoutMs = sec * 1000
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun rename() {
        val f = field(R.string.script_name, scriptName)
        val form = vbox(20)
        form.add(f)
        AlertDialog.Builder(this)
            .setTitle(R.string.rename)
            .setView(form)
            .setPositiveButton(R.string.ok) { _, _ ->
                val n = f.text.toString().trim()
                if (n.isNotEmpty()) {
                    scriptName = n
                    titleView.text = n
                    val id = scriptId
                    AppScope.launch { db.scripts().rename(id, n) }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.ok) { _, _ ->
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

    companion object {
        const val EXTRA_ID = "script_id"
    }
}
