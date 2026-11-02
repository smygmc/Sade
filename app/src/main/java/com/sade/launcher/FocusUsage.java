package com.sade.launcher;

import java.util.HashMap;
import java.util.Map;

/** Foreground-only accounting. Monotonic time prevents clock edits adding usage. */
public final class FocusUsage {
    public String day;
    public final Map<String, Long> totals;
    public long sessionMs;
    private long lastElapsed;

    public FocusUsage(String day, Map<String, Long> totals, long elapsed, long wall) {
        this.day = day;
        this.totals = new HashMap<>(totals);
        lastElapsed = elapsed;
    }

    public void account(String pkg, boolean counting, long elapsed, long wall, String today, long midnight) {
        long delta = Math.max(0, elapsed - lastElapsed);
        boolean tracking = counting && pkg != null;
        long credit = delta;
        if (!day.equals(today)) {
            totals.clear();
            day = today;
            // On a date/time-zone change, credit at most real elapsed time since midnight.
            credit = Math.min(delta, Math.max(0, wall - midnight));
        }
        if (tracking) {
            totals.put(pkg, totals.getOrDefault(pkg, 0L) + credit);
            sessionMs += delta;
        }
        lastElapsed = elapsed;
    }

    public void resetSession() { sessionMs = 0; }
}
