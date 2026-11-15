package com.webautomator.app.work

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.webautomator.app.R
import com.webautomator.app.engine.RunOutcome
import com.webautomator.app.engine.ScriptEngine

/**
 * Periodic entry point (intervals of 15 minutes or more).
 * It asks for foreground priority so the WebView is less likely to be killed; Android may refuse
 * that from the background, in which case the run continues with its own bounded timeout.
 */
class ScriptWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1L)
        if (id < 0) return Result.failure()
        try {
            setForeground(foregroundInfo())
        } catch (e: Exception) {
            // not allowed right now: continue as a normal worker
        }
        return when (val outcome = ScriptEngine.run(applicationContext, id, false)) {
            is RunOutcome.Failure -> if (outcome.retryable) Result.retry() else Result.success()
            else -> Result.success()
        }
    }

    private fun foregroundInfo(): ForegroundInfo {
        Notifier.ensureChannels(applicationContext)
        val n = NotificationCompat.Builder(applicationContext, Notifier.CH_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.notif_running))
            .setOngoing(true)
            .build()
        return ForegroundInfo(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        const val KEY_ID = "id"
    }
}
