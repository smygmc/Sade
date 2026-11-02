package com.sade.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.WindowInsetsController

/** Color the actual window as well as content during resize and task transitions. */
object WindowSurface {
    @Suppress("DEPRECATION")
    fun apply(activity: Activity, paper: Int) {
        val window = activity.window
        val dark = Appearance.dark(paper)
        window.setBackgroundDrawable(ColorDrawable(paper))
        window.decorView.setBackgroundColor(paper)
        window.statusBarColor = paper
        window.navigationBarColor = paper
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= 30) {
            // Content listeners own system-bar/IME padding; do not also fit the decor.
            window.setDecorFitsSystemWindows(false)
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (dark) 0 else mask, mask)
        } else {
            val mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.decorView.systemUiVisibility = (window.decorView.systemUiVisibility and mask.inv()) or (if (dark) 0 else mask)
        }
    }
}
