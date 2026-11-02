package com.sade.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object Lang {
    val codes = arrayOf("en", "tr", "de", "fr", "es", "it", "pt", "nl", "sv", "nb", "da", "fi", "is", "pl", "cs", "sk", "hu", "ro", "bg", "el", "hr", "sr", "bs", "sl", "sq", "mk", "uk", "be", "ru", "et", "lv", "lt", "ga", "mt", "cy", "ca", "eu", "gl", "lb", "hy", "ka", "ar", "fa", "ja", "ko", "zh", "zh-Hant", "he", "id", "th")
    private val names = arrayOf("English", "Türkçe", "Deutsch", "Français", "Español", "Italiano", "Português", "Nederlands", "Svenska", "Norsk bokmål", "Dansk", "Suomi", "Íslenska", "Polski", "Čeština", "Slovenčina", "Magyar", "Română", "Български", "Ελληνικά", "Hrvatski", "Српски", "Bosanski", "Slovenščina", "Shqip", "Македонски", "Українська", "Беларуская", "Русский", "Eesti", "Latviešu", "Lietuvių", "Gaeilge", "Malti", "Cymraeg", "Català", "Euskara", "Galego", "Lëtzebuergesch", "Հայերեն", "ქართული", "العربية", "فارسی", "日本語", "한국어", "简体中文", "繁體中文", "עברית", "Bahasa Indonesia", "ไทย")
    fun selected(context: Context): String = if (Build.VERSION.SDK_INT >= 33)
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        else context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("language", "") ?: ""
    fun wrap(context: Context): Context {
        val tag = selected(context)
        val requested = if (tag.isEmpty()) android.content.res.Resources.getSystem().configuration.locales[0]
            else Locale.forLanguageTag(tag.split(',')[0])
        val language = LanguagePolicy.choose(requested.toLanguageTag(), codes)
        // Only the primary system language is used. Unsupported languages fall back to English.
        val locale = Locale.forLanguageTag(language)
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(locale)); config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
    fun choose(activity: Activity) {
        val labels = arrayOf(activity.getString(R.string.ui_128), *names)
        val tags = arrayOf("", *codes)
        val stored = selected(activity)
        val current = if (stored.isEmpty()) "" else LanguagePolicy.choose(stored.split(',')[0], codes)
        DialogStyle.builder(activity).setTitle(activity.getString(R.string.ui_022))
            .setSingleChoiceItems(labels, tags.indexOf(current).coerceAtLeast(0)) { dialog, i ->
                dialog.dismiss()
                if (Build.VERSION.SDK_INT >= 33) {
                    activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tags[i])
                } else {
                    activity.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("language", tags[i]).apply()
                    activity.recreate()
                }
            }.setNegativeButton(activity.getString(R.string.ui_063), null).show()
    }
}

open class LocalizedActivity : Activity() {
    private var attachedLanguage = ""
    override fun attachBaseContext(newBase: Context) {
        attachedLanguage = Lang.selected(newBase)
        super.attachBaseContext(Lang.wrap(newBase))
    }
    override fun onResume() {
        super.onResume()
        if (Lang.selected(this) != attachedLanguage) recreate()
    }
}
