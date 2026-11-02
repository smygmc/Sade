package com.sade.launcher;

import java.util.HashMap;
import java.util.Map;

/** In-memory return grace, measured with elapsed realtime. No background timers. */
public final class EntryPauseGrace {
    public static final long WINDOW_MS = 10 * 60_000L;
    private final Map<String, Long> departures = new HashMap<>();

    public void leave(String app, boolean passed, long now) {
        prune(now);
        if (app == null) return;
        if (passed) departures.put(app, now);
        else departures.remove(app);
    }

    public boolean enter(String app, long now) {
        prune(now);
        Long left = departures.remove(app);
        return left != null && now >= left && now - left < WINDOW_MS;
    }

    public boolean hasGrace(String app, long now) {
        prune(now);
        Long left = departures.get(app);
        return left != null && now >= left && now - left < WINDOW_MS;
    }

    public void clear() { departures.clear(); }

    private void prune(long now) {
        departures.entrySet().removeIf(entry -> now < entry.getValue() || now - entry.getValue() >= WINDOW_MS);
    }
}
