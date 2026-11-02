package com.sade.launcher

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*
import java.time.LocalDateTime

/** Launcher-owned gate: the target activity does not exist until Continue is pressed. */
class LaunchGateActivity : LocalizedActivity() {
    private lateinit var target: ComponentName
    private val handler = Handler(Looper.getMainLooper())
    private var deadline = 0L
    private var pauseSeconds = -1
    private var launched = false
    private var ready = false
    private var resumed = false
    private var usageFailed = false
    private var blocked = false
    private var needsPause = false
    private var reason: TextView? = null
    private var message: TextView? = null
    private var countdown: TextView? = null
    private var proceed: Button? = null
    private var groupName: TextView? = null
    private var gateContent: View? = null
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            if (!isFinishing && !launched) handler.postDelayed(this, 1000)
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val component = intent.getStringExtra("target")?.let { ComponentName.unflattenFromString(it) }
        if (component == null || component.packageName == packageName) { finish(); return }
        target = component
        deadline = state?.getLong("deadline") ?: 0L
        pauseSeconds = state?.getInt("pauseSeconds", -1) ?: -1
        val prefs = getSharedPreferences("appearance", MODE_PRIVATE)
        val paper = Color.parseColor(prefs.getString("paper", "#F3F0E8"))
        val ink = Appearance.ink(prefs, paper)
        val font = Typeface.create(prefs.getString("font", "sans-serif"), Typeface.NORMAL)
        val scale = prefs.getInt("size", 26) / 26f
        WindowSurface.apply(this, paper)
        val body = LinearLayout(this).apply {
            visibility = View.INVISIBLE
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(28), dp(32), dp(28), dp(32))
        }
        gateContent = body
        fun label(size: Float) = TextView(this).apply {
            textSize = size * scale; typeface = font; setTextColor(ink)
            setPadding(0, dp(8), 0, dp(12)); body.addView(this)
        }
        fun button(title: Int, action: () -> Unit) = Button(this).apply {
            text = getString(title); isAllCaps = false; textSize = 16f * scale; typeface = font
            setTextColor(ink); minHeight = dp(52); ControlStyle.apply(this, ink)
            setOnClickListener { action() }
            body.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        groupName = label(16f); reason = label(26f); message = label(20f)
        countdown = label(48f).apply { gravity = Gravity.CENTER }
        button(R.string.ui_124) {
            FocusService.closePipOnHome(target.packageName)
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }.also { ControlStyle.primary(it, ink, paper) }
        proceed = button(R.string.ui_125) {
            proceed?.isEnabled = false
            AndroidUsage.refresh(this, force = true) { ok ->
                if (!resumed || isFinishing || isDestroyed) return@refresh
                ready = ok || !AndroidUsage.allowed(this)
                usageFailed = !ready
                refresh()
                if (ready && !blocked && (!needsPause || SystemClock.elapsedRealtime() >= deadline)) launchTarget()
            }
        }
        button(R.string.ui_126) { startActivity(Intent(this, FocusActivity::class.java)); finish() }
        val scroll = ScrollView(this).apply { setBackgroundColor(paper); isFillViewport = true; addView(body) }
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
        setContentView(scroll)
    }
    private fun refresh() {
        if (!::target.isInitialized) return
        if (usageFailed) {
            gateContent?.visibility = View.VISIBLE
            reason?.text = getString(R.string.usage_unavailable)
            message?.visibility = View.GONE; countdown?.visibility = View.GONE; proceed?.visibility = View.GONE
            return
        }
        FocusService.checkpointUsage()
        val store = FocusStore(this)
        val g = store.groups().firstOrNull { store.prefs.getBoolean("consent", false) && it.enabled &&
            it.minutes > 0 && it.reminder > 0 && target.packageName in it.packages }
        val now = LocalDateTime.now()
        val day = now.dayOfWeek.value
        val limit = if (g != null && day in g.days) g.limitFor(day) else 0
        val schedule = g != null && FocusPolicy.scheduleBlocks(g.schedule, g.scheduleAllDay, day,
            now.hour * 60 + now.minute, g.start, g.end, g.scheduleDays)
        val reached = g != null && FocusPolicy.limitReached(AndroidUsage.used(this, target.packageName, store.usage(now.toLocalDate().toString())[target.packageName] ?: 0L), limit)
        val decision = FocusPolicy.entryDecision(schedule, reached, g?.blockAtLimit == true,
            g != null && day in g.days && g.waitBeforeOpen, FocusService.canSkipEntry(target.packageName))
        val dailyBlock = decision == FocusPolicy.BLOCK_DAILY
        blocked = dailyBlock || decision == FocusPolicy.BLOCK_SCHEDULE
        needsPause = decision == FocusPolicy.PAUSE
        gateContent?.visibility = if (ready && (blocked || needsPause)) View.VISIBLE else View.INVISIBLE
        if (ready && !blocked && !needsPause) { launchTarget(); return }
        if (needsPause && (deadline == 0L || pauseSeconds != g!!.waitSeconds)) {
            pauseSeconds = g!!.waitSeconds; deadline = SystemClock.elapsedRealtime() + pauseSeconds * 1000L
        }
        groupName?.text = g?.name ?: "Sade"
        reason?.text = getString(if (dailyBlock) R.string.ui_122 else if (schedule) R.string.ui_121 else R.string.ui_132, limit)
        message?.apply {
            visibility = if (dailyBlock) View.VISIBLE else View.GONE
            text = g?.limitMessage?.ifBlank { getString(R.string.limit_message_default) } ?: ""
        }
        val remaining = if (needsPause) FocusPolicy.remainingSeconds(SystemClock.elapsedRealtime(), deadline) else 0
        countdown?.apply { visibility = if (needsPause) View.VISIBLE else View.GONE; text = getString(R.string.ui_134, remaining) }
        proceed?.apply { visibility = if (blocked) View.GONE else View.VISIBLE; isEnabled = ready && remaining == 0; alpha = if (isEnabled) 1f else .4f }
    }
    private fun launchTarget() {
        if (launched || !resumed) return
        FocusService.approveEntry(target.packageName)
        try {
            val launch = if (intent.getBooleanExtra("camera", false)) Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                else Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            startActivity(launch.setComponent(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            launched = true; finish()
        } catch (_: RuntimeException) {
            FocusService.revokeEntry(target.packageName)
            Toast.makeText(this, R.string.ui_007, Toast.LENGTH_SHORT).show()
        }
    }
    override fun onResume() {
        super.onResume(); resumed = true
        if (!::target.isInitialized) return
        ready = false; usageFailed = false
        tick.run()
        AndroidUsage.refresh(this, force = true) { ok ->
            if (!resumed || isFinishing || isDestroyed) return@refresh
            ready = ok || !AndroidUsage.allowed(this)
            usageFailed = !ready
            refresh()
        }
    }
    override fun onPause() { resumed = false; handler.removeCallbacks(tick); super.onPause() }
    override fun onDestroy() { handler.removeCallbacks(tick); super.onDestroy() }
    override fun onSaveInstanceState(out: Bundle) {
        out.putLong("deadline", deadline); out.putInt("pauseSeconds", pauseSeconds); super.onSaveInstanceState(out)
    }
}
