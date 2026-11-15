package com.webautomator.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.webautomator.app.data.ScriptEntity
import java.util.concurrent.TimeUnit

object Scheduler {
    /** WorkManager cannot repeat faster than this; shorter intervals use the foreground monitor. */
    const val MIN_PERIODIC = 15

    private fun workName(id: Long) = "script_$id"

    fun apply(ctx: Context, s: ScriptEntity, refreshService: Boolean = true) {
        val wm = WorkManager.getInstance(ctx.applicationContext)
        if (s.enabled && s.intervalMinutes >= MIN_PERIODIC) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (s.onlyWithInternet) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
                .build()
            val req = PeriodicWorkRequestBuilder<ScriptWorker>(s.intervalMinutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(workDataOf(ScriptWorker.KEY_ID to s.id))
                .setBackoffCriteria(BackoffPolicy.LINEAR, 2, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(workName(s.id), ExistingPeriodicWorkPolicy.UPDATE, req)
        } else {
            wm.cancelUniqueWork(workName(s.id))
        }
        if (refreshService) MonitorService.refresh(ctx)
    }

    fun cancel(ctx: Context, id: Long) {
        WorkManager.getInstance(ctx.applicationContext).cancelUniqueWork(workName(id))
        MonitorService.refresh(ctx)
    }

    /** Estimate only: Android does not guarantee exact timing. 0 means not scheduled. */
    fun nextEstimate(s: ScriptEntity): Long {
        if (!s.enabled) return 0
        val now = System.currentTimeMillis()
        if (s.lastRunAt == 0L) return now + s.intervalMinutes * 60_000L
        return maxOf(now, s.lastRunAt + s.intervalMinutes * 60_000L)
    }
}
