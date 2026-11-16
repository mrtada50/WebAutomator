package com.webautomator.app.ui

import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.webautomator.app.R
import com.webautomator.app.util.UrlResult
import com.webautomator.app.util.UrlUtils
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
        val urlField = activity.input(R.string.default_url, "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        urlField.help(R.string.h_default_url)

        val form = activity.vbox(20)
        form.add(activity.caption(activity.getString(R.string.theme), 12f))
        form.add(themeSpinner, top = 4)
        form.add(activity.caption(activity.getString(R.string.language), 12f), top = 16)
        form.add(langSpinner, top = 4)
        form.add(activity.caption(activity.getString(R.string.default_url_title), 12f), top = 16)
        form.add(urlField, top = 4)

        activity.lifecycleScope.launch {
            val (t, l) = AppSettings.load(activity)
            themeSpinner.setSelection(t.coerceIn(0, 2))
            langSpinner.setSelection(langCodes.indexOf(l).coerceAtLeast(0))
            urlField.editText?.setText(AppSettings.loadDefaultUrl(activity))
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.settings)
            .setView(form)
            .setPositiveButton(R.string.ok) { _, _ ->
                val t = themeSpinner.selectedItemPosition
                val l = langCodes[langSpinner.selectedItemPosition]
                val typed = urlField.str()
                val valid = if (typed.isEmpty()) null else UrlUtils.normalize(typed)
                if (typed.isNotEmpty() && valid !is UrlResult.Valid) {
                    Toast.makeText(activity, R.string.invalid_url, Toast.LENGTH_SHORT).show()
                }
                activity.lifecycleScope.launch {
                    if (typed.isEmpty()) AppSettings.saveDefaultUrl(activity, "")
                    else if (valid is UrlResult.Valid) AppSettings.saveDefaultUrl(activity, valid.url)
                    AppSettings.save(activity, t, l)
                    AppSettings.apply(t, l)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
