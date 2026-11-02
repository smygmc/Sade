from pathlib import Path
p=Path(__file__).resolve().parents[1]/'app/src/main/java/com/sade/launcher/SettingsActivity.kt'
s=p.read_text(encoding='utf-8')
a=s.index('            val clockPanel =')
b=s.index('            val styles =',a)
s=s[:a]+'''            button(getString(R.string.ui_024)) { screen = "clock"; render() }
            button(getString(R.string.ui_032)) { screen = "paper"; render() }
            button(getString(R.string.text_color)) { screen = "ink"; render() }
'''+s[b:]
a=s.index('            val styles =')
b=s.index('            button(getString(R.string.ui_032)) { select',a)
clock=s[a:b].replace(', clockPanel)', ')').replace('clockPanel.addView','body.addView')
s=s[:a]+s[b:]
a=s.index('            button(getString(R.string.ui_032)) { select')
b=s.index('\n',a)
s=s[:a]+s[b+1:]
a=s.index('        } else {\n            body.addView(label(getString(R.string.ui_047)')
s=s[:a]+'''        } else if (screen == "clock") {
            body.addView(label(getString(R.string.ui_024), 30f))
'''+clock+'''        } else if (screen == "paper" || screen == "ink") {
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
            val choices = Appearance.colors.filter { backgroundMode || Appearance.contrast(Color.parseColor(it), paper) >= 4.5 }
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
                            if (backgroundMode && Appearance.contrast(ink, color) < 4.5) edit.remove("ink")
                            edit.apply()
                            render()
                            if (backgroundMode && changed) WallpaperSync.apply(applicationContext, false) {}
                        }
                    }, LinearLayout.LayoutParams(0, dp(64), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
                }
                repeat(4 - rowColors.size) { row.addView(android.view.View(this), LinearLayout.LayoutParams(0, dp(64), 1f)) }
            }
'''+s[a:]
s=s.replace('        if (step < 0 && screen != "main")', '        if (step < 0 && screen in setOf("clock", "paper", "ink")) { screen = "theme"; render(); return }\n        if (step < 0 && screen != "main")')
p.write_text(s,encoding='utf-8')
