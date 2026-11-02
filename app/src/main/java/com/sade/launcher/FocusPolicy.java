package com.sade.launcher;

/** Pure policy: local-clock windows, inclusive start and exclusive end. */
public final class FocusPolicy {
    public static final int MAX_ENTRY_SECONDS = 300;
    private FocusPolicy() {}
    public static final int ALLOW = 0, BLOCK_DAILY = 1, BLOCK_SCHEDULE = 2, PAUSE = 3;
    /** A completed pause/grace never overrides either kind of block. */
    public static int entryDecision(boolean scheduled, boolean reached, boolean blockAtLimit,
                                    boolean pauseEnabled, boolean pausePassed) {
        if (reached && blockAtLimit) return BLOCK_DAILY;
        if (scheduled) return BLOCK_SCHEDULE;
        if (pauseEnabled && !pausePassed) return PAUSE;
        return ALLOW;
    }
    public static boolean inWindow(int minute, int start, int end) {
        if (start == end) return true; // Explicit all-day window.
        return start < end ? minute >= start && minute < end : minute >= start || minute < end;
    }
    public static boolean limitReached(long usedMs, int limitMinutes) {
        return limitMinutes > 0 && usedMs >= limitMinutes * 60_000L;
    }
    public static boolean scheduledOnDay(int day, int minute, int start, int end, java.util.Set<Integer> days) {
        if (!inWindow(minute, start, end)) return false;
        // Overnight intervals belong to the day on which they START.
        int owner = start > end && minute < end ? (day == 1 ? 7 : day - 1) : day;
        return days.contains(owner);
    }
    public static int dailyLimit(int day, int standard, java.util.Map<Integer, Integer> overrides) {
        return overrides.getOrDefault(day, standard);
    }
    public static boolean scheduleBlocks(boolean enabled, boolean allDay, int day, int minute,
                                         int start, int end, java.util.Set<Integer> days) {
        return enabled && (allDay ? days.contains(day) : scheduledOnDay(day, minute, start, end, days));
    }
    public static int remainingSeconds(long now, long deadline) {
        return (int)Math.max(0, (deadline - now + 999) / 1000);
    }
}
