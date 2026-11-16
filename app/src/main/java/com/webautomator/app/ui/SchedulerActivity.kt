package com.webautomator.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputLayout
import com.webautomator.app.AppScope
import com.webautomator.app.R
import com.webautomator.app.data.AppDatabase
import com.webautomator.app.data.ConditionType
import com.webautomator.app.data.NotifyMode
import com.webautomator.app.data.ScriptEntity
import com.webautomator.app.data.enumOr
import com.webautomator.app.engine.ScriptEngine
import com.webautomator.app.work.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** When and how often an automation runs, and when it should notify. */
class SchedulerActivity : AppCompatActivity() {

    private var scriptId = 0L
    private lateinit var db: AppDatabase
    private lateinit var subtitle: TextView
    private lateinit var auto: SwitchRow
    private lateinit var intervalChips: ChipGroup
    private lateinit var intervalInput: TextInputLayout
    private lateinit var shortInfo: View
    private lateinit var nextView: TextView
    private lateinit var onlyNet: SwitchRow
    private lateinit var dailyLimit: TextInputLayout
    private lateinit var modeChips: ChipGroup
    private lateinit var condBox: View
    private lateinit var condSpinner: Spinner
    private lateinit var condKey: TextInputLayout
    private lateinit var condValue: TextInputLayout
    private lateinit var notifyFail: SwitchRow

    private val presets = listOf(1, 5, 10, 15, 30, 60, 180, 360, 720, 1440)
    private val presetIds = HashMap<Int, Int>() // chip id -> minutes
    private val modes = NotifyMode.values()
    private val modeIds = HashMap<Int, NotifyMode>()
    private val conds = ConditionType.values()
    private var syncing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppDatabase.get(this)
        scriptId = intent.getLongExtra(EditorActivity.EXTRA_ID, 0L)

        val root = vbox(0)
        root.setPadding(dp(16), dp(8), dp(16), dp(8))

        val header = hbox()
        header.addWrap(iconBtn(R.drawable.ic_back, R.string.back, R.string.h_back) { finish() })
        val titles = vbox(0)
        titles.add(tv(getString(R.string.sched_title), 20f, true))
        subtitle = caption("", 12f)
        titles.add(subtitle)
        header.add(titles, 0, WRAP, 1f, start = 4)
        root.add(header)

        val body = vbox(0)
        body.setPadding(0, dp(8), 0, dp(16))

        // ---- automatic run ----
        body.add(cardWith(16) {
            auto = switchRow(R.string.auto_run, R.string.auto_run_sub, R.string.h_enabled)
            add(auto.view)

            add(heading(getString(R.string.repeat_every)), top = 16)
            intervalChips = ChipGroup(this@SchedulerActivity)
            intervalChips.isSingleSelection = true
            intervalChips.isSelectionRequired = false
            for (m in presets) {
                val ch = chip(shortInterval(this@SchedulerActivity, m), 0, true)
                presetIds[ch.id] = m
                intervalChips.addView(ch)
            }
            add(intervalChips, top = 4)

            intervalInput = input(R.string.interval_min, "60", InputType.TYPE_CLASS_NUMBER)
            add(intervalInput, top = 8)

            shortInfo = cardWith(12, R.color.warning_bg, false) {
                val t = tv(getString(R.string.short_interval_info), 13f)
                t.setTextColor(color(R.color.warning))
                add(t)
                add(textBtn(getString(R.string.battery_opt), 0, R.string.h_battery) { openBatterySettings() }, WRAP, WRAP, top = 4)
            }
            shortInfo.visibility = View.GONE
            add(shortInfo, top = 12)

            nextView = caption("", 12f)
            add(nextView, top = 10)
        }, top = 8)

        // ---- rules ----
        body.add(cardWith(16) {
            add(heading(getString(R.string.rules_title)))
            onlyNet = switchRow(R.string.only_internet, 0, R.string.h_only_net)
            add(onlyNet.view, top = 12)
            dailyLimit = input(R.string.daily_limit, "0", InputType.TYPE_CLASS_NUMBER)
            add(dailyLimit, top = 12)
        }, top = 12)

        // ---- notifications ----
        body.add(cardWith(16) {
            add(heading(getString(R.string.notify_me)))
            modeChips = ChipGroup(this@SchedulerActivity)
            modeChips.isSingleSelection = true
            modeChips.isSelectionRequired = true
            for (m in modes) {
                val ch = chip(notifyModeLabel(this@SchedulerActivity, m), 0, true)
                modeIds[ch.id] = m
                modeChips.addView(ch)
            }
            modeChips.help(R.string.h_mode)
            add(modeChips, top = 4)

            val cb = vbox(0)
            cb.add(caption(getString(R.string.cond_hint), 12f))
            condSpinner = Spinner(this@SchedulerActivity)
            condSpinner.adapter = ArrayAdapter(
                this@SchedulerActivity, android.R.layout.simple_spinner_dropdown_item,
                conds.map { conditionLabel(this@SchedulerActivity, it) }
            )
            condSpinner.help(R.string.h_cond)
            cb.add(condSpinner, top = 4)
            condKey = input(R.string.cond_key)
            cb.add(condKey, top = 8)
            condValue = input(R.string.cond_value)
            cb.add(condValue, top = 8)
            condBox = cb
            condBox.visibility = View.GONE
            add(cb, top = 8)

            notifyFail = switchRow(R.string.notify_failure, 0, R.string.h_notify_fail)
            add(notifyFail.view, top = 12)
        }, top = 12)

