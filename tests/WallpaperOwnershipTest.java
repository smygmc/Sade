import com.sade.launcher.WallpaperOwnership;
import static com.sade.launcher.WallpaperOwnership.Result.*;

public final class WallpaperOwnershipTest {
    static class Device implements WallpaperOwnership.Backend {
        int owned, current = 10, writes;
        String selected = "paper", wallpaper = "photo";
        boolean allowed = true, readFails, writeFails, externalDuringWrite;
        public int ownedId() { return owned; }
        public int currentId() { if (readFails) throw new SecurityException(); return current; }
        public boolean allowed() { return allowed; }
        public void saveOwnedId(int id) { owned = id; }
        public int setSelectedColor() {
            writes++;
            if (writeFails) return 0;
            wallpaper = selected;
            int result = ++current;
            if (externalDuringWrite) { current++; wallpaper = "external"; }
            return result;
        }
    }
    static int checks;
    static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
    public static void main(String[] args) {
        Device d = new Device();
        check(WallpaperOwnership.apply(d,false) == SKIPPED && d.writes == 0, "no consent leaves photo alone");
        check(WallpaperOwnership.apply(d,true) == APPLIED && d.owned == d.current, "explicit write establishes ownership");
        d.selected = "black";
        check(WallpaperOwnership.apply(d,false) == APPLIED && d.wallpaper.equals("black"), "owned color follows selection");
        d.current++; d.wallpaper = "new photo";
        int writes = d.writes;
        check(WallpaperOwnership.apply(d,false) == SKIPPED && d.writes == writes && d.owned == 0, "external photo revokes sync without writing");
        d.selected = "white";
        check(WallpaperOwnership.apply(d,false) == SKIPPED && d.wallpaper.equals("new photo"), "later colors still preserve photo");
        check(WallpaperOwnership.apply(d,true) == APPLIED && d.wallpaper.equals("white"), "explicit re-enable restores sync");
        d.readFails = true;
        writes = d.writes;
        check(WallpaperOwnership.apply(d,false) == FAILED && d.writes == writes && d.owned == 0, "unknown ownership must never write");
        d.readFails = false; d.writeFails = true;
        check(WallpaperOwnership.apply(d,true) == FAILED && d.owned == 0, "failed write cannot enable sync");
        d.writeFails = false; d.allowed = false;
        check(WallpaperOwnership.apply(d,true) == UNSUPPORTED && d.owned == 0, "device restriction leaves sync off");
        d.allowed = true; d.externalDuringWrite = true;
        WallpaperOwnership.apply(d,true);
        writes = d.writes;
        check(d.owned != d.current && WallpaperOwnership.apply(d,false) == SKIPPED && d.writes == writes,
            "never adopt an external wallpaper ID returned by a later query");
        System.out.println("Wallpaper ownership: " + checks + " checks passed");
    }
}
