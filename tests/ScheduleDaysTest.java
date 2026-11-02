import com.sade.launcher.FocusPolicy;
import java.util.Set;

public final class ScheduleDaysTest {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    static boolean blocked(boolean enabled, boolean allDay, int day, int minute, Set<Integer> days) {
        return FocusPolicy.scheduleBlocks(enabled, allDay, day, minute, 1320, 480, days);
    }
    public static void main(String[] args) {
        var monday = Set.of(1);
        check(blocked(true,true,1,0,monday), "Monday starts blocked at midnight");
        check(blocked(true,true,1,1439,monday), "Monday stays blocked until midnight");
        check(!blocked(true,true,2,0,monday), "all-day Monday does not spill into Tuesday");
        check(!blocked(true,true,7,720,monday), "Sunday remains free of schedule block");
        check(!blocked(false,true,1,720,monday), "disabled schedule does not block");
        check(!blocked(true,false,1,1319,monday), "timed block has not started");
        check(blocked(true,false,1,1320,monday), "timed block begins at selected time");
        check(blocked(true,false,2,479,monday), "overnight interval ends the following morning");
        check(!blocked(true,false,2,480,monday), "overnight end is exclusive");
        check(blocked(true,true,1,720,monday) && !Set.of(7).contains(1), "schedule applies even outside daily-limit days");
        check(!blocked(true,true,7,720,monday) && FocusPolicy.limitReached(60000,1), "daily limit remains independent of schedule");
        check(!blocked(true,true,1,720,Set.of()), "no selected days does not block");
        System.out.println("Schedule days: " + checks + " checks passed");
    }
}
