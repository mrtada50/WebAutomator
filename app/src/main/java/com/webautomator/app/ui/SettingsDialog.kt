package com.webautomator.app.ui

import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.webautomator.app.R
import kotlinx.coroutines.launch

object SettingsDialog {
    fun show(activity: AppCompatActivity) {
        val themes = listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark).map { activity.getString(it) }
        val langs = listOf(R.string.lang_system, R.string.lang_ar, R.string.lang_en).map { activity.getString(it) }
        val langCodes = listOf("", "ar", "en")

        val themeSpinner = Spinner(activity)
        themeSpinner.help(R.string.h_theme)
        themeSpinner.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, themes)
        val langSpinner = Spinner(activity)
        langSpinner.help(R.string.h_lang)
        langSpinner.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, langs)

        val form = activity.vbox(20)
        form.add(activity.tv(activity.getString(R.string.theme), 14f, true))
        form.add(themeSpinner)
        form.add(activity.tv(activity.getString(R.string.language), 14f, true), top = 12)
        form.add(langSpinner)

        activity.lifecycleScope.launch {
            val (t, l) = AppSettings.load(activity)
            themeSpinner.setSelection(t.coerceIn(0, 2))
            langSpinner.setSelection(langCodes.indexOf(l).coerceAtLeast(0))
        }

        AlertDialog.Builder(activity)
            .setTitle(R.string.settings)
            .setView(form)
            .setPositiveButton(R.string.ok) { _, _ ->
                val t = themeSpinner.selectedItemPosition
                val l = langCodes[langSpinner.selectedItemPosition]
                activity.lifecycleScope.launch {
                    AppSettings.save(activity, t, l)
                    AppSettings.apply(t, l)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
