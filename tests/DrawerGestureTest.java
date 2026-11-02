import com.sade.launcher.DrawerGesture;

public class DrawerGestureTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Check " + checks); }
    public static void main(String[] args) {
        check(DrawerGesture.capture(0, -80, false, false));
        check(!DrawerGesture.capture(0, 80, false, false));
        check(DrawerGesture.capture(1, 80, false, false));
        check(!DrawerGesture.capture(1, 80, false, true)); // Normal list scrolling stays in list.
        check(DrawerGesture.capture(1, 80, true, true)); // Handle closes even at bottom of list.
        check(!DrawerGesture.capture(1, -80, true, true));
        check(DrawerGesture.target(1, 110, 0, 1800, 1, 8, true) == 0); // Tablet: no huge drag required.
        check(DrawerGesture.target(1, 20, 0, 1800, 1, 8, true) == 1);
        check(DrawerGesture.target(1, 25, 900, 1800, 1, 8, true) == 0);
        check(DrawerGesture.target(0, -110, 0, 1800, 1, 8, true) == 1);
        check(DrawerGesture.target(0, -25, -900, 1800, 1, 8, true) == 1);
        check(DrawerGesture.target(1, 110, 900, 1800, 1, 8, false) == 1);
        System.out.println("Drawer gestures: " + checks + " checks passed.");
    }
}
