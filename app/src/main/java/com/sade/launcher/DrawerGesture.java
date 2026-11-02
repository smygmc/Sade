package com.sade.launcher;

/** Gesture decisions kept independent of Android views for regression checks. */
public final class DrawerGesture {
    private DrawerGesture() {}
    public static boolean capture(int page, float dy, boolean handle, boolean childCanScroll) {
        boolean direction = page == 0 ? dy < 0 : dy > 0;
        return direction && (handle || !childCanScroll);
    }
    public static int target(int page, float dy, float velocity, float extent, float density, int slop, boolean dragging) {
        boolean fling = Math.abs(velocity) > 700 * density && Math.abs(dy) > slop * 2;
        if (!dragging || (!fling && Math.abs(dy) < Math.min(extent * .16f, 96 * density))) return page;
        return Math.max(0, Math.min(1, page + (dy < 0 ? 1 : -1)));
    }
}
