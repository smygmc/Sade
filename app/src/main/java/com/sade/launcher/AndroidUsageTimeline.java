package com.sade.launcher;

import java.util.*;

/** Replays Android activity events; totals are per package, never per group. */
public final class AndroidUsageTimeline {
    private final long start;
    private long cursor;
    private final Map<String, Set<String>> resumed = new HashMap<>();
    private final Map<String, Long> totals = new HashMap<>();
    public AndroidUsageTimeline(long start, long queryStart) { this.start = start; cursor = queryStart; }
    private void advance(long time) {
        long delta = Math.max(0, time - Math.max(start, cursor));
        for (String pkg : resumed.keySet()) totals.put(pkg, totals.getOrDefault(pkg, 0L) + delta);
        cursor = Math.max(cursor, time);
    }
    public void event(long time, int type, String pkg, String activity) {
        advance(time);
        String key = activity == null ? "" : activity;
        if (type == 1 && pkg != null) resumed.computeIfAbsent(pkg, p -> new HashSet<>()).add(key);
        else if ((type == 2 || type == 23) && pkg != null) {
            Set<String> activities = resumed.get(pkg);
            if (activities != null) { activities.remove(key); if (activities.isEmpty()) resumed.remove(pkg); }
        } else if (type == 16 || type == 17 || type == 26 || type == 27) resumed.clear();
    }
    public Map<String, Long> finish(long now) { advance(now); return new HashMap<>(totals); }
    public Set<String> foreground() { return new HashSet<>(resumed.keySet()); }
}
