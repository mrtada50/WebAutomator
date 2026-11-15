package com.webautomator.app.work

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.webautomator.app.AppScope
import com.webautomator.app.MainActivity
import com.webautomator.app.R
import com.webautomator.app.data.AppDatabase
import com.webautomator.app.engine.ScriptEngine
import com.webautomator.app.util.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * User-visible foreground mode for intervals shorter than 15 minutes.
 * Runs only while at least one enabled script needs it, and shows a persistent notification.
 */
class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            Notifier.ensureChannels(this)
            val pi = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(this, Notifier.CH_SERVICE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notif_service_title))
                .setContentText(getString(R.string.notif_service_text))
                .setOngoing(true)
                .setContentIntent(pi)
                .build()
            ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (e: Exception) {
            Log.w("MonitorService", "cannot start foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop?.isActive != true) loop = scope.launch { monitor() }
        return START_STICKY
    }

    private suspend fun monitor() {
        val app = applicationContext
        val dao = AppDatabase.get(app).scripts()
        while (true) {
            val short = dao.getAll().filter { it.enabled && it.intervalMinutes < Scheduler.MIN_PERIODIC }
            if (short.isEmpty()) {
                stopSelf()
                return
            }
            val now = System.currentTimeMillis()
            for (s in short) {
                if (now - s.lastRunAt < s.intervalMinutes * 60_000L) continue
                if (s.onlyWithInternet && !Network.isOnline(app)) continue
                ScriptEngine.run(app, s.id, false)
            }
            delay(15_000)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun refresh(ctx: Context) {
            val app = ctx.applicationContext
            AppScope.launch {
                val any = AppDatabase.get(app).scripts().getAll()
                    .any { it.enabled && it.intervalMinutes < Scheduler.MIN_PERIODIC }
                val intent = Intent(app, MonitorService::class.java)
                try {
                    if (any) ContextCompat.startForegroundService(app, intent) else app.stopService(intent)
                } catch (e: Exception) {
                    Log.w("MonitorService", "refresh failed", e)
                }
            }
        }
    }
}
