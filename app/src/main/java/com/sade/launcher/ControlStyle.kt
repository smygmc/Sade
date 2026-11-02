package com.sade.launcher

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.Button

object ControlStyle {
    fun input(field: android.widget.EditText, ink: Int) {
        field.setTextColor(ink)
        field.setHintTextColor(Color.argb(170, Color.red(ink), Color.green(ink), Color.blue(ink)))
        field.backgroundTintList = ColorStateList.valueOf(ink)
        field.highlightColor = Color.argb(60, Color.red(ink), Color.green(ink), Color.blue(ink))
        if (android.os.Build.VERSION.SDK_INT >= 29) field.textCursorDrawable?.setTint(ink)
    }

    fun toggle(control: android.widget.CompoundButton, ink: Int) {
        control.setTextColor(ink)
        val muted = Color.argb(145, Color.red(ink), Color.green(ink), Color.blue(ink))
        val colors = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(ink, muted))
        if (control is android.widget.Switch) {
            control.thumbTintList = colors
            control.trackTintList = ColorStateList.valueOf(Color.argb(75, Color.red(ink), Color.green(ink), Color.blue(ink)))
        } else control.buttonTintList = colors
    }

    fun quiet(button: Button, ink: Int) {
        apply(button, ink)
        val shape = GradientDrawable().apply {
            cornerRadius = 12 * button.resources.displayMetrics.density
            setColor(Color.TRANSPARENT)
        }
        button.backgroundTintList = null
        button.background = RippleDrawable(ColorStateList.valueOf(Color.argb(30,
            Color.red(ink), Color.green(ink), Color.blue(ink))), shape, null)
        button.gravity = android.view.Gravity.CENTER
    }

    fun primary(button: Button, ink: Int, paper: Int) {
        apply(button, ink)
        val d = button.resources.displayMetrics.density
        val shape = GradientDrawable().apply {
            cornerRadius = 12 * d
            setColor(ink)
        }
        button.backgroundTintList = null
        button.background = RippleDrawable(ColorStateList.valueOf(Color.argb(55,
            Color.red(paper), Color.green(paper), Color.blue(paper))), shape, null)
        button.setTextColor(paper)
        button.gravity = android.view.Gravity.CENTER
        button.typeface = android.graphics.Typeface.create(button.typeface, android.graphics.Typeface.BOLD)
    }

    fun apply(button: Button, ink: Int) {
        button.backgroundTintList = null
        val d = button.resources.displayMetrics.density
        val shape = GradientDrawable().apply {
            cornerRadius = 12 * d
            setColor(Color.argb(8, Color.red(ink), Color.green(ink), Color.blue(ink)))
            setStroke(maxOf(1, d.toInt()), Color.argb(65, Color.red(ink), Color.green(ink), Color.blue(ink)))
        }
        button.background = RippleDrawable(ColorStateList.valueOf(Color.argb(30, Color.red(ink), Color.green(ink), Color.blue(ink))), shape, null)
        button.setPadding((16*d).toInt(), (12*d).toInt(), (16*d).toInt(), (12*d).toInt())
    }
}
