package com.sade.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.text.Collator
import java.util.Locale
import java.util.concurrent.Executors

/** Home UI has no polling or network access. Optional focus monitoring is separate. */
class MainActivity : LocalizedActivity() {
    private data class App(val label: String, val component: ComponentName) {
        val key: String get() = component.flattenToString()
    }
    private val prefs by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }
    private val worker = Executors.newSingleThreadExecutor()
    private var apps = emptyList<App>()
    private var visible = emptyList<App>()
    private var favorites = mutableSetOf<String>()
    private var currentPage = 0
    private var launchRequest = 0
    private var homeResumed = false
    private var favoriteApps = emptyList<App>()
    private var generation = 0
    private lateinit var list: ListView
    private lateinit var search: EditText
    private lateinit var pager: TwoPageLayout
    private lateinit var launcherRoot: LinearLayout
    private lateinit var favoriteList: ListView
    private lateinit var favoriteAdapter: BaseAdapter
    private lateinit var pageIndicator: TextView
    private lateinit var adapter: BaseAdapter
    private var ink = Color.BLACK
    private var paper = Color.WHITE
    private var font = Typeface.SANS_SERIF
    private var size = 26f
    private var appearanceKey = ""
    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in setOf("paper", "ink", "font", "size", "clock", "clock_style", "show_date", "app_list_mode", "camera_shortcut", "phone_shortcut")) appearanceKey = ""
    }
    private val packagesChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { loadApps() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { handleHomeBack() }
        }
        favorites = prefs.getStringSet("favorites", emptySet())!!.sorted().take(5).toMutableSet()
        prefs.edit().putStringSet("favorites", favorites.toSet()).apply()
        currentPage = savedInstanceState?.getInt("page") ?: 0
        buildUi()
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        if (!prefs.getBoolean("tutorial_done", false)) {
            startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(packagesChanged, filter, RECEIVER_EXPORTED)
        else registerReceiver(packagesChanged, filter)
        loadApps()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun text(value: String, sp: Float) = TextView(this).apply {
        text = value; textSize = sp; setTextColor(ink); typeface = font
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(8), dp(4), dp(8))
    }
    private fun action(label: String, callback: () -> Unit) = text(label, 15f).apply {
        minHeight = dp(48); isClickable = true; isFocusable = true
        val value = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        setBackgroundResource(value.resourceId)
        setOnClickListener { callback() }
    }

    private fun buildUi() {
        paper = Color.parseColor(prefs.getString("paper", "#F3F0E8"))
        WindowSurface.apply(this, paper)
        ink = Appearance.ink(prefs, paper)
        font = Typeface.create(prefs.getString("font", "sans-serif"), Typeface.NORMAL)
        size = prefs.getInt("size", 26).toFloat()
        appearanceKey = "${paper}|${font}|${size}"
        val drawerMode = prefs.getString("app_list_mode", "page") == "drawer"
        val horizontalPadding = if (drawerMode) 0 else dp(24)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(paper)
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
        }
        launcherRoot = root
        val drawer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(horizontalPadding + bars.left, bars.top, horizontalPadding + bars.right, bars.bottom)
                val ime = insets.getInsets(WindowInsets.Type.ime())
                val bottom = LauncherInsets.searchBottom(currentPage, ::search.isInitialized && search.hasFocus(),
                    insets.isVisible(WindowInsets.Type.ime()), ime.bottom, bars.bottom)
                drawer.setPadding(0, 0, 0, bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(horizontalPadding + insets.stableInsetLeft, insets.stableInsetTop,
                    horizontalPadding + insets.stableInsetRight, insets.stableInsetBottom)
            }
            insets
        }
        pager = TwoPageLayout(this, drawerMode)
        val home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(paper)
            if (drawerMode) setPadding(dp(24), 0, dp(24), 0)
        }
        if (drawerMode) {
            // Separate the sheet from the home surface, including on pure black themes.
            val tone = if (Appearance.dark(paper)) 255 else 0
            fun surfaceChannel(value: Int) = (value * .94f + tone * .06f).toInt()
            val drawerPaper = Color.rgb(surfaceChannel(Color.red(paper)), surfaceChannel(Color.green(paper)), surfaceChannel(Color.blue(paper)))
            drawer.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(drawerPaper)
                cornerRadii = floatArrayOf(dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), 0f, 0f, 0f, 0f)
                setStroke(dp(1), Color.argb(45, Color.red(ink), Color.green(ink), Color.blue(ink)))
            }
            drawer.clipToOutline = true
            drawer.elevation = dp(12).toFloat()
            val handle = TextView(this).apply {
                text = "━━"; setTextColor(ink); alpha = .6f; gravity = Gravity.CENTER
                contentDescription = getString(R.string.ui_124)
                setOnClickListener { pager.showPage(0) }
            }
            handle.includeFontPadding = false
            drawer.addView(handle, LinearLayout.LayoutParams(-1, dp(32)))
            pager.drawerHandleHeight = dp(32)
        }
        val clockRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(4), dp(40), dp(4), dp(16))
        }
        val clockStyle = prefs.getString("clock_style", "small")
        val large = clockStyle == "analog" || clockStyle == "large"
        clockRow.orientation = LinearLayout.VERTICAL
        if (clockStyle == "analog") {
            clockRow.addView(ClockFace(this, ink), LinearLayout.LayoutParams(-1, dp(180)))
        } else if (clockStyle != "hidden") {
            clockRow.addView(TextClock(this).apply {
                format12Hour = "HH:mm"; format24Hour = "HH:mm"
                textSize = if (large) 64f else size * 0.65f
                typeface = font; setTextColor(ink); gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, -2))
        }
        if (prefs.getBoolean("show_date", true)) clockRow.addView(TextClock(this).apply {
            format12Hour = "d MMMM"; format24Hour = "d MMMM"
            textSize = size * 0.65f; typeface = font; setTextColor(ink)
            gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
        home.addView(clockRow)
        search = EditText(this).apply {
            ControlStyle.input(this, ink)
            hint = getString(R.string.ui_001); textSize = 17f; typeface = font
            setTextColor(ink); setHintTextColor(ink); setSingleLine(true)
            minHeight = dp(48); inputType = android.text.InputType.TYPE_CLASS_TEXT
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        drawer.addView(search, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(if (drawerMode) 2 else 18); bottomMargin = dp(8)
            if (drawerMode) { marginStart = dp(12); marginEnd = dp(12) }
        })
        val frame = FrameLayout(this)
        list = ListView(this).apply { divider = null; isVerticalScrollBarEnabled = false }
        val empty = text(getString(R.string.ui_002), 17f).apply { gravity = Gravity.CENTER }
        frame.addView(list, FrameLayout.LayoutParams(-1, -1))
        frame.addView(empty, FrameLayout.LayoutParams(-1, -1))
        list.emptyView = empty
        adapter = object : BaseAdapter() {
            override fun getCount() = visible.size
            override fun getItem(position: Int) = visible[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val row = (convertView as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(text("", 12f))
                    addView(text("", size))
                }
                val initial = visible[position].label.take(1).uppercase(Locale.getDefault())
                (row.getChildAt(0) as TextView).apply {
                    text = initial; alpha = 0.65f
                    visibility = if (position == 0 || visible[position - 1].label.take(1).uppercase(Locale.getDefault()) != initial) View.VISIBLE else View.GONE
                    setPadding(dp(12), dp(if (drawerMode && position == 0) 8 else 20), dp(12), dp(2))
                }
                (row.getChildAt(1) as TextView).apply {
                    text = visible[position].label
                    textSize = size; typeface = font; minHeight = dp(58); setTextColor(ink)
                    // Reset recycled rows too, including when search or favorites change.
                    background = if (visible[position].key in favorites) {
                        android.graphics.drawable.InsetDrawable(
                            android.graphics.drawable.GradientDrawable().apply {
                                cornerRadius = dp(12).toFloat()
                                setColor(Color.argb(26, Color.red(ink), Color.green(ink), Color.blue(ink)))
                            }, 0, dp(3), 0, dp(3))
                    } else null
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
                return row
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> launch(visible[position]) }
        list.setOnItemLongClickListener { _, _, position, _ -> appOptions(visible[position]); true }
        drawer.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))

        val favoriteFrame = FrameLayout(this)
        favoriteList = ListView(this).apply {
            divider = null; isVerticalScrollBarEnabled = false; isStackFromBottom = true
            setPadding(0, 0, 0, dp(20)); clipToPadding = false
        }
        val favoriteEmpty = View(this)
        favoriteFrame.addView(favoriteList, FrameLayout.LayoutParams(-1, -1))
        favoriteFrame.addView(favoriteEmpty, FrameLayout.LayoutParams(-1, -1))
        favoriteList.emptyView = favoriteEmpty
        favoriteAdapter = object : BaseAdapter() {
            override fun getCount() = favoriteApps.size
            override fun getItem(position: Int) = favoriteApps[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val row = (convertView as? FrameLayout) ?: FrameLayout(this@MainActivity).apply {
                    addView(text("", size), FrameLayout.LayoutParams(-2, -2, Gravity.START))
                }
                val app = favoriteApps[position]
                (row.getChildAt(0) as TextView).apply {
                    text = app.label; textSize = size; typeface = font; minHeight = dp(58); setTextColor(ink)
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    setOnClickListener { launch(app) }
                    setOnLongClickListener { appOptions(app); true }
                    isFocusable = true
                }
                return row
            }
        }
        favoriteList.setSelector(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        favoriteList.adapter = favoriteAdapter
        home.addView(favoriteFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        // Reserve one row even when the shortcut is hidden, so favorites stay in place.
        val shortcuts = FrameLayout(this)
        if (prefs.getBoolean("camera_shortcut", false)) {
            shortcuts.addView(ImageButton(this).apply {
                contentDescription = getString(R.string.camera_shortcut)
                setImageResource(R.drawable.ic_camera_outline)
                imageTintList = android.content.res.ColorStateList.valueOf(ink)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.argb(35, Color.red(ink), Color.green(ink), Color.blue(ink))),
                    null, android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.WHITE) })
                setOnClickListener { openCamera() }
            }, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.RIGHT or Gravity.BOTTOM).apply { bottomMargin = dp(4) })
        }
        if (prefs.getBoolean("phone_shortcut", false)) {
            shortcuts.addView(ImageButton(this).apply {
                contentDescription = getString(R.string.phone_shortcut)
                setImageResource(R.drawable.ic_phone_outline)
                imageTintList = android.content.res.ColorStateList.valueOf(ink)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.argb(35, Color.red(ink), Color.green(ink), Color.blue(ink))),
                    null, android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.WHITE) })
                setOnClickListener { openPhone() }
            }, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.LEFT or Gravity.BOTTOM).apply { bottomMargin = dp(4) })
        }
        home.addView(shortcuts, LinearLayout.LayoutParams(-1, dp(58)))
        pager.addPage(home)
        pager.addPage(drawer)
        root.addView(pager, LinearLayout.LayoutParams(-1, 0, 1f))
        pageIndicator = action("") { pager.showPage(1 - currentPage) }.apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        root.addView(pageIndicator)
        if (drawerMode) pageIndicator.visibility = View.GONE
        pager.onPageChanged = { page ->
            currentPage = page
            updateKeyboardLayout()
            pageIndicator.text = if (drawerMode) { if (page == 0) "⌃" else "⌄" } else if (page == 0) "━  ─" else "─  ━"
            pageIndicator.contentDescription = if (drawerMode) getString(if (page == 0) R.string.app_list_title else R.string.ui_124)
                else if (page == 0) getString(R.string.ui_003) else getString(R.string.ui_004)
            if (page == 0) {
                search.text.clear(); search.clearFocus()
                getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(search.windowToken, 0)
            }
        }
        pager.showPage(currentPage, false)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { filter() }
            override fun afterTextChanged(s: Editable?) {}
        })
        search.setOnFocusChangeListener { _, _ -> updateKeyboardLayout() }
        search.setOnEditorActionListener { _, _, _ ->
            if (visible.size == 1) { launch(visible.first()); true } else false
        }
        root.isFocusableInTouchMode = true
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.setSystemBarsAppearance(
                if (!Appearance.dark(paper)) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        }
        root.requestFocus()
        updateKeyboardLayout()
        filter()
    }

    private fun updateKeyboardLayout() {
        // API 30+ reports IME insets separately. Keep Home full-sized, including during PiP transitions.
        val adjustment = if (Build.VERSION.SDK_INT < 30 && currentPage == 1 &&
            ::search.isInitialized && search.hasFocus()) WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            else WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        val current = window.attributes.softInputMode
        val mode = (current and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or adjustment
        if (current != mode) window.setSoftInputMode(mode)
        if (::launcherRoot.isInitialized) {
            launcherRoot.requestApplyInsets()
            launcherRoot.requestLayout()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateKeyboardLayout()
    }

    private fun loadApps() {
        val request = ++generation
        worker.execute {
            val result = runCatching {
                val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                @Suppress("DEPRECATION")
                val collator = Collator.getInstance()
                packageManager.queryIntentActivities(query, 0)
                    .map { App(it.loadLabel(packageManager).toString(), ComponentName(it.activityInfo.packageName, it.activityInfo.name)) }
                    .distinctBy { it.key }
                    .sortedWith { a, b -> collator.compare(a.label, b.label) }
            }
            runOnUiThread {
                if (!isDestroyed && request == generation) {
                    result.onSuccess {
                        apps = it
                        if (favorites.retainAll(apps.map { app -> app.key }.toSet())) {
                            prefs.edit().putStringSet("favorites", favorites.toSet()).apply()
                        }
                        filter()
                    }.onFailure {
                        Toast.makeText(this, getString(R.string.ui_005), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun filter() {
        val query = search.text.toString().trim().lowercase(Locale.getDefault())
        visible = apps.filter { it.label.lowercase(Locale.getDefault()).contains(query) }
        favoriteApps = apps.filter { it.key in favorites }.take(5)
        adapter.notifyDataSetChanged()
        favoriteAdapter.notifyDataSetChanged()
        (list.emptyView as TextView).text = getString(R.string.ui_006)
    }

    private fun launch(app: App) {
        launchRequest++
        try {
            if (app.component.packageName == packageName) {
                startActivity(Intent(this, SettingsActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                return
            }
            if (blockBeforeLaunch(app.component)) return
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        } catch (_: RuntimeException) {
            Toast.makeText(this, getString(R.string.ui_007), Toast.LENGTH_SHORT).show()
            loadApps()
        }
    }

    private fun openPhone() {
        launchRequest++
        val dial = Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val defaultPhone = runCatching { getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
        if (defaultPhone != null) {
            try { startActivity(Intent(dial).setPackage(defaultPhone)); return }
            catch (_: RuntimeException) { /* Fall back to Android's dialer resolver. */ }
        }
        try { startActivity(dial) }
        catch (_: RuntimeException) { Toast.makeText(this, R.string.ui_007, Toast.LENGTH_SHORT).show() }
    }

    private fun openCamera() {
        launchRequest++
        try {
            val camera = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            @Suppress("DEPRECATION")
            val candidates = packageManager.queryIntentActivities(camera, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            val resolved = camera.resolveActivity(packageManager)
            val component = resolved?.takeIf { c -> candidates.any { it.activityInfo.packageName == c.packageName && it.activityInfo.name == c.className } }
                ?: candidates.firstOrNull()?.activityInfo?.let { ComponentName(it.packageName, it.name) }
                ?: throw ActivityNotFoundException()
            if (blockBeforeLaunch(component, camera = true)) return
            startActivity(camera.setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.ui_007, Toast.LENGTH_SHORT).show()
        }
    }

    private fun blockBeforeLaunch(component: ComponentName, camera: Boolean = false): Boolean {
        val focus = FocusStore(this)
        if (!focus.prefs.getBoolean("consent", false) || focus.groups().none {
            it.enabled && it.minutes > 0 && it.reminder > 0 && component.packageName in it.packages
        }) return false
        val request = ++launchRequest
        // Keep Home visible while reading usage. An allowed launch never creates the gate activity.
        AndroidUsage.refresh(this, force = true) { ok ->
            if (request != launchRequest || !homeResumed || isFinishing || isDestroyed) return@refresh
            if (!ok && AndroidUsage.allowed(this)) {
                Toast.makeText(this, R.string.usage_unavailable, Toast.LENGTH_SHORT).show()
                return@refresh
            }
            FocusService.checkpointUsage()
            val latest = FocusStore(this)
            val group = latest.groups().firstOrNull { latest.prefs.getBoolean("consent", false) &&
                it.enabled && it.minutes > 0 && it.reminder > 0 && component.packageName in it.packages }
            val now = java.time.LocalDateTime.now()
            val day = now.dayOfWeek.value
            val scheduled = group != null && FocusPolicy.scheduleBlocks(group.schedule, group.scheduleAllDay, day,
                now.hour * 60 + now.minute, group.start, group.end, group.scheduleDays)
            val limit = if (group != null && day in group.days) group.limitFor(day) else 0
            val used = AndroidUsage.used(this, component.packageName, latest.usage()[component.packageName] ?: 0L)
            val decision = FocusPolicy.entryDecision(scheduled, FocusPolicy.limitReached(used, limit), group?.blockAtLimit == true,
                group != null && day in group.days && group.waitBeforeOpen, FocusService.canSkipEntry(component.packageName))
            try {
                if (decision == FocusPolicy.ALLOW) {
                    val launch = if (camera) Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                        else Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    startActivity(launch.setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
                } else {
                    startActivity(Intent(this, LaunchGateActivity::class.java)
                        .putExtra("target", component.flattenToString()).putExtra("camera", camera))
                }
            } catch (_: RuntimeException) { Toast.makeText(this, R.string.ui_007, Toast.LENGTH_SHORT).show() }
        }
        return true
    }

    private fun appOptions(app: App) {
        DialogStyle.builder(this).setTitle(app.label)
            .setItems(arrayOf(if (app.key in favorites) getString(R.string.ui_008) else getString(R.string.ui_009), getString(R.string.ui_010))) { _, which ->
                if (which == 0) {
                    if (app.key in favorites) favorites.remove(app.key)
                    else if (favorites.size < 5) favorites.add(app.key)
                    else {
                        Toast.makeText(this, getString(R.string.ui_011), Toast.LENGTH_LONG).show()
                        return@setItems
                    }
                    prefs.edit().putStringSet("favorites", favorites.toSet()).apply(); filter()
                } else startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${app.component.packageName}")))
            }.show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pager.showPage(0, false)
        search.text.clear(); search.clearFocus()
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(search.windowToken, 0)
    }

    private fun handleHomeBack() {
        // Home is a destination, never a task to pop and reveal the previous launcher.
        if (::pager.isInitialized) {
            getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(search.windowToken, 0)
            search.clearFocus()
            search.text.clear()
            if (currentPage != 0) pager.showPage(0)
        }
    }

    @Deprecated("Legacy Android Back callback")
    // API 33+ uses the platform OnBackInvokedDispatcher registered in onCreate.
    // This override is retained only for API 26–32, including Android 11.
    @android.annotation.SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        handleHomeBack()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("page", currentPage); super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        unregisterReceiver(packagesChanged); worker.shutdownNow(); super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        homeResumed = true
        updateKeyboardLayout()
        FocusService.setHomeVisible(true)
        if (::pager.isInitialized && appearanceKey.isEmpty()) buildUi()
        if (::pager.isInitialized) {
            pager.showPage(currentPage, false)
            pager.requestLayout()
        }
    }
    override fun onPause() {
        homeResumed = false; launchRequest++
        FocusService.setHomeVisible(false)
        super.onPause()
    }
}
