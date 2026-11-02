import com.sade.launcher.FocusPolicy;

public final class EntryDecisionTest {
    private static int checks;
    private static void check(boolean value, String reason) {
        checks++; if (!value) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        for (boolean pause : new boolean[]{false, true}) for (boolean passed : new boolean[]{false, true}) {
            check(FocusPolicy.entryDecision(false, true, true, pause, passed) == FocusPolicy.BLOCK_DAILY,
                "Daily block must win over countdown/grace");
            check(FocusPolicy.entryDecision(true, false, true, pause, passed) == FocusPolicy.BLOCK_SCHEDULE,
                "Schedule block must win over countdown/grace");
            check(FocusPolicy.entryDecision(true, true, true, pause, passed) == FocusPolicy.BLOCK_DAILY,
                "Overlapping blocks keep the custom daily message");
        }
        check(FocusPolicy.entryDecision(false, false, true, true, false) == FocusPolicy.PAUSE, "Pause only when unblocked");
        check(FocusPolicy.entryDecision(false, false, true, true, true) == FocusPolicy.ALLOW, "Valid Continue");
        check(FocusPolicy.entryDecision(false, true, false, false, false) == FocusPolicy.ALLOW, "Warning-only setting is respected");
        check(FocusPolicy.entryDecision(false, FocusPolicy.limitReached(1_799_999L, 30), true, true, false) == FocusPolicy.PAUSE,
            "Just below YouTube's 30-minute limit");
        check(FocusPolicy.entryDecision(false, FocusPolicy.limitReached(1_800_000L, 30), true, true, true) == FocusPolicy.BLOCK_DAILY,
            "At Continue, a reached limit still blocks even with a completed pause");
        check(FocusPolicy.entryDecision(true, false, true, true, true) == FocusPolicy.BLOCK_SCHEDULE,
            "Schedule starting during countdown must block Continue");
        System.out.println("Entry decisions: " + checks + " checks passed");
    }
}
