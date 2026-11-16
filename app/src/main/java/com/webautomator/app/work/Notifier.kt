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
    const val CH_POPUP = "results_popup"
    const val CH_ALERTS = "alerts"
    const val CH_SERVICE = "service"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        // Important (sound, status bar) but never a pop-up
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULTS, ctx.getString(R.string.ch_results), NotificationManager.IMPORTANCE_DEFAULT)
        )
        // Important and pop-up (heads-up on top of the screen)
        nm.createNotificationChannel(
            NotificationChannel(CH_POPUP, ctx.getString(R.string.ch_popup), NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, ctx.getString(R.string.ch_alerts), NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, ctx.getString(R.string.ch_service), NotificationManager.IMPORTANCE_LOW)
        )
    }

    @SuppressLint("MissingPermission")
    fun post(ctx: Context, scriptId: Long, title: String, text: String, alert: Boolean, popup: Boolean = true) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        ensureChannels(ctx)
        val intent = Intent(ctx, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_ID, scriptId)
        val pi = PendingIntent.getActivity(
            ctx, scriptId.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val channel = when {
            alert -> CH_ALERTS
            popup -> CH_POPUP
            else -> CH_RESULTS
        }
        val heads = alert || popup
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(if (heads) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentTitle(title)
            .setContentText(text.lineSequence().firstOrNull() ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify((scriptId % 100000).toInt() * 2 + (if (alert) 1 else 0), n)
    }

    /** Opens the system screen where the user can change sound, pop-up and importance per channel. */
    fun openSystemSettings(ctx: Context) {
        val i = if (Build.VERSION.SDK_INT >= 26) {
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        } else {
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.parse("package:" + ctx.packageName))
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (e: Exception) {
            // no settings screen available
        }
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
