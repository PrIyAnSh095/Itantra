package com.itantra.app.localization

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.itantra.app.data.AppLanguage
import java.util.Locale

object AppLanguageManager {

    private const val PREFS_NAME = "itantra_locale_prefs"
    private const val KEY_APP_LANGUAGE = "selected_app_language"

    fun getSelectedLanguage(context: Context): AppLanguage {
        val prefs = getPrefs(context)
        val code = prefs.getString(KEY_APP_LANGUAGE, AppLanguage.ENGLISH.code) ?: AppLanguage.ENGLISH.code
        return AppLanguage.fromCode(code)
    }

    fun setSelectedLanguage(context: Context, language: AppLanguage) {
        getPrefs(context).edit().putString(KEY_APP_LANGUAGE, language.code).apply()
    }

    fun wrapContext(context: Context): Context {
        val lang = getSelectedLanguage(context)
        val locale = Locale(lang.code)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        return context.createConfigurationContext(config)
    }

    fun applyAppLanguage(activity: Activity, language: AppLanguage) {
        val currentLang = getSelectedLanguage(activity)
        if (currentLang == language) return

        setSelectedLanguage(activity, language)
        val locale = Locale(language.code)
        Locale.setDefault(locale)

        val config = Configuration(activity.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        @Suppress("DEPRECATION")
        activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
        activity.recreate()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
