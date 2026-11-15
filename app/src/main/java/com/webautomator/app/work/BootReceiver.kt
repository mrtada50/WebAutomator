package com.webautomator.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.webautomator.app.AppScope
import com.webautomator.app.data.AppDatabase
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext
        val pending = goAsync()
        AppScope.launch {
            try {
                val enabled = AppDatabase.get(app).scripts().getAll().filter { it.enabled }
                enabled.forEach { Scheduler.apply(app, it, refreshService = false) }
                if (enabled.any { it.intervalMinutes < Scheduler.MIN_PERIODIC }) {
                    Notifier.postResumeMonitoring(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
