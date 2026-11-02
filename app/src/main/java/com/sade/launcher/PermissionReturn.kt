package com.sade.launcher

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock

/** One short, explicitly requested settings trip; no polling or permanent observer. */
object PermissionReturn {
    private val handler = Handler(Looper.getMainLooper())
    private var deadline = 0L
    private var usage = false
    private var settingsPackage: String? = null
    private var ops: AppOpsManager? = null
    private var watcher: AppOpsManager.OnOpChangedListener? = null
    private val timeout = Runnable { cancel() }

    fun begin(context: Context, usageAccess: Boolean, settingsIntent: Intent) {
        cancel()
        usage = usageAccess
        deadline = SystemClock.elapsedRealtime() + 120_000
        @Suppress("DEPRECATION")
        settingsPackage = context.packageManager.resolveActivity(settingsIntent, 0)?.activityInfo?.packageName
        handler.postDelayed(timeout, 120_000)
        if (usage) {
            val app = context.applicationContext
            ops = app.getSystemService(AppOpsManager::class.java)
            watcher = AppOpsManager.OnOpChangedListener { _, pkg ->
                if (pkg == app.packageName) handler.post { granted(app, usageAccess = true) }
            }
            runCatching { ops?.startWatchingMode(AppOpsManager.OPSTR_GET_USAGE_STATS, app.packageName, watcher!!) }
        }
    }

    fun windowChanged(pkg: String, ownPackage: String) {
        if (deadline == 0L) return
        // Leaving settings for another app cancels the requested return.
        if (pkg != settingsPackage && pkg != "com.android.systemui" && pkg != ownPackage &&
            pkg != "com.android.permissioncontroller" && pkg != "com.google.android.permissioncontroller") cancel()
    }

    fun granted(context: Context, usageAccess: Boolean) {
        if (deadline == 0L || usage != usageAccess) return
        if (SystemClock.elapsedRealtime() >= deadline ||
            !context.getSystemService(PowerManager::class.java).isInteractive) { cancel(); return }
        if (usage && !AndroidUsage.allowed(context)) return
        cancel()
        val target = FocusService.permissionReturnContext() ?: context.applicationContext
        // OEMs may still restrict background launches; normal Back remains available.
        runCatching {
            target.startActivity(Intent(target, FocusActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }

    fun cancel() {
        deadline = 0L
        handler.removeCallbacks(timeout)
        watcher?.let { listener -> runCatching { ops?.stopWatchingMode(listener) } }
        watcher = null; ops = null; settingsPackage = null
    }
}
