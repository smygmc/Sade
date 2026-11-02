import com.sade.launcher.FocusPolicy;
import com.sade.launcher.FocusUsage;
import java.util.Map;

/** Run with Java assertions unnecessary: every failed check throws. */
public final class FocusCoreTest {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(FocusPolicy.inWindow(22 * 60, 22 * 60, 8 * 60), "night start included");
        check(FocusPolicy.inWindow(0, 22 * 60, 8 * 60), "night crosses midnight");
        check(!FocusPolicy.inWindow(8 * 60, 22 * 60, 8 * 60), "night end excluded");
        check(!FocusPolicy.inWindow(12 * 60, 22 * 60, 8 * 60), "no daytime block");
        check(FocusPolicy.inWindow(10 * 60, 9 * 60, 17 * 60), "daytime interval");
        check(!FocusPolicy.inWindow(17 * 60, 9 * 60, 17 * 60), "daytime end excluded");
        check(FocusPolicy.inWindow(123, 0, 0), "equal times explicitly all day");
        check(!FocusPolicy.limitReached(9_000_000, 0), "zero disables limit");
        check(!FocusPolicy.limitReached(1_799_999, 30), "below daily limit");
        check(FocusPolicy.limitReached(1_800_000, 30), "exact daily limit");

        var usage = new FocusUsage("day1", Map.of(), 0, 0);
        usage.account("instagram", true, 300_000, 300_000, "day1", 0);
        check(usage.sessionMs == 300_000, "five-minute session");
        usage.resetSession();
        usage.account("youtube", true, 600_000, 600_000, "day1", 0);
        check(usage.totals.get("instagram") == 300_000, "independent Instagram total");
        check(usage.totals.get("youtube") == 300_000, "independent YouTube total");
        usage.resetSession();
        usage.account("instagram", true, 900_000, 900_000, "day1", 0);
        check(usage.sessionMs == 300_000, "return starts a new five-minute session");
        check(usage.totals.get("instagram") == 600_000, "daily usage survives exit");
        usage.account("instagram", false, 1_500_000, 1_500_000, "day1", 0);
        check(usage.totals.get("instagram") == 600_000, "overlay and screen off do not count");
        usage.account("instagram", true, 1_560_000, 9_999_999, "day1", 0);
        check(usage.totals.get("instagram") == 660_000, "clock jump credits real elapsed only");

        var midnight = new FocusUsage("yesterday", Map.of("instagram", 1_700_000L), 1000, 86_390_000);
        midnight.account("instagram", true, 21_000, 86_410_000, "today", 86_400_000);
        check(midnight.totals.get("instagram") == 10_000, "only post-midnight portion credited");
        check(midnight.sessionMs == 20_000, "session spans midnight");
        var restart = new FocusUsage("today", midnight.totals, 50_000, 86_440_000);
        check(restart.totals.get("instagram") == 10_000 && restart.sessionMs == 0, "daily restored, session fresh");
        var monday = java.util.Set.of(1);
        check(FocusPolicy.scheduledOnDay(1, 22*60, 22*60, 8*60, monday), "Monday overnight start");
        check(FocusPolicy.scheduledOnDay(2, 7*60, 22*60, 8*60, monday), "Monday interval continues Tuesday");
        check(!FocusPolicy.scheduledOnDay(2, 8*60, 22*60, 8*60, monday), "Tuesday overnight end");
        check(!FocusPolicy.scheduledOnDay(2, 22*60, 22*60, 8*60, monday), "No new Tuesday interval");
        check(FocusPolicy.scheduledOnDay(1, 7*60, 22*60, 8*60, java.util.Set.of(7)), "Sunday crosses into Monday");
        check(!FocusPolicy.scheduledOnDay(2, 12*60, 0, 0, monday), "All-day only on selected day");
        check(FocusPolicy.dailyLimit(1,30,Map.of(6,60,7,60)) == 30, "weekday default");
        check(FocusPolicy.dailyLimit(6,30,Map.of(6,60,7,60)) == 60, "Saturday override");
        check(FocusPolicy.dailyLimit(7,30,Map.of(6,60,7,60)) == 60, "Sunday override");
        check(FocusPolicy.remainingSeconds(1000,6000) == 5, "entry pause starts at five");
        check(FocusPolicy.remainingSeconds(5999,6000) == 1, "cannot continue a millisecond early");
        check(FocusPolicy.remainingSeconds(6000,6000) == 0, "continue at deadline");
        check(FocusPolicy.remainingSeconds(9000,6000) == 0, "no negative countdown");
        System.out.println("Focus core: 33 checks passed");
    }
}
