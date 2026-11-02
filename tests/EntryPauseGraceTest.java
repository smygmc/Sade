import com.sade.launcher.EntryPauseGrace;

public final class EntryPauseGraceTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        EntryPauseGrace grace = new EntryPauseGrace();
        check(!grace.enter("instagram", 0), "first opening requires pause");
        grace.leave("instagram", false, 1000);
        check(!grace.enter("instagram", 2000), "abandoned countdown gives no grace");
        grace.leave("instagram", true, 3000);
        check(grace.hasGrace("instagram", 4000), "launcher can inspect return grace");
        check(grace.hasGrace("instagram", 4500), "launcher inspection does not consume grace");
        check(!grace.hasGrace("youtube", 4500), "launcher grace remains app-specific");
        grace.leave(null, false, 4000);
        check(grace.enter("instagram", 5000), "brief Home or Recents visit preserves grace");
        check(!grace.enter("instagram", 6000), "return consumes departure, service retains passed state");
        grace.leave("instagram", true, 900_000);
        check(grace.enter("instagram", 1_499_999), "window begins at departure, not initial Continue");
        grace.leave("instagram", true, 1_500_000);
        check(!grace.hasGrace("instagram", 2_100_000), "launcher also respects the exact expiration");
        check(!grace.enter("instagram", 2_100_000), "exactly ten minutes requires a new pause");
        grace.leave("instagram", true, 3_000_000);
        check(!grace.enter("youtube", 3_000_001), "one app cannot grant another app grace");
        grace.leave("youtube", true, 3_100_000);
        check(grace.enter("instagram", 3_200_000), "app A departure is preserved across app B");
        check(grace.enter("youtube", 3_300_000), "app B keeps its independent grace");
        grace.leave("instagram", true, 4_000_000);
        grace.clear();
        check(!grace.enter("instagram", 4_000_001), "rule change revokes grace");
        grace.leave("instagram", true, 5_000_000);
        check(!new EntryPauseGrace().enter("instagram", 5_000_001), "service restart starts fresh");
        check(!grace.enter("instagram", 4_999_999), "invalid elapsed clock cannot extend grace");
        System.out.println("Entry pause grace: " + checks + " checks passed");
    }
}
