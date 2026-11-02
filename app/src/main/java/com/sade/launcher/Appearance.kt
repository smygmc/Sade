package com.sade.launcher

import android.content.SharedPreferences
import android.graphics.Color
import kotlin.math.pow

object Appearance {
    val colors = arrayOf("#F3F0E8","#FFFFFF","#E5E9DF","#E0E8EF","#EDE1E5","#EFE1D1","#000000","#202724","#202733","#30252C","#342C24","#272B27","#EAE7DE","#B5CDBD","#BBCDE4","#DEC0CA","#DEC9AD","#315E4B","#355674","#754859","#785A36","#68636F","#C6BDE0","#342C46")
    val backgrounds = intArrayOf(R.style.PaletteBackground0,R.style.PaletteBackground1,R.style.PaletteBackground2,R.style.PaletteBackground3,R.style.PaletteBackground4,R.style.PaletteBackground5,R.style.PaletteBackground6,R.style.PaletteBackground7,R.style.PaletteBackground8,R.style.PaletteBackground9,R.style.PaletteBackground10,R.style.PaletteBackground11,R.style.PaletteBackground12,R.style.PaletteBackground13,R.style.PaletteBackground14,R.style.PaletteBackground15,R.style.PaletteBackground16,R.style.PaletteBackground17,R.style.PaletteBackground18,R.style.PaletteBackground19,R.style.PaletteBackground20,R.style.PaletteBackground21,R.style.PaletteBackground22,R.style.PaletteBackground23)
    val texts = intArrayOf(R.style.PaletteText0,R.style.PaletteText1,R.style.PaletteText2,R.style.PaletteText3,R.style.PaletteText4,R.style.PaletteText5,R.style.PaletteText6,R.style.PaletteText7,R.style.PaletteText8,R.style.PaletteText9,R.style.PaletteText10,R.style.PaletteText11,R.style.PaletteText12,R.style.PaletteText13,R.style.PaletteText14,R.style.PaletteText15,R.style.PaletteText16,R.style.PaletteText17,R.style.PaletteText18,R.style.PaletteText19,R.style.PaletteText20,R.style.PaletteText21,R.style.PaletteText22,R.style.PaletteText23)
    fun luminance(color: Int): Double {
        fun linear(channel: Int): Double {
            val v = channel / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return .2126 * linear(Color.red(color)) + .7152 * linear(Color.green(color)) + .0722 * linear(Color.blue(color))
    }
    fun contrast(a: Int, b: Int): Double {
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
    fun dark(paper: Int) = luminance(paper) < .179
    fun ink(prefs: SharedPreferences, paper: Int): Int {
        val selected = prefs.getString("ink", null)?.let { runCatching { Color.parseColor(it) }.getOrNull() }
        if (selected != null) return selected
        val preferred = Color.parseColor(if (dark(paper)) "#EAE7DE" else "#272B27")
        if (contrast(preferred, paper) >= 4.5) return preferred
        return if (dark(paper)) Color.WHITE else Color.BLACK
    }
}
