package it.leonardo.antivirus;

/** I fatti osservati su un'app installata, senza alcun legame con Android: serve a poterli provare. */
public final class AppFacts {
    public final boolean fromStore;
    public final boolean accessibilityOn;
    public final boolean deviceAdmin;
    public final boolean notificationListener;
    public final boolean readsSms;
    public final boolean overlay;
    public final boolean canInstallApps;
    public final boolean allFilesAccess;
    public final boolean debuggable;
    public final boolean hasLauncher;
    public final int targetSdk;

    private AppFacts(Builder b) {
        fromStore = b.fromStore;
        accessibilityOn = b.accessibilityOn;
        deviceAdmin = b.deviceAdmin;
        notificationListener = b.notificationListener;
        readsSms = b.readsSms;
        overlay = b.overlay;
        canInstallApps = b.canInstallApps;
        allFilesAccess = b.allFilesAccess;
        debuggable = b.debuggable;
        hasLauncher = b.hasLauncher;
        targetSdk = b.targetSdk;
    }

    /** Valori di partenza: un'app tranquilla, da un negozio, con icona e costruita di recente. */
    public static final class Builder {
        private boolean fromStore = true;
        private boolean accessibilityOn;
        private boolean deviceAdmin;
        private boolean notificationListener;
        private boolean readsSms;
        private boolean overlay;
        private boolean canInstallApps;
        private boolean allFilesAccess;
        private boolean debuggable;
        private boolean hasLauncher = true;
        private int targetSdk = 34;

        public Builder fromStore(boolean v) { fromStore = v; return this; }
        public Builder accessibilityOn(boolean v) { accessibilityOn = v; return this; }
        public Builder deviceAdmin(boolean v) { deviceAdmin = v; return this; }
        public Builder notificationListener(boolean v) { notificationListener = v; return this; }
        public Builder readsSms(boolean v) { readsSms = v; return this; }
        public Builder overlay(boolean v) { overlay = v; return this; }
        public Builder canInstallApps(boolean v) { canInstallApps = v; return this; }
        public Builder allFilesAccess(boolean v) { allFilesAccess = v; return this; }
        public Builder debuggable(boolean v) { debuggable = v; return this; }
        public Builder hasLauncher(boolean v) { hasLauncher = v; return this; }
        public Builder targetSdk(int v) { targetSdk = v; return this; }

        public AppFacts build() {
            return new AppFacts(this);
        }
    }
}
