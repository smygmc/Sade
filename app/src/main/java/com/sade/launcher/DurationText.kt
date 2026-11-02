package com.sade.launcher

import android.content.Context
import java.text.NumberFormat

object DurationText {
    fun minutes(context: Context, total: Int): String {
        val locale = context.resources.configuration.locales[0]
        val numbers = NumberFormat.getIntegerInstance(locale)
        val hours = total / 60
        val minutes = total % 60
        val parts = mutableListOf<String>()
        if (hours > 0) parts.add("${numbers.format(hours)} ${context.getString(R.string.duration_hours)}")
        if (minutes > 0 || hours == 0) {
            val unit = if (locale.language == "tr") "dk" else context.getString(R.string.duration_minutes)
            parts.add("${numbers.format(minutes)} $unit")
        }
        return parts.joinToString(" ")
    }
}
