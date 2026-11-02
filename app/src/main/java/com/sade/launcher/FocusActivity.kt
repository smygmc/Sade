package com.sade.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.os.*
import android.provider.Settings
import android.text.InputType
import android.view.*
import android.view.accessibility.AccessibilityManager
import android.widget.*
import java.text.Collator
import java.util.concurrent.Executors

class FocusActivity : LocalizedActivity() {
    private val store by lazy { FocusStore(this) }
    private val worker = Executors.newSingleThreadExecutor()
    private var apps = emptyList<Pair<String, String>>()
    private var loaded = false
    private lateinit var body: LinearLayout
    private lateinit var footer: LinearLayout
    private lateinit var pageHeading: TextView
    private lateinit var pageScroll: ScrollView
    private val editorPanels = mutableListOf<LinearLayout>()
    private var editorStep = -1
    private var editorReviewMode = false
    private var creating = false
    private var summaryPanel: LinearLayout? = null
    private var editorProgress: ProgressBar? = null
    private var editorProgressText: TextView? = null
    private var editorPrimary: Button? = null
    private var editorDelete: Button? = null
    private var ink = Color.BLACK
    private var paper = Color.WHITE
    private var font = Typeface.SANS_SERIF
    private var scale = 1f
    private var appearanceStamp = ""
    private fun currentAppearance(): String {
        val prefs = getSharedPreferences("appearance", MODE_PRIVATE)
        return listOf("paper", "ink", "font", "size").joinToString("|") { prefs.all[it].toString() }
    }
    private var permissionDialog: AlertDialog? = null
    private var usageStatus: TextView? = null
    private val usageBars = mutableListOf<Triple<ProgressBar, String, Int>>()
    private fun updateUsageBars() {
        FocusService.checkpointUsage()
        usageBars.forEach { it.first.visibility = View.INVISIBLE }
        AndroidUsage.refresh(this, force = true) { ok ->
            if (isDestroyed || isFinishing) return@refresh
            val available = ok && AndroidUsage.allowed(this)
            usageStatus?.visibility = if (available || !AndroidUsage.allowed(this) || store.groups().isEmpty()) View.GONE else View.VISIBLE
            usageBars.forEach { (bar, pkg, limit) ->
                bar.visibility = if (available) View.VISIBLE else View.INVISIBLE
                if (!available) return@forEach
                val used = AndroidUsage.used(this, pkg, 0L)
                bar.progress = ((used.toDouble() / (limit * 60_000L)) * 1000).toInt().coerceIn(0, 1000)
            }
        }
    }
    private var editing: FocusGroup? = null
    private var initialDraft: FocusGroup? = null
    private lateinit var nameInput: EditText
    private lateinit var messageInput: EditText
    private lateinit var limitMessageInput: EditText
    private lateinit var limitInput: EditText
    private lateinit var reminderInput: EditText
    private lateinit var blockInput: Switch
    private lateinit var scheduleInput: Switch
    private lateinit var allDayInput: Switch
    private var scheduleDays = (1..7).toMutableSet()
    private var scheduleDaysButton: Button? = null
    private lateinit var enabledInput: Switch
    private lateinit var waitInput: Switch
    private lateinit var waitSecondsInput: EditText
    private var selected = mutableSetOf<String>()
    private var exitDialog: AlertDialog? = null
    private var pendingExitPrompt = false
    private var startMinute = 1320
    private var endMinute = 480
    private var appsButton: Button? = null
    private var selectedAppsLabel: TextView? = null
    private var selectedDays = (1..7).toMutableSet()
    private var dayLimits = mutableMapOf<Int, Int>()
    private var daysButton: Button? = null
    private var daySummary: TextView? = null
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeCandidate = false
    private var swipeCaptured = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = event.x
                swipeStartY = event.y
                swipeCaptured = false
                swipeCandidate = editing != null && editorStep >= 0 &&
                    !touchesEditableControl(window.decorView, event.rawX, event.rawY)
            }
            MotionEvent.ACTION_POINTER_DOWN -> swipeCandidate = false
            MotionEvent.ACTION_MOVE -> if (swipeCandidate && !swipeCaptured) {
                val dx = event.x - swipeStartX
                val dy = kotlin.math.abs(event.y - swipeStartY)
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                // Once scrolling vertically (or moving left), leave the entire gesture to the form.
                if (dy > slop && dy > kotlin.math.abs(dx) || dx < -slop) {
                    swipeCandidate = false
                } else if (dx >= dp(64) && dx > dy * 2) {
                    swipeCaptured = true
                    val cancel = MotionEvent.obtain(event)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                }
            }
            MotionEvent.ACTION_UP -> {
                val captured = swipeCaptured
                swipeCaptured = false
                swipeCandidate = false
                if (captured) {
                    if (event.x - swipeStartX >= dp(64) &&
                        kotlin.math.abs(event.y - swipeStartY) < (event.x - swipeStartX) / 2) back()
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                val captured = swipeCaptured
                swipeCandidate = false
                swipeCaptured = false
                if (captured) return true
            }
        }
        return if (swipeCaptured) true else super.dispatchTouchEvent(event)
    }

    private fun touchesEditableControl(view: View, x: Float, y: Float): Boolean {
        if (view.visibility != View.VISIBLE) return false
        val bounds = android.graphics.Rect()
        if (!view.getGlobalVisibleRect(bounds) || !bounds.contains(x.toInt(), y.toInt())) return false
        // Preserve text selection, cursor movement and switch gestures.
        if (view is EditText || view is CompoundButton || view is SeekBar) return true
        if (view is ViewGroup) for (i in view.childCount - 1 downTo 0) {
            if (touchesEditableControl(view.getChildAt(i), x, y)) return true
        }
        return false
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { back() }
        showList()
        state?.getBundle("draft")?.let { b ->
            val savedStep = state.getInt("editorStep", -1)
            val restoredStep = if (state.getInt("editorVersion", 1) < 2 && savedStep >= 2) savedStep - 1 else savedStep
            edit(readGroup(b), restoredStep, state.getBoolean("creating"), state.getBoolean("reviewMode"))
            state.getBundle("initial")?.let { initialDraft = readGroup(it) }
        }
        pendingExitPrompt = state?.getBoolean("pendingExitPrompt") ?: false
        worker.execute {
            val result = runCatching {
                @Suppress("DEPRECATION")
                val excluded = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                    .map { it.activityInfo.packageName }.toMutableSet()
                excluded.addAll(listOf(packageName, "com.android.settings", "com.android.systemui", "com.android.phone",
                    "com.android.permissioncontroller", "com.google.android.permissioncontroller"))
                getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage?.let { excluded.add(it) }
                @Suppress("DEPRECATION")
                val settingsPackage = packageManager.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)?.activityInfo?.packageName
                settingsPackage?.let { excluded.add(it) }
                val collator = Collator.getInstance()
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .filter { it.activityInfo.packageName !in excluded }
                    .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
                    .distinctBy { it.first }.sortedWith { a, b -> collator.compare(a.second, b.second) }
            }
            runOnUiThread {
                if (!isDestroyed) {
                    result.onSuccess { apps = it; loaded = true }
                    result.onFailure { toast(getString(R.string.ui_069)) }
                    updateAppsButton()
                    if (editing != null && editorStep == -1) refreshSummary()
                }
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun page(title: String) {
        appearanceStamp = currentAppearance()
        usageBars.clear()
        usageStatus = null; appsButton = null; selectedAppsLabel = null; daysButton = null; daySummary = null
        val prefs = getSharedPreferences("appearance", MODE_PRIVATE)
        scale = prefs.getInt("size", 26) / 26f
        font = Typeface.create(prefs.getString("font", "sans-serif"), Typeface.NORMAL)
        paper = Color.parseColor(prefs.getString("paper", "#F3F0E8"))
        WindowSurface.apply(this, paper)
        val dark = Appearance.dark(paper)
        ink = Appearance.ink(prefs, paper)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(20), dp(24), dp(24)) }
        val scroll = ScrollView(this).apply { setBackgroundColor(paper); addView(body); isFillViewport = true }
        pageScroll = scroll
        footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(12), dp(24), dp(12)); setBackgroundColor(paper)
            visibility = View.GONE
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(paper)
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(footer, LinearLayout.LayoutParams(-1, -2))
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }; insets
        }
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 30) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (dark) 0 else mask, mask)
        }
        pageHeading = label(title, 28f)
    }
    private fun label(value: String, size: Float = 16f): TextView = TextView(this).apply {
        text = value; textSize = size * scale; setTextColor(ink); typeface = font
        setPadding(0, dp(12), 0, dp(12)); body.addView(this)
    }
    private fun button(value: String, action: () -> Unit): Button = Button(this).apply {
        text = value; textSize = 16f * scale; isAllCaps = false; typeface = font
        gravity = Gravity.START or Gravity.CENTER_VERTICAL; setTextColor(ink)
        ControlStyle.apply(this, ink)
        minHeight = dp(52); setOnClickListener { action() }
        body.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }
    private fun input(title: String, value: String, number: Boolean = false, multiline: Boolean = false): EditText {
        label(title)
        return EditText(this).apply {
            ControlStyle.input(this, ink)
            setText(value); textSize = 18f * scale; setTextColor(ink); typeface = font
            inputType = if (number) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or
                if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(!multiline); minHeight = dp(48)
            typeface = font
            filters = arrayOf(android.text.InputFilter.LengthFilter(if (multiline) 400 else 80))
            body.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    }
    private fun toggle(title: String, checked: Boolean): Switch = Switch(this).apply {
        ControlStyle.toggle(this, ink)
        text = title; textSize = 16f * scale; setTextColor(ink); typeface = font; minHeight = dp(56)
        isChecked = checked; body.addView(this, LinearLayout.LayoutParams(-1, -2))
    }

    private fun durationInput(value: String, unit: Int, title: TextView,
                              parent: LinearLayout = body, context: Context = this): EditText {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val fieldTextSize = if (context === this) 18f * scale else 18f
        val number = object : EditText(context) {
            override fun getAccessibilityClassName(): CharSequence = "android.widget.Button"
        }.apply {
            ControlStyle.input(this, ink)
            id = View.generateViewId()
            inputType = InputType.TYPE_NULL
            keyListener = null
            showSoftInputOnFocus = false; isCursorVisible = false
            isFocusable = false; isLongClickable = false
            setSingleLine(true); setText(value)
            textSize = fieldTextSize; setTextColor(ink); typeface = font
            minHeight = dp(48); gravity = Gravity.CENTER
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
        }
        // Keep the numeric value for draft/save validation; show a human-readable duration.
        number.visibility = View.GONE
        row.addView(number, LinearLayout.LayoutParams(0, 0))
        val display = TextView(context).apply {
            id = View.generateViewId()
            textSize = fieldTextSize; setTextColor(ink); typeface = font
            minHeight = dp(48); gravity = Gravity.CENTER_VERTICAL
        }
        fun updateDisplay() {
            val amount = number.text.toString().toIntOrNull() ?: 1
            display.text = if (unit == R.string.duration_seconds) "$amount ${getString(unit)}"
                else DurationText.minutes(context, amount)
        }
        updateDisplay()
        number.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateDisplay() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        title.labelFor = display.id
        row.addView(display, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(context).apply {
            text = "›"; textSize = fieldTextSize; setTextColor(ink); typeface = font
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        fun choose() {
            DurationPicker.show(this, title.text, number.text.toString().toIntOrNull() ?: 1,
                unit == R.string.duration_seconds) { selected ->
                number.setText(selected.toString()); number.error = null
            }
        }
        number.setOnClickListener { choose() }
        row.setOnClickListener { choose() }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2))
        return number
    }

    private fun section(content: () -> Unit): LinearLayout {
        val parent = body
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.argb(22, Color.red(ink), Color.green(ink), Color.blue(ink)))
            }
        }
        parent.addView(panel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) })
        body = panel
        try { content() } finally { body = parent }
        return panel
    }

    private fun serviceEnabled(): Boolean = getSystemService(AccessibilityManager::class.java)
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == packageName && it.resolveInfo.serviceInfo.name == FocusService::class.java.name }

    private fun needsPermission() = !store.prefs.getBoolean("consent", false) || !serviceEnabled() || !AndroidUsage.allowed(this)
    private fun showList() {
        editing = null; initialDraft = null; pendingExitPrompt = false; page(getString(R.string.ui_023))
        usageStatus = label(getString(R.string.usage_unavailable), 14f).apply { visibility = View.GONE }
        button(getString(R.string.ui_071)) {
            val existing = store.groups()
            val largest = existing.mapNotNull { Regex("Grup\\s*(\\d+)", RegexOption.IGNORE_CASE).matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
            var number = maxOf(largest, existing.size) + 1
            val names = existing.map { it.name }.toSet()
            while (getString(R.string.ui_072, number) in names) number++
            edit(FocusGroup(name = getString(R.string.ui_072, number)))
        }
        val groups = store.groups()
        if (groups.isNotEmpty()) label(getString(R.string.ui_073), 14f)
        groups.forEach { g ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
                val fill = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(Color.argb(18, Color.red(ink), Color.green(ink), Color.blue(ink)))
                }
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.argb(30, Color.red(ink), Color.green(ink), Color.blue(ink))), fill, null)
                isClickable = true; isFocusable = true
                setOnClickListener { edit(g) }
            }
            fun line(value: String, size: Float, bold: Boolean = false) {
                card.addView(TextView(this).apply {
                    text = value; textSize = size * scale; setTextColor(ink)
                    typeface = Typeface.create(font, if (bold) Typeface.BOLD else Typeface.NORMAL)
                    setPadding(0, dp(4), 0, dp(4))
                })
            }
            line("${g.name}${if (!g.enabled) getString(R.string.ui_074) else ""}  ›", 21f, true)
            line(if (g.minutes < 1 || g.reminder < 1) getString(R.string.ui_075) else
                getString(R.string.ui_085) + " · " + DurationText.minutes(this, g.limitFor(java.time.LocalDate.now().dayOfWeek.value)), 14f)
            g.packages.sorted().forEach { pkg ->
                val name = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                line(name, 14f)
                val today = java.time.LocalDate.now().dayOfWeek.value
                if (g.enabled && today in g.days && g.limitFor(today) > 0) {
                    val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                        visibility = View.INVISIBLE
                        max = 1000
                        progressTintList = android.content.res.ColorStateList.valueOf(ink)
                        progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.argb(35, Color.red(ink), Color.green(ink), Color.blue(ink)))
                        contentDescription = name + ", " + getString(R.string.ui_085)
                    }
                    card.addView(bar, LinearLayout.LayoutParams(-1, dp(4)).apply { bottomMargin = dp(10) })
                    usageBars.add(Triple(bar, pkg, g.limitFor(today)))
                }
            }
            body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        }
        updateUsageBars()
    }

    private fun disclose() {
        if (permissionDialog?.isShowing == true) return
        if (store.prefs.getBoolean("consent", false) && serviceEnabled()) {
            if (!AndroidUsage.allowed(this)) usagePermission()
            return
        }
        permissionDialog = DialogStyle.builder(this).setTitle(getString(R.string.ui_077))
            .setMessage(getString(R.string.ui_078))
            .setNegativeButton(getString(R.string.ui_079), null)
            .setPositiveButton(getString(R.string.ui_080)) { _, _ ->
                store.prefs.edit().putBoolean("consent", true).apply()
                showList()
                val component = ComponentName(this, FocusService::class.java)
                // Some devices implement this direct route; public list route is the fallback.
                val intent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                    .putExtra(Intent.EXTRA_COMPONENT_NAME, component.flattenToString())
                val fallback = highlightSetting(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), component.flattenToString())
                openPermissionSettings(intent, fallback, usageAccess = false)
            }.show()
    }

    private fun usagePermission() {
        if (permissionDialog?.isShowing == true) return
        permissionDialog = DialogStyle.builder(this).setTitle(R.string.ui_077)
            .setMessage(R.string.usage_access_body)
            .setNegativeButton(R.string.ui_079, null)
            .setPositiveButton(R.string.ui_080) { _, _ ->
                val direct = highlightSetting(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,
                    android.net.Uri.parse("package:$packageName")), "app_ops_settings_switch")
                val fallback = highlightSetting(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), packageName)
                openPermissionSettings(direct, fallback, usageAccess = true)
            }.show()
    }

    private fun highlightSetting(intent: Intent, key: String): Intent = intent.apply {
        // Optional AOSP hint: unsupported vendor Settings apps may ignore it.
        putExtra(":settings:fragment_args_key", key)
        putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", key) })
    }

    private fun openPermissionSettings(direct: Intent, fallback: Intent, usageAccess: Boolean) {
        for (intent in listOf(direct, fallback)) {
            try {
                PermissionReturn.begin(this, usageAccess, intent)
                startActivity(intent)
                return
            } catch (_: RuntimeException) { PermissionReturn.cancel() }
        }
        toast(getString(R.string.ui_081))
    }

    private fun edit(g: FocusGroup, restoredStep: Int? = null, restoredCreating: Boolean? = null, restoredReview: Boolean? = null) {
        editing = g; initialDraft = g; selected = g.packages.toMutableSet()
        creating = restoredCreating ?: store.groups().none { it.id == g.id }
        editorReviewMode = restoredReview ?: !creating
        editorStep = restoredStep ?: if (creating) 0 else -1
        editorPanels.clear()
        selectedDays = g.days.toMutableSet(); dayLimits = g.dayLimits.toMutableMap()
        scheduleDays = g.scheduleDays.toMutableSet()
        startMinute = g.start; endMinute = g.end
        page(if (store.groups().none { it.id == g.id }) getString(R.string.ui_071) else getString(R.string.ui_082))
        editorProgressText = label("", 14f)
        editorProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 4; progressTintList = android.content.res.ColorStateList.valueOf(ink)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.argb(35, Color.red(ink), Color.green(ink), Color.blue(ink)))
            body.addView(this, LinearLayout.LayoutParams(-1, dp(4)).apply { bottomMargin = dp(24) })
        }
        editorPanels += section {
        nameInput = input(getString(R.string.ui_083), g.name)
        enabledInput = toggle(getString(R.string.ui_084), g.enabled)
        appsButton = button("") { chooseApps() }
        selectedAppsLabel = label("", 15f)
        updateAppsButton()
        }
        editorPanels += section {
        limitInput = durationInput(g.minutes.toString(), R.string.duration_minutes, label(getString(R.string.ui_085)))
        label(getString(R.string.ui_100), 16f)
        daysButton = button("") { chooseDays() }
        button(getString(R.string.ui_086)) { chooseDayLimits() }
        daySummary = label("", 14f)
        updateDays()
        blockInput = toggle(getString(R.string.ui_087), g.blockAtLimit)
        limitMessageInput = input(getString(R.string.limit_message),
            g.limitMessage.ifBlank { getString(R.string.limit_message_default) }, multiline = true)
        reminderInput = durationInput(g.reminder.toString(), R.string.duration_minutes, label(getString(R.string.ui_088)))
        messageInput = input(getString(R.string.ui_089),
            g.message.ifBlank { getString(R.string.ui_090) }, multiline = true)
        button(getString(R.string.ui_091)) { messageInput.setText(getString(R.string.ui_090)) }
        }
        editorPanels += section {
        waitInput = toggle(getString(R.string.ui_130), g.waitBeforeOpen)
        val waitLabel = label(getString(R.string.ui_131))
        waitSecondsInput = durationInput(g.waitSeconds.toString(), R.string.duration_seconds, waitLabel)
        fun showWait(checked: Boolean) {
            waitLabel.visibility = if (checked) View.VISIBLE else View.GONE
            (waitSecondsInput.parent as View).visibility = if (checked) View.VISIBLE else View.GONE
        }
        showWait(g.waitBeforeOpen)
        waitInput.setOnCheckedChangeListener { _, checked -> showWait(checked) }
        }
        editorPanels += section {
        scheduleInput = toggle(getString(R.string.ui_092), g.schedule)
        val scheduleDaysLabel = label(getString(R.string.focus_block_days))
        scheduleDaysButton = button(scheduleDays.sorted().joinToString(" · ") { dayName(it, true) }) { chooseScheduleDays() }
        allDayInput = toggle(getString(R.string.focus_all_day), g.scheduleAllDay)
        val startButton = button(getString(R.string.ui_093, time(startMinute))) {}
        startButton.setOnClickListener { pickTime(startMinute) { startMinute = it; startButton.text = getString(R.string.ui_093, time(it)) } }
        val endButton = button(getString(R.string.ui_094, time(endMinute))) {}
        endButton.setOnClickListener { pickTime(endMinute) { endMinute = it; endButton.text = getString(R.string.ui_094, time(it)) } }
        fun showSchedule(checked: Boolean) {
            scheduleDaysLabel.visibility = if (checked) View.VISIBLE else View.GONE
            scheduleDaysButton?.visibility = if (checked) View.VISIBLE else View.GONE
            allDayInput.visibility = if (checked) View.VISIBLE else View.GONE
            startButton.visibility = if (checked && !allDayInput.isChecked) View.VISIBLE else View.GONE
            endButton.visibility = if (checked && !allDayInput.isChecked) View.VISIBLE else View.GONE
        }
        showSchedule(g.schedule)
        scheduleInput.setOnCheckedChangeListener { _, checked -> showSchedule(checked) }
        allDayInput.setOnCheckedChangeListener { _, _ -> showSchedule(scheduleInput.isChecked) }
        }
        summaryPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; body.addView(this) }
        val form = body
        body = footer
        editorPrimary = button(getString(R.string.ui_019)) {
            if (editorStep == -1) save()
            else if (validatePanel(editorStep, true)) {
                displayEditorStep(if (editorReviewMode || editorStep == editorPanels.lastIndex) -1 else editorStep + 1)
            }
        }.apply {
            ControlStyle.primary(this, ink, paper)
            minHeight = dp(56)
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(4)
        }
        editorDelete = if (!creating) button(getString(R.string.ui_096)) {
            DialogStyle.builder(this).setTitle(getString(R.string.ui_097, g.name))
                .setNegativeButton(getString(R.string.ui_063), null).setPositiveButton(getString(R.string.ui_098)) { _, _ ->
                    store.save(store.groups().filter { it.id != g.id }); showList()
                }.show()
        }.apply { ControlStyle.quiet(this, ink) } else null
        body = form
        footer.visibility = View.VISIBLE
        displayEditorStep(editorStep)
        body.isFocusableInTouchMode = true; body.requestFocus()
    }

    private fun displayEditorStep(step: Int) {
        editorStep = step.coerceIn(-1, editorPanels.lastIndex)
        val summary = editorStep == -1
        if (summary) editorReviewMode = true
        val titles = intArrayOf(R.string.focus_step_apps, R.string.ui_085, R.string.focus_step_pause, R.string.ui_092)
        pageHeading.text = if (summary) nameInput.text.toString().ifBlank { getString(R.string.ui_071) } else getString(titles[editorStep])
        pageHeading.visibility = if (summary) View.GONE else View.VISIBLE
        editorProgressText?.apply { visibility = if (summary || editorReviewMode) View.GONE else View.VISIBLE; text = "${editorStep + 1} / 4" }
        editorProgress?.apply { visibility = if (summary || editorReviewMode) View.GONE else View.VISIBLE; progress = editorStep + 1 }
        editorPanels.forEachIndexed { index, panel -> panel.visibility = if (index == editorStep) View.VISIBLE else View.GONE }
        summaryPanel?.visibility = if (summary) View.VISIBLE else View.GONE
        editorDelete?.visibility = if (summary) View.VISIBLE else View.GONE
        editorPrimary?.text = getString(if (summary) R.string.ui_095 else if (editorReviewMode) R.string.ui_101 else R.string.ui_019)
        if (summary) refreshSummary()
        currentFocus?.clearFocus()
        getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(body.windowToken, 0)
        body.requestFocus()
        pageScroll.post { pageScroll.scrollTo(0, 0) }
    }

    private fun refreshSummary() {
        val container = summaryPanel ?: return
        container.removeAllViews()
        val original = body
        body = container
        fun row(title: String, value: String, target: Int, action: (() -> Unit)? = null) {
            val card = section {
                if (target == 0) {
                    label("$title  ›", 16f)
                    label(value, 18f)
                } else {
                    val line = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(48) }
                    line.addView(TextView(this).apply {
                        text = title; textSize = 17f * scale; typeface = font; setTextColor(ink)
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    line.addView(TextView(this).apply {
                        text = "$value  ›"; textSize = 16f * scale; typeface = font; setTextColor(ink)
                        gravity = Gravity.END; setPadding(dp(12), 0, 0, 0)
                    }, LinearLayout.LayoutParams(-2, -2))
                    body.addView(line)
                }
            }
            card.isClickable = true; card.isFocusable = true
            card.setOnClickListener { if (action != null) action() else displayEditorStep(target) }
            val fill = card.background
            card.background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(30, Color.red(ink), Color.green(ink), Color.blue(ink))), fill, null)
        }
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val editableName = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true); typeface = font; textSize = 26f * scale
            setText(nameInput.text); contentDescription = getString(R.string.ui_083)
            filters = arrayOf(android.text.InputFilter.LengthFilter(80))
            ControlStyle.input(this, ink)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { nameInput.setText(s) }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        heading.addView(editableName, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(Switch(this).apply {
            contentDescription = getString(R.string.ui_084); isChecked = enabledInput.isChecked
            ControlStyle.toggle(this, ink)
            setOnCheckedChangeListener { _, checked -> enabledInput.isChecked = checked }
        }, LinearLayout.LayoutParams(-2, dp(48)))
        body.addView(heading, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        val appNames = selected.map { pkg -> apps.firstOrNull { it.first == pkg }?.second ?: pkg }.sorted()
        row(getString(R.string.app_list_title), appNames.joinToString("\n").ifBlank { getString(R.string.ui_110) }, 0) { chooseApps() }
        val minutes = limitInput.text.toString().toIntOrNull() ?: 0
        row(getString(R.string.ui_085), DurationText.minutes(this, minutes), 1)
        row(getString(R.string.ui_130), if (waitInput.isChecked) waitSecondsInput.text.toString() + " " + getString(R.string.duration_seconds) else getString(R.string.focus_off), 2)
        row(getString(R.string.ui_092), getString(if (scheduleInput.isChecked) R.string.ui_084 else R.string.focus_off), 3)
        body = original
    }

    private fun validatePanel(index: Int, showError: Boolean): Boolean {
        fun invalid(field: EditText?, text: Int): Boolean {
            if (showError) {
                if (field == null) toast(getString(text)) else { field.error = getString(text); field.requestFocus() }
            }
            return false
        }
        when (index) {
            0 -> {
                if (nameInput.text.toString().isBlank()) return invalid(nameInput, R.string.ui_113)
                if (selected.isEmpty()) return invalid(null, R.string.ui_114)
            }
            1 -> {
                if (selectedDays.isEmpty()) return invalid(null, R.string.ui_102)
                val n = limitInput.text.toString().toIntOrNull()
                if (n == null || n !in 1..1440) return invalid(limitInput, if (n == 0) R.string.ui_115 else R.string.ui_107)
                val reminder = reminderInput.text.toString().toIntOrNull()
                if (reminder == null || reminder !in 1..1440) return invalid(reminderInput, if (reminder == 0) R.string.ui_115 else R.string.ui_107)
            }
            2 -> if (waitInput.isChecked) {
                val n = waitSecondsInput.text.toString().toIntOrNull()
                if (n == null || n !in 1..FocusPolicy.MAX_ENTRY_SECONDS) return invalid(waitSecondsInput, R.string.ui_133)
            }
            3 -> if (scheduleInput.isChecked) {
                if (scheduleDays.isEmpty()) return invalid(null, R.string.ui_102)
                if (!allDayInput.isChecked && startMinute == endMinute) return invalid(null, R.string.focus_distinct_times)
            }
        }
        return true
    }

    private fun dayName(day: Int, short: Boolean = false): String = java.time.DayOfWeek.of(day).getDisplayName(
        if (short) java.time.format.TextStyle.SHORT else java.time.format.TextStyle.FULL, resources.configuration.locales[0])
    private fun updateDays() {
        daysButton?.text = selectedDays.sorted().joinToString(" · ") { dayName(it, true) }
        daySummary?.text = dayLimits.filterKeys { it in selectedDays }.toSortedMap().entries.joinToString("\n") { (d, limit) -> "${dayName(d)} · ${DurationText.minutes(this, limit)}" }
        daySummary?.visibility = if (dayLimits.keys.any { it in selectedDays }) View.VISIBLE else View.GONE
    }
    private fun chooseDays() {
        val picked = selectedDays.toMutableSet()
        val dialog = DialogStyle.builder(this).setTitle(getString(R.string.ui_100))
            .setMultiChoiceItems((1..7).map { dayName(it) }.toTypedArray(), (1..7).map { it in picked }.toBooleanArray()) { _, i, checked ->
                if (checked) picked.add(i + 1) else picked.remove(i + 1)
            }.setPositiveButton(getString(R.string.ui_101), null).setNegativeButton(getString(R.string.ui_063), null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (picked.isEmpty()) toast(getString(R.string.ui_102))
            else { selectedDays = picked; updateDays(); dialog.dismiss() }
        } }; dialog.show()
    }
    private fun chooseDayLimits() {
        val dialogContext = DialogStyle.context(this)
        val layout = LinearLayout(dialogContext).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0) }
        val picked = mutableSetOf<Int>()
        selectedDays.sorted().forEach { day -> layout.addView(CheckBox(dialogContext).apply {
            text = dayName(day)
            ControlStyle.toggle(this, ink); typeface = font; minHeight = dp(48)
            setOnCheckedChangeListener { _, checked -> if (checked) picked.add(day) else picked.remove(day) }
        }) }
        val durationTitle = TextView(dialogContext).apply { text = getString(R.string.ui_103); setTextColor(ink); typeface = font; layout.addView(this) }
        val number = durationInput(limitInput.text.toString(), R.string.duration_minutes, durationTitle, layout, dialogContext)
        val scroll = ScrollView(dialogContext).apply { addView(layout) }
        val dialog = DialogStyle.builder(this).setTitle(getString(R.string.ui_104)).setView(scroll)
            .setPositiveButton(getString(R.string.ui_105), null).setNegativeButton(getString(R.string.ui_063), null)
            .setNeutralButton(getString(R.string.ui_106), null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = number.text.toString().toIntOrNull()
                if (picked.isEmpty()) toast(getString(R.string.ui_102))
                else if (value == null || value !in 1..1440) number.error = getString(R.string.ui_107)
                else { picked.forEach { dayLimits[it] = value }; updateDays(); dialog.dismiss() }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (picked.isEmpty()) toast(getString(R.string.ui_102))
                else { picked.forEach { dayLimits.remove(it) }; updateDays(); dialog.dismiss() }
            }
        }; dialog.show()
    }

    private fun chooseScheduleDays() {
        val picked = scheduleDays.toMutableSet()
        val dialog = DialogStyle.builder(this).setTitle(getString(R.string.focus_block_days))
            .setMultiChoiceItems((1..7).map { dayName(it) }.toTypedArray(), (1..7).map { it in picked }.toBooleanArray()) { _, i, checked ->
                if (checked) picked.add(i + 1) else picked.remove(i + 1)
            }.setPositiveButton(getString(R.string.ui_101), null).setNegativeButton(getString(R.string.ui_063), null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (picked.isEmpty()) toast(getString(R.string.ui_102))
            else {
                scheduleDays = picked
                scheduleDaysButton?.text = scheduleDays.sorted().joinToString(" · ") { dayName(it, true) }
                dialog.dismiss()
            }
        } }; dialog.show()
    }

    private fun updateAppsButton() {
        appsButton?.text = if (loaded) getString(R.string.ui_108, selected.size) else getString(R.string.ui_002)
        selectedAppsLabel?.apply {
            text = selected.map { pkg -> apps.firstOrNull { it.first == pkg }?.second ?: pkg }.sorted().joinToString("\n")
            visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
        }
    }
    private fun chooseApps() {
        if (!loaded) { toast(getString(R.string.ui_109)); return }
        val occupied = store.groups().filter { it.id != editing?.id }.flatMap { it.packages }.toSet()
        val available = apps.filter { it.first !in occupied }
        val chosen = selected.toMutableSet()
        var visible = available
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0)
        }
        val search = EditText(this).apply {
            hint = getString(R.string.ui_001); contentDescription = hint
            isSingleLine = true; textSize = 16f * scale; typeface = font
            ControlStyle.input(this, ink)
        }
        val list = ListView(this).apply { divider = null }
        // label() attaches to the editor body; this view belongs only to the dialog.
        val empty = TextView(this).apply {
            text = getString(R.string.ui_006); textSize = 16f * scale; typeface = font
            setTextColor(ink); setPadding(0, dp(12), 0, dp(12))
        }
        val adapter = object : BaseAdapter() {
            override fun getCount() = visible.size
            override fun getItem(position: Int) = visible[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, recycled: View?, parent: ViewGroup?): View {
                return ((recycled as? CheckBox) ?: CheckBox(this@FocusActivity).apply {
                    minHeight = dp(52); textSize = 16f * scale; typeface = font
                    isClickable = false; isFocusable = false
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    ControlStyle.toggle(this, ink)
                }).apply {
                    text = visible[position].second
                    isChecked = visible[position].first in chosen
                }
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            val pkg = visible[position].first
            if (!chosen.add(pkg)) chosen.remove(pkg)
            adapter.notifyDataSetChanged()
        }
        content.addView(search, LinearLayout.LayoutParams(-1, dp(56)))
        val results = android.widget.FrameLayout(this)
        results.addView(list, android.widget.FrameLayout.LayoutParams(-1, -1))
        results.addView(empty, android.widget.FrameLayout.LayoutParams(-1, -2))
        content.addView(results, LinearLayout.LayoutParams(-1, minOf(dp(320), resources.displayMetrics.heightPixels / 3)))
        list.emptyView = empty
        fun normalized(value: String) = java.text.Normalizer.normalize(value.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace('ı', 'i')
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = normalized(s.toString().trim())
                visible = available.filter { normalized(it.second).contains(query) }
                adapter.notifyDataSetChanged(); list.setSelection(0)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        DialogStyle.builder(this).setTitle(getString(R.string.ui_110))
            .setView(content).setPositiveButton(getString(R.string.ui_101)) { _, _ ->
                selected = chosen; updateAppsButton()
                if (editorStep == -1) refreshSummary()
            }.setNegativeButton(getString(R.string.ui_063), null).setNeutralButton(getString(R.string.ui_111)) { _, _ ->
                toast(getString(R.string.ui_112))
            }.show()
    }
    private fun draft(): FocusGroup = editing!!.copy(name = nameInput.text.toString().trim(), packages = selected.toSet(),
        minutes = limitInput.text.toString().toIntOrNull() ?: -1, reminder = reminderInput.text.toString().toIntOrNull() ?: -1,
        blockAtLimit = blockInput.isChecked,
        message = messageInput.text.toString().trim().takeUnless { it == getString(R.string.ui_090) } ?: "",
        limitMessage = limitMessageInput.text.toString().trim().takeUnless { it == getString(R.string.limit_message_default) } ?: "", schedule = scheduleInput.isChecked,
        start = startMinute, end = endMinute, enabled = enabledInput.isChecked,
        days = selectedDays.toSet(), dayLimits = dayLimits.filterKeys { it in selectedDays },
        waitBeforeOpen = waitInput.isChecked, waitSeconds = if (waitInput.isChecked) waitSecondsInput.text.toString().toIntOrNull() ?: -1 else editing!!.waitSeconds,
        scheduleDays = scheduleDays.toSet(), scheduleAllDay = allDayInput.isChecked)
    private fun save() {
        for (index in editorPanels.indices) {
            if (!validatePanel(index, false)) {
                displayEditorStep(index)
                validatePanel(index, true)
                return
            }
        }
        val g = draft()
        if (g.name.isBlank()) { nameInput.error = getString(R.string.ui_113); return }
        if (g.packages.isEmpty()) { toast(getString(R.string.ui_114)); return }
        if (g.days.isEmpty()) { toast(getString(R.string.ui_102)); return }
        if (g.waitBeforeOpen && g.waitSeconds !in 1..FocusPolicy.MAX_ENTRY_SECONDS) {
            waitSecondsInput.error = getString(R.string.ui_133); waitSecondsInput.requestFocus(); return
        }
        if (g.minutes !in 1..1440) {
            limitInput.error = if (g.minutes == 0) getString(R.string.ui_115) else getString(R.string.ui_107)
            limitInput.requestFocus(); return
        }
        if (g.reminder !in 1..1440) {
            reminderInput.error = if (g.reminder == 0) getString(R.string.ui_115) else getString(R.string.ui_107)
            reminderInput.requestFocus(); return
        }
        val saved = if (g.message.isBlank()) g.copy(message = FocusGroup().message) else g
        store.save(store.groups().filter { it.id != g.id } + saved)
        showList(); toast(getString(R.string.ui_116))
        if (saved.enabled && needsPermission()) disclose()
    }
    private fun pickTime(minute: Int, action: (Int) -> Unit) { TimePickerDialog(DialogStyle.context(this), R.style.SadeDialogOverlay, { _, h, m -> action(h * 60 + m) }, minute / 60, minute % 60, true).show() }
    private fun time(minute: Int) = String.format(java.util.Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60)
    private fun toast(value: String) { Toast.makeText(this, value, Toast.LENGTH_LONG).show() }
    override fun onResume() {
        super.onResume()
        PermissionReturn.cancel()
        if (::body.isInitialized && appearanceStamp != currentAppearance()) {
            val pending = editing?.let { draft() }
            val baseline = initialDraft
            if (pending == null) showList() else {
                edit(pending, editorStep, creating, editorReviewMode)
                initialDraft = baseline
            }
        }
        updateUsageBars()
        if (needsPermission()) body.post {
            if (!isFinishing && !isDestroyed && needsPermission()) disclose()
        }
        if (pendingExitPrompt) {
            pendingExitPrompt = false
            if (hasChanges()) body.post { if (!isFinishing && !isDestroyed) confirmExit() }
        }
    }
    private fun hasChanges() = editing != null && draft() != initialDraft
    private fun back() {
        if (editing == null) finish()
        else if (editorStep >= 0 && editorReviewMode) displayEditorStep(-1)
        else if (editorStep > 0) displayEditorStep(editorStep - 1)
        else exitEditor()
    }
    private fun exitEditor() { if (hasChanges()) confirmExit() else showList() }
    private fun confirmExit() {
        if (exitDialog?.isShowing == true) return
        exitDialog = DialogStyle.builder(this)
            .setTitle(getString(R.string.ui_117))
            .setPositiveButton(getString(R.string.ui_118)) { _, _ -> save() }
            .setNegativeButton(getString(R.string.ui_119)) { _, _ -> showList() }
            .setNeutralButton(getString(R.string.ui_120), null)
            .create().also { it.show() }
    }
    override fun onUserLeaveHint() {
        // Android Home cannot be intercepted. Preserve the draft and ask on return.
        pendingExitPrompt = hasChanges()
        super.onUserLeaveHint()
    }
    @Deprecated("Legacy back")
    @android.annotation.SuppressLint("GestureBackNavigation")
    override fun onBackPressed() { back() }
    override fun onSaveInstanceState(out: Bundle) {
        editing?.let {
            out.putBundle("draft", groupBundle(draft()))
            initialDraft?.let { out.putBundle("initial", groupBundle(it)) }
        }
        out.putBoolean("pendingExitPrompt", pendingExitPrompt || exitDialog?.isShowing == true)
        out.putInt("editorStep", editorStep)
        out.putInt("editorVersion", 2)
        out.putBoolean("creating", creating)
        out.putBoolean("reviewMode", editorReviewMode)
        super.onSaveInstanceState(out)
    }
    private fun groupBundle(g: FocusGroup) = Bundle().apply {
                putString("id", g.id); putString("name", g.name); putStringArrayList("packages", ArrayList(g.packages))
                putInt("minutes", g.minutes); putInt("reminder", g.reminder); putBoolean("block", g.blockAtLimit)
                putString("message", g.message); putBoolean("schedule", g.schedule); putBoolean("enabled", g.enabled)
                putString("limitMessage", g.limitMessage)
                putInt("start", g.start); putInt("end", g.end)
                putIntegerArrayList("days", ArrayList(g.days))
                putBundle("dayLimits", Bundle().apply { g.dayLimits.forEach { (d, v) -> putInt(d.toString(), v) } })
                putBoolean("waitBeforeOpen", g.waitBeforeOpen); putInt("waitSeconds", g.waitSeconds)
                putIntegerArrayList("scheduleDays", ArrayList(g.scheduleDays)); putBoolean("scheduleAllDay", g.scheduleAllDay)
    }
    private fun readGroup(b: Bundle) = FocusGroup(b.getString("id")!!, b.getString("name", ""), b.getStringArrayList("packages")!!.toSet(),
        b.getInt("minutes"), b.getBoolean("block"), b.getString("message", ""), b.getBoolean("schedule"),
        b.getInt("start"), b.getInt("end"), b.getBoolean("enabled"), b.getInt("reminder"),
        b.getIntegerArrayList("days")?.toSet() ?: (1..7).toSet(),
        b.getBundle("dayLimits")?.let { limits -> limits.keySet().associate { it.toInt() to limits.getInt(it) } } ?: emptyMap(),
        b.getBoolean("waitBeforeOpen"), b.getInt("waitSeconds", 5),
        (b.getIntegerArrayList("scheduleDays") ?: b.getIntegerArrayList("days"))?.toSet() ?: (1..7).toSet(),
        b.getBoolean("scheduleAllDay", b.getInt("start") == b.getInt("end")), b.getString("limitMessage", ""))
    override fun onDestroy() { permissionDialog?.dismiss(); worker.shutdownNow(); super.onDestroy() }
}

