package com.sade.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*

/** Touch-only wheels; changes are committed only when Done is pressed. */
object DurationPicker {
    fun show(base: Context, title: CharSequence, initial: Int, seconds: Boolean, picked: (Int) -> Unit) {
        val context = DialogStyle.context(base)
        val prefs = base.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val paper = Color.parseColor(prefs.getString("paper", "#F3F0E8"))
        val ink = Appearance.ink(prefs, paper)
        val font = Typeface.create(prefs.getString("font", "sans-serif"), Typeface.NORMAL)
        val density = context.resources.displayMetrics.density
        fun dp(n: Int) = (n * density).toInt()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        fun wheel(unit: Int, min: Int, max: Int, value: Int): NumberPicker {
            val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            column.addView(TextView(context).apply {
                text = base.getString(unit); textSize = 16f; typeface = font; setTextColor(ink); gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, -2))
            val picker = NumberPicker(context).apply {
                minValue = min; maxValue = max; this.value = value.coerceIn(min, max)
                wrapSelectorWheel = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                contentDescription = base.getString(unit)
                if (Build.VERSION.SDK_INT >= 29) {
                    textColor = ink
                    textSize = 24f * context.resources.displayMetrics.scaledDensity
                }
                for (i in 0 until childCount) (getChildAt(i) as? EditText)?.apply {
                    showSoftInputOnFocus = false; isCursorVisible = false; isFocusable = false
                    typeface = font; setTextColor(ink)
                }
            }
            column.addView(picker, LinearLayout.LayoutParams(-1, dp(168)))
            row.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
            return picker
        }
        val current = initial.coerceIn(1, if (seconds) FocusPolicy.MAX_ENTRY_SECONDS else 1440)
        val hours = if (seconds) null else wheel(R.string.duration_hours, 0, 24, current / 60)
        val amount = wheel(if (seconds) R.string.duration_seconds else R.string.duration_minutes,
            if (seconds) 1 else 0, if (seconds) FocusPolicy.MAX_ENTRY_SECONDS else 59, if (seconds) current else current % 60)
        fun updateMinutes() {
            val h = hours?.value ?: return
            amount.minValue = 0
            amount.maxValue = if (h == 24) 0 else 59
            amount.minValue = if (h == 0) 1 else 0
            amount.isEnabled = h != 24
            amount.alpha = if (h == 24) .5f else 1f
        }
        hours?.setOnValueChangedListener { _, _, _ -> updateMinutes() }
        updateMinutes()
        DialogStyle.builder(base).setTitle(title).setView(row)
            .setNegativeButton(R.string.ui_063, null)
            .setPositiveButton(R.string.ui_101) { _, _ -> picked((hours?.value ?: 0) * 60 + amount.value) }
            .create().apply {
                window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
                show()
            }
    }
}
