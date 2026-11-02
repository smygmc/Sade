package com.sade.launcher;

/** Keyboard space belongs to the focused app search, never to the Home viewport. */
public final class LauncherInsets {
    private LauncherInsets() {}
    public static int searchBottom(int page, boolean focused, boolean imeVisible, int imeBottom, int systemBottom) {
        if (page != 1 || !focused || !imeVisible) return 0;
        return Math.max(0, imeBottom - Math.max(0, systemBottom));
    }
}
