package com.sade.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.*

/** App-list entry point. Home intent remains in MainActivity. */
class SettingsActivity : LocalizedActivity() {
    private val prefs by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }
    private var step = -1
    private var screen = "main"
    private var ink = Color.BLACK
    private var font = Typeface.SANS_SERIF
    private var homeStatus: TextView? = null
    private var homeButton: Button? = null
    private var textScale = 1f
    private var applyingWallpaper = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { handleSettingsBack() }
        }
        step = savedInstanceState?.getInt("step") ?: if (prefs.getBoolean("tutorial_done", false)) -1 else 0
        screen = savedInstanceState?.getString("screen") ?: "main"
        render()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Float) = TextView(this).apply {
        text = value; textSize = size * textScale; setTextColor(ink); typeface = font
        setPadding(0, dp(12), 0, dp(12))
    }

    private fun render() {
        textScale = prefs.getInt("size", 26) / 26f
        homeStatus = null
        homeButton = null
        val paper = Color.parseColor(prefs.getString("paper", "#F3F0E8"))
        WindowSurface.apply(this, paper)
        val dark = Appearance.dark(paper)
        ink = Appearance.ink(prefs, paper)
        font = Typeface.create(prefs.getString("font", "sans-serif"), Typeface.NORMAL)
        val scroll = ScrollView(this).apply { setBackgroundColor(paper); isFillViewport = true }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(28), dp(24), dp(28), dp(24)) }
        scroll.addView(body)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        fun button(title: String, parent: LinearLayout = body, action: () -> Unit): Button {
            val result = Button(this).apply {
                text = title; textSize = 16f * textScale; isAllCaps = false; typeface = font; minHeight = dp(52)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL; setTextColor(ink)
                ControlStyle.apply(this, ink)
                setOnClickListener { action() }
            }
            parent.addView(result, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            return result
        }
        if (step >= 0) {
            body.addView(label("${step + 1} / 3", 14f))
            val titles = arrayOf(getString(R.string.ui_012), getString(R.string.ui_013), getString(R.string.ui_014))
            val descriptions = arrayOf(
                getString(R.string.ui_015),
                getString(R.string.ui_016),
                getString(R.string.ui_017)
            )
            body.addView(label(titles[step], 29f))
            body.addView(label(descriptions[step], 18f))
            if (step == 0) {
                homeStatus = label(homeStatusText(), 16f).also { body.addView(it) }
                homeButton = button(homeButtonText()) { chooseHome() }
                button(getString(R.string.ui_023)) { startActivity(Intent(this, FocusActivity::class.java)) }

            }
            button(if (step == 2) getString(R.string.ui_018) else getString(R.string.ui_019)) {
                if (step == 2) completeTutorial() else { step++; render() }
            }
            button(getString(R.string.ui_020)) { completeTutorial() }
        } else if (screen == "main") {
            body.addView(TextView(this).apply {
                text = "Sade"
                // Brand typography is independent of the user's content font and text size.
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                textSize = 42f
                letterSpacing = .04f
                setTextColor(ink)
                setPadding(0, dp(12), 0, dp(24))
                if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
            })
            button(getString(R.string.ui_022)) { Lang.choose(this) }
            button(getString(R.string.theme_menu)) { screen = "theme"; render() }
            button(getString(R.string.ui_047)) { screen = "setup"; render() }
            body.addView(android.view.View(this), LinearLayout.LayoutParams(-1, dp(32)))
            button(getString(R.string.ui_023)) { startActivity(Intent(this, FocusActivity::class.java)) }
        } else if (screen == "theme") {
            body.addView(label(getString(R.string.theme_menu), 30f))
            val listModes = arrayOf("page", "drawer")
            val listModeLabels = arrayOf(getString(R.string.app_list_page), getString(R.string.app_list_drawer))
            button(getString(R.string.app_list_title) + " · " + listModeLabels[listModes.indexOf(prefs.getString("app_list_mode", "page")).coerceAtLeast(0)]) {
                select(getString(R.string.app_list_title), listModeLabels, listModes, "app_list_mode")
            }
            button(getString(R.string.ui_024)) { screen = "clock"; render() }
            body.addView(Switch(this).apply {
                text = getString(R.string.camera_shortcut); textSize = 16f * textScale
                setTextColor(ink); typeface = font; minHeight = dp(52)
                isChecked = prefs.getBoolean("camera_shortcut", false)
                ControlStyle.apply(this, ink)
                ControlStyle.toggle(this, ink)
                setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("camera_shortcut", checked).apply() }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            body.addView(Switch(this).apply {
                text = getString(R.string.phone_shortcut); textSize = 16f * textScale
                setTextColor(ink); typeface = font; minHeight = dp(52)
                isChecked = prefs.getBoolean("phone_shortcut", false)
                ControlStyle.apply(this, ink)
                ControlStyle.toggle(this, ink)
                setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("phone_shortcut", checked).apply() }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            body.addView(android.view.View(this), LinearLayout.LayoutParams(-1, dp(32)))
            button(getString(R.string.ui_032)) { screen = "paper"; render() }
            button(getString(R.string.text_color)) { screen = "ink"; render() }
            button(getString(R.string.ui_037)) { select(getString(R.string.ui_037), arrayOf(getString(R.string.ui_038), getString(R.string.ui_039), getString(R.string.ui_040)), arrayOf("sans-serif", "serif", "monospace"), "font") }
            button(getString(R.string.ui_041)) {
                val sizes = listOf(22, 26, 32, 38)
                DialogStyle.builder(this).setTitle(getString(R.string.ui_041)).setSingleChoiceItems(arrayOf(getString(R.string.ui_042), getString(R.string.ui_043), getString(R.string.ui_044), getString(R.string.ui_045)), sizes.indexOf(prefs.getInt("size", 26))) { dialog, which ->
                    prefs.edit().putInt("size", sizes[which]).apply(); dialog.dismiss(); render()
                }.show()
            }
            button(getString(R.string.ui_046)) { confirmWallpaper() }
        } else if (screen == "clock") {
            body.addView(label(getString(R.string.ui_024), 30f))
            val styles = arrayOf("analog", "large", "small", "hidden")
            val styleNames = arrayOf(getString(R.string.ui_025), getString(R.string.ui_026), getString(R.string.ui_027), getString(R.string.ui_129))
            body.addView(label(getString(R.string.ui_029), 18f))
            val cards = mutableListOf<LinearLayout>()
            val dates = mutableListOf<TextClock>()
            body.addView(Switch(this).apply {
                text = getString(R.string.ui_030); textSize = 16f * textScale; setTextColor(ink); typeface = font; minHeight = dp(56)
                isChecked = prefs.getBoolean("show_date", true)
                ControlStyle.toggle(this, ink)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean("show_date", checked).apply()
                    dates.forEach { it.visibility = if (checked) android.view.View.VISIBLE else android.view.View.GONE }
                }
            }, LinearLayout.LayoutParams(-1, -2))
            fun updateClockSelection() {
                cards.forEachIndexed { index, card ->
                    val selected = prefs.getString("clock_style", "small") == styles[index]
                    card.isSelected = selected
                    val shape = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = dp(16).toFloat()
                        setColor(Color.argb(if (selected) 18 else 5, Color.red(ink), Color.green(ink), Color.blue(ink)))
                        setStroke(dp(if (selected) 2 else 1), Color.argb(if (selected) 210 else 65, Color.red(ink), Color.green(ink), Color.blue(ink)))
                    }
                    card.background = android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(Color.argb(30, Color.red(ink), Color.green(ink), Color.blue(ink))), shape, null)
                }
            }
            styles.forEachIndexed { index, style ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    minimumHeight = dp(64)
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    isClickable = true; isFocusable = true
                    contentDescription = styleNames[index]
                    setOnClickListener {
                        prefs.edit().putString("clock_style", style).apply()
                        updateClockSelection()
                    }
                }
                when (style) {
                    "analog" -> card.addView(ClockFace(this, ink).apply {
                        importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LinearLayout.LayoutParams(-1, dp(180)))
                    "large", "small" -> card.addView(TextClock(this).apply {
                        format12Hour = "HH:mm"; format24Hour = "HH:mm"
                        textSize = if (style == "large") 64f else prefs.getInt("size", 26) * .65f
                        typeface = font; setTextColor(ink); gravity = Gravity.CENTER
                        importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LinearLayout.LayoutParams(-1, dp(if (style == "large") 104 else 64)))
                }
                val date = TextClock(this).apply {
                    format12Hour = "d MMMM"; format24Hour = "d MMMM"
                    textSize = prefs.getInt("size", 26) * .65f
                    typeface = font; setTextColor(ink); gravity = Gravity.CENTER
                    setPadding(0, dp(6), 0, 0)
                    visibility = if (prefs.getBoolean("show_date", true)) android.view.View.VISIBLE else android.view.View.GONE
                    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                card.addView(date, LinearLayout.LayoutParams(-1, -2))
                dates.add(date)
                cards.add(card)
                body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            }
            updateClockSelection()
        } else if (screen == "paper" || screen == "ink") {
            val backgroundMode = screen == "paper"
            body.addView(label(getString(if (backgroundMode) R.string.ui_032 else R.string.text_color), 30f))
            body.addView(label("Sade", 36f).apply {
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(28), dp(16), dp(28))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(paper); cornerRadius = dp(20).toFloat()
                    setStroke(dp(1), Color.argb(80, Color.red(ink), Color.green(ink), Color.blue(ink)))
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(24) })
            val choices = Appearance.colors.toList()
            choices.chunked(4).forEach { rowColors ->
                val row = LinearLayout(this)
                body.addView(row, LinearLayout.LayoutParams(-1, -2))
                rowColors.forEach { value ->
                    val color = Color.parseColor(value)
                    val selected = color == if (backgroundMode) paper else ink
                    row.addView(TextView(this).apply {
                        text = if (selected) "✓" else ""
                        textSize = 24f; gravity = Gravity.CENTER
                        setTextColor(if (Appearance.dark(color)) Color.WHITE else Color.BLACK)
                        contentDescription = value
                        isSelected = selected
                        isFocusable = true
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(color); cornerRadius = dp(16).toFloat()
                            setStroke(dp(if (selected) 3 else 1), if (selected) ink else Color.GRAY)
                        }
                        setOnClickListener {
                            val changed = !selected
                            val edit = prefs.edit().putString(if (backgroundMode) "paper" else "ink", value)
                            edit.apply()
                            render()
                            if (backgroundMode && changed) WallpaperSync.apply(applicationContext, false) {}
                        }
                    }, LinearLayout.LayoutParams(0, dp(64), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
                }
                repeat(4 - rowColors.size) { row.addView(android.view.View(this), LinearLayout.LayoutParams(0, dp(64), 1f)) }
            }
            val currentColor = if (backgroundMode) paper else ink
            val value = String.format(java.util.Locale.ROOT, "#%06X", currentColor and 0xFFFFFF)
            button(getString(R.string.custom_color) + if (value !in Appearance.colors) " · $value" else "") { customColor(currentColor, backgroundMode) }
        } else {
            body.addView(label(getString(R.string.ui_047), 30f))
            homeStatus = label(homeStatusText(), 16f).also { body.addView(it) }
            homeButton = button(homeButtonText()) { chooseHome() }

            button(getString(R.string.ui_048)) { step = 0; render() }
        }
        setContentView(scroll)
        if (Build.VERSION.SDK_INT >= 30) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (dark) 0 else mask, mask)
        }
    }

    private fun customColor(initial: Int, backgroundMode: Boolean) {
        var updating = false
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(12), dp(24), dp(12))
        }
        val preview = label("Sade", 26f).apply { gravity = Gravity.CENTER; minHeight = dp(64) }
        val square = ColorSquare(this).apply { setColor(initial); contentDescription = getString(R.string.custom_color) }
        val hue = SeekBar(this).apply {
            max = 359; contentDescription = getString(R.string.color_hue)
            thumbTintList = android.content.res.ColorStateList.valueOf(ink)
            progressDrawable = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED)
            ).apply { cornerRadius = dp(8).toFloat() }
        }
        val hex = EditText(this).apply {
            hint = "#RRGGBB"; contentDescription = "HEX"
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            ControlStyle.input(this, ink)
        }
        fun update(color: Int, writeHex: Boolean) {
            val background = if (backgroundMode) color else Color.parseColor(prefs.getString("paper", "#F3F0E8"))
            preview.setBackgroundColor(background)
            preview.setTextColor(if (backgroundMode) Appearance.ink(prefs, color) else color)
            if (writeHex) {
                updating = true
                hex.setText(String.format(java.util.Locale.ROOT, "#%06X", color and 0xFFFFFF))
                hex.setSelection(hex.length()); hex.error = null
                updating = false
            }
        }
        val hsv = FloatArray(3)
        Color.colorToHSV(initial, hsv); hue.progress = hsv[0].toInt()
        square.onColor = { update(it, true) }
        hue.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) { if (fromUser) square.setHue(value.toFloat()) }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        fun parseHex(): Int? {
            var value = hex.text.toString().trim().removePrefix("#")
            if (!value.matches(Regex("[0-9a-fA-F]{3}|[0-9a-fA-F]{6}"))) return null
            if (value.length == 3) value = value.map { "$it$it" }.joinToString("")
            return Color.parseColor("#$value")
        }
        hex.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (updating) return
                val color = parseHex() ?: return
                hex.error = null; square.setColor(color)
                Color.colorToHSV(color, hsv); hue.progress = hsv[0].toInt()
                update(color, false)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        content.addView(preview, LinearLayout.LayoutParams(-1, -2))
        content.addView(square, LinearLayout.LayoutParams(-1, dp(180)).apply { topMargin = dp(16) })
        content.addView(label(getString(R.string.color_hue), 14f))
        content.addView(hue, LinearLayout.LayoutParams(-1, dp(48)))
        content.addView(hex, LinearLayout.LayoutParams(-1, dp(56)))
        update(initial, true)
        val dialog = DialogStyle.builder(this).setTitle(R.string.custom_color)
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton(R.string.ui_063, null).setPositiveButton(R.string.ui_101, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val parsed = parseHex()
                if (parsed == null) { hex.error = "#RRGGBB / #RGB"; return@setOnClickListener }
                val chosen = parsed
                val value = String.format(java.util.Locale.ROOT, "#%06X", chosen and 0xFFFFFF)
                val edit = prefs.edit().putString(if (backgroundMode) "paper" else "ink", value)
                edit.apply(); dialog.dismiss(); render()
                if (backgroundMode && chosen != initial) WallpaperSync.apply(applicationContext, false) {}
            }
        }
        dialog.show()
    }

    private fun select(title: String, labels: Array<String>, values: Array<String>, key: String) {
        DialogStyle.builder(this).setTitle(title).setSingleChoiceItems(labels, values.indexOf(prefs.getString(key, values[0]))) { dialog, which ->
            val changed = prefs.getString(key, values[0]) != values[which]
            prefs.edit().putString(key, values[which]).apply(); dialog.dismiss(); render()
            if (key == "paper" && changed) WallpaperSync.apply(applicationContext, false) {}
        }.show()
    }

    private fun isDefaultHome(): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roles.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        @Suppress("DEPRECATION")
        val selected = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )
        return selected?.activityInfo?.packageName == packageName
    }

    private fun homeButtonText() = if (isDefaultHome()) getString(R.string.ui_049) else getString(R.string.ui_050)

    private fun chooseHome() {
        val stopping = isDefaultHome()
        // Use public system routes, rather than manufacturer-specific class names.
        val routes = listOf(
            Settings.ACTION_HOME_SETTINGS to getString(if (stopping) R.string.choose_other_home else R.string.ui_051),
            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS to getString(if (stopping) R.string.choose_other_home else R.string.ui_052)
        )
        for ((action, instruction) in routes) {
            if (openSettingsRoute(action)) {
                Toast.makeText(this, instruction, Toast.LENGTH_LONG).show()
                return
            }
        }
        if (!stopping && Build.VERSION.SDK_INT >= 29) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 100)
                    return
                } catch (_: android.content.ActivityNotFoundException) {
                    // Continue to the last supported settings route.
                } catch (_: SecurityException) {
                    // The device may restrict role requests.
                }
            }
        }
        DialogStyle.builder(this).setTitle(getString(R.string.ui_053))
            .setMessage(getString(if (stopping) R.string.choose_other_home else R.string.ui_054))
            .setPositiveButton(getString(R.string.ui_055)) { _, _ ->
                if (!openSettingsRoute(Settings.ACTION_SETTINGS)) {
                    Toast.makeText(this, getString(R.string.ui_056), Toast.LENGTH_LONG).show()
                }
            }.setNegativeButton(getString(R.string.ui_057), null).show()
    }

    private fun openSettingsRoute(action: String): Boolean {
        return try {
            startActivity(Intent(action))
            true
        } catch (_: android.content.ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun homeStatusText() = if (isDefaultHome())
        getString(R.string.ui_058)
    else getString(R.string.ui_059)

    @Deprecated("Platform activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && !isDefaultHome()) {
            homeStatus?.text = getString(R.string.ui_060)
        }
    }

    override fun onResume() {
        super.onResume()
        homeStatus?.text = homeStatusText()
        homeButton?.text = homeButtonText()
    }
    private fun completeTutorial() {
        prefs.edit().putBoolean("tutorial_done", true).apply()
        step = -1
        screen = "main"
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        screen = "main"
        render()
    }

    private fun confirmWallpaper() {
        if (applyingWallpaper) return
        DialogStyle.builder(this).setTitle(getString(R.string.ui_061))
            .setMessage(getString(R.string.ui_062))
            .setNegativeButton(getString(R.string.ui_063), null)
            .setPositiveButton(getString(R.string.ui_064)) { _, _ -> applyWallpaper() }.show()
    }

    private fun applyWallpaper() {
        applyingWallpaper = true
        Toast.makeText(this, getString(R.string.ui_066), Toast.LENGTH_SHORT).show()
        val appContext = applicationContext
        WallpaperSync.apply(appContext, true) { result ->
            applyingWallpaper = false
            val text = when (result) {
                WallpaperOwnership.Result.APPLIED -> R.string.ui_067
                WallpaperOwnership.Result.UNSUPPORTED -> R.string.ui_065
                else -> R.string.ui_068
            }
            Toast.makeText(appContext, getString(text), Toast.LENGTH_LONG).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("screen", screen)
        outState.putInt("step", step); super.onSaveInstanceState(outState)
    }

    private fun handleSettingsBack() {
        if (step < 0 && screen in setOf("clock", "paper", "ink")) { screen = "theme"; render(); return }
        if (step < 0 && screen != "main") { screen = "main"; render(); return }
        if (step > 0) {
            step--
            render()
            return
        }
        // Settings has its own task. Return through Android's HOME route rather than
        // exposing whichever task happened to be behind it before Sade was selected.
        if (isDefaultHome()) {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    @Deprecated("Legacy Android Back callback")
    // Modern gestures use OnBackInvokedDispatcher above; this is the API 26–32 path.
    @android.annotation.SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        handleSettingsBack()
    }
}
