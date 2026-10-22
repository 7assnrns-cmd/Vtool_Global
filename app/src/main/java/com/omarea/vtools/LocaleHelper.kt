package com.omarea.vtools

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {
    private const val PREFS = "vtool_language_prefs"
    private const val KEY = "app_language"
    const val DEFAULT = "en"

    val supported: List<Pair<String, String>> = listOf(
        "en" to "English",
        "ar" to "العربية",
        "ja" to "日本語",
        "zh" to "中文"
    )

    fun getSaved(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, DEFAULT) ?: DEFAULT
    }

    fun isFirstRun(context: Context): Boolean {
        return !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY)
    }

    fun save(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, lang).apply()
    }

    fun wrap(context: Context): Context {
        val lang = getSaved(context)
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun apply(activity: Activity, lang: String) {
        save(activity, lang)
        activity.recreate()
    }
}
