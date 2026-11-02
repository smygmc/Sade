package com.sade.launcher;

/** Ownership transaction, separate from Android I/O so external-change behavior can be tested. */
public final class WallpaperOwnership {
    public enum Result { APPLIED, SKIPPED, FAILED, UNSUPPORTED }
    public interface Backend {
        int ownedId();
        int currentId();
        boolean allowed();
        int setSelectedColor() throws Exception;
        void saveOwnedId(int id);
    }
    public static Result apply(Backend backend, boolean explicit) {
        try {
            int owned = backend.ownedId();
            if (!explicit && owned <= 0) return Result.SKIPPED;
            if (!explicit && backend.currentId() != owned) {
                backend.saveOwnedId(0);
                return Result.SKIPPED;
            }
            if (!backend.allowed()) {
                backend.saveOwnedId(0);
                return Result.UNSUPPORTED;
            }
            int id = backend.setSelectedColor();
            if (id <= 0) throw new IllegalStateException("Wallpaper write failed");
            backend.saveOwnedId(id);
            return Result.APPLIED;
        } catch (Exception failure) {
            try { backend.saveOwnedId(0); } catch (Exception ignored) { }
            return Result.FAILED;
        }
    }
}
