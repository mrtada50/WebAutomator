package com.webautomator.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.webautomator.app.AppScope
import com.webautomator.app.R
import com.webautomator.app.data.AppDatabase
import com.webautomator.app.data.RunEntity
import com.webautomator.app.data.ScriptEntity
import com.webautomator.app.data.Status
import com.webautomator.app.engine.ScriptEngine
import com.webautomator.app.work.Scheduler
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private lateinit var stats: TextView
    private lateinit var scriptsAdapter: RowAdapter<ScriptEntity>
    private lateinit var runsAdapter: RowAdapter<RunEntity>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val c = requireContext()
        val root = c.vbox(12)

        val head = c.hbox()
        head.add(c.tv(getString(R.string.app_name), 20f, true), 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        head.add(c.smallBtn(getString(R.string.settings)) { SettingsDialog.show(requireActivity() as AppCompatActivity) },
            ViewGroup.LayoutParams.WRAP_CONTENT)
        root.add(head)

        stats = c.tv("", 14f)
        root.add(stats, top = 8)

        root.add(c.tv(getString(R.string.scripts_title), 16f, true), top = 12)
        scriptsAdapter = RowAdapter { ctx, box, s, _ -> bindScript(ctx, box, s) }
        val scriptsList = RecyclerView(c)
        scriptsList.layoutManager = LinearLayoutManager(c)
        scriptsList.adapter = scriptsAdapter
        scriptsList.addItemDecoration(DividerItemDecoration(c, DividerItemDecoration.VERTICAL))
        root.add(scriptsList, ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)

        root.add(c.tv(getString(R.string.latest_results), 16f, true), top = 8)
        runsAdapter = RowAdapter { ctx, box, r, _ -> bindRun(ctx, box, r) }
        val runsList = RecyclerView(c)
        runsList.layoutManager = LinearLayoutManager(c)
        runsList.adapter = runsAdapter
        runsList.addItemDecoration(DividerItemDecoration(c, DividerItemDecoration.VERTICAL))
        root.add(runsList, ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val db = AppDatabase.get(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    db.scripts().observeAll().collect { list ->
                        scriptsAdapter.items = list
                        updateStats(list)
                    }
                }
                launch {
                    db.runs().observeLatest(10).collect { runsAdapter.items = it }
                }
            }
        }
    }

    private fun updateStats(list: List<ScriptEntity>) {
        val last = list.maxOfOrNull { it.lastRunAt } ?: 0L
        val status = when {
            list.any { it.status == Status.RUNNING.name } -> statusLabel(requireContext(), Status.RUNNING.name)
            list.any { it.status == Status.NEEDS_ATTENTION.name } -> statusLabel(requireContext(), Status.NEEDS_ATTENTION.name)
            list.any { it.status == Status.ERROR.name } -> statusLabel(requireContext(), Status.ERROR.name)
            list.isEmpty() -> statusLabel(requireContext(), Status.IDLE.name)
            else -> statusLabel(requireContext(), Status.OK.name)
        }
        stats.text = getString(R.string.stats_fmt, list.size, list.count { it.enabled }) + "\n" +
            getString(R.string.last_run_fmt, fmtTime(last)) + "\n" +
            getString(R.string.status_fmt, status)
    }

    private fun bindScript(c: android.content.Context, box: android.widget.LinearLayout, s: ScriptEntity) {
        box.add(c.tv(s.name, 16f, true))
        box.add(c.tv(s.url, 12f))
        val next = Scheduler.nextEstimate(s)
        box.add(
            c.tv(
                getString(R.string.status_line, statusLabel(c, s.status), fmtTime(s.lastRunAt), if (next == 0L) "—" else fmtTime(next)),
                12f
            ),
            top = 2
        )
        if (s.lastMessage.isNotBlank()) box.add(c.tv(s.lastMessage, 12f))

        val row = c.hbox()
        row.add(c.smallBtn(getString(R.string.run)) {
            Toast.makeText(c, R.string.run_started, Toast.LENGTH_SHORT).show()
            AppScope.launch { ScriptEngine.run(c.applicationContext, s.id, true) }
        })
        row.add(c.smallBtn(getString(R.string.edit)) { openEditor(s.id) })
        row.add(c.smallBtn(getString(R.string.schedule)) {
            startActivity(Intent(c, SchedulerActivity::class.java).putExtra(EditorActivity.EXTRA_ID, s.id))
        })
        box.add(row)
        box.setOnClickListener { openEditor(s.id) }
    }

    private fun openEditor(id: Long) {
        startActivity(Intent(requireContext(), EditorActivity::class.java).putExtra(EditorActivity.EXTRA_ID, id))
    }
}
