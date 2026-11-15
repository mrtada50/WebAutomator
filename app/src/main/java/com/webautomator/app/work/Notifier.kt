package com.webautomator.app.work

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.webautomator.app.MainActivity
import com.webautomator.app.R
import com.webautomator.app.ui.EditorActivity

object Notifier {
    const val CH_RESULTS = "results"
    const val CH_ALERTS = "alerts"
    const val CH_SERVICE = "service"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULTS, ctx.getString(R.string.ch_results), NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, ctx.getString(R.string.ch_alerts), NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, ctx.getString(R.string.ch_service), NotificationManager.IMPORTANCE_LOW)
        )
    }

    @SuppressLint("MissingPermission")
    fun post(ctx: Context, scriptId: Long, title: String, text: String, alert: Boolean) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        ensureChannels(ctx)
        val intent = Intent(ctx, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_ID, scriptId)
        val pi = PendingIntent.getActivity(
            ctx, scriptId.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, if (alert) CH_ALERTS else CH_RESULTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text.lineSequence().firstOrNull() ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify((scriptId % 100000).toInt() * 2 + (if (alert) 1 else 0), n)
    }

    @SuppressLint("MissingPermission")
    fun postResumeMonitoring(ctx: Context) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        ensureChannels(ctx)
        val intent = Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_RESUME, true)
        val pi = PendingIntent.getActivity(ctx, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.notif_boot_title))
            .setContentText(ctx.getString(R.string.notif_boot_text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(999_999, n)
    }
}
