package com.sade.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

data class FocusGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val packages: Set<String> = emptySet(),
    val minutes: Int = 30,
    val blockAtLimit: Boolean = true,
    val message: String = "",
    val schedule: Boolean = false,
    val start: Int = 22 * 60,
    val end: Int = 8 * 60,
    val enabled: Boolean = true,
    val reminder: Int = 10,
    val days: Set<Int> = (1..7).toSet(),
    val dayLimits: Map<Int, Int> = emptyMap(),
    val waitBeforeOpen: Boolean = false,
    val waitSeconds: Int = 5,
    val scheduleDays: Set<Int> = days,
    val scheduleAllDay: Boolean = start == end,
    val limitMessage: String = ""
) {
    fun limitFor(day: Int) = FocusPolicy.dailyLimit(day, minutes, dayLimits)
}

class FocusStore(context: Context) {
    val prefs = context.getSharedPreferences("focus", Context.MODE_PRIVATE)
    fun groups(): List<FocusGroup> = runCatching {
        val array = JSONArray(prefs.getString("groups", "[]") ?: "[]")
        (0 until array.length()).map { index ->
            val o = array.getJSONObject(index)
            val p = o.getJSONArray("packages")
            FocusGroup(o.getString("id"), o.getString("name"),
                (0 until p.length()).map { p.getString(it) }.toSet(),
                o.optInt("minutes", 30).coerceIn(0, 1440), o.optBoolean("block", true),
                o.optString("message", "").takeUnless { it == "Biraz ara vermek ister misin? Telefonu ne için açtığını hatırla." } ?: "", o.optBoolean("schedule"),
                o.optInt("start", 1320).coerceIn(0, 1439), o.optInt("end", 480).coerceIn(0, 1439),
                o.optBoolean("enabled", true), o.optInt("reminder", 10).coerceIn(0, 1440),
                o.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.getInt(it) }.filter { it in 1..7 }.toSet() } ?: (1..7).toSet(),
                o.optJSONObject("dayLimits")?.let { limits -> limits.keys().asSequence().mapNotNull { key ->
                    val d = key.toIntOrNull(); val value = limits.optInt(key)
                    if (d != null && d in 1..7 && value in 1..1440) d to value else null
                }.toMap() } ?: emptyMap(), o.optBoolean("waitBeforeOpen"), o.optInt("waitSeconds", 5).coerceIn(1, FocusPolicy.MAX_ENTRY_SECONDS),
                (o.optJSONArray("scheduleDays") ?: o.optJSONArray("days"))?.let { a ->
                    (0 until a.length()).map { a.getInt(it) }.filter { it in 1..7 }.toSet()
                } ?: (1..7).toSet(), o.optBoolean("scheduleAllDay", o.optInt("start", 1320) == o.optInt("end", 480)), o.optString("limitMessage", ""))
        }
    }.getOrDefault(emptyList())

    fun save(groups: List<FocusGroup>) {
        val array = JSONArray()
        groups.forEach { g -> array.put(JSONObject().apply {
            put("id", g.id); put("name", g.name); put("packages", JSONArray(g.packages.toList()))
            put("minutes", g.minutes); put("block", g.blockAtLimit); put("message", g.message)
            put("limitMessage", g.limitMessage)
            put("schedule", g.schedule); put("start", g.start); put("end", g.end); put("enabled", g.enabled)
            put("reminder", g.reminder)
            put("days", JSONArray(g.days.sorted()))
            put("dayLimits", JSONObject(g.dayLimits.mapKeys { it.key.toString() }))
            put("waitBeforeOpen", g.waitBeforeOpen); put("waitSeconds", g.waitSeconds)
            put("scheduleDays", JSONArray(g.scheduleDays.sorted())); put("scheduleAllDay", g.scheduleAllDay)
        }) }
        prefs.edit().putString("groups", array.toString()).apply()
    }

    fun usage(day: String = LocalDate.now().toString()): MutableMap<String, Long> {
        if (prefs.getString("day", "") != day) return mutableMapOf()
        return runCatching {
            val o = JSONObject(prefs.getString("usage", "{}") ?: "{}")
            o.keys().asSequence().associateWith { o.getLong(it).coerceAtLeast(0) }.toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    fun persistUsage(day: String, usage: Map<String, Long>, warned: Set<String>) {
        prefs.edit().putString("day", day).putString("usage", JSONObject(usage).toString())
            .putStringSet("warned", warned.toSet()).apply()
    }
    fun warned(day: String): MutableSet<String> = if (prefs.getString("day", "") == day)
        prefs.getStringSet("warned", emptySet())!!.toMutableSet() else mutableSetOf()
}
