package com.webautomator.app.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** Theme (0 system, 1 light, 2 dark) and language ("" system, "ar", "en"), kept in DataStore. */
object AppSettings {
    private val THEME = intPreferencesKey("theme")
    private val LANG = stringPreferencesKey("lang")
    private val DEFAULT_URL = stringPreferencesKey("default_url")

    suspend fun load(ctx: Context): Pair<Int, String> {
        val p = ctx.settingsStore.data.first()
        return Pair(p[THEME] ?: 0, p[LANG] ?: "")
    }

    /** Website opened automatically when the user starts building an automation. */
    suspend fun loadDefaultUrl(ctx: Context): String = ctx.settingsStore.data.first()[DEFAULT_URL] ?: ""

    suspend fun saveDefaultUrl(ctx: Context, url: String) {
        ctx.settingsStore.edit { it[DEFAULT_URL] = url }
    }

    suspend fun save(ctx: Context, theme: Int, lang: String) {
        ctx.settingsStore.edit {
            it[THEME] = theme
            it[LANG] = lang
        }
    }

    fun apply(theme: Int, lang: String) {
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
        AppCompatDelegate.setApplicationLocales(
            if (lang.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(lang)
        )
    }
}
