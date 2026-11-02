import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
colors = ['#F3F0E8','#FFFFFF','#E5E9DF','#E0E8EF','#EDE1E5','#EFE1D1','#000000','#202724','#202733','#30252C','#342C24','#272B27','#EAE7DE','#B5CDBD','#BBCDE4','#DEC0CA','#DEC9AD','#315E4B','#355674','#754859','#785A36','#68636F','#C6BDE0','#342C46']
styles = ['<resources>']
for i,c in enumerate(colors):
    styles += [f'<style name="PaletteBackground{i}" parent="SadeDialogBase">',f'<item name="android:colorBackground">{c}</item>',f'<item name="android:windowBackground">@drawable/palette_{i}</item>','</style>']
    (root/f'app/src/main/res/drawable/palette_{i}.xml').write_text(f'<shape xmlns:android="http://schemas.android.com/apk/res/android"><solid android:color="{c}"/><corners android:radius="20dp"/></shape>',encoding='utf-8')
    styles += [f'<style name="PaletteText{i}" parent="">']
    for attr in ['colorAccent','colorControlNormal','colorControlActivated','textColorPrimary','textColorSecondary','textColorHint','textColorAlertDialogListItem']:
        styles += [f'<item name="android:{attr}">{c}</item>']
    styles += [f'<item name="android:colorControlHighlight">#30{c[1:]}</item>','</style>']
styles += ['</resources>']
(root/'app/src/main/res/values/palette_styles.xml').write_text('\n'.join(styles),encoding='utf-8')
source = '''package com.sade.launcher

import android.content.SharedPreferences
import android.graphics.Color
import kotlin.math.pow

object Appearance {
    val colors = arrayOf(COLORS)
    val backgrounds = intArrayOf(BACKGROUNDS)
    val texts = intArrayOf(TEXTS)
    fun luminance(color: Int): Double {
        fun linear(channel: Int): Double {
            val v = channel / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return .2126 * linear(Color.red(color)) + .7152 * linear(Color.green(color)) + .0722 * linear(Color.blue(color))
    }
    fun contrast(a: Int, b: Int): Double {
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
    fun dark(paper: Int) = luminance(paper) < .179
    fun ink(prefs: SharedPreferences, paper: Int): Int {
        val selected = prefs.getString("ink", null)?.let { runCatching { Color.parseColor(it) }.getOrNull() }
        if (selected != null && contrast(selected, paper) >= 4.5) return selected
        return Color.parseColor(if (dark(paper)) "#EAE7DE" else "#272B27")
    }
}
'''.replace('COLORS', ','.join(json.dumps(c) for c in colors)).replace('BACKGROUNDS', ','.join(f'R.style.PaletteBackground{i}' for i in range(len(colors)))).replace('TEXTS', ','.join(f'R.style.PaletteText{i}' for i in range(len(colors))))
(root/'app/src/main/java/com/sade/launcher/Appearance.kt').write_text(source,encoding='utf-8')
for name in ['MainActivity','SettingsActivity','FocusActivity','FocusService','WindowSurface']:
    p=root/f'app/src/main/java/com/sade/launcher/{name}.kt'
    s=p.read_text(encoding='utf-8').replace('paper == Color.BLACK || paper == Color.parseColor("#202724")','Appearance.dark(paper)')
    s=s.replace('Color.parseColor(if (dark) "#EAE7DE" else "#272B27")','Appearance.ink(prefs, paper)')
    s=s.replace('if (Appearance.dark(paper)) Color.parseColor("#EAE7DE") else Color.parseColor("#272B27")','Appearance.ink(prefs, paper)')
    s=s.replace('ink != Color.parseColor("#EAE7DE")','!Appearance.dark(paper)')
    p.write_text(s,encoding='utf-8')
for code in ['tr','en']:
    p=root/('tools/strings-tr.json' if code=='tr' else 'tools/translations/en.json')
    data=json.loads(p.read_text(encoding='utf-8'))
    data['ui_024']='Saat & tarih' if code=='tr' else 'Clock & date'
    data['text_color']='Metin rengi' if code=='tr' else 'Text color'
    p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
