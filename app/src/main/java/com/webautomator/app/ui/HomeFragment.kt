package com.webautomator.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.webautomator.app.AppScope
import com.webautomator.app.MainActivity
import com.webautomator.app.R
import com.webautomator.app.data.AppDatabase
import com.webautomator.app.data.MapJson
import com.webautomator.app.data.RunEntity
import com.webautomator.app.data.ScriptEntity
import com.webautomator.app.data.Status
import com.webautomator.app.engine.ScriptEngine
import com.webautomator.app.util.UrlUtils
import com.webautomator.app.work.Scheduler
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private lateinit var content: LinearLayout
    private var scripts: List<ScriptEntity> = emptyList()
    private var latest: Map<Long, RunEntity> = emptyMap()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val c = requireContext()
        val frame = FrameLayout(c)
        val scroll = ScrollView(c)
        scroll.clipToPadding = false
        content = c.vbox(0)
        content.setPadding(c.dp(16), c.dp(16), c.dp(16), c.dp(96))
        scroll.addView(content, ViewGroup.LayoutParams(MATCH, WRAP))
        frame.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))

        val fab = ExtendedFloatingActionButton(c)
        fab.text = getString(R.string.new_automation)
        fab.setIconResource(R.drawable.ic_add)
        fab.setOnClickListener { goCreate() }
        val lp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END)
        lp.setMargins(c.dp(16), c.dp(16), c.dp(16), c.dp(16))
        frame.addView(fab, lp)
        return frame
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val db = AppDatabase.get(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    db.scripts().observeAll().collect {
                        scripts = it
                        render()
                    }
                }
                launch {
                    db.runs().observeLatest(100).collect { runs ->
                        val map = HashMap<Long, RunEntity>()
                        for (r in runs) if (r.success && !map.containsKey(r.scriptId)) map[r.scriptId] = r
                        latest = map
                        render()
                    }
                }
            }
        }
    }

    private fun goCreate() {
        (activity as? MainActivity)?.selectTab(R.id.nav_create)
    }

    private fun render() {
        if (!isAdded || view == null) return
        val c = requireContext()
        content.removeAllViews()

        // header
        val head = c.hbox()
        val titles = c.vbox(0)
        titles.add(c.screenTitle(getString(R.string.app_name)))
        titles.add(c.caption(getString(R.string.home_subtitle), 13f), top = 2)
        head.add(titles, 0, WRAP, 1f)
        val more = c.iconBtn(R.drawable.ic_more, R.string.more, R.string.h_settings) {}
        more.setOnClickListener { v -> showMenu(v) }
        head.addWrap(more)
        content.add(head)

        if (scripts.isEmpty()) {
            content.add(emptyCard(c), top = 20)
            return
        }

        content.add(heroCard(c), top = 16)

        val sectionRow = c.hbox()
        sectionRow.add(c.heading(getString(R.string.your_automations)), 0, WRAP, 1f)
        content.add(sectionRow, top = 24, bottom = 4)
        for (s in scripts) content.add(scriptCard(c, s), top = 8)
    }

    private fun showMenu(anchor: View) {
        val pm = PopupMenu(requireContext(), anchor)
        pm.menu.add(0, 1, 0, R.string.settings)
        pm.setOnMenuItemClickListener {
            SettingsDialog.show(requireActivity() as AppCompatActivity)
            true
        }
        pm.show()
    }

    private fun statColumn(c: Context, value: String, label: String): LinearLayout {
        val col = c.vbox(0)
        val v = c.tv(value, 24f, true)
        v.setTextColor(c.color(R.color.on_brand_container))
        v.maxLines = 1
        v.ellipsize = TextUtils.TruncateAt.END
        col.add(v)
        val l = c.caption(label, 12f)
        l.setTextColor(c.color(R.color.on_brand_container))
        l.alpha = 0.75f
        col.add(l, top = 2)
        return col
    }

    private fun heroCard(c: Context): View {
        val active = scripts.count { it.enabled }
        val lastRun = scripts.maxOfOrNull { it.lastRunAt } ?: 0L
        val attention = scripts.count { it.status == Status.NEEDS_ATTENTION.name || it.status == Status.ERROR.name }
        return c.cardWith(18, R.color.brand_container, false) {
            val stats = c.hbox()
            stats.add(statColumn(c, scripts.size.toString(), c.getString(R.string.stat_total)), 0, WRAP, 1f)
            stats.add(statColumn(c, active.toString(), c.getString(R.string.stat_active)), 0, WRAP, 1f)
            stats.add(statColumn(c, relTime(lastRun), c.getString(R.string.stat_last_run)), 0, WRAP, 1.6f)
            add(stats)
            val msg = if (attention > 0) c.getString(R.string.hero_attention, attention) else c.getString(R.string.hero_ok)
            val m = c.tv(msg, 13f, true)
            m.setTextColor(c.color(R.color.on_brand_container))
            add(m, top = 14)
        }
    }

    private fun scriptCard(c: Context, s: ScriptEntity): View {
        val st = statusStyle(s.status)
        val card = c.cardWith(14) {
            val top = c.hbox()
            val name = c.heading(s.name)
            name.maxLines = 1
            name.ellipsize = TextUtils.TruncateAt.END
            top.add(name, 0, WRAP, 1f)
            top.addWrap(c.pill(c.getString(st.label), st.fg, st.bg), start = 8)
            add(top)

            val host = UrlUtils.hostOf(s.url).ifEmpty { s.url }
            val schedule = if (s.enabled) c.getString(R.string.every_fmt, shortInterval(c, s.intervalMinutes))
            else c.getString(R.string.manual_only)
            add(c.caption("$host • $schedule", 12f), top = 2)

            val vals = latest[s.id]?.let { MapJson.decode(it.valuesJson) }
            if (vals != null && vals.isNotEmpty()) {
                val box = c.vbox(10)
                val bg = GradientDrawable()
                bg.cornerRadius = c.dp(10).toFloat()
                bg.setColor(c.color(R.color.bg))
                box.background = bg
                for ((k, v) in vals.entries.take(3)) {
                    val r = c.hbox()
                    val key = c.caption(k, 13f)
                    key.maxLines = 1
                    key.ellipsize = TextUtils.TruncateAt.END
                    r.add(key, 0, WRAP, 1f, end = 8)
                    val value = c.tv(v.replace('\n', ' '), 14f, true)
                    value.maxLines = 1
                    value.ellipsize = TextUtils.TruncateAt.END
                    value.gravity = Gravity.END
                    r.add(value, 0, WRAP, 2f)
                    box.add(r, top = 2)
                }
                add(box, top = 10)
            }

            if (s.lastRunAt > 0) add(c.caption(c.getString(R.string.last_run_fmt, relTime(s.lastRunAt)), 12f), top = 8)
            if ((s.status == Status.ERROR.name || s.status == Status.NEEDS_ATTENTION.name) && s.lastMessage.isNotBlank()) {
                val e = c.caption(s.lastMessage, 12f)
                e.setTextColor(c.color(st.fg))
                add(e, top = 2)
            }

            val row = c.hbox()
            val sw = MaterialSwitch(c)
            sw.isChecked = s.enabled
            sw.setOnCheckedChangeListener { _, on -> toggle(s, on) }
            sw.help(R.string.h_enabled)
            row.addWrap(sw)
            row.add(c.caption(c.getString(R.string.auto_run), 13f), 0, WRAP, 1f, start = 8)
            row.addWrap(c.iconBtn(R.drawable.ic_play, R.string.run, R.string.h_run) {
                Toast.makeText(c, R.string.run_started, Toast.LENGTH_SHORT).show()
                AppScope.launch { ScriptEngine.run(c.applicationContext, s.id, true) }
            })
            row.addWrap(c.iconBtn(R.drawable.ic_edit, R.string.edit, R.string.h_edit) { openEditor(s.id) })
            add(row, top = 6)
        }
        card.isClickable = true
        card.setOnClickListener { openEditor(s.id) }
        return card
    }

    private fun emptyCard(c: Context): View {
        return c.cardWith(20) {
            val e = c.emptyState(R.drawable.ic_cursor, R.string.empty_title, R.string.empty_body, R.string.start_now) { goCreate() }
            add(e)
            val steps = c.vbox(0)
            steps.setPadding(c.dp(8), c.dp(12), c.dp(8), 0)
            steps.add(stepRow(c, "1", R.string.how_1))
            steps.add(stepRow(c, "2", R.string.how_2), top = 10)
            steps.add(stepRow(c, "3", R.string.how_3), top = 10)
            add(steps)
        }
    }

    private fun stepRow(c: Context, n: String, text: Int): View {
        val r = c.hbox()
        val p = c.pill(n, R.color.brand, R.color.brand_container)
        r.add(p, c.dp(26), c.dp(26))
        r.add(c.tv(getString(text), 14f), 0, WRAP, 1f, start = 12)
        return r
    }

    private fun toggle(s: ScriptEntity, on: Boolean) {
        val app = requireContext().applicationContext
        AppScope.launch {
            val dao = AppDatabase.get(app).scripts()
            dao.setEnabled(s.id, on)
            dao.get(s.id)?.let { Scheduler.apply(app, it) }
        }
    }

    private fun openEditor(id: Long) {
        startActivity(Intent(requireContext(), EditorActivity::class.java).putExtra(EditorActivity.EXTRA_ID, id))
    }
}
