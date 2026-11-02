package com.sade.launcher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors

/** System history is read off the UI thread. No network, persistent history or polling when idle. */
object AndroidUsage {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private data class Snapshot(val day: String, val elapsed: Long, val totals: Map<String, Long>, val foreground: Set<String>)
    private var snapshot: Snapshot? = null
    private var running = false
    private val pending = mutableListOf<(Boolean) -> Unit>()
    fun allowed(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }
    fun used(context: Context, pkg: String, fallback: Long): Long {
        if (!allowed(context)) return fallback
        val s = snapshot?.takeIf { it.day == LocalDate.now().toString() } ?: return fallback
        val interactive = context.getSystemService(PowerManager::class.java).isInteractive &&
            !context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
        val live = if (interactive && pkg in s.foreground) (SystemClock.elapsedRealtime() - s.elapsed).coerceAtLeast(0) else 0
        return (s.totals[pkg] ?: 0L) + live
    }
    fun foreground(): String? = snapshot?.foreground?.singleOrNull()
    fun refresh(context: Context, force: Boolean = false, done: (Boolean) -> Unit = {}) {
        if (!allowed(context)) { snapshot = null; done(false); return }
        val old = snapshot
        if (!force && old != null && old.day == LocalDate.now().toString() && SystemClock.elapsedRealtime() - old.elapsed < 2000) { done(true); return }
        pending.add(done)
        if (running) return
        running = true
        val app = context.applicationContext
        worker.execute {
            val result = runCatching {
                val date = LocalDate.now()
                val zone = ZoneId.systemDefault()
                val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
                // Previous day seeds an activity that was already open at midnight.
                val from = date.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val now = System.currentTimeMillis()
                val elapsed = SystemClock.elapsedRealtime()
                val events = app.getSystemService(UsageStatsManager::class.java).queryEvents(from, now)
                    ?: error("Usage history unavailable")
                val timeline = AndroidUsageTimeline(start, from)
                val event = UsageEvents.Event()
                while (events.hasNextEvent()) { events.getNextEvent(event); timeline.event(event.timeStamp, event.eventType, event.packageName, event.className) }
                Snapshot(date.toString(), elapsed, timeline.finish(now), timeline.foreground())
            }
            main.post {
                result.onSuccess { snapshot = it }
                running = false
                val callbacks = pending.toList(); pending.clear()
                callbacks.forEach { it(result.isSuccess) }
            }
        }
    }
}
