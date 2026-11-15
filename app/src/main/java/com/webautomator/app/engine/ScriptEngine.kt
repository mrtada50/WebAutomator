package com.webautomator.app.engine

import android.content.Context
import android.os.PowerManager
import com.webautomator.app.R
import com.webautomator.app.data.AppDatabase
import com.webautomator.app.data.ActionJson
import com.webautomator.app.data.ConditionType
import com.webautomator.app.data.MapJson
import com.webautomator.app.data.NotifyMode
import com.webautomator.app.data.RunEntity
import com.webautomator.app.data.ScriptEntity
import com.webautomator.app.data.Status
import com.webautomator.app.data.enumOr
import com.webautomator.app.ui.errorText
import com.webautomator.app.util.Network
import com.webautomator.app.work.Notifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

sealed class RunOutcome {
    object Success : RunOutcome()
    data class Failure(val kind: ErrorKind, val message: String) : RunOutcome() {
        val retryable: Boolean get() = kind == ErrorKind.NETWORK || kind == ErrorKind.TIMEOUT
    }
    data class Skipped(val reason: String) : RunOutcome()
}

object ScriptEngine {
    private val running: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val execMutex = Mutex()

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Runs one script. Never runs the same script twice at once; runs are serialized. */
    suspend fun run(ctx: Context, scriptId: Long, manual: Boolean): RunOutcome {
        val app = ctx.applicationContext
        val db = AppDatabase.get(app)
        val script = db.scripts().get(scriptId) ?: return RunOutcome.Skipped("missing")
        if (!running.add(scriptId)) return RunOutcome.Skipped("already running")
        try {
            return execMutex.withLock { execute(app, db, script, manual) }
        } finally {
            running.remove(scriptId)
        }
    }

