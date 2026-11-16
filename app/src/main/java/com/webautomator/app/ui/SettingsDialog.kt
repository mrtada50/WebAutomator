package com.webautomator.app.ui

import android.widget.ArrayAdapter
import android.widget.Spinner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        form.add(activity.caption(activity.getString(R.string.theme), 12f))
        form.add(themeSpinner, top = 4)
        form.add(activity.caption(activity.getString(R.string.language), 12f), top = 16)
        form.add(langSpinner, top = 4)

        activity.lifecycleScope.launch {
            val (t, l) = AppSettings.load(activity)
            themeSpinner.setSelection(t.coerceIn(0, 2))
            langSpinner.setSelection(langCodes.indexOf(l).coerceAtLeast(0))
        }

        MaterialAlertDialogBuilder(activity)
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