        val scroll = ScrollView(this)
        scroll.addView(body)
        root.add(scroll, MATCH, 0, 1f)

        // ---- sticky actions ----
        val bar = hbox()
        bar.setPadding(0, dp(8), 0, 0)
        bar.add(filledBtn(getString(R.string.save_changes), R.drawable.ic_check, R.string.h_save) { save() }, 0, WRAP, 2f)
        bar.add(
            tonalBtn(getString(R.string.run_now), R.drawable.ic_play, R.string.h_run_now) {
                Toast.makeText(this, R.string.run_started, Toast.LENGTH_SHORT).show()
                AppScope.launch { ScriptEngine.run(applicationContext, scriptId, true) }
            },
            0, WRAP, 1.4f, start = 8
        )
        root.add(bar)
        setContentView(root)

        wireInteractions()

        lifecycleScope.launch {
            val s = db.scripts().get(scriptId)
            if (s == null) {
                finish()
                return@launch
            }
            fill(s)
        }
    }

    private fun wireInteractions() {
        intervalChips.setOnCheckedStateChangeListener { _, ids ->
            if (syncing) return@setOnCheckedStateChangeListener
            val m = ids.firstOrNull()?.let { presetIds[it] }
            if (m != null) {
                syncing = true
                intervalInput.editText?.setText(m.toString())
                syncing = false
                refreshInfo()
            }
        }
        intervalInput.editText?.doAfterTextChanged {
            if (syncing) return@doAfterTextChanged
            val v = it?.toString()?.toIntOrNull()
            syncing = true
            val match = presetIds.entries.firstOrNull { e -> e.value == v }
            if (match != null) intervalChips.check(match.key) else intervalChips.clearCheck()
            syncing = false
            refreshInfo()
        }
        modeChips.setOnCheckedStateChangeListener { _, ids ->
            val mode = ids.firstOrNull()?.let { modeIds[it] }
            condBox.visibility = if (mode == NotifyMode.ON_CONDITION) View.VISIBLE else View.GONE
        }
    }

    private fun refreshInfo() {
        val m = intervalInput.str().toIntOrNull() ?: 0
        shortInfo.visibility = if (m in 1 until Scheduler.MIN_PERIODIC) View.VISIBLE else View.GONE
    }

    private fun fill(s: ScriptEntity) {
        subtitle.text = s.name
        auto.sw.isChecked = s.enabled
        syncing = true
        intervalInput.editText?.setText(s.intervalMinutes.toString())
        val match = presetIds.entries.firstOrNull { it.value == s.intervalMinutes }
        if (match != null) intervalChips.check(match.key) else intervalChips.clearCheck()
        syncing = false
        refreshInfo()
        onlyNet.sw.isChecked = s.onlyWithInternet
        dailyLimit.editText?.setText(s.dailyLimit.toString())
        val mode = enumOr(s.notifyMode, NotifyMode.ON_CHANGE)
        val modeChip = modeIds.entries.firstOrNull { it.value == mode }
        if (modeChip != null) modeChips.check(modeChip.key)
        condSpinner.setSelection(conds.indexOf(enumOr(s.conditionType, ConditionType.NONE)))
        condKey.editText?.setText(s.conditionKey)
        condValue.editText?.setText(s.conditionValue)
        notifyFail.sw.isChecked = s.notifyOnFailure
        refreshNext(s)
    }

    private fun refreshNext(s: ScriptEntity) {
        val next = Scheduler.nextEstimate(s)
        nextView.text = if (next == 0L) getString(R.string.next_none) else getString(R.string.next_run_fmt, fmtTime(next))
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.export_fail, Toast.LENGTH_SHORT).show()
        }
    }

    private fun save() {
        val minutes = intervalInput.str().toIntOrNull()
        if (minutes == null || minutes < 1) {
            Toast.makeText(this, R.string.interval_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        val limit = dailyLimit.str().toIntOrNull()?.coerceAtLeast(0) ?: 0
        val isEnabled = auto.sw.isChecked
        val net = onlyNet.sw.isChecked
        val mode = (modeIds[modeChips.checkedChipId] ?: NotifyMode.ON_CHANGE).name
        val ct = conds[condSpinner.selectedItemPosition].name
        val ck = condKey.str()
        val cv = condValue.str()
        val nf = notifyFail.sw.isChecked
        val id = scriptId
        val app = applicationContext

        AppScope.launch {
            val dao = db.scripts()
            dao.updateSchedule(id, minutes, isEnabled, net, limit)
            dao.updateNotify(id, mode, ct, ck, cv, nf)
            val fresh = dao.get(id) ?: return@launch
            Scheduler.apply(app, fresh)
            withContext(Dispatchers.Main) {
                val msg = if (isEnabled && minutes < Scheduler.MIN_PERIODIC) R.string.saved_foreground else R.string.saved_toast
                Toast.makeText(this@SchedulerActivity, msg, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
