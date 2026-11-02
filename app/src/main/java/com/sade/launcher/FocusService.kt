package com.sade.launcher

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.*
import android.graphics.Color
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import java.time.*

/** Opt-in and window-event driven. PiP handling reads root package metadata, never text. */
class FocusService : AccessibilityService() {
    companion object {
        private var instance: FocusService? = null
        fun permissionReturnContext(): Context? = instance
        fun closePipOnHome(pkg: String) { instance?.pipBlocker?.leave(pkg) }
        private var homeVisible = false
        private val entryGrace = EntryPauseGrace()
        fun canSkipEntry(pkg: String) = entryGrace.hasGrace(pkg, SystemClock.elapsedRealtime())
        fun approveEntry(pkg: String) { entryGrace.leave(pkg, true, SystemClock.elapsedRealtime()) }
        fun revokeEntry(pkg: String) { entryGrace.leave(pkg, false, SystemClock.elapsedRealtime()) }
        /** Flush current in-memory usage before the launcher decides whether to start an app. */
        fun checkpointUsage() {
            instance?.let { if (it::meter.isInitialized) it.account() }
        }
        fun setHomeVisible(visible: Boolean) {
            homeVisible = visible
            if (visible) instance?.let {
                it.transitionId++
                if (it::meter.isInitialized) it.switchTo(null)
            }
        }
    }
    private val pipBlocker by lazy { PipBlocker(this) { pkg -> isPackageBlocked(pkg) } }
    private fun isPackageBlocked(pkg: String): Boolean {
        if (!enabled()) return false
        val g = groups.firstOrNull { it.enabled && it.minutes > 0 && it.reminder > 0 && pkg in it.packages } ?: return false
        val now = LocalDateTime.now()
        val day = now.dayOfWeek.value
        val schedule = FocusPolicy.scheduleBlocks(g.schedule, g.scheduleAllDay, day, now.hour * 60 + now.minute,
            g.start, g.end, g.scheduleDays)
        val limit = if (day in g.days) g.limitFor(day) else 0
        return schedule || (g.blockAtLimit && FocusPolicy.limitReached(used(pkg), limit))
    }
    private fun goHome() {
        pipBlocker.leave(active)
        switchTo(null)
        performGlobalAction(GLOBAL_ACTION_HOME)
    }
    private val systemRefresh = Runnable { refreshAndroidUsage(force = true) }
    private var transitionId = 0
    private lateinit var store: FocusStore
    private val handler = Handler(Looper.getMainLooper())
    private var groups = emptyList<FocusGroup>()
    private var active: String? = null
    private lateinit var meter: FocusUsage
    private val sessionMs get() = meter.sessionMs
    private var nextReminder = 0L
    private var counting = false
    private val day get() = meter.day
    private val usage get() = meter.totals
    private var dailyWarned = mutableSetOf<String>()
    private var overlay: View? = null
    private var overlayKey = ""
    private var entryDeadline = 0L
    private var entryPassed = false
    private var countdownLabel: TextView? = null
    private var entryButton: Button? = null
    private val activityCache = mutableMapOf<String, Boolean>()
    private val tick = Runnable { account(); evaluate(); refreshAndroidUsage() }
    private fun used(pkg: String) = AndroidUsage.used(this, pkg, usage[pkg] ?: 0L)
    private fun refreshAndroidUsage(force: Boolean = false) {
        if (!enabled()) return
        AndroidUsage.refresh(this, force) { ok ->
            if (instance !== this || !enabled()) return@refresh
            val foreground = if (ok) AndroidUsage.foreground() else null
            if (overlay == null && !homeVisible && foreground != null && foreground != active &&
                getSystemService(PowerManager::class.java).isInteractive &&
                !getSystemService(KeyguardManager::class.java).isKeyguardLocked) switchTo(foreground)
            else evaluate()
            pipBlocker.check()
        }
    }
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "groups" || key == "consent") {
            entryGrace.clear()
            account(); groups = store.groups(); nextReminder = 0; entryDeadline = 0; entryPassed = false; removeOverlay(); evaluate(); refreshAndroidUsage()
        }
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> switchTo(null)
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> { account(); evaluate() }
            }
        }
    }

    override fun onServiceConnected() {
        store = FocusStore(this)
        val today = LocalDate.now().toString()
        meter = FocusUsage(today, store.usage(today), SystemClock.elapsedRealtime(), System.currentTimeMillis())
        instance = this
        PermissionReturn.granted(this, usageAccess = false)
        groups = store.groups(); dailyWarned = store.warned(day)
        store.prefs.registerOnSharedPreferenceChangeListener(listener)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenReceiver, filter)
        refreshAndroidUsage()
    }

    private fun enabled() = ::store.isInitialized && store.prefs.getBoolean("consent", false) && groups.any { it.enabled && it.minutes > 0 && it.reminder > 0 }
    // Legacy zero-duration groups require editing instead of silently assigning a new limit.
    private fun group() = groups.firstOrNull { it.enabled && it.minutes > 0 && it.reminder > 0 && active in it.packages }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::store.isInitialized || event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (enabled()) pipBlocker.check()
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        PermissionReturn.windowChanged(pkg, packageName)
        // Includes custom windows that cannot be resolved as Activity components.
        handler.removeCallbacks(systemRefresh)
        handler.postDelayed(systemRefresh, 300)
        if (pkg == packageName && overlay != null) return
        if (!enabled()) { if (active != null) switchTo(null); return }
        if (!getSystemService(PowerManager::class.java).isInteractive || getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            switchTo(null); return
        }
        // System panels pause a session. Input methods/dialogs are not new app sessions.
        if (pkg == "com.android.systemui") { switchTo(null); return }
        // Activity changes inside the same app do not start a new session.
        if (pkg == active) { transitionId++; return }
        val className = event.className?.toString() ?: return
        val key = "${pkg}/${className}"
        val isActivity = activityCache.getOrPut(key) {
            runCatching {
                @Suppress("DEPRECATION")
                packageManager.getActivityInfo(ComponentName(pkg, className), 0)
                true
            }.getOrDefault(false)
        }
        if (!isActivity) return
        // PiP may emit application events while Home is resumed. Wait for the
        // activity transition to settle, then let Home visibility take precedence.
        val transition = ++transitionId
        handler.postDelayed({
            if (transition == transitionId && !homeVisible && enabled() &&
                getSystemService(PowerManager::class.java).isInteractive &&
                !getSystemService(KeyguardManager::class.java).isKeyguardLocked && active != pkg) switchTo(pkg)
        }, 150)
    }

    private fun switchTo(pkg: String?) {
        transitionId++
        val now = SystemClock.elapsedRealtime()
        entryGrace.leave(active, entryPassed, now)
        account(); handler.removeCallbacks(tick); removeOverlay()
        active = pkg; meter.resetSession(); nextReminder = 0; counting = false
        entryDeadline = 0; entryPassed = pkg != null && entryGrace.enter(pkg, now)
        evaluate()
    }

    private fun account() {
        if (!::store.isInitialized) return
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val today = LocalDate.now().toString()
        val tracking = counting && active != null
        val changedDay = day != today
        if (changedDay) dailyWarned.clear()
        val midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        meter.account(active, tracking, nowElapsed, nowWall, today, midnight)
        if (tracking || changedDay) store.persistUsage(day, usage, dailyWarned)
    }

    private fun evaluate() {
        handler.removeCallbacks(tick)
        counting = false
        if (!enabled() || homeVisible || active == null) { removeOverlay(); return }
        val g = group() ?: run { removeOverlay(); return }
        val pkg = active!!
        val now = LocalTime.now()
        val weekday = LocalDate.now().dayOfWeek.value
        val activeToday = weekday in g.days
        if (!activeToday) { meter.resetSession(); nextReminder = 0 }
        val scheduled = FocusPolicy.scheduleBlocks(g.schedule, g.scheduleAllDay, weekday, now.hour * 60 + now.minute, g.start, g.end, g.scheduleDays)
        val limit = if (activeToday) g.limitFor(weekday) else 0
        val reached = FocusPolicy.limitReached(used(pkg), limit)
        val decision = FocusPolicy.entryDecision(scheduled, reached, g.blockAtLimit,
            activeToday && g.waitBeforeOpen, entryPassed)
        val dailyBlocking = decision == FocusPolicy.BLOCK_DAILY
        val blocking = dailyBlocking || decision == FocusPolicy.BLOCK_SCHEDULE
        val reminderMs = if (activeToday) g.reminder * 60_000L else 0L
        if (nextReminder == 0L && reminderMs > 0) nextReminder = reminderMs
        val dueReminder = reminderMs > 0 && sessionMs >= nextReminder
        val dueDaily = reached && pkg !in dailyWarned
        if (blocking) {
            pipBlocker.check()
            val key = "${pkg}:${if (dailyBlocking) "daily" else "schedule"}"
            if (overlayKey != key) {
                removeOverlay()
                showOverlay(g, true, if (dailyBlocking) Lang.wrap(this@FocusService).getString(R.string.ui_122, limit) else Lang.wrap(this@FocusService).getString(R.string.ui_121), key)
            }
        } else if (!activeToday || overlayKey.endsWith(":schedule") || overlayKey.endsWith(":daily")) removeOverlay()
        val waiting = decision == FocusPolicy.PAUSE
        if (waiting) {
            if (overlayKey != "$pkg:entry") {
                removeOverlay()
                if (entryDeadline == 0L) entryDeadline = SystemClock.elapsedRealtime() + g.waitSeconds * 1000L
                showOverlay(g, false, Lang.wrap(this).getString(R.string.ui_132), "$pkg:entry", entry = true)
            }
            val remaining = FocusPolicy.remainingSeconds(SystemClock.elapsedRealtime(), entryDeadline)
            countdownLabel?.text = Lang.wrap(this).getString(R.string.ui_134, remaining)
            entryButton?.isEnabled = remaining == 0
        } else if (overlayKey.endsWith(":entry")) removeOverlay()
        if (!blocking && overlay == null && (dueDaily || dueReminder)) {
            if (dueDaily) dailyWarned.add(pkg)
            if (dueReminder) nextReminder = sessionMs + reminderMs
            store.persistUsage(day, usage, dailyWarned)
            showOverlay(g, false, if (dueDaily) Lang.wrap(this@FocusService).getString(R.string.ui_122, limit) else Lang.wrap(this@FocusService).getString(R.string.ui_123, g.reminder), "${pkg}:reminder", daily = dueDaily)
        }
        counting = overlay == null && activeToday
        // No repeating wake-up when idle; at most one checkpoint per minute in a selected app.
        var delay = 60_000L
        if (!blocking && overlay == null) {
            if (limit > 0 && !reached) delay = minOf(delay, limit * 60_000L - used(pkg))
            if (reminderMs > 0) delay = minOf(delay, nextReminder - sessionMs)
        }
        // Align schedule changes and daily reset to the wall-clock minute.
        delay = minOf(delay, 60_000L - (System.currentTimeMillis() % 60_000L))
        if (waiting && SystemClock.elapsedRealtime() < entryDeadline) delay = minOf(delay, 1000L, entryDeadline - SystemClock.elapsedRealtime())
        handler.postDelayed(tick, delay.coerceAtLeast(250))
    }

    private fun showOverlay(g: FocusGroup, blocked: Boolean, reason: String, key: String, entry: Boolean = false, daily: Boolean = key.endsWith(":daily")) {
        val context = ContextThemeWrapper(Lang.wrap(this), android.R.style.Theme_Material_Light_NoActionBar)
        val prefs = getSharedPreferences("appearance", MODE_PRIVATE)
        val paper = Color.parseColor(prefs.getString("paper", "#F3F0E8"))
        val ink = Appearance.ink(prefs, paper)
        val font = android.graphics.Typeface.create(prefs.getString("font", "sans-serif"), android.graphics.Typeface.NORMAL)
        val textScale = prefs.getInt("size", 26) / 26f
        val scale = resources.displayMetrics.density
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            setPadding((28 * scale).toInt(), (32 * scale).toInt(), (28 * scale).toInt(), (32 * scale).toInt())
            setBackgroundColor(paper)
        }
        fun label(value: String, sp: Float) { body.addView(TextView(context).apply {
            text = value; textSize = sp * textScale; typeface = font; setTextColor(ink); setPadding(0, 12, 0, 24)
        }) }
        label(g.name, 16f); label(reason, 26f)
        if (entry) {
            countdownLabel = TextView(context).apply {
                textSize = 48f * textScale; typeface = font; setTextColor(ink); gravity = Gravity.CENTER; body.addView(this)
            }
        } else if (daily) label(g.limitMessage.ifBlank { Lang.wrap(this@FocusService).getString(R.string.limit_message_default) }, 20f)
        else if (!key.endsWith(":schedule")) label(g.message.ifBlank { Lang.wrap(this@FocusService).getString(R.string.ui_090) }, 20f)
        fun button(title: String, action: () -> Unit): Button = Button(context).apply {
            text = title; textSize = 16f * textScale; typeface = font
            isAllCaps = false; setTextColor(ink); minHeight = (52 * scale).toInt()
            ControlStyle.apply(this, ink)
            stateListAnimator = null
            setOnClickListener { action() }
            body.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (8 * scale).toInt() })
        }
        button(Lang.wrap(this@FocusService).getString(R.string.ui_124)) { goHome() }
            .also { ControlStyle.primary(it, ink, paper) }
        if (!blocked) {
            val proceed = button(Lang.wrap(this@FocusService).getString(R.string.ui_125)) {
                // Re-evaluate against current rules/usage, never trust the state when this button was created.
                account()
                val current = group()
                if (current == null || !enabled()) { removeOverlay(); evaluate(); return@button }
                val now = LocalDateTime.now()
                val weekday = now.dayOfWeek.value
                val limit = if (weekday in current.days) current.limitFor(weekday) else 0
                val scheduled = FocusPolicy.scheduleBlocks(current.schedule, current.scheduleAllDay, weekday,
                    now.hour * 60 + now.minute, current.start, current.end, current.scheduleDays)
                val decision = FocusPolicy.entryDecision(scheduled, FocusPolicy.limitReached(active?.let { used(it) } ?: 0L, limit),
                    current.blockAtLimit, false, false)
                if (decision != FocusPolicy.ALLOW) { removeOverlay(); evaluate(); return@button }
                if (!entry || FocusPolicy.remainingSeconds(SystemClock.elapsedRealtime(), entryDeadline) == 0) {
                    if (entry) entryPassed = true
                    account(); removeOverlay(); evaluate()
                }
            }
            if (entry) { entryButton = proceed; proceed.isEnabled = false }
        }
        button(Lang.wrap(this@FocusService).getString(R.string.ui_126)) {
            switchTo(null)
            startActivity(Intent(this, FocusActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        val scroll = ScrollView(context).apply { isFillViewport = true; setBackgroundColor(paper); addView(body) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        scroll.isFocusableInTouchMode = true
        scroll.setOnKeyListener { _, code, event ->
            if (code == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) { goHome() }
                true
            } else false
        }
        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            android.graphics.PixelFormat.OPAQUE).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 30) {
                setFitInsetsTypes(0)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        try {
            getSystemService(WindowManager::class.java).addView(scroll, params)
            overlay = scroll; overlayKey = key; scroll.requestFocus()
        } catch (_: RuntimeException) {
            Toast.makeText(this, Lang.wrap(this@FocusService).getString(R.string.ui_127), Toast.LENGTH_LONG).show()
        }
    }

    private fun removeOverlay() {
        overlay?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        overlay = null; overlayKey = ""
        countdownLabel = null; entryButton = null
    }
    override fun onInterrupt() { if (::meter.isInitialized) switchTo(null) }
    override fun onDestroy() {
        instance = null
        entryGrace.clear()
        if (::store.isInitialized) {
            account(); store.prefs.unregisterOnSharedPreferenceChangeListener(listener)
            runCatching { unregisterReceiver(screenReceiver) }
        }
        pipBlocker.close()
        handler.removeCallbacksAndMessages(null); removeOverlay(); super.onDestroy()
    }
}

