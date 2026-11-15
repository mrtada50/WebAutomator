package com.webautomator.app

import android.app.Application
import com.webautomator.app.ui.AppSettings
import com.webautomator.app.work.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/** Process-wide scope for work that must outlive any screen (runs, DB writes). */
object AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
        val (theme, lang) = runBlocking { AppSettings.load(this@App) }
        AppSettings.apply(theme, lang)
    }
}
