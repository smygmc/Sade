package com.sade.launcher

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

/** Only PiP root metadata/actions are inspected; no text, descendants or screenshots. */
class PipBlocker(private val service: AccessibilityService, private val blocked: (String) -> Boolean) {
    private val handler = Handler(Looper.getMainLooper())
    private var leaving: String? = null
    private var until = 0L
    private var lastAttempt = 0L
    private var lastPackage: String? = null
    private val scan = Runnable { check() }

    fun leave(pkg: String?) {
        if (pkg == null) return
        leaving = pkg
        until = SystemClock.elapsedRealtime() + 4000
        handler.removeCallbacks(scan)
        // Home can create PiP after the button click and its window animation.
        for (delay in longArrayOf(150, 500, 1200, 2500)) handler.postDelayed(scan, delay)
    }

    fun check() {
        val now = SystemClock.elapsedRealtime()
        if (now >= until) leaving = null
        val windows = runCatching { service.windows }.getOrDefault(emptyList())
        for (window in windows) {
            try {
                if (!window.isInPictureInPictureMode) continue
                val root = window.root ?: continue
                try {
                    val pkg = root.packageName?.toString() ?: continue
                    if (pkg != leaving && !blocked(pkg)) continue
                    if (pkg == lastPackage && now - lastAttempt < 500) continue
                    lastPackage = pkg; lastAttempt = now
                    // This is Android's PiP dismiss action, not a guessed screen coordinate.
                    root.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id)
                } finally {
                    @Suppress("DEPRECATION")
                    root.recycle()
                }
            } catch (_: RuntimeException) {
                // The window may disappear during the query. Never act on another window.
            } finally {
                @Suppress("DEPRECATION")
                window.recycle()
            }
        }
    }

    fun close() { handler.removeCallbacksAndMessages(null); leaving = null }
}
