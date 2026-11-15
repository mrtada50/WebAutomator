package com.webautomator.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.webautomator.app.AppScope
import com.webautomator.app.R
import com.webautomator.app.data.AppDatabase
import com.webautomator.app.data.ConditionType
import com.webautomator.app.data.NotifyMode
import com.webautomator.app.data.ScriptEntity
import com.webautomator.app.data.enumOr
import com.webautomator.app.engine.ScriptEngine
import com.webautomator.app.work.Scheduler
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/** Schedule, start/stop, run now and notification rules for one script. */
class SchedulerActivity : AppCompatActivity() {

    private var scriptId = 0L
    private lateinit var db: AppDatabase
    private lateinit var titleView: TextView
    private lateinit var enabled: MaterialSwitch
    private lateinit var interval: android.widget.EditText
    private lateinit var onlyNet: CheckBox
    private lateinit var dailyLimit: android.widget.EditText
    private lateinit var modeSpinner: Spinner
    private lateinit var condSpinner: Spinner
    private lateinit var condKey: android.widget.EditText
    private lateinit var condValue: android.widget.EditText
    private lateinit var notifyFail: CheckBox
    private lateinit var nextView: TextView
    private lateinit var startStop: MaterialButton

    private val modes = NotifyMode.values()
    private val conds = ConditionType.values()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppDatabase.get(this)
        scriptId = intent.getLongExtra(EditorActivity.EXTRA_ID, 0L)

        val form = vbox(16)
        titleView = tv("", 20f, true)
        form.add(titleView)

        enabled = MaterialSwitch(this)
        enabled.setText(R.string.enabled)
        form.add(enabled, top = 8)

        interval = field(R.string.interval_min, "60", InputType.TYPE_CLASS_NUMBER)
        form.add(interval, top = 8)
        form.add(tv(getString(R.string.interval_note), 12f))

        onlyNet = CheckBox(this)
        onlyNet.setText(R.string.only_internet)
        form.add(onlyNet, top = 8)

        dailyLimit = field(R.string.daily_limit, "0", InputType.TYPE_CLASS_NUMBER)
        form.add(dailyLimit, top = 4)

        form.add(tv(getString(R.string.notif_section), 16f, true), top = 16)
        modeSpinner = Spinner(this)
        modeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modes.map { notifyModeLabel(this, it) })
        form.add(modeSpinner, top = 4)
        condSpinner = Spinner(this)
        condSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, conds.map { conditionLabel(this, it) })
        form.add(condSpinner, top = 4)
        condKey = field(R.string.cond_key)
        form.add(condKey)
        condValue = field(R.string.cond_value)
        form.add(condValue)
        notifyFail = CheckBox(this)
        notifyFail.setText(R.string.notify_failure)
        form.add(notifyFail, top = 4)

        nextView = tv("", 13f)
        form.add(nextView, top = 16)

        form.add(btn(R.string.save) { save(false) }, top = 8)
        startStop = btn(R.string.start) { toggleEnabled() }
        form.add(startStop, top = 4)
        form.add(btn(R.string.run_now) {
            Toast.makeText(this, R.string.run_started, Toast.LENGTH_SHORT).show()
            AppScope.launch { ScriptEngine.run(applicationContext, scriptId, true) }
        }, top = 4)
        form.add(btn(R.string.battery_opt) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, R.string.export_fail, Toast.LENGTH_SHORT).show()
            }
        }, top = 4)

        setContentView(ScrollView(this).apply {
            addView(form, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })

        lifecycleScope.launch {
            val s = db.scripts().get(scriptId)
            if (s == null) {
                finish()
                return@launch
            }
            fill(s)
        }
    }

    private fun fill(s: ScriptEntity) {
        titleView.text = s.name
        enabled.isChecked = s.enabled
        interval.setText(s.intervalMinutes.toString())
        onlyNet.isChecked = s.onlyWithInternet
        dailyLimit.setText(s.dailyLimit.toString())
        modeSpinner.setSelection(modes.indexOf(enumOr(s.notifyMode, NotifyMode.ON_CHANGE)))
        condSpinner.setSelection(conds.indexOf(enumOr(s.conditionType, ConditionType.NONE)))
        condKey.setText(s.conditionKey)
        condValue.setText(s.conditionValue)
        notifyFail.isChecked = s.notifyOnFailure
        refreshNext(s)
    }

    private fun refreshNext(s: ScriptEntity) {
        val next = Scheduler.nextEstimate(s)
        nextView.text = if (next == 0L) getString(R.string.next_none) else getString(R.string.next_run_fmt, fmtTime(next))
        startStop.setText(if (s.enabled) R.string.stop else R.string.start)
    }

    private fun toggleEnabled() {
        enabled.isChecked = !enabled.isChecked
        save(true)
    }

    private fun save(fromToggle: Boolean) {
        val minutes = interval.text.toString().toIntOrNull()
        if (minutes == null || minutes < 1) {
            Toast.makeText(this, R.string.interval_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        val limit = dailyLimit.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: 0
        val isEnabled = enabled.isChecked
        val net = onlyNet.isChecked
        val mode = modes[modeSpinner.selectedItemPosition].name
        val ct = conds[condSpinner.selectedItemPosition].name
        val ck = condKey.text.toString().trim()
        val cv = condValue.text.toString().trim()
        val nf = notifyFail.isChecked
        val id = scriptId
        val app = applicationContext

        AppScope.launch {
            val dao = db.scripts()
            dao.updateSchedule(id, minutes, isEnabled, net, limit)
            dao.updateNotify(id, mode, ct, ck, cv, nf)
            val fresh = dao.get(id) ?: return@launch
            Scheduler.apply(app, fresh)
            withContext(Dispatchers.Main) {
                refreshNext(fresh)
                val msg = if (isEnabled && minutes < Scheduler.MIN_PERIODIC) R.string.saved_foreground else R.string.saved_toast
                Toast.makeText(this@SchedulerActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
}
