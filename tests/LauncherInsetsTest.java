import com.sade.launcher.LauncherInsets;

public class LauncherInsetsTest {
    private static int checks;
    private static void eq(int actual, int expected) {
        checks++; if (actual != expected) throw new AssertionError(actual + " != " + expected);
    }
    public static void main(String[] args) {
        // Returning Home from another app/PiP with stale keyboard data must not reserve space.
        eq(LauncherInsets.searchBottom(0, false, true, 800, 48), 0);
        eq(LauncherInsets.searchBottom(0, true, true, 800, 48), 0);
        eq(LauncherInsets.searchBottom(1, false, true, 800, 48), 0);
        eq(LauncherInsets.searchBottom(1, true, false, 800, 48), 0);
        // Only the active search gets the keyboard overlap, without double-counting navigation.
        eq(LauncherInsets.searchBottom(1, true, true, 800, 48), 752);
        eq(LauncherInsets.searchBottom(1, true, true, 400, 0), 400);
        eq(LauncherInsets.searchBottom(1, true, true, 0, 48), 0);
        eq(LauncherInsets.searchBottom(1, true, true, 20, 48), 0);
        System.out.println("Launcher insets: " + checks + " checks passed");
    }
}
