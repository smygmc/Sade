import com.sade.launcher.AndroidUsageTimeline;
import com.sade.launcher.FocusPolicy;
import java.util.Map;

public class AndroidUsageTimelineTest {
    private static int checks;
    private static void eq(long actual, long expected) { checks++; if (actual != expected) throw new AssertionError(actual + " != " + expected); }
    public static void main(String[] args) {
        AndroidUsageTimeline t = new AndroidUsageTimeline(0, 0);
        t.event(0, 1, "other", "A");
        t.event(120_000, 2, "other", "A");
        t.event(120_000, 1, "instagram", "A");
        Map<String, Long> total = t.finish(840_000);
        eq(total.get("other"), 120_000); eq(total.get("instagram"), 720_000);
        eq(FocusPolicy.limitReached(total.get("instagram"), 10) ? 1 : 0, 1);
        eq(FocusPolicy.limitReached(total.get("other"), 10) ? 1 : 0, 0);
        // Multiple activities in one package contribute a union, not duplicate time.
        t = new AndroidUsageTimeline(0, 0);
        t.event(0, 1, "app", "A"); t.event(10, 1, "app", "B");
        t.event(20, 2, "app", "A"); t.event(30, 2, "app", "B");
        eq(t.finish(100).get("app"), 30);
        // A duplicate resume does not reset the session.
        t = new AndroidUsageTimeline(0, 0);
        t.event(0, 1, "app", "A"); t.event(20, 1, "app", "A");
        eq(t.finish(40).get("app"), 40);
        // Midnight clips yesterday's time, without losing the open activity.
        t = new AndroidUsageTimeline(100, 0);
        t.event(50, 1, "app", "A"); t.event(150, 2, "app", "A");
        eq(t.finish(200).get("app"), 50);
        for (int stop : new int[]{16, 17, 26, 27}) {
            t = new AndroidUsageTimeline(0, 0); t.event(0, 1, "app", "A");
            t.event(40, stop, null, null); eq(t.finish(200).get("app"), 40);
            eq(t.foreground().size(), 0);
        }
        // Stale pause for another activity cannot terminate the current one.
        t = new AndroidUsageTimeline(0, 0); t.event(0, 1, "app", "B");
        t.event(10, 2, "app", "A"); eq(t.finish(50).get("app"), 50);
        // Two visible/resumed packages retain independent totals.
        t.event(50, 1, "second", "A");
        total = t.finish(70); eq(total.get("app"), 70); eq(total.get("second"), 20);
        System.out.println("AndroidUsageTimelineTest: " + checks + " checks passed");
    }
}
