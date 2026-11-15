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
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.webautomator.app.AppScope
import com.webautomator.app.R
import com.webautomator.app.data.Action
import com.webautomator.app.data.ActionJson
import com.webautomator.app.data.ActionType
import com.webautomator.app.data.AppDatabase
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppDatabase.get(this)
        scriptId = intent.getLongExtra(EXTRA_ID, 0L)

        val root = vbox(12)
        titleView = tv("", 20f, true)
        titleView.setOnClickListener { rename() }
        urlView = tv("", 12f)
        root.add(titleView)
        root.add(urlView)

        val bar = hbox()
        bar.add(btn(R.string.add_action) { showActionDialog(null) { a -> actions.add(a); persist() } },
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        bar.add(btn(R.string.schedule) {
            startActivity(Intent(this, SchedulerActivity::class.java).putExtra(EXTRA_ID, scriptId))
        }, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f, start = 8)
        root.add(bar, top = 8)

        val bar2 = hbox()
        bar2.add(btn(R.string.run_now) {
            Toast.makeText(this, R.string.run_started, Toast.LENGTH_SHORT).show()
            AppScope.launch { ScriptEngine.run(applicationContext, scriptId, true) }
        }, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        bar2.add(btn(R.string.delete_script) { confirmDelete() }, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f, start = 8)
        root.add(bar2, top = 4)

        rows = RowAdapter { c, box, a, i -> bindAction(c, box, a, i) }
        val list = RecyclerView(this)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = rows
        list.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        root.add(list, ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f, top = 8)
        setContentView(root)

        lifecycleScope.launch {
            val s = db.scripts().get(scriptId)
            if (s == null) {
                finish()
                return@launch
            }
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
        row.add(c.smallBtn("▲") { move(i, -1) })
        row.add(c.smallBtn("▼") { move(i, 1) })
        row.add(c.smallBtn("✎") { showActionDialog(a) { n -> actions[i] = n; persist() } })
        row.add(c.smallBtn("⧉") { actions.add(i + 1, a.copy()); persist() })
        row.add(c.smallBtn("✕") { actions.removeAt(i); persist() })
        box.add(row)
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
