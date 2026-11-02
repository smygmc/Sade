package com.sade.launcher

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper

/** All app-owned dialogs use the same palette, font and text scale as Sade. */
object DialogStyle {
    fun builder(base: Context): android.app.AlertDialog.Builder = object : android.app.AlertDialog.Builder(context(base), R.style.SadeDialogOverlay) {
        override fun create(): android.app.AlertDialog {
            val dialog = super.create()
            val prefs = base.getSharedPreferences("appearance", Context.MODE_PRIVATE)
            val paper = android.graphics.Color.parseColor(prefs.getString("paper", "#F3F0E8"))
            val ink = Appearance.ink(prefs, paper)
            val window = dialog.window ?: return dialog
            val callback = window.callback
            window.callback = object : android.view.Window.Callback by callback {
                override fun onAttachedToWindow() {
                    callback.onAttachedToWindow()
                    // AlertDialog must install its content before we access the decor.
                    val v = window.decorView
                    dialog.window?.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                        setColor(paper); cornerRadius = 20 * base.resources.displayMetrics.density
                    })
                    fun tint(view: android.view.View) {
                        if (view is android.widget.TextView) view.setTextColor(ink)
                        if (view is android.widget.EditText) ControlStyle.input(view, ink)
                        if (view is android.widget.CompoundButton) ControlStyle.toggle(view, ink)
                        if (view is android.widget.CheckedTextView) view.checkMarkTintList = android.content.res.ColorStateList.valueOf(ink)
                        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) tint(view.getChildAt(i))
                    }
                    tint(v)
                }
            }
            return dialog
        }
    }

    fun context(base: Context): Context {
        val prefs = base.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val config = Configuration(base.resources.configuration).apply {
            fontScale *= prefs.getInt("size", 26) / 26f
        }
        val paper = android.graphics.Color.parseColor(prefs.getString("paper", "#F3F0E8"))
        val ink = Appearance.ink(prefs, paper)
        // Resource-only controls use the nearest palette; dialog views get exact colors above.
        val paletteIndex = Appearance.colors.indices.minByOrNull {
            val c = android.graphics.Color.parseColor(Appearance.colors[it])
            fun delta(a: Int, b: Int) = (a - b).toDouble() * (a - b)
            delta(android.graphics.Color.red(c), android.graphics.Color.red(paper)) +
                delta(android.graphics.Color.green(c), android.graphics.Color.green(paper)) +
                delta(android.graphics.Color.blue(c), android.graphics.Color.blue(paper))
        } ?: 0
        val palette = Appearance.backgrounds[paletteIndex]
        return ContextThemeWrapper(base, palette).apply {
            applyOverrideConfiguration(config)
            val textIndex = Appearance.colors.indices.minByOrNull {
                val c = android.graphics.Color.parseColor(Appearance.colors[it])
                kotlin.math.abs(android.graphics.Color.red(c) - android.graphics.Color.red(ink)) +
                    kotlin.math.abs(android.graphics.Color.green(c) - android.graphics.Color.green(ink)) +
                    kotlin.math.abs(android.graphics.Color.blue(c) - android.graphics.Color.blue(ink))
            } ?: 0
            theme.applyStyle(Appearance.texts[textIndex], true)
            theme.applyStyle(when (prefs.getString("font", "sans-serif")) {
                "serif" -> R.style.SadeDialogFontSerif
                "monospace" -> R.style.SadeDialogFontMono
                else -> R.style.SadeDialogFontSans
            }, true)
        }
    }
}