    private suspend fun execute(app: Context, db: AppDatabase, script: ScriptEntity, manual: Boolean): RunOutcome {
        val dao = db.scripts()
        val day = today()
        val runsToday = if (script.runsDay == day) script.runsToday else 0
        if (!manual && script.dailyLimit > 0 && runsToday >= script.dailyLimit) {
            return RunOutcome.Skipped("daily limit")
        }

        if (!Network.isOnline(app)) {
            return failure(app, db, script, ScriptException(ErrorKind.NO_INTERNET, ""), runsToday, day, notify = false)
        }

        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "webautomator:run")
        wl.setReferenceCounted(false)
        wl.acquire(3 * 60 * 1000L)
        try {
            dao.updateStatus(script.id, Status.RUNNING.name)

            val actions = try {
                ActionJson.decode(script.actionsJson)
            } catch (e: Exception) {
                emptyList()
            }
            if (actions.none { it.type.isExtraction() }) {
                return failure(app, db, script, ScriptException(ErrorKind.EMPTY, "no extraction actions"), runsToday, day, true)
            }

            val prevRun = db.runs().lastSuccess(script.id)
            val prev: Map<String, String>? = prevRun?.let { MapJson.decode(it.valuesJson) }

            var saved: RunEntity? = null
            var savedCmp: Comparison? = null
            var finalValues: Map<String, String> = emptyMap()

            val hooks = object : RunHooks {
                override suspend fun save(values: Map<String, String>) {
                    if (values.isEmpty()) throw ScriptException(ErrorKind.EMPTY, "no data to save")
                    saved?.let { db.runs().deleteById(it.id) }
                    val cmp = ValueComparator.compare(prev, values)
                    val run = RunEntity(
                        scriptId = script.id,
                        scriptName = script.name,
                        timestamp = System.currentTimeMillis(),
                        success = true,
                        valuesJson = MapJson.encode(values),
                        changeSummary = cmp.summary,
                        error = "",
                        changed = cmp.changed
                    )
                    val id = db.runs().insert(run)
                    saved = run.copy(id = id)
                    savedCmp = cmp
                    finalValues = values
                }

                override suspend fun notify(message: String, values: Map<String, String>) {
                    val text = if (message.isBlank()) summarize(values) else render(message, values)
                    Notifier.post(app, script.id, script.name, text, alert = false)
                }
            }

            var attempt = 0
            var lastErr: ScriptException? = null
            while (attempt < 3) {
                attempt++
                try {
                    val values = WebRunner(app).run(actions, script.url, hooks)
                    if (values.isEmpty()) throw ScriptException(ErrorKind.EMPTY, "no data collected")
                    if (saved == null) hooks.save(values)
                    lastErr = null
                    break
                } catch (e: ScriptException) {
                    lastErr = e
                    val retry = (e.kind == ErrorKind.NETWORK || e.kind == ErrorKind.TIMEOUT) && attempt < 3
                    if (!retry) break
                    delay(5_000L * attempt)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastErr = ScriptException(ErrorKind.JS_ERROR, e.message ?: e.javaClass.simpleName)
                    break
                }
            }

            val err = lastErr
            if (err != null) {
                saved?.let { db.runs().deleteById(it.id) }
                return failure(app, db, script, err, runsToday, day, true)
            }

            val cmp = savedCmp ?: Comparison(false, "", true)
            dao.updateRunInfo(
                script.id, System.currentTimeMillis(), Status.OK.name,
                app.getString(R.string.msg_ok, finalValues.size), runsToday + 1, day
            )
            db.runs().trim(script.id, 200)
            maybeNotify(app, script, finalValues, prev, cmp)
            return RunOutcome.Success
        } finally {
            if (wl.isHeld) wl.release()
        }
    }

    private suspend fun failure(
        app: Context,
        db: AppDatabase,
        script: ScriptEntity,
        e: ScriptException,
        runsToday: Int,
        day: String,
        notify: Boolean
    ): RunOutcome {
        val stored = e.kind.name + "|" + (e.message ?: "")
        val needsAttention = e.kind == ErrorKind.SELECTOR_MISSING || e.kind == ErrorKind.SESSION_EXPIRED ||
            e.kind == ErrorKind.EMPTY || e.kind == ErrorKind.DOMAIN
        val now = System.currentTimeMillis()
        db.runs().insert(
            RunEntity(
                scriptId = script.id, scriptName = script.name, timestamp = now, success = false,
                valuesJson = "{}", changeSummary = "", error = stored, changed = false
            )
        )
        db.runs().trim(script.id, 200)
        val counted = if (e.kind == ErrorKind.NO_INTERNET) runsToday else runsToday + 1
        db.scripts().updateRunInfo(
            script.id, now,
            if (needsAttention) Status.NEEDS_ATTENTION.name else Status.ERROR.name,
            errorText(app, stored), counted, day
        )
        val mustNotify = e.kind == ErrorKind.SELECTOR_MISSING || e.kind == ErrorKind.SESSION_EXPIRED
        if (notify && e.kind != ErrorKind.NO_INTERNET && (mustNotify || script.notifyOnFailure)) {
            Notifier.post(
                app, script.id,
                app.getString(R.string.notif_failed_title, script.name),
                errorText(app, stored),
                alert = true
            )
        }
        return RunOutcome.Failure(e.kind, e.message ?: "")
    }

    private fun maybeNotify(
        app: Context,
        script: ScriptEntity,
        values: Map<String, String>,
        prev: Map<String, String>?,
        cmp: Comparison
    ) {
        val mode = enumOr(script.notifyMode, NotifyMode.ON_CHANGE)
        val condType = enumOr(script.conditionType, ConditionType.NONE)
        val key = script.conditionKey.trim()
        val target = if (key.isNotEmpty()) values[key] else values.values.firstOrNull()
        val prevTarget = if (key.isNotEmpty()) prev?.get(key) else prev?.values?.firstOrNull()
        val conditionMet = condType != ConditionType.NONE &&
            Condition.evaluate(condType, target, script.conditionValue, prevTarget)

        val send = when (mode) {
            NotifyMode.ALWAYS -> true
            NotifyMode.ON_CHANGE -> cmp.changed
            NotifyMode.ON_CONDITION -> conditionMet
            NotifyMode.ON_DISAPPEAR, NotifyMode.NEVER -> false
        }
        if (!send) return
        val body = if (cmp.changed && !cmp.first) cmp.summary else summarize(values)
        Notifier.post(app, script.id, app.getString(R.string.notif_title, script.name), body, alert = false)
    }

    private fun summarize(values: Map<String, String>): String =
        values.entries.joinToString("\n") { it.key + ": " + it.value.replace('\n', ' ').take(120) }

    private fun render(template: String, values: Map<String, String>): String {
        var s = template
        for ((k, v) in values) s = s.replace("{" + k + "}", v)
        return s
    }
}
